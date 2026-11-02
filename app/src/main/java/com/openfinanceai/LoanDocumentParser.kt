package com.openfinanceai

internal data class LoanDocumentScheduleRow(
    val installmentNumber: Int,
    val openingPrincipal: Long,
    val principal: Long,
    val interest: Long,
    val installment: Long
)

internal data class LoanDocumentExtraction(
    val lender: String? = null,
    val loanNumber: String? = null,
    val borrowerName: String? = null,
    val principal: Long? = null,
    val emi: Long? = null,
    val annualInterestRate: Double? = null,
    val apr: Double? = null,
    val tenureMonths: Int? = null,
    val repaymentStartDate: String? = null,
    val processingFee: Long? = null,
    val totalInterest: Long? = null,
    val declaredTotalRepayment: Long? = null,
    val netDisbursedAmount: Long? = null,
    val prepaymentCharge: String? = null,
    val foreclosureCharge: String? = null,
    val bounceCharge: String? = null,
    val scheduleRows: List<LoanDocumentScheduleRow> = emptyList(),
    val illustrativeSchedule: Boolean = false,
    val warnings: List<String> = emptyList()
)

/** Conservative, source-preserving parser. It never calculates missing lender figures. */
internal object LoanDocumentParser {
    private val amountToken = Regex("""([0-9][0-9,]*(?:\.[0-9]{1,2})?)""")

