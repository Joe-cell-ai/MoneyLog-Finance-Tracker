package com.openfinanceai

import java.util.Locale

/**
 * Category names produced by [SmsCategorizer]. Every expense category here is a member of
 * [DefaultFinanceCategories.MONEY], so imported transactions always land in a category the
 * category picker offers. [SALARY] and [OTHER_INCOME] are appended to the picker separately.
 */
internal object SmsCategories {
    const val SHOPPING = "Shopping"
    const val FOOD = "Food"
    const val FOOD_DINING_OUT = "Food/Dining out"
    const val FOOD_COFFEE = "Food/Coffee"
    const val FOOD_TAKEOUT = "Food/Takeout"
    const val TAXI = "Transport/Taxi"
    const val FUEL = "Transport/Fuel"
    const val PUBLIC_TRANSIT = "Transport/Public transit"
    const val TRAVEL = "Leisure/Travel"
    const val LEISURE = "Leisure"
    const val HEALTHCARE = "Healthcare"
    const val ELECTRICITY = "Utilities/Electricity"
    const val PHONE = "Utilities/Phone"
    const val INTERNET = "Utilities/Internet"
    const val WATER = "Utilities/Water"
    const val GAS = "Utilities/Gas"
    const val EDUCATION = "Education"
    const val INSURANCE = "Insurance"
    const val CREDIT_CARD_BILL = "Bills/Credit card"
    const val BANK_TRANSFER = "Bank transfer"
    const val OWN_ACCOUNT_TRANSFER = "Own account transfer"
    const val PERSON_TO_PERSON = "Person-to-person"
    const val INCOME = "Income"
    const val SALARY = "Salary"
    const val OTHER_INCOME = "Other income"
    const val OTHER = "Other"

    /** Categories that did not exist in the original default list and are added to it. */
    val ADDED_DEFAULTS: List<String> = listOf(
        SHOPPING, EDUCATION, INSURANCE, CREDIT_CARD_BILL, BANK_TRANSFER, OWN_ACCOUNT_TRANSFER, PERSON_TO_PERSON
    )

    /** Every category the engine can return. */
    val ALL: List<String> = listOf(
        SHOPPING, FOOD, FOOD_DINING_OUT, FOOD_COFFEE, FOOD_TAKEOUT, TAXI, FUEL, PUBLIC_TRANSIT, TRAVEL, LEISURE,
        HEALTHCARE, ELECTRICITY, PHONE, INTERNET, WATER, GAS, EDUCATION, INSURANCE, CREDIT_CARD_BILL,
        BANK_TRANSFER, OWN_ACCOUNT_TRANSFER, PERSON_TO_PERSON, INCOME, SALARY, OTHER_INCOME, OTHER
    )
}

/** The app's default category list (previously a private list in MainActivity). */
internal object DefaultFinanceCategories {
    val EXISTING: List<String> = listOf(
        "Food", "Food/Dining out", "Healthcare", "Income", "Leisure/Travel",
        "Savings/Emergency", "Transport/Fuel", "Transport/Taxi", "Utilities/Gas",
        "Utilities/Phone", "Food/Coffee", "Food/Takeout", "Household", "Leisure",
        "Other", "Savings/Investment", "Transport", "Transport/Public transit",
        "Utilities/Electricity", "Utilities/Internet", "Utilities/Water"
    )
    val MONEY: List<String> = EXISTING + SmsCategories.ADDED_DEFAULTS
}

/** Whether a transaction's category was chosen by the engine or by the user. */
internal object CategoryOrigin {
    /** Legacy rows, or rows created before origin tracking existed. */
    const val UNKNOWN = ""
    const val AUTO = "auto"
    const val MANUAL = "manual"

    /**
     * Origin to store after the user saves an edit: changing the category makes it a manual choice;
     * leaving it as it was keeps whatever origin it already had.
     */
    fun afterEdit(previousCategory: String, previousOrigin: String, newCategory: String): String =
        if (newCategory.trim().equals(previousCategory.trim(), ignoreCase = true)) previousOrigin else MANUAL
}

internal enum class SmsPaymentKind { INCOME, MERCHANT, CREDIT_CARD_BILL, BANK_TRANSFER, OWN_ACCOUNT, PERSON_TO_PERSON, KEYWORD, UNKNOWN }

