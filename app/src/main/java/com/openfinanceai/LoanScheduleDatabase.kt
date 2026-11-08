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
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.UUID

/** Monetary columns are integer paise. Migration 1->2 converts the old whole-rupee values. */
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

/** Monetary columns are integer paise. */
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

/** Permanent loan identity. Amounts retain the legacy loan-list unit until loan screens are migrated. */
@Entity(tableName = "loans")
internal data class LoanRecord(
    @PrimaryKey val loanId: String,
    val sortOrder: Int,
    val lender: String,
    val balance: Long,
    val emi: Long,
    val due: String,
    val annualRate: Double,
    val originalPrincipal: Long,
    val paymentsMade: Long,
    val totalPayments: Long,
    val borrowerName: String,
    val agreementNumber: String,
    val loanNumber: String,
    val startDate: String
)

@Dao
internal interface LoanScheduleDao {
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertLoans(rows: List<LoanRecord>)

    @Query("SELECT * FROM loans ORDER BY sortOrder, loanId")
    suspend fun allLoans(): List<LoanRecord>

    @Query("DELETE FROM loans")
    suspend fun deleteAllLoans()

    /** lenderKey must already be trimmed and lower-cased; ambiguous lenders must not be passed here. */
    @Query("UPDATE loan_document_imports SET loanId = :newId WHERE lower(trim(loanId)) = :lenderKey")
    suspend fun rekeyDocuments(lenderKey: String, newId: String)

    @Query("UPDATE loan_schedule_installments SET loanId = :newId WHERE lower(trim(loanId)) = :lenderKey")
    suspend fun rekeyInstallments(lenderKey: String, newId: String)

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

@Database(entities = [LoanDocumentRecord::class, LoanScheduleRecord::class, LoanRecord::class], version = 3, exportSchema = false)
internal abstract class LoanScheduleDatabase : RoomDatabase() {
    abstract fun loanScheduleDao(): LoanScheduleDao

    companion object {
        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                listOf("principal", "declaredInterest", "declaredRepayment").forEach { column ->
                    db.execSQL("UPDATE loan_document_imports SET $column = $column * 100 WHERE $column IS NOT NULL")
                }
                listOf("openingBalance", "installmentAmount", "principal", "interest", "closingBalance").forEach { column ->
                    db.execSQL("UPDATE loan_schedule_installments SET $column = $column * 100 WHERE $column IS NOT NULL")
                }
            }
        }

        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `loans` (" +
                        "`loanId` TEXT NOT NULL, `sortOrder` INTEGER NOT NULL, `lender` TEXT NOT NULL, " +
                        "`balance` INTEGER NOT NULL, `emi` INTEGER NOT NULL, `due` TEXT NOT NULL, " +
                        "`annualRate` REAL NOT NULL, `originalPrincipal` INTEGER NOT NULL, " +
                        "`paymentsMade` INTEGER NOT NULL, `totalPayments` INTEGER NOT NULL, " +
                        "`borrowerName` TEXT NOT NULL, `agreementNumber` TEXT NOT NULL, " +
                        "`loanNumber` TEXT NOT NULL, `startDate` TEXT NOT NULL, PRIMARY KEY(`loanId`))"
                )
            }
        }

        @Volatile private var instance: LoanScheduleDatabase? = null
        fun get(context: Context): LoanScheduleDatabase = instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(
                context.applicationContext,
                LoanScheduleDatabase::class.java,
                "moneylog_loan_schedules.db"
            ).addMigrations(MIGRATION_1_2, MIGRATION_2_3).build().also { instance = it }
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
            LoanScheduleRecord(
                id, loanId, row.installmentNumber, row.dueDate, row.openingBalance,
                row.installmentAmount, row.principal, row.interest, row.closingBalance,
                sourcePageByInstallment[row.installmentNumber], sourceTextByInstallment[row.installmentNumber]
            )
        }
        dao.replaceVerifiedSchedule(document, entities)
        id
    }
}
