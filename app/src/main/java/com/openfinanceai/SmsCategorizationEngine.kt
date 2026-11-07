package com.openfinanceai

import java.util.Locale

/**
 * Pure, shared SMS categorisation rules used by live imports, inbox scans and
 * the opt-in re-categorisation preview. No Android APIs or network access.
 */
internal object SmsCategorizationEngine {
    internal data class Decision(
        val category: String,
        val transferKind: String = "",
        val iconKey: String,
        val colorHex: String
    )

    private data class Rule(
        val category: String,
        val iconKey: String,
        val colorHex: String,
        val pattern: Regex
    )

    private val merchantRules = listOf(
        Rule("Other", "brand_cred", "#64748B", Regex("""(?i)\b(cred(?:club)?|dreamplug|dreamplug technologies)\b""")),
        Rule("Household", "brand_amazon", "#DB4765", Regex("""(?i)\b(amazon(?:\s+pay)?|amzn|flipkart)\b""")),
        Rule("Food/Takeout", "food", "#148D91", Regex("""(?i)\b(swiggy|zomato|dominos|domino'?s|mcdonald'?s|kfc|pizza\s*hut|burger\s*king|subway|starbucks|cafe\s*coffee\s*day|behrouz|faasos|eatclub)\b""")),
        Rule("Food", "groceries", "#148D91", Regex("""(?i)\b(bigbasket|bbnow|blinkit|zepto|instamart|dmart|more\s+supermarket|reliance\s+fresh)\b""")),
        Rule("Transport/Taxi", "transport", "#B18A16", Regex("""(?i)\b(ola|uber|rapido|namma\s+metro|metro\s+rail|irctc|redbus|makemytrip|goibibo|cleartrip)\b""")),
        Rule("Transport/Fuel", "fuel", "#B18A16", Regex("""(?i)\b(indian\s+oil|bharat\s+petroleum|hpcl|hindustan\s+petroleum|iocl|shell|nayara\s+energy)\b""")),
        Rule("Utilities/Electricity", "bills", "#D97732", Regex("""(?i)\b(airtel|jio\s*fiber|jio|vodafone|vi\s+recharge|bsnl|tata\s+power|bescom|adani\s+electricity)\b""")),
        Rule("Healthcare", "health", "#477DAD", Regex("""(?i)\b(apollo\s+pharmacy|1mg|tata\s*1mg|pharmeasy|netmeds|medplus|wellness\s+forever|practo)\b""")),
        Rule("Leisure", "entertainment", "#AF5AB5", Regex("""(?i)\b(netflix|spotify|hotstar|prime\s+video|youtube\s+premium|bookmyshow|pvr|inox)\b""")),
        Rule("Household", "home", "#DB4765", Regex("""(?i)\b(myntra|ajio|meesho|nykaa|tata\s*cliq|jiomart|croma|decathlon|ikea|zara)\b"""))
    )

    private val ownAccountTransfer = Regex(
        """(?i)\b(own account|my own account|self transfer|own transfer|between (?:my|your|own) accounts|transfer to (?:my|your|own) account|transferred to (?:my|your|own) account|same name account|internal account transfer)\b"""
    )
    private val bankTransfer = Regex(
        """(?i)\b(neft|imps|rtgs|bank transfer|fund transfer|account transfer|wire transfer)\b"""
    )
    private val personUpiTransfer = Regex(
        """(?i)\b(upi (?:transfer|payment)|sent via upi|paid via upi|person[- ]to[- ]person|p2p)\b"""
    )