internal data class SmsCategoryInput(
    /** SMS sender address (for example "AX-ICICIB-S"); may be blank. */
    val sender: String = "",
    /** Payee/merchant extracted by [BankSmsParser], without the "To "/"From " prefix. */
    val payee: String? = null,
    /** Full SMS text; blank when only a stored transaction is being re-categorised. */
    val body: String = "",
    val income: Boolean,
    /** Keys from [SmsCategorizer.ownAccountKeyFrom] for accounts known to belong to the user. */
    val ownAccountKeys: Set<String> = emptySet()
)

internal data class SmsCategorization(val category: String, val kind: SmsPaymentKind, val rule: String?)

/**
 * The single categorisation engine used by automatic SMS import, manual SMS import and the
 * re-categorise action. It is pure Kotlin (no Android types) so every rule is unit-testable.
 *
 * Order of precedence for expenses: own-account transfer, known merchant (payee, then sender, then
 * SMS text), credit-card bill, bank transfer, generic keywords, person-to-person UPI, "Other".
 */
internal object SmsCategorizer {
    private class Rule(val id: String, val category: String, val pattern: Regex)

    private fun rule(id: String, category: String, pattern: String) =
        Rule(id, category, Regex(pattern, RegexOption.IGNORE_CASE))

    // Specific merchants first: "Swiggy Instamart" must be groceries, "Amazon Prime Video" leisure.
    private val merchantRules: List<Rule> = listOf(
        rule("groceries", SmsCategories.FOOD,
            """\b(?:bigbasket|bbnow|blinkit|zepto|instamart|dmart|d\s*mart|more\s+supermarket|reliance\s+fresh|nature'?s\s+basket)\b"""),
        rule("streaming-entertainment", SmsCategories.LEISURE,
            """\b(?:netflix|spotify|hotstar|jio\s*hotstar|prime\s+video|amazon\s+prime|youtube\s+premium|bookmyshow|pvr|inox)\b"""),
        rule("cred", SmsCategories.CREDIT_CARD_BILL, """\b(?:cred|dreamplug)\b"""),
        rule("swiggy", SmsCategories.FOOD_TAKEOUT, """\b(?:swiggy|bundl\s+technologies)\b"""),
        rule("zomato", SmsCategories.FOOD_TAKEOUT, """\bzomato\b"""),
        rule("coffee", SmsCategories.FOOD_COFFEE, """\b(?:starbucks|cafe\s+coffee\s+day|ccd)\b"""),
        rule("restaurant-chains", SmsCategories.FOOD_DINING_OUT,
            """\b(?:dominos|domino'?s|mcdonald'?s|kfc|pizza\s*hut|burger\s*king|subway|behrouz|faasos|eatclub)\b"""),
        rule("flipkart", SmsCategories.SHOPPING, """\b(?:flipkart|fkrt)\b"""),
        rule("amazon", SmsCategories.SHOPPING, """\b(?:amazon(?:\s*pay)?|amzn)\b"""),
        rule("online-shopping", SmsCategories.SHOPPING,
            """\b(?:myntra|ajio|meesho|nykaa|tata\s*cliq|jiomart|croma|reliance\s+digital|decathlon|shoppers\s+stop|lifestyle|westside|ikea|zara|h\s*&\s*m)\b"""),
        rule("taxi", SmsCategories.TAXI, """\b(?:ola|uber|rapido)\b"""),
        rule("public-transit", SmsCategories.PUBLIC_TRANSIT, """\b(?:apsrtc|andhra\\s+pradesh\\s+state\\s+road\\s+transport|namma\s+metro|metro\s+rail|bmtc|irctc|redbus)\b"""),
        rule("travel-booking", SmsCategories.TRAVEL, """\b(?:makemytrip|goibibo|cleartrip|ixigo)\b"""),
        rule("fuel", SmsCategories.FUEL,
            """\b(?:indian\s+oil|bharat\s+petroleum|hpcl|hindustan\s+petroleum|iocl|shell|essar\s+oil|nayara\s+energy)\b"""),
        rule("internet", SmsCategories.INTERNET, """\b(?:jio\s*fiber|act\s+fibernet|airtel\s+xstream)\b"""),
        rule("electricity", SmsCategories.ELECTRICITY,
            """\b(?:tata\s+power|bescom|adani\s+electricity|electricity\s+board)\b"""),
        rule("phone", SmsCategories.PHONE, """\b(?:airtel|jio|vodafone|vi\s+recharge|bsnl)\b"""),
        rule("health-brands", SmsCategories.HEALTHCARE,
            """\b(?:apollo\s+pharmacy|1mg|tata\s*1mg|pharmeasy|netmeds|medplus|wellness\s+forever|practo)\b"""),
        rule("education-brands", SmsCategories.EDUCATION,
            """\b(?:udemy|coursera|unacademy|byju'?s|upgrad|vedantu|simplilearn)\b""")
    )

