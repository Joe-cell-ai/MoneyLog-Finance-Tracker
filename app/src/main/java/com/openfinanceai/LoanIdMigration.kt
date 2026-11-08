package com.openfinanceai

import org.json.JSONArray
import java.util.Locale
import java.util.UUID

internal data class LegacyLoan(
    val lender: String, val balance: Long, val emi: Long, val due: String, val annualRate: Double,
    val originalPrincipal: Long, val paymentsMade: Long, val totalPayments: Long, val borrowerName: String,
    val agreementNumber: String, val loanNumber: String, val startDate: String
)

internal data class PlannedLoan(val loanId: String, val position: Int, val legacy: LegacyLoan) {
    fun toRecord() = LoanRecord(
        loanId = loanId, sortOrder = position, lender = legacy.lender, balance = legacy.balance, emi = legacy.emi,
        due = legacy.due, annualRate = legacy.annualRate, originalPrincipal = legacy.originalPrincipal,
        paymentsMade = legacy.paymentsMade, totalPayments = legacy.totalPayments, borrowerName = legacy.borrowerName,
        agreementNumber = legacy.agreementNumber, loanNumber = legacy.loanNumber, startDate = legacy.startDate
    )
}

/** Only unambiguous lender keys can be re-keyed automatically. */
internal data class LoanIdPlan(
    val loans: List<PlannedLoan>, val lenderToLoanId: Map<String, String>, val ambiguousLenders: Set<String>
)

internal object LoanIdMigration {
    fun lenderKey(lender: String): String = lender.trim().lowercase(Locale.ROOT)

    /** Mirrors the defaults in the legacy loadLoans() function. */
    fun parseLegacy(raw: String?): List<LegacyLoan>? {
        if (raw == null) return null
        return try {
            val array = JSONArray(raw)
            (0 until array.length()).map { index ->
                val item = array.getJSONObject(index)
                val balance = item.getLong("balance")
                LegacyLoan(
                    lender = item.getString("lender"),
                    balance = balance,
                    emi = item.getLong("emi"),
                    due = item.optString("due", "Not set"),
                    annualRate = item.optDouble("annualRate", 0.0),
                    originalPrincipal = item.optLong("originalPrincipal", balance),
                    paymentsMade = item.optLong("paymentsMade", 0L),
                    totalPayments = item.optLong("totalPayments", 0L),
                    borrowerName = item.optString("borrowerName", ""),
                    agreementNumber = item.optString("agreementNumber", item.optString("loanNumber", "")),
                    loanNumber = item.optString("loanNumber", ""),
                    startDate = item.optString("startDate", item.optString("due", ""))
                )
            }
        } catch (_: Exception) {
            null
        }
    }

    /** Stable across retries while the legacy list and order remain unchanged. */
    fun deterministicId(position: Int, loan: LegacyLoan): String = UUID.nameUUIDFromBytes(
        ("moneylog-loan-v1|" + position + "|" + lenderKey(loan.lender) + "|" +
            loan.loanNumber.trim() + "|" + loan.agreementNumber.trim() + "|" + loan.originalPrincipal)
            .toByteArray(Charsets.UTF_8)
    ).toString()

    fun plan(legacy: List<LegacyLoan>): LoanIdPlan {
        val loans = legacy.mapIndexed { index, loan -> PlannedLoan(deterministicId(index, loan), index, loan) }
        val grouped = loans.groupBy { lenderKey(it.legacy.lender) }
        val uniqueMappings = LinkedHashMap<String, String>()
        val ambiguous = LinkedHashSet<String>()
        grouped.forEach { (key, matches) ->
            if (matches.size == 1) uniqueMappings[key] = matches.single().loanId else ambiguous += key
        }
        return LoanIdPlan(loans, uniqueMappings, ambiguous)
    }

    fun verify(plan: LoanIdPlan, stored: List<LoanRecord>): List<String> {
        val errors = mutableListOf<String>()
        if (stored.size != plan.loans.size) errors += "Expected ${plan.loans.size} loans but found ${stored.size}"
        val byId = stored.associateBy { it.loanId }
        plan.loans.forEach { planned ->
            val saved = byId[planned.loanId]
            if (saved == null) errors += "Missing loan ${planned.loanId} (${planned.legacy.lender})"
            else if (saved != planned.toRecord()) errors += "Stored values differ for ${planned.legacy.lender}"
        }
        return errors
    }
}
