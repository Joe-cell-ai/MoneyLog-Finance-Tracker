package com.openfinanceai

internal data class FinancialScheduleRow(
    val installmentNumber: Int,
    val dueDate: String?,
    val openingBalance: Long?,
    val installmentAmount: Long,
    val principal: Long,
    val interest: Long,
    val closingBalance: Long?
)

internal data class FinancialValidationResult(
    val accepted: Boolean,
    val errors: List<String>,
    val warnings: List<String>,
    val principalTotal: Long,
    val interestTotal: Long,
    val installmentTotal: Long
)

/** Deterministic reconciliation. No missing financial value is ever estimated here. */
internal object LoanFinancialValidator {
    fun validate(
        rows: List<FinancialScheduleRow>,
        declaredTenure: Int? = null,
        declaredPrincipal: Long? = null,
        declaredInterest: Long? = null,
        declaredRepayment: Long? = null,
        processingFee: Long? = null,
        toleranceRupees: Long = 1L
    ): FinancialValidationResult {
        val errors = mutableListOf<String>()
        val warnings = mutableListOf<String>()
        if (rows.isEmpty()) errors += "No repayment rows were extracted."
        if (rows.size > 600) errors += "Schedule exceeds the supported 600 instalments."
        if (rows.map { it.installmentNumber } != (1..rows.size).toList()) errors += "Instalment numbers are missing, duplicated or out of order."
        if (declaredTenure != null && rows.size != declaredTenure) errors += "Extracted ${rows.size} rows but the document declares $declaredTenure instalments."
        if (rows.any { it.installmentAmount <= 0L || it.principal < 0L || it.interest < 0L }) errors += "A row contains a zero/negative instalment or negative principal/interest."
        rows.forEachIndexed { index, row ->
            if (row.principal + row.interest != row.installmentAmount) {
                errors += "Instalment ${row.installmentNumber}: principal + interest does not equal the instalment amount."
            }
            if (row.openingBalance != null && row.closingBalance != null &&
                row.openingBalance - row.principal != row.closingBalance) {
                errors += "Instalment ${row.installmentNumber}: opening balance minus principal does not equal closing balance."
            }
            if (index > 0) {
                val previous = rows[index - 1]
                if (previous.closingBalance != null && row.openingBalance != null &&
                    previous.closingBalance != row.openingBalance) {
                    errors += "Balance continuity breaks between instalments ${previous.installmentNumber} and ${row.installmentNumber}."
                }
                if (previous.dueDate != null && row.dueDate != null && row.dueDate <= previous.dueDate) {
                    errors += "Due dates are not strictly increasing at instalment ${row.installmentNumber}."
                }
            }
        }
        val principal = rows.sumOf { it.principal }
        val interest = rows.sumOf { it.interest }
        val repayment = rows.sumOf { it.installmentAmount }
        fun differs(a: Long, b: Long) = kotlin.math.abs(a - b) > toleranceRupees
        if (declaredPrincipal != null && differs(principal, declaredPrincipal)) errors += "Principal total $principal does not reconcile with declared principal $declaredPrincipal."
        if (declaredInterest != null && differs(interest, declaredInterest)) errors += "Interest total $interest does not reconcile with declared interest $declaredInterest."
        if (declaredRepayment != null && differs(repayment, declaredRepayment)) {
            val includesFee = processingFee != null && !differs(repayment + processingFee, declaredRepayment)
            if (includesFee) warnings += "Declared repayment includes the separately stated processing fee; schedule instalments reconcile before that fee."
            else errors += "Instalment total $repayment does not reconcile with declared repayment $declaredRepayment."
        }
        if (rows.any { it.openingBalance == null || it.closingBalance == null }) warnings += "Opening/closing balance is not available for every row; balance-chain validation is partial."
        if (rows.any { it.dueDate == null }) warnings += "Some due dates are missing; no dates have been generated."
        return FinancialValidationResult(errors.isEmpty(), errors.distinct(), warnings.distinct(), principal, interest, repayment)
    }
}