    // Generic words, used only when no merchant, transfer or card-bill rule matched.
    private val keywordRules: List<Rule> = listOf(
        rule("kw-taxi", SmsCategories.TAXI, """\b(?:taxi|cab\s+ride)\b"""),
        rule("kw-transit", SmsCategories.PUBLIC_TRANSIT, """\b(?:metro)\b"""),
        rule("kw-dining", SmsCategories.FOOD_DINING_OUT, """\b(?:restaurant|cafe|food\s+delivery|food\s+order)\b"""),
        rule("kw-groceries", SmsCategories.FOOD, """\b(?:grocery|groceries)\b"""),
        rule("kw-fuel", SmsCategories.FUEL, """\b(?:petrol|fuel|diesel)\b"""),
        rule("kw-electricity", SmsCategories.ELECTRICITY, """\belectricity\b"""),
        rule("kw-water", SmsCategories.WATER, """\bwater\s+bill\b"""),
        rule("kw-gas", SmsCategories.GAS, """\bgas\s+bill\b"""),
        rule("kw-internet", SmsCategories.INTERNET, """\bbroadband\b"""),
        rule("kw-phone", SmsCategories.PHONE, """\bmobile\s+recharge\b"""),
        rule("kw-health", SmsCategories.HEALTHCARE, """\b(?:pharmacy|medical|hospital|clinic|medicine)\b"""),
        rule("kw-leisure", SmsCategories.LEISURE, """\b(?:movie|cinema)\b"""),
        rule("kw-education", SmsCategories.EDUCATION, """\b(?:school|college|university|tuition|course\s+fee|education)\b"""),
        rule("kw-insurance", SmsCategories.INSURANCE, """\b(?:insurance|premium\s+payment|lic\s+premium)\b"""),
        // Last on purpose: "purchase" appears in most card SMS, so any more specific keyword should win.
        rule("kw-shopping", SmsCategories.SHOPPING, """\b(?:shopping|retail|purchase|online\s+order|e-commerce)\b""")
    )

    /** Every rule id [categorize] can report; tests assert each one has a covering case. */
    val ruleIds: Set<String> = (merchantRules + keywordRules).map { it.id }.toSet() +
        setOf("own-account", "salary", "loan-receipt", "credit-card-bill", "bank-transfer", "person-to-person")

    private val salaryWords = Regex("""(?i)\b(?:salary|payroll|wages)\b""")
    // Count borrowing as received money only when the credit explicitly identifies a loan
    // or comes from a recognised loan provider. Generic incoming transfers remain Other income.
    private val loanReceiptWords = Regex(
        """(?i)\b(?:loan(?:\s+(?:amount|proceeds))?\s+(?:credited|received|disbursed|paid\s+out|transferred|payout)|(?:credited|received|disbursed)\s+(?:your\s+)?loan(?:\s+amount)?|loan\s+disbursement|loan\s+from|borrowed\s+from)\b"""
    )
    private val loanProviderNames = Regex(
        """(?i)\b(?:fibe|kreditbee|navi|moneyview|earlysalary|paysense|cashe|mpokket|bajaj\s+finserv|kissht|truebalance|lazypay)\b"""
    )
    private val creditEventWords = Regex("""(?i)\b(?:credited|received|deposited|disbursed)\b""")

    // ---- transfers --------------------------------------------------------------------------