    fun parse(sourceText: String): LoanDocumentExtraction {
        val text = sourceText.replace('\u00A0', ' ').replace('\u200B', ' ')
        val lines = text.lines().map { it.trim().replace(Regex("""[ \t]+"""), " ") }
        fun number(raw: String?): Long? = raw?.replace(",", "")?.toDoubleOrNull()
            ?.takeIf { it >= 0.0 && it <= Long.MAX_VALUE.toDouble() }
            ?.let { kotlin.math.round(it).toLong() }
        fun amountOnLine(label: Regex): Long? {
            for (i in lines.indices) {
                val match = label.find(lines[i]) ?: continue
                val tail = lines[i].substring(match.range.last + 1)
                amountToken.findAll(tail).lastOrNull()?.let { return number(it.groupValues[1]) }
                val standalone = Regex("""^(?:₹|Rs\.?|INR)?\s*[0-9][0-9,]*(?:\.[0-9]{1,2})?\s*$""", RegexOption.IGNORE_CASE)
                val previous = lines.take(i).lastOrNull { it.isNotBlank() }
                if (previous != null && standalone.matches(previous)) {
                    amountToken.find(previous)?.let { return number(it.groupValues[1]) }
                }
                val next = lines.drop(i + 1).firstOrNull { it.isNotBlank() }
                if (next != null && standalone.matches(next)) {
                    amountToken.find(next)?.let { return number(it.groupValues[1]) }
                }
            }
            return null
        }
        fun directAmount(pattern: String): Long? =
            Regex(pattern, setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))
                .find(text)?.groupValues?.getOrNull(1)?.let(::number)
        fun directDecimal(pattern: String): Double? =
            Regex(pattern, setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))
                .find(text)?.groupValues?.getOrNull(1)?.replace(",", "")?.toDoubleOrNull()

        val lender = Regex("""(?im)^\s*Lender\s*:\s*(.+?)\s*$""").find(text)?.groupValues?.get(1)?.trim()
            ?: Regex("""(?im)^Name of the Regulated Entity\s*:\s*(.+?)\s*$""").find(text)?.groupValues?.get(1)?.trim()
        val loanNumber = Regex("""(?i)(?:Loan\s*ID|Loan proposal\s*/\s*account\s*No\.?|Loan account\s*(?:number|no\.?))\s*[:.]?\s*([A-Z0-9/-]{5,40})""")
            .find(text)?.groupValues?.get(1)
        val borrowerName = Regex("""(?im)^\s*(?:Dear\s+|[0-9]{1,2}\s+Name\s+of\s+the\s+Borrower\s*\(\s*["']Borrower["']\s*\)\s+|Applicant\s+Name\s*[:：]?\s*)([A-Z][A-Z .'-]{2,80})\s*$""")
            .find(text)?.groupValues?.get(1)?.trim()
        val principal = directAmount("""Total\s+Loan\s+Amount\s*\(\s*Principal\s+Amount\s*\)\s*[:\-]?\s*(?:INR|Rs\.?|₹)?\s*([0-9][0-9,]*(?:\.[0-9]{1,2})?)""")
            ?: directAmount("""Sanctioned\s+Loan\s+amount\s*\(in\s+Rupees\)\s*([0-9][0-9,]*(?:\.[0-9]{1,2})?)""")
        val emi = amountOnLine(Regex("""Amount\s+of\s+EMI\s+Payable"""))
            ?: Regex("""(?is)Amount\s+of\s+each\s+EPI.{0,350}?([0-9][0-9,]*(?:\.[0-9]{1,2})?)\s+nos\.?\s+of\s+EPIs""")
                .find(text)?.groupValues?.get(1)?.let(::number)
        val fixedRate = directDecimal("""Fixed\s+Rate\s+of\s+Interest\s*[:\-]?\s*([0-9]+(?:\.[0-9]{1,4})?)""")
            ?: directDecimal("""Interest\s+Rate\s+per\s+annum\s*\(%\)\s*([0-9]+(?:\.[0-9]{1,4})?)""")
        val apr = directDecimal("""Annual\s+Percentage\s+Rate\s*\(APR\)\s*\(%\)\s*([0-9]+(?:\.[0-9]{1,4})?)""")
            ?: directDecimal("""Annual\s+Percentage\s+Rate\s*[:(].{0,40}?([0-9]+(?:\.[0-9]{1,4})?)""")
        val tenure = directAmount("""Tenure\s*\(in\s+months\)\s*([0-9]{1,3})""")
            ?: directAmount("""Loan\s+term\s*\(months\)\s*([0-9]{1,3})""")
        val repaymentStartDate = Regex("""(?i)Commencement\s+of\s+repayments?.{0,250}?(\d{4}-\d{2}-\d{2})""", RegexOption.DOT_MATCHES_ALL)
            .find(text)?.groupValues?.get(1)
            ?: Regex("""(?i)(?:repayment|first\s*EMI)\s*(?:start|commencement)?\s*date\s*[:\-]?\s*(\d{4}-\d{2}-\d{2})""")
                .find(text)?.groupValues?.get(1)
            ?: Regex("""(?i)Monthly\s+\d{1,3}\s+[0-9][0-9,]*(?:\.[0-9]{1,2})?\s+(\d{4}-\d{2}-\d{2})""")
                .find(text)?.groupValues?.get(1)
        val processingFee = amountOnLine(Regex("""Processing\s+Fees?\s*\(INR\)"""))
            ?: directAmount("""Processing\s+fees?.{0,100}?([0-9][0-9,]*(?:\.[0-9]{1,2})?)""")
        val totalInterest = directAmount("""Total\s+Interest\s+Amount\s+to\s+be\s+charged[^\n]*\n\s*\d{1,3}\s+([0-9][0-9,]*(?:\.[0-9]{1,2})?)""")
            ?: directAmount("""Total\s+Interest\s+Amount\s+to\s+be\s+charged[^\n]*?([0-9][0-9,]*(?:\.[0-9]{1,2})?)""")
        val declaredTotal = directAmount("""Total\s+amount\s+to\s+be\s+paid\s+by\s+the\s+borrower.{0,250}?\(in\s+Rupees\)\s*([0-9][0-9,]*(?:\.[0-9]{1,2})?)""")
        val netDisbursed = directAmount("""Net\s+Disbursed\s+amount\s*\(INR\)\s*([0-9][0-9,]*(?:\.[0-9]{1,2})?)""")
        val prepaymentLabelIndex = lines.indexOfFirst { Regex("""Prepayment\s+Charges\s*\(INR\)""", RegexOption.IGNORE_CASE).containsMatchIn(it) }
        val prepaymentCharge = if (prepaymentLabelIndex >= 0) {
            val valueLine = lines.take(prepaymentLabelIndex).takeLast(3).lastOrNull { Regex("""\bNA\b|[0-9][0-9,]*(?:\.[0-9]{1,2})?""", RegexOption.IGNORE_CASE).containsMatchIn(it) }
                ?: lines.drop(prepaymentLabelIndex + 1).firstOrNull { it.isNotBlank() }
            valueLine?.let { Regex("""\bNA\b|[0-9][0-9,]*(?:\.[0-9]{1,2})?""", RegexOption.IGNORE_CASE).find(it)?.value }
        } else null
        val foreclosureCharge = Regex("""(?im)^\s*(?:\([ivx]+\)\s*)?Foreclosure\s+charges,?\s*if\s+applicable\s*(.+?)\s*$""")
            .find(text)?.groupValues?.get(1)?.trim()
        val bounceCharge = run {
            val index = lines.indexOfFirst { Regex("""^\s*Bounce\s+Charges?\b""", RegexOption.IGNORE_CASE).containsMatchIn(it) }
            if (index < 0) null else {
                val first = lines[index].replace(Regex("""^.*?Bounce\s+Charges?\b""", RegexOption.IGNORE_CASE), "").trim()
                val details = mutableListOf<String>()
                if (first.isNotBlank()) details += first
                for (line in lines.drop(index + 1)) {
                    if (line.isBlank() || Regex("""^(?:Overdue\s+Amount|Late\s+Payment\s+Charges|Penal\s+Charges)\b""", RegexOption.IGNORE_CASE).containsMatchIn(line)) break
                    details += line
                }
                details.joinToString(" ").trim().takeIf { it.isNotBlank() }
            }
        }

        val scheduleHeader = Regex("""(?i)Illustrative\s+Repayment\s+Schedule""").find(text)
        val scheduleText = scheduleHeader?.let { text.substring(it.range.last + 1) }.orEmpty()
        val rowPattern = Regex(
            """(?m)^\s*(\d{1,3})\s+([0-9][0-9,]*(?:\.[0-9]{1,2})?)\s+([0-9][0-9,]*(?:\.[0-9]{1,2})?)\s+([0-9][0-9,]*(?:\.[0-9]{1,2})?)\s+([0-9][0-9,]*(?:\.[0-9]{1,2})?)\s*$"""
        )
        val rows = rowPattern.findAll(scheduleText).mapNotNull { m ->
            val values = (2..5).map { number(m.groupValues[it]) ?: return@mapNotNull null }
            val no = m.groupValues[1].toIntOrNull() ?: return@mapNotNull null
            LoanDocumentScheduleRow(no, values[0], values[1], values[2], values[3])
        }.toList()

        val warnings = mutableListOf<String>()
        if (rows.isEmpty() && tenure != null) {
            warnings += "No detailed monthly principal/interest schedule was extracted. MoneyLog will not invent a monthly split from the EMI and interest rate."
        }
        if (scheduleHeader != null && rows.isEmpty()) {
            warnings += "A repayment schedule heading was found, but no complete five-column instalment rows could be read. No schedule rows were invented."
        }
        if (tenure != null && rows.isNotEmpty() && rows.size != tenure.toInt()) {
            warnings += "The document states " + tenure + " instalments, but only " + rows.size + " complete schedule rows were extracted."
        }
        if (rows.isNotEmpty()) {
            if (rows.map { it.installmentNumber } != (1..rows.size).toList()) {
                warnings += "Repayment schedule instalment numbers are missing, duplicated or out of order."
            }
            rows.filter { it.principal + it.interest != it.installment }.forEach {
                warnings += "Instalment " + it.installmentNumber + ": principal plus interest is " +
                    (it.principal + it.interest) + ", but the document's instalment column says " + it.installment + "."
            }
            rows.zipWithNext().forEach { (current, next) ->
                val expectedNextOpening = current.openingPrincipal - current.principal
                if (expectedNextOpening != next.openingPrincipal) {
                    warnings += "Balance mismatch between instalments " + current.installmentNumber + " and " +
                        next.installmentNumber + ": expected opening principal " + expectedNextOpening +
                        ", document shows " + next.openingPrincipal + "."
                }
            }
            val schedulePrincipal = rows.sumOf { it.principal }
            val scheduleInterest = rows.sumOf { it.interest }
            if (principal != null && schedulePrincipal != principal) {
                warnings += "Schedule principal total " + schedulePrincipal + " does not match loan principal " + principal + "."
            }
            if (totalInterest != null && scheduleInterest != totalInterest) {
                warnings += "Schedule interest total " + scheduleInterest + " does not match stated total interest " + totalInterest + "."
            }
            val finalClosingBalance = rows.last().openingPrincipal - rows.last().principal
            if (finalClosingBalance != 0L) {
                warnings += "The final extracted schedule row leaves principal balance " + finalClosingBalance + "; the schedule does not close to zero."
            }
            if (emi != null && rows.any { it.installment != emi }) {
                warnings += "Some schedule instalment amounts differ from the stated EMI " + emi + "; review the first and final instalments."
            }
            if (processingFee != null && rows.first().installment - rows.first().principal - rows.first().interest == processingFee) {
                warnings += "The first instalment includes an extra amount equal to the processing fee; the document does not explain whether this fee is collected with that instalment."
            }
        }
        if (prepaymentCharge.equals("NA", ignoreCase = true) && !foreclosureCharge.isNullOrBlank()) {
            warnings += "The sanction section lists prepayment charges as NA, while the KFS lists foreclosure charges as " + foreclosureCharge + "; confirm which term applies."
        }
        if (principal != null && totalInterest != null && processingFee != null && declaredTotal != null) {
            val calculated = principal + totalInterest + processingFee
            if (calculated != declaredTotal) {
                warnings += "APR total repayment mismatch: principal + stated interest + processing fee is " +
                    calculated + ", but the document declares " + declaredTotal + "."
            }
        }
        val illustrative = scheduleHeader != null && Regex("""(?i)illustrative\s+purposes\s+only""").containsMatchIn(scheduleText)
        if (illustrative) warnings += "This is an illustrative schedule, not the lender's final disbursement-based repayment schedule."
        return LoanDocumentExtraction(
            lender = lender,
            loanNumber = loanNumber,
            borrowerName = borrowerName,
            principal = principal,
            emi = emi,
            annualInterestRate = fixedRate,
            apr = apr,
            tenureMonths = tenure?.toInt(),
            repaymentStartDate = repaymentStartDate,
            processingFee = processingFee,
            totalInterest = totalInterest,
            declaredTotalRepayment = declaredTotal,
            netDisbursedAmount = netDisbursed,
            prepaymentCharge = prepaymentCharge,
            foreclosureCharge = foreclosureCharge,
            bounceCharge = bounceCharge,
            scheduleRows = rows,
            illustrativeSchedule = illustrative,
            warnings = warnings.distinct()
        )
    }
}
