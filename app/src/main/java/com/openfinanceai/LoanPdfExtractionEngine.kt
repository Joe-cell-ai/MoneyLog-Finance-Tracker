package com.openfinanceai

import android.content.Context
import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.text.PDFTextStripper
import kotlinx.coroutines.suspendCancellableCoroutine
import java.io.File
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

internal data class LoanPdfPageText(
    val pageNumber: Int,
    val text: String,
    val extractionMethod: String,
    val lowConfidence: Boolean
)

internal data class LoanPdfExtractionResult(
    val pages: List<LoanPdfPageText>,
    val combinedText: String,
    val usedOcr: Boolean,
    val warnings: List<String>
)

/**
 * Text-first PDF extraction with page-level OCR fallback for scanned/image-only pages.
 * All processing is local to the device. Original PDFs are not uploaded.
 */
internal class LoanPdfExtractionEngine(private val context: Context) {
    suspend fun extract(uri: Uri): LoanPdfExtractionResult {
        val file = File.createTempFile("moneylog-loan-", ".pdf", context.cacheDir)
        try {
            context.contentResolver.openInputStream(uri)?.use { input ->
                file.outputStream().use { output -> input.copyTo(output) }
            } ?: error("Cannot open the selected PDF.")
            require(file.length() in 8L..(80L * 1024L * 1024L)) { "PDF is empty or exceeds the 80 MB import limit." }
            val pages = mutableListOf<LoanPdfPageText>()
            PDFBoxResourceLoader.init(context.applicationContext)
            PDDocument.load(file).use { pdf ->
                require(!pdf.isEncrypted) { "Password-protected PDFs are not supported. Unlock the PDF first." }
                require(pdf.numberOfPages in 1..300) { "PDF page count must be between 1 and 300." }
                // One PDFBox traversal avoids rescanning the document once per page.
                val pageTexts = PDFTextStripper().apply {
                    setSortByPosition(true)
                    setPageEnd("\u000C")
                }.getText(pdf).split('\u000C').take(pdf.numberOfPages)
                val rendererDescriptor = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
                rendererDescriptor.use { descriptor ->
                    PdfRenderer(descriptor).use { renderer ->
                        for (pageIndex in 0 until pdf.numberOfPages) {
                            val text = pageTexts.getOrNull(pageIndex).orEmpty().trim()
                            if (!LoanPdfTextQuality.needsOcr(text)) {
                                pages += LoanPdfPageText(pageIndex + 1, text, "PDF_TEXT", false)
                            } else {
                                val bitmap = renderer.openPage(pageIndex).use { page ->
                                    val scale = 1.5f
                                    Bitmap.createBitmap(
                                        (page.width * scale).toInt().coerceAtLeast(1),
                                        (page.height * scale).toInt().coerceAtLeast(1),
                                        Bitmap.Config.ARGB_8888
                                    ).also {
                                        page.render(it, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                                    }
                                }
                                val recognized = try {
                                    recognize(bitmap)
                                } finally {
                                    bitmap.recycle()
                                }
                                val selectedText = LoanPdfTextQuality.chooseBest(text, recognized)
                                val selectedByOcr = selectedText == recognized && (recognized != text || text.isBlank())
                                pages += LoanPdfPageText(
                                    pageIndex + 1, selectedText,
                                    if (selectedByOcr) "ML_KIT_OCR" else "PDF_TEXT",
                                    LoanPdfTextQuality.score(selectedText) < 20
                                )
                            }
                        }
                    }
                }
            }
            val warnings = pages.filter { it.lowConfidence }.map {
                "Page ${it.pageNumber}: OCR returned very little readable text. Review this page before importing."
            }
            return LoanPdfExtractionResult(pages, pages.joinToString("\n\n") { it.text }, pages.any { it.extractionMethod == "ML_KIT_OCR" }, warnings)
        } finally {
            file.delete()
        }
    }

    private suspend fun recognize(bitmap: Bitmap): String = suspendCancellableCoroutine { continuation ->
        val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
        recognizer.process(InputImage.fromBitmap(bitmap, 0))
            .addOnSuccessListener { result ->
                recognizer.close()
                if (continuation.isActive) continuation.resume(result.text)
            }
            .addOnFailureListener { error ->
                recognizer.close()
                if (continuation.isActive) continuation.resumeWithException(error)
            }
    }
}