    /** Payee text that means "my own account". Only trusted for expenses (see [categorize]). */
    private val ownAccountPayee = Regex(
        """(?i)^(?:your|my|own|self)(?:\s+own)?(?:\s+(?:bank|savings|current|salary))*(?:\s+(?:a/c|acct|account))?$"""
    )
    private val ownAccountBody = Regex(
        """(?i)\b(?:own[- ]account|self[- ]?transfer|(?:transfer(?:red)?|sent)\s+to\s+(?:your\s+)?own\s+account|between\s+(?:your\s+)?(?:own\s+)?accounts)\b"""
    )
    private val maskedAccountPayee = Regex(
        """(?i)^(.*?)\s*(?:(?:a/c|acct?|account)\s*(?:no\.?\s*)?[x*]+|[x*]{2,})\s*(\d{3,4})$"""
    )
    // "mobile number XXXXX2400" ends in a mask-like suffix but is a phone number, not an account.
    private val mobilePayee = Regex("""(?i)\bmobile\s*(?:number|no\.?)\b|^[x*\d]{8,}$""")
    private val bankTransferBody = Regex("""(?i)\b(?:neft|imps|rtgs|bank\s+transfer|transfer\s+between\s+accounts)\b""")
    private val bankInPayee = Regex("""(?i)\bbank\b""")
    private val creditCardPayee = Regex("""(?i)\b(?:credit\s*card|cc|card\s+(?:bill|payment))\b""")
    private val creditCardBillBody = Regex("""(?i)\b(?:credit\s*card\s+(?:bill|payment|dues)|card\s+bill)\b""")

    // Used to learn which accounts belong to the user, from SMS the user's own bank sent them.
    private val ownAccountInSmsWithBank = Regex(
        """(?i)\b([a-z][a-z&]{1,14})\s+bank\s+(?:a/c|acct?|account)\s*(?:no\.?\s*)?[x*]+\s*(\d{3,4})\b"""
    )
    private val ownAccountInSmsNoBank = Regex(
        """(?i)\b([a-z][a-z&]{1,14})\s+(?:a/c|acct?|account)\s*(?:no\.?\s*)?[x*]+\s*(\d{3,4})\b"""
    )
    private val notABankName = setOf("bank", "your", "the", "savings", "current", "salary", "to", "from", "with", "in", "my", "own")

    private fun accountKey(bankName: String, last4: String): String? {
        val bank = bankName.lowercase(Locale.ROOT)
            .replace(Regex("""\b(?:bank|a/c|acct|account)\b"""), " ")
            .trim().split(Regex("""\s+""")).firstOrNull { it.isNotBlank() }
            ?.filter { it.isLetter() } ?: return null
        if (bank.isBlank() || bank in notABankName) return null
        return "$bank:$last4"
    }

    /**
     * Returns a key such as "icici:450" for the account the SMS was sent about (the first account
     * mentioned), or null. Keys are stored locally and used to recognise own-account transfers.
     */
    fun ownAccountKeyFrom(body: String): String? {
        ownAccountInSmsWithBank.find(body)?.let { m ->
            accountKey(m.groupValues[1], m.groupValues[2])?.let { return it }
        }
        ownAccountInSmsNoBank.findAll(body).forEach { m ->
            accountKey(m.groupValues[1], m.groupValues[2])?.let { return it }
        }
        return null
    }

    // ---- person-to-person -------------------------------------------------------------------

    private val businessWords = setOf(
        "pvt", "private", "ltd", "limited", "llp", "inc", "corp", "company", "co", "services", "service", "servi",
        "technologies", "technology", "tech", "solutions", "enterprises", "enterprise", "traders", "trading",
        "stores", "store", "mart", "market", "supermarket", "super", "hotel", "restaurant", "cafe", "bakery",
        "sweets", "foods", "food", "kitchen", "medicals", "medical", "pharmacy", "hospital", "clinic", "labs",
        "lab", "school", "college", "institute", "academy", "agency", "agencies", "motors", "auto", "garage",
        "petrol", "fuel", "bank", "finance", "insurance", "pay", "payments", "wallet", "upi", "recharge", "bill",
        "bills", "electricals", "electronics", "mobiles", "digital", "online", "retail", "wholesale", "fashion",
        "textiles", "jewellers", "jewelers", "salon", "parlour", "gym", "fitness", "travels", "tours", "transport",
        "logistics", "courier", "merchant", "shop", "mall", "club", "app", "global", "international", "industries"
    )
    private val mobileNumberPayee = Regex("""(?i)^(?:mobile\s*(?:number|no\.?)\s*)?[x*\d]{8,}$""")
    private val vpaPhone = Regex("""^\d{10}@[a-z0-9.]+$""", RegexOption.IGNORE_CASE)
    private val personNameShape = Regex("""^[A-Za-z][A-Za-z.'-]*(?: [A-Za-z][A-Za-z.'-]*){0,3}$""")
    private val upiEvidence = Regex("""(?i)\bupi\b|@[a-z0-9]+""")

