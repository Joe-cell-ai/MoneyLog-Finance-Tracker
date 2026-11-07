package com.openfinanceai

import java.util.Locale

/** One suggested change, shown to the user before anything is saved. */
internal data class RecategorizeProposal(
    val transaction: Transaction,
    val newCategory: String,
    val kind: SmsPaymentKind
)

/**
 * Re-categorises previously imported SMS transactions that were left as "Other".
 *
 * Safety rules:
 *  - Only SMS-imported, non-loan expenses currently labelled "Other" are considered.
 *  - A transaction whose category was chosen by the user ([CategoryOrigin.MANUAL]) is never touched.
 *  - Only the category (and its origin marker) changes. Title, date, amount, direction, source and
 *    notification key are preserved, so duplicate detection keeps working.
 *  - Nothing is applied unless the caller passes the proposals the user approved.
 *
 * Limitation: rows imported before origin tracking existed have [CategoryOrigin.UNKNOWN]. If the user
 * had deliberately set such a row to "Other", it is indistinguishable from an automatic "Other", so it
 * can appear in the preview. That is why the preview lets the user untick any row.
 */
internal object SmsRecategorizer {
    private val smsSources = setOf("SMS auto import", "SMS import")

    fun isEligible(transaction: Transaction): Boolean =
        transaction.source in smsSources &&
            !transaction.income &&
            transaction.category.trim().equals(SmsCategories.OTHER, ignoreCase = true) &&
            transaction.categoryOrigin != CategoryOrigin.MANUAL &&
            transaction.transactionKind.isBlank() &&
            transaction.loanLender.isBlank()

    /** Notification keys look like "sms-import:<sender>:<timestamp>:<bodyHash>". */
    fun senderFromKey(notificationKey: String): String {
        if (!notificationKey.startsWith("sms-import:")) return ""
        val rest = notificationKey.removePrefix("sms-import:")
        val withoutHash = rest.substringBeforeLast(':', "")
        return withoutHash.substringBeforeLast(':', "")
    }

    /** Importers title transactions "To <payee>" / "From <payee>"; titles without a payee return null. */
    fun payeeFromTitle(title: String): String? {
        val trimmed = title.trim()
        val payee = Regex("""(?i)^(?:to|from)\s+(.+)$""").find(trimmed)?.groupValues?.getOrNull(1)?.trim()
        return payee?.takeIf { it.isNotBlank() }
    }

    fun propose(transactions: List<Transaction>, ownAccountKeys: Set<String> = emptySet()): List<RecategorizeProposal> =
        transactions.filter(::isEligible).mapNotNull { transaction ->
            val result = SmsCategorizer.categorize(
                SmsCategoryInput(
                    sender = senderFromKey(transaction.notificationKey),
                    payee = payeeFromTitle(transaction.title),
                    body = "",
                    income = false,
                    ownAccountKeys = ownAccountKeys
                )
            )
            if (result.category.equals(SmsCategories.OTHER, ignoreCase = true)) null
            else RecategorizeProposal(transaction, result.category, result.kind)
        }

    /**
     * Applies only the approved proposals. A proposal is skipped if its transaction no longer exists
     * unchanged (for example because the user edited it after opening the preview).
     */
    fun apply(transactions: List<Transaction>, approved: Collection<RecategorizeProposal>): List<Transaction> {
        if (approved.isEmpty()) return transactions
        val byTransaction = approved.associateBy { it.transaction }
        return transactions.map { transaction ->
            val proposal = byTransaction[transaction]
            if (proposal != null && isEligible(transaction)) {
                transaction.copy(category = proposal.newCategory, categoryOrigin = CategoryOrigin.AUTO)
            } else transaction
        }
    }

    /** Short label for the preview list. */
    fun describe(kind: SmsPaymentKind): String = when (kind) {
        SmsPaymentKind.MERCHANT -> "Known merchant"
        SmsPaymentKind.CREDIT_CARD_BILL -> "Credit card bill"
        SmsPaymentKind.BANK_TRANSFER -> "Bank transfer"
        SmsPaymentKind.OWN_ACCOUNT -> "Own-account transfer"
        SmsPaymentKind.PERSON_TO_PERSON -> "Looks like a payment to a person"
        SmsPaymentKind.KEYWORD -> "Matched from the description"
        SmsPaymentKind.INCOME, SmsPaymentKind.UNKNOWN -> ""
    }.let { it.replaceFirstChar { c -> c.titlecase(Locale.ROOT) } }
}
