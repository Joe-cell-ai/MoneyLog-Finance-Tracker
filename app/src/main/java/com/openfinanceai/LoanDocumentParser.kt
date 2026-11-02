package com.openfinanceai

/** Every monetary field is stored as integer paise, never rounded rupees. */
internal data class LoanDocumentScheduleRow(
    val installmentNumber: Int,
    val openingPrincipal: Long,
    val principal: Long,
    val interest: Long,
    val installment: Long,
    val annualRate: Double? = null,
    val dueDate: String? = null,
    val closingPrincipal: Long? = null
)

/** Every monetary field is stored as integer paise, never rounded rupees. */
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
    val repaymentEndDate: String? = null,
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
            .replace("\uFB03", "ffi").replace("\uFB01", "fi").replace("\uFB02", "fl").replace("\uFB00", "ff")
        val lines = text.lines().map { it.trim().replace(Regex("""[ \t]+"""), " ") }
        fun number(raw: String?): Long? = raw?.let(LoanMoney::parsePaise)
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

        fun directInteger(pattern: String): Long? =
            Regex(pattern, setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))
                .find(text)?.groupValues?.getOrNull(1)?.replace(",", "")?.toLongOrNull()
        fun directDecimal(pattern: String): Double? =
            Regex(pattern, setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))
                .find(text)?.groupValues?.getOrNull(1)?.replace(",", "")?.toDoubleOrNull()

        val lender = Regex("""(?im)^\s*Lender\s*:\s*(.+?)\s*$""").find(text)?.groupValues?.get(1)?.trim()
            ?: Regex("""(?im)^Name of the Regulated Entity\s*:\s*(.+?)\s*$""").find(text)?.groupValues?.get(1)?.trim()
            ?: Regex("""(?im)^Corporate\s*&\s*Registered\s+office\s*:\s*([A-Z][A-Z &]+Limited)\b""").find(text)?.groupValues?.get(1)?.trim()
        val loanNumber = Regex("""(?i)(?:Loan\s*ID|Loan proposal\s*/\s*account\s*No\.?|Loan account\s*(?:number|no\.?)|Agreement\s*(?:No\.?|Number))\s*[:.]?\s*([A-Z0-9/-]{5,40})""")
            .find(text)?.groupValues?.get(1)
        val borrowerName = Regex("""(?im)^\s*(?:Dear\s+|Customer\s+Name\s*[:：]?\s*|[0-9]{1,2}\s+Name\s+of\s+the\s+Borrower\s*\(\s*["']Borrower["']\s*\)\s+|Applicant\s+Name\s*[:：]?\s*)([A-Z][A-Z .'-]{2,80})\s*$""")
            .find(text)?.groupValues?.get(1)?.trim()
        val principal = directAmount("""Total\s+Loan\s+Amount\s*\(\s*Principal\s+Amount\s*\)\s*[:\-]?\s*(?:INR|Rs\.?|₹)?\s*([0-9][0-9,]*(?:\.[0-9]{1,2})?)""")
            ?: directAmount("""Sanctioned\s+Loan\s+amount\s*\(in\s+Rupees\)\s*([0-9][0-9,]*(?:\.[0-9]{1,2})?)""")
            ?: directAmount("""Loan\s+Amount\s*\(\s*INR\s*\)\s*:\s*(?:INR|Rs\.?|₹)?\s*([0-9][0-9,]*(?:\.[0-9]{1,2})?)""")
            ?: directAmount("""Loan\s+Amount\s*:\s*(?:INR|Rs\.?|₹)?\s*([0-9][0-9,]*(?:\.[0-9]{1,2})?)""")
            ?: directAmount("""Amount\s+Financed\s*:\s*(?:INR|Rs\.?|₹)?\s*([0-9][0-9,]*(?:\.[0-9]{1,2})?)""")
        val emi = amountOnLine(Regex("""Amount\s+of\s+EMI\s+Payable"""))
            ?: amountOnLine(Regex("""Monthly\s+EMI"""))
            ?: amountOnLine(Regex("""EMI\s+Amount"""))
            ?: Regex("""(?is)Amount\s+of\s+each\s+EPI.{0,350}?([0-9][0-9,]*(?:\.[0-9]{1,2})?)\s+nos\.?\s+of\s+EPIs""")
                .find(text)?.groupValues?.get(1)?.let(::number)
        val fixedRate = directDecimal("""Fixed\s+Rate\s+of\s+Interest\s*[:\-]?\s*([0-9]+(?:\.[0-9]{1,4})?)""")
            ?: directDecimal("""Interest\s+Rate\s*:\s*([0-9]+(?:\.[0-9]{1,4})?)\s*%""")
            ?: directDecimal("""Interest\s+Rate\s+per\s+annum\s*\(%\)\s*([0-9]+(?:\.[0-9]{1,4})?)""")
            ?: directDecimal("""ROI\s*\(%\)\s*:?\s*([0-9]+(?:\.[0-9]{1,4})?)""")
        val apr = directDecimal("""Annual\s+Percentage\s+Rate\s*\(APR\)\s*\(%\)\s*([0-9]+(?:\.[0-9]{1,4})?)""")
            ?: directDecimal("""Annual\s+Percentage\s+Rate\s*[:(].{0,40}?([0-9]+(?:\.[0-9]{1,4})?)""")
        val tenure = directInteger("""Tenure\s*\(in\s+months\)\s*([0-9]{1,3})""")
            ?: directInteger("""Loan\s+term\s*\(months\)\s*([0-9]{1,3})""")
            ?: directInteger("""Tenure\s*:\s*([0-9]{1,3})\s*Months?""")
            ?: directInteger("""Total\s+Tenure\s*:?\s*([0-9]{1,3})""")
        val repaymentStartDate = Regex("""(?i)Commencement\s+of\s+repayments?.{0,250}?(\d{4}-\d{2}-\d{2})""", RegexOption.DOT_MATCHES_ALL)
            .find(text)?.groupValues?.get(1)
            ?: Regex("""(?i)(?:repayment|first\s*EMI)\s*(?:start|commencement)?\s*date\s*[:\-]?\s*(\d{4}-\d{2}-\d{2})""")
                .find(text)?.groupValues?.get(1)
            ?: Regex("""(?i)Monthly\s+\d{1,3}\s+[0-9][0-9,]*(?:\.[0-9]{1,2})?\s+(\d{4}-\d{2}-\d{2})""")
                .find(text)?.groupValues?.get(1)
            ?: Regex("""(?i)(?:Repayment|Installment)\s+Start\s+Date\s*[:\-]?\s*(\d{1,2}[-/][A-Za-z0-9]{2,4}[-/]\d{2,4})""")
                .find(text)?.groupValues?.get(1)?.let { raw ->
                    val patterns = listOf("d-MMM-yy", "dd-MMM-yy", "d-MMM-yyyy", "dd-MMM-yyyy", "d/MM/yy", "dd/MM/yy", "d/MM/yyyy", "dd/MM/yyyy")
                    patterns.firstNotNullOfOrNull { pattern ->
                        runCatching { java.time.LocalDate.parse(raw, java.time.format.DateTimeFormatterBuilder()
                            .parseCaseInsensitive().appendPattern(pattern).toFormatter(java.util.Locale.ENGLISH)).toString() }.getOrNull()
                    }
                }
        val repaymentEndDate = Regex("""(?i)(?:repayment|installment|instalment)\s+end\s+date\s*[:\-]?\s*(\d{1,2}[-/][A-Za-z0-9]{2,4}[-/]\d{2,4}|\d{4}-\d{2}-\d{2})""")
            .find(text)?.groupValues?.get(1)?.let { raw ->
                val patterns = listOf("d-MMM-yy", "dd-MMM-yy", "d-MMM-yyyy", "dd-MMM-yyyy", "d/MM/yy", "dd/MM/yy", "d/MM/yyyy", "dd/MM/yyyy", "yyyy-MM-dd")
                patterns.firstNotNullOfOrNull { pattern ->
                    runCatching {
                        java.time.LocalDate.parse(raw, java.time.format.DateTimeFormatterBuilder()
                            .parseCaseInsensitive().appendPattern(pattern).toFormatter(java.util.Locale.ENGLISH)).toString()
                    }.getOrNull()
                }
            }
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
            val index = lines.indexOfLast { Regex("""^\s*Bounce\s+Charges?\b""", RegexOption.IGNORE_CASE).containsMatchIn(it) }
            if (index < 0) null else {
                val first = lines[index].replace(Regex("""^.*?Bounce\s+Charges?\b""", RegexOption.IGNORE_CASE), "").trim()
                val details = mutableListOf<String>()
                if (first.isNotBlank()) details += first
                for (line in lines.drop(index + 1)) {
                    if (line.isBlank() || Regex("""^(?:Key\s+Facts\s+Statement|Computation\s+of\s+APR|Total\s+Interest\s+Amount|Total\s+amount\s+to\s+be\s+paid|Illustrative\s+Repayment\s+Schedule|Repayment\s+Schedule|Instalment\s+No\.?|Installment\s+No\.?|Please\s+Note|Overdue\s+Amount|Late\s+Payment\s+Charges|Penal\s+Charges|Foreclosure\s+Charges|Prepayment\s+Charges|Processing\s+Fees)\b""", RegexOption.IGNORE_CASE).containsMatchIn(line)) break
                    details += line
                }
                details.joinToString(" ").trim().takeIf { it.isNotBlank() }
            }
        }

        val scheduleHeader = Regex("""(?im)^\s*(?:[0-9]{1,2}\s+)?(?:Official\s+)?(?:Illustrative\s+)?(?:Repayment\s+Schedule|Amortization\s+(?:Timeline(?:\s+Statement)?|Schedule))\s*$""").find(text)
        val scheduleText = scheduleHeader?.let { text.substring(it.range.last + 1) }.orEmpty()
            .replace("₹", "").replace(Regex("""(?i)\bRs\.?\s*"""), "")
        val scheduleHeaderLines = scheduleText.lines().take(30).joinToString(" ")
        val hasScheduleRateColumn = Regex("""(?i)(?:interest\s+rate|rate\s+of\s+interest|rate\s*\(%\)|\bROI\b)""").containsMatchIn(scheduleHeaderLines)
        // Column order differs between lenders. Read the component order from the table header
        // instead of assuming every lender prints Principal before Interest.
        val componentHeader = Regex("""(?i)(?:installment|instalment|emi|epi)[^\n]*""")
            .find(scheduleHeaderLines)?.value.orEmpty()
        val principalColumnIndex = componentHeader.indexOf("principal", ignoreCase = true)
        val interestColumnIndex = componentHeader.indexOf("interest", ignoreCase = true)
        val principalBeforeInterest = principalColumnIndex < 0 || interestColumnIndex < 0 ||
            principalColumnIndex < interestColumnIndex
        val scheduleDatePattern = Regex("""(?i)\b(\d{4}-\d{2}-\d{2}|\d{1,2}[/-]\d{1,2}[/-]\d{2,4}|\d{1,2}[- ](?:Jan|Feb|Mar|Apr|May|Jun|Jul|Aug|Sep|Oct|Nov|Dec)[a-z]*[- ,]+\d{2,4})\b""")
        fun normalizedScheduleDate(raw: String): String? {
            val value = raw.trim()
            val patterns = listOf("yyyy-MM-dd", "d MMMM yyyy", "d MMM yyyy", "d-MMM-yyyy", "d-MMM-yy", "dd-MMM-yy", "d/MM/yyyy", "dd/MM/yyyy", "d/MM/yy", "dd/MM/yy", "d-MM-yyyy", "dd-MM-yyyy")
            return patterns.firstNotNullOfOrNull { pattern ->
                runCatching {
                    java.time.LocalDate.parse(value, java.time.format.DateTimeFormatterBuilder()
                        .parseCaseInsensitive().appendPattern(pattern).toFormatter(java.util.Locale.ENGLISH)).toString()
                }.getOrNull()
            }
        }
        val rowPattern = Regex("""(?m)^\s*(\d{1,3})\s+(.+?)\s*$""")
        fun exactScheduleAmount(raw: String): Long? = LoanMoney.parsePaise(raw)
        // All lender amounts are parsed directly to paise. Interest-rate tokens are
        // handled separately and never enter the monetary parser.
        val rows = rowPattern.findAll(scheduleText).mapNotNull { match ->
            val no = match.groupValues[1].toIntOrNull() ?: return@mapNotNull null
            val rawValues = match.groupValues[2]
            val dateMatch = scheduleDatePattern.find(rawValues)
            val dueDate = dateMatch?.groupValues?.get(1)?.let(::normalizedScheduleDate)
            if (dateMatch != null && dueDate == null) return@mapNotNull null
            val numericSource = if (dateMatch != null) rawValues.removeRange(dateMatch.range) else rawValues
            val tokens = Regex("""[0-9][0-9,]*(?:\.[0-9]{1,4})?""").findAll(numericSource).map { it.value }.toList()
            if (hasScheduleRateColumn && tokens.size == 5) {
                val rate = tokens[1].replace(",", "").toDoubleOrNull() ?: return@mapNotNull null
                val amounts = listOf(0, 2, 3, 4).map { exactScheduleAmount(tokens[it]) ?: return@mapNotNull null }
                if (rate !in 0.0..100.0) return@mapNotNull null
                LoanDocumentScheduleRow(no, amounts[0], amounts[1], amounts[2], amounts[3], rate, dueDate)
            } else if (!hasScheduleRateColumn && tokens.size == 5) {
                // Standard bank statements: opening principal, installment, principal, interest, closing principal.
                val amounts = tokens.map { exactScheduleAmount(it) ?: return@mapNotNull null }
                val opening = amounts[0]
                val installment = amounts[1]
                val principalPaid = if (principalBeforeInterest) amounts[2] else amounts[3]
                val interestPaid = if (principalBeforeInterest) amounts[3] else amounts[2]
                val closing = amounts[4]
                if (opening - principalPaid != closing || principalPaid + interestPaid != installment) return@mapNotNull null
                LoanDocumentScheduleRow(no, opening, principalPaid, interestPaid, installment, null, dueDate, closing)
            } else if (!hasScheduleRateColumn && tokens.size == 4) {
                val amounts = tokens.map { exactScheduleAmount(it) ?: return@mapNotNull null }
                LoanDocumentScheduleRow(no, amounts[0], amounts[1], amounts[2], amounts[3], null, dueDate)
            } else null
        }.toList()

        val warnings = mutableListOf<String>()
        if (rows.any { it.dueDate != null } && rows.any { it.dueDate == null }) {
            warnings += "Only some repayment rows contain readable due dates. MoneyLog will not invent dates for the remaining rows."
        }
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
            emi = emi ?: rows.firstOrNull()?.installment,
            annualInterestRate = fixedRate,
            apr = apr,
            tenureMonths = tenure?.toInt() ?: rows.size.takeIf { it > 0 },
            repaymentStartDate = repaymentStartDate,
            repaymentEndDate = repaymentEndDate,
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