    internal fun looksLikePerson(rawPayee: String): Boolean {
        val payee = rawPayee.trim().replace(Regex("""\s+"""), " ")
            .replace(Regex("""(?i)^(?:mr|mrs|ms|shri|smt|sri)\.?\s+"""), "")
        if (payee.isBlank() || payee.length > 40) return false
        if (mobileNumberPayee.matches(payee) || vpaPhone.matches(payee)) return true
        if (!personNameShape.matches(payee)) return false
        return payee.lowercase(Locale.ROOT).split(' ').none { it.trim('.', '\'', '-') in businessWords }
    }

    // ---- entry point ------------------------------------------------------------------------

    fun normalizeSender(sender: String): String = sender.trim()
        .replace(Regex("""^[A-Za-z]{2}-"""), "")
        .replace(Regex("""-[A-Za-z]$"""), "")
        .trim()

    fun categorize(input: SmsCategoryInput): SmsCategorization {
        val payee = input.payee.orEmpty().trim()
        val sender = normalizeSender(input.sender)
        val body = input.body

        fun done(category: String, kind: SmsPaymentKind, rule: String?) = SmsCategorization(category, kind, rule)

        // 1. Own account. "your account" as a payee is only trusted for expenses, because a credit
        //    SMS says "credited to your account" and that is not a transfer between own accounts.
        val masked = if (mobilePayee.containsMatchIn(payee)) null else maskedAccountPayee.find(payee)
        val maskedKey = masked?.let { accountKey(it.groupValues[1], it.groupValues[2]) }
        val ownByPayee = (!input.income && ownAccountPayee.matches(payee)) ||
            (maskedKey != null && maskedKey in input.ownAccountKeys)
        if (ownByPayee || ownAccountBody.containsMatchIn(body)) {
            return done(SmsCategories.OWN_ACCOUNT_TRANSFER, SmsPaymentKind.OWN_ACCOUNT, "own-account")
        }

        if (input.income) {
            val incomeText = "$sender $payee $body"
            val loanReceipt = loanReceiptWords.containsMatchIn(incomeText) ||
                (loanProviderNames.containsMatchIn("$sender $payee") && creditEventWords.containsMatchIn(body))
            if (loanReceipt) return done(SmsCategories.INCOME, SmsPaymentKind.INCOME, "loan-receipt")
            val salary = salaryWords.containsMatchIn("$payee $body")
            return if (salary) done(SmsCategories.SALARY, SmsPaymentKind.INCOME, "salary")
            else done(SmsCategories.OTHER_INCOME, SmsPaymentKind.INCOME, null)
        }

        // 2. Known merchants: payee first, then sender, then the SMS description.
        for (text in listOf(payee, sender, body)) {
            if (text.isBlank()) continue
            merchantRules.firstOrNull { it.pattern.containsMatchIn(text) }?.let {
                return done(it.category, SmsPaymentKind.MERCHANT, it.id)
            }
        }

        // 3. Credit-card bill payments to a card, without a known merchant brand.
        if (creditCardPayee.containsMatchIn(payee) || creditCardBillBody.containsMatchIn(body)) {
            return done(SmsCategories.CREDIT_CARD_BILL, SmsPaymentKind.CREDIT_CARD_BILL, "credit-card-bill")
        }

        // 4. Bank transfers: NEFT/IMPS/RTGS, a masked account payee, or a bank named as the payee.
        if (bankTransferBody.containsMatchIn(body) || masked != null || bankInPayee.containsMatchIn(payee)) {
            return done(SmsCategories.BANK_TRANSFER, SmsPaymentKind.BANK_TRANSFER, "bank-transfer")
        }

        // 5. Generic keywords anywhere in the payee, sender or SMS text.
        val combined = "$payee $sender $body"
        keywordRules.firstOrNull { it.pattern.containsMatchIn(combined) }?.let {
            return done(it.category, SmsPaymentKind.KEYWORD, it.id)
        }

        // 6. A UPI payment to a person. When the SMS text is unavailable (stored transactions) the
        //    shape of the payee name is the only evidence.
        if (payee.isNotBlank() && looksLikePerson(payee) && (body.isBlank() || upiEvidence.containsMatchIn(body))) {
            return done(SmsCategories.PERSON_TO_PERSON, SmsPaymentKind.PERSON_TO_PERSON, "person-to-person")
        }

        return done(SmsCategories.OTHER, SmsPaymentKind.UNKNOWN, null)
    }
}
