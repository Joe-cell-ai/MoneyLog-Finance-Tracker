package com.openfinanceai

import android.content.Context
import android.content.SharedPreferences
import androidx.room.withTransaction
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.io.IOException

internal sealed class LoanIdMigrationResult {
    object AlreadyDone : LoanIdMigrationResult()
    object NothingToMigrate : LoanIdMigrationResult()
    data class Migrated(val count: Int, val ambiguousLenders: Set<String>) : LoanIdMigrationResult()
    data class Failed(val reason: String) : LoanIdMigrationResult()
}

/**
 * Creates a verified pre-migration snapshot and copies the legacy loan list into Room.
 * Legacy preferences are deliberately retained. This runner is not wired to app startup yet:
 * callers must not switch reads to Room until schedule/status migration is implemented and verified.
 */
internal object LoanIdMigrator {
    private const val PREFS = "open_finance_data"
    const val RECORDS_IMPORTED_FLAG = "loan_identity_records_v1_done"
    const val LENDER_MAP_KEY = "loan_id_by_lender_v1"
    private const val BACKUP_FILE = "moneylog_loan_migration_backup_v1.json"
    private val BACKUP_PREFIXES = listOf(
        "bank_schedule_", "loan_document_metadata_", "emi_status_", "emi_amount_", "emi_rescheduled_", "emi_paid_"
    )

    suspend fun run(context: Context): LoanIdMigrationResult = withContext(Dispatchers.IO) {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        if (prefs.getBoolean(RECORDS_IMPORTED_FLAG, false)) return@withContext LoanIdMigrationResult.AlreadyDone

        val raw = prefs.getString("loans", null)
        val legacy = if (raw == null) emptyList()
        else LoanIdMigration.parseLegacy(raw)
            ?: return@withContext LoanIdMigrationResult.Failed("Saved loan data could not be read. Nothing was changed.")

        if (legacy.isEmpty()) {
            val saved = prefs.edit().putBoolean(RECORDS_IMPORTED_FLAG, true).commit()
            return@withContext if (saved) LoanIdMigrationResult.NothingToMigrate
            else LoanIdMigrationResult.Failed("Could not save migration state. Nothing was changed.")
        }

        try {
            writeAndVerifyBackup(context, prefs)
        } catch (e: Exception) {
            return@withContext LoanIdMigrationResult.Failed("Backup failed, so migration was not started: ${e.message}")
        }

        val plan = LoanIdMigration.plan(legacy)
        val db = LoanScheduleDatabase.get(context)
        val dao = db.loanScheduleDao()
        try {
            db.withTransaction {
                dao.insertLoans(plan.loans.map { it.toRecord() })

                // Ambiguous lender keys are intentionally not re-keyed.
                plan.lenderToLoanId.forEach { (lenderKey, loanId) ->
                    dao.rekeyDocuments(lenderKey, loanId)
                    dao.rekeyInstallments(lenderKey, loanId)
                }

                val errors = LoanIdMigration.verify(plan, dao.allLoans())
                if (errors.isNotEmpty()) throw IllegalStateException(errors.joinToString("; "))
            }
        } catch (e: Exception) {
            return@withContext LoanIdMigrationResult.Failed("Room transaction rolled back; legacy preferences are untouched: ${e.message}")
        }

        val lenderMap = JSONObject().also { json ->
            plan.lenderToLoanId.forEach { (key, value) -> json.put(key, value) }
        }
        val saved = prefs.edit()
            .putString(LENDER_MAP_KEY, lenderMap.toString())
            .putBoolean(DONE_FLAG, true)
            .commit()

        if (!saved) {
            return@withContext LoanIdMigrationResult.Failed(
                "Room loan rows were stored, but the completion flag failed. Retry will verify and replace these rows."
            )
        }
        LoanIdMigrationResult.Migrated(plan.loans.size, plan.ambiguousLenders)
    }

    private fun writeAndVerifyBackup(context: Context, prefs: SharedPreferences) {
        val target = File(context.filesDir, BACKUP_FILE)
        val expected = JSONObject()
        prefs.all.forEach { (key, value) ->
            if (key == "loans" || BACKUP_PREFIXES.any { key.startsWith(it) }) expected.put(key, value)
        }

        if (target.exists()) {
            val existingText = target.readText()
            val existing = JSONObject(existingText)
            if (existingText != expected.toString() || existing.length() != expected.length()) {
                throw IOException("An earlier migration backup exists but does not match current legacy data")
            }
            return
        }

        val tmp = File(context.filesDir, "$BACKUP_FILE.tmp")
        FileOutputStream(tmp).use { stream ->
            stream.write(expected.toString().toByteArray(Charsets.UTF_8))
            stream.fd.sync()
        }
        if (!tmp.renameTo(target)) {
            tmp.delete()
            throw IOException("Could not finalise backup file")
        }

        val writtenText = target.readText()
        val verified = JSONObject(writtenText)
        if (writtenText != expected.toString() || verified.length() != expected.length()) {
            target.delete()
            throw IOException("Backup verification failed")
        }
        expected.keys().forEach { key ->
            if (!verified.has(key)) {
                target.delete()
                throw IOException("Backup is missing key $key")
            }
        }
    }
}
