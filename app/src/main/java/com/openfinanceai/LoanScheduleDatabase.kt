package com.openfinanceai

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Index
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.Transaction
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.UUID

@Entity(tableName = "loan_document_imports", indices = [Index("loanId")])
internal data class LoanDocumentRecord(
    @PrimaryKey val documentId: String,
    val loanId: String,
    val sourceName: String,
    val importedAtEpochMs: Long,
    val lender: String?,
    val borrowerName: String?,
    val agreementNumber: String?,
    val principal: Long?,
    val annualRatePercent: Double?,
    val aprPercent: Double?,
    val tenureMonths: Int?,
    val repaymentStartDate: String?,
    val repaymentEndDate: String?,
    val declaredInterest: Long?,
    val declaredRepayment: Long?,
    val extractionEngine: String,
    val verified: Boolean
)

@Entity(
    tableName = "loan_schedule_installments",
    primaryKeys = ["documentId", "installmentNumber"],
    indices = [Index("loanId"), Index("dueDate")]
)
internal data class LoanScheduleRecord(
    val documentId: String,
    val loanId: String,
    val installmentNumber: Int,
    val dueDate: String?,
    val openingBalance: Long?,
    val installmentAmount: Long,
    val principal: Long,
    val interest: Long,
    val closingBalance: Long?,
    val sourcePage: Int?,
    val sourceText: String?
)

@Dao
internal interface LoanScheduleDao {
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertDocument(record: LoanDocumentRecord)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertInstallments(rows: List<LoanScheduleRecord>)

    @Query("SELECT * FROM loan_document_imports WHERE loanId = :loanId ORDER BY importedAtEpochMs DESC LIMIT 1")
    suspend fun latestDocument(loanId: String): LoanDocumentRecord?

    @Query("SELECT * FROM loan_schedule_installments WHERE loanId = :loanId AND documentId = :documentId ORDER BY installmentNumber")
    suspend fun installments(loanId: String, documentId: String): List<LoanScheduleRecord>

    @Query("SELECT COALESCE(SUM(installmentAmount), 0) FROM loan_schedule_installments WHERE loanId = :loanId AND documentId = :documentId AND dueDate >= :fromDate")
    suspend fun scheduledAmountFrom(loanId: String, documentId: String, fromDate: String): Long

    @Query("DELETE FROM loan_schedule_installments WHERE loanId = :loanId")
    suspend fun deleteLoanSchedules(loanId: String)

    @Query("DELETE FROM loan_document_imports WHERE loanId = :loanId")
    suspend fun deleteLoanDocuments(loanId: String)

    @Transaction
    suspend fun replaceVerifiedSchedule(document: LoanDocumentRecord, rows: List<LoanScheduleRecord>) {
        require(document.verified) { "Unverified documents cannot be saved." }
        require(rows.isNotEmpty() && rows.all { it.documentId == document.documentId && it.loanId == document.loanId })
        deleteLoanSchedules(document.loanId)
        deleteLoanDocuments(document.loanId)
        insertDocument(document)
        insertInstallments(rows)
    }
}

@Database(entities = [LoanDocumentRecord::class, LoanScheduleRecord::class], version = 1, exportSchema = false)
internal abstract class LoanScheduleDatabase : RoomDatabase() {
    abstract fun loanScheduleDao(): LoanScheduleDao

    companion object {
        @Volatile private var instance: LoanScheduleDatabase? = null
        fun get(context: Context): LoanScheduleDatabase = instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(context.applicationContext, LoanScheduleDatabase::class.java, "moneylog_loan_schedules.db")
                .build().also { instance = it }
        }
    }
}

/** Persists only a fully reconciled schedule. Failed imports leave the previous schedule untouched. */
internal class VerifiedLoanScheduleRepository(private val dao: LoanScheduleDao) {
    suspend fun save(
        loanId: String,
        sourceName: String,
        extraction: LoanDocumentExtraction,
        rows: List<FinancialScheduleRow>,
        validation: FinancialValidationResult,
        sourcePageByInstallment: Map<Int, Int> = emptyMap(),
        sourceTextByInstallment: Map<Int, String> = emptyMap()
    ): String = withContext(Dispatchers.IO) {
        require(loanId.isNotBlank()) { "Loan identifier is required." }
        require(validation.accepted) { validation.errors.joinToString(" ") }
        require(rows.isNotEmpty()) { "Cannot save an empty schedule." }
        val id = UUID.randomUUID().toString()
        val document = LoanDocumentRecord(
            documentId = id, loanId = loanId, sourceName = sourceName,
            importedAtEpochMs = System.currentTimeMillis(), lender = extraction.lender,
            borrowerName = extraction.borrowerName, agreementNumber = extraction.loanNumber,
            principal = extraction.principal, annualRatePercent = extraction.annualInterestRate,
            aprPercent = extraction.apr, tenureMonths = extraction.tenureMonths,
            repaymentStartDate = extraction.repaymentStartDate, repaymentEndDate = extraction.repaymentEndDate,
            declaredInterest = extraction.totalInterest, declaredRepayment = extraction.declaredTotalRepayment,
            extractionEngine = "PDFBOX_MLKIT_VALIDATED", verified = true
        )
        val entities = rows.map { row ->
            LoanScheduleRecord(id, loanId, row.installmentNumber, row.dueDate, row.openingBalance,
                row.installmentAmount, row.principal, row.interest, row.closingBalance,
                sourcePageByInstallment[row.installmentNumber], sourceTextByInstallment[row.installmentNumber])
        }
        dao.replaceVerifiedSchedule(document, entities)
        id
    }
}