    fun categorize(
        sender: String?,
        payee: String?,
        description: String?,
        income: Boolean,
        availableCategories: List<String>
    ): Decision {
        val text = listOf(sender, payee, description)
            .filterNot { it.isNullOrBlank() }
            .joinToString(" ")
            .lowercase(Locale.ROOT)

        val proposed = when {
            income -> if (Regex("""(?i)\b(salary|payroll|wages)\b""").containsMatchIn(text)) "Salary" else "Other income"
            else -> merchantRules.firstOrNull { it.pattern.containsMatchIn(text) }?.category
                ?: when {
                    ownAccountTransfer.containsMatchIn(text) -> "Transfers/Own account"
                    bankTransfer.containsMatchIn(text) -> "Transfers/Bank"
                    personUpiTransfer.containsMatchIn(text) && hasPersonPayee(payee, text) -> "Transfers/Person-to-person UPI"
                    Regex("""(?i)\b(shopping|retail|purchase|online order|e-commerce)\b""").containsMatchIn(text) -> "Household"
                    Regex("""(?i)\b(restaurant|cafe|food delivery|food order|takeout|take away)\b""").containsMatchIn(text) -> "Food/Takeout"
                    Regex("""(?i)\b(grocery|groceries|supermarket|bigbasket|blinkit|zepto|instamart)\b""").containsMatchIn(text) -> "Food"
                    Regex("""(?i)\b(petrol|fuel|diesel|indian oil|bharat petroleum|hpcl|shell)\b""").containsMatchIn(text) -> "Transport/Fuel"
                    Regex("""(?i)\b(ola|uber|rapido|taxi|cab ride|metro|irctc|redbus)\b""").containsMatchIn(text) -> "Transport/Taxi"
                    Regex("""(?i)\b(electricity|water bill|gas bill|broadband|mobile recharge|utility)\b""").containsMatchIn(text) -> "Utilities/Electricity"
                    Regex("""(?i)\b(pharmacy|medical|hospital|clinic|medicine)\b""").containsMatchIn(text) -> "Healthcare"
                    Regex("""(?i)\b(movie|cinema|netflix|spotify|hotstar|prime video|bookmyshow)\b""").containsMatchIn(text) -> "Leisure"
                    else -> "Other"
                }
        }

        val resolved = resolveCategory(proposed, availableCategories)
        val transferKind = when (proposed) {
            "Transfers/Own account" -> "OWN_ACCOUNT_TRANSFER"
            "Transfers/Bank" -> "BANK_TRANSFER"
            "Transfers/Person-to-person UPI" -> "PERSON_UPI_TRANSFER"
            else -> ""
        }
        return Decision(
            category = resolved,
            transferKind = transferKind,
            iconKey = iconFor(resolved, text, transferKind),
            colorHex = colorFor(resolved, transferKind)
        )
    }

    private fun hasPersonPayee(payee: String?, text: String): Boolean {
        val candidate = payee.orEmpty().trim()
        if (candidate.isBlank()) return Regex("""(?i)\b(to|from)\s+[a-z]{2,}(?:\s+[a-z]{2,}){0,2}\b""").containsMatchIn(text)
        return !Regex("""(?i)\b(amazon|flipkart|swiggy|zomato|cred|dreamplug|bank|account|wallet|merchant)\b""").containsMatchIn(candidate)
    }

    private fun resolveCategory(proposed: String, available: List<String>): String {
        if (available.isEmpty()) return proposed
        val exact = available.firstOrNull { it.equals(proposed, ignoreCase = true) }
        if (exact != null) return exact
        val normalized = normalize(proposed)
        available.firstOrNull { normalize(it) == normalized }?.let { return it }
        val aliases = when (proposed) {
            "Household" -> listOf("Shopping", "Leisure/Travel", "Other")
            "Food/Takeout" -> listOf("Food/Dining out", "Food", "Other")
            "Food" -> listOf("Food/Dining out", "Food/Takeout", "Other")
            "Healthcare" -> listOf("Health", "Other")
            "Income", "Salary", "Other income" -> listOf("Income", "Salary", "Other income", "Other")
            "Transfers/Bank", "Transfers/Own account", "Transfers/Person-to-person UPI" -> listOf("Bank transfer", "Other")
            else -> listOf("Other")
        }
        return aliases.firstNotNullOfOrNull { alias ->
            available.firstOrNull { it.equals(alias, ignoreCase = true) }
        } ?: available.firstOrNull { it.equals("Other", ignoreCase = true) } ?: available.first()
    }

    private fun normalize(value: String): String =
        value.lowercase(Locale.ROOT).replace(Regex("[^a-z0-9]+"), "")

    private fun iconFor(category: String, text: String, transferKind: String): String = when {
        transferKind.isNotBlank() -> "bank"
        Regex("""(?i)\b(cred(?:club)?|dreamplug)\b""").containsMatchIn(text) -> "brand_cred"
        Regex("""(?i)\bamazon\b""").containsMatchIn(text) -> "brand_amazon"
        Regex("""(?i)\bflipkart\b""").containsMatchIn(text) -> "brand_flipkart"
        Regex("""(?i)\bswiggy\b""").containsMatchIn(text) -> "brand_swiggy"
        Regex("""(?i)\bzomato\b""").containsMatchIn(text) -> "brand_zomato"
        category.startsWith("Transfers/") -> "bank"
        category.startsWith("Food") -> "food"
        category == "Household" -> "home"
        category == "Healthcare" -> "health"
        category == "Income" -> "income"
        category.startsWith("Transport") -> "transport"
        category.startsWith("Utilities") -> "bills"
        else -> "app"
    }

    private fun colorFor(category: String, transferKind: String): String = when {
        transferKind.isNotBlank() -> "#477DAD"
        category.startsWith("Transfers/") -> "#477DAD"
        category.startsWith("Food") -> "#148D91"
        category == "Household" -> "#DB4765"
        category == "Healthcare" -> "#477DAD"
        category == "Income" -> "#356AE6"
        category.startsWith("Transport") -> "#B18A16"
        category.startsWith("Utilities") -> "#D97732"
        else -> "#64748B"
    }
}
