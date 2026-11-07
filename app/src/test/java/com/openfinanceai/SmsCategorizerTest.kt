package com.openfinanceai

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SmsCategorizerTest {
    private fun expense(
        payee: String? = null,
        sender: String = "",
        body: String = "",
        own: Set<String> = emptySet()
    ) = SmsCategorizer.categorize(SmsCategoryInput(sender, payee, body, income = false, ownAccountKeys = own))

    private fun income(payee: String? = null, body: String = "", own: Set<String> = emptySet()) =
        SmsCategorizer.categorize(SmsCategoryInput("", payee, body, income = true, ownAccountKeys = own))

    // ---- requested merchants: matched from payee, sender and SMS description -----------------

    @Test
    fun credMatchesPayeeSenderAndDescription() {
        assertEquals(SmsCategories.CREDIT_CARD_BILL, expense(payee = "CRED Club").category)
        assertEquals(SmsCategories.CREDIT_CARD_BILL, expense(payee = "cred.club@axisb").category)
        assertEquals(SmsCategories.CREDIT_CARD_BILL, expense(sender = "AX-CRED-S", body = "Rs 871 debited").category)
        assertEquals(SmsCategories.CREDIT_CARD_BILL, expense(body = "Rs 871 debited; CRED Club credited. UPI:123").category)
    }

    @Test
    fun dreamplugMatchesPayeeSenderAndDescriptionIncludingTruncatedNames() {
        assertEquals(SmsCategories.CREDIT_CARD_BILL, expense(payee = "Dreamplug Servi").category)
        assertEquals(SmsCategories.CREDIT_CARD_BILL, expense(payee = "DREAMPLUG TECHNOLOGIES PRIVATE LIMITED").category)
        assertEquals(SmsCategories.CREDIT_CARD_BILL, expense(sender = "VM-DREAMPLUG", body = "Rs 27032 debited").category)
        assertEquals(SmsCategories.CREDIT_CARD_BILL, expense(body = "Rs 27032 debited; Dreamplug Services credited. UPI:1").category)
    }

    @Test
    fun amazonMatchesPayeeSenderAndDescription() {
        assertEquals(SmsCategories.SHOPPING, expense(payee = "Amazon").category)
        assertEquals(SmsCategories.SHOPPING, expense(payee = "AMAZONPAY").category)
        assertEquals(SmsCategories.SHOPPING, expense(payee = "amazon@apl").category)
        assertEquals(SmsCategories.SHOPPING, expense(sender = "AX-AMAZON-S", body = "Rs 499 paid").category)
        assertEquals(SmsCategories.SHOPPING, expense(body = "Transferred to Amazon by UPI").category)
    }

    @Test
    fun flipkartMatchesPayeeSenderAndDescription() {
        assertEquals(SmsCategories.SHOPPING, expense(payee = "Flipkart").category)
        assertEquals(SmsCategories.SHOPPING, expense(payee = "FLIPKART INTERNET PVT LTD").category)
        assertEquals(SmsCategories.SHOPPING, expense(sender = "VK-FLIPKT", body = "Flipkart order Rs 999 paid").category)
        assertEquals(SmsCategories.SHOPPING, expense(body = "UPI purchase at Flipkart").category)
    }

    @Test
    fun swiggyMatchesPayeeSenderAndDescription() {
        assertEquals(SmsCategories.FOOD_TAKEOUT, expense(payee = "Swiggy").category)
        assertEquals(SmsCategories.FOOD_TAKEOUT, expense(payee = "Bundl Technologies").category)
        assertEquals(SmsCategories.FOOD_TAKEOUT, expense(sender = "AD-SWIGGY", body = "Rs 320 paid").category)
        assertEquals(SmsCategories.FOOD_TAKEOUT, expense(body = "Order payment to Swiggy").category)
    }

    @Test
    fun zomatoMatchesPayeeSenderAndDescription() {
        assertEquals(SmsCategories.FOOD_TAKEOUT, expense(payee = "Zomato").category)
        assertEquals(SmsCategories.FOOD_TAKEOUT, expense(payee = "ZOMATO LTD").category)
        assertEquals(SmsCategories.FOOD_TAKEOUT, expense(sender = "VM-ZOMATO", body = "Rs 410 paid").category)
        assertEquals(SmsCategories.FOOD_TAKEOUT, expense(body = "Paid Rs 410 to Zomato via UPI").category)
    }

    @Test
    fun specificMerchantsBeatMoreGeneralOnes() {
        assertEquals(SmsCategories.FOOD, expense(payee = "Swiggy Instamart").category)
        assertEquals(SmsCategories.FOOD, expense(payee = "Blinkit").category)
        assertEquals(SmsCategories.LEISURE, expense(payee = "Amazon Prime Video").category)
    }

    @Test
    fun payeeTakesPrecedenceOverSenderAndDescription() {
        assertEquals(SmsCategories.FOOD_TAKEOUT, expense(payee = "Swiggy", sender = "AX-AMAZON", body = "Amazon").category)
    }

    @Test
    fun matchingUsesWordBoundariesSoSubstringsDoNotFalseMatch() {
        // "credited"/"credit" must not trigger CRED, and "Ola" must not match inside other words.
        assertEquals(SmsCategories.OTHER, expense(payee = "Unknown Merchant", body = "Rs 100 credited reversal").category)
        assertEquals(SmsCategories.OTHER, expense(payee = "Unknown Merchant", body = "Payment successful").category)
        assertFalse(expense(payee = "Nikola Tesla Fan Club").category == SmsCategories.TAXI)
    }

    // ---- pre-existing merchant and keyword behaviour, now using the app's default names -----

    @Test
    fun existingMerchantRulesMapToDefaultCategoryNames() {
        assertEquals(SmsCategories.TAXI, expense(payee = "Ola", body = "Ride payment").category)
        assertEquals(SmsCategories.TAXI, expense(payee = "Uber", body = "Trip payment").category)
        assertEquals(SmsCategories.FUEL, expense(payee = "Indian Oil").category)
        assertEquals(SmsCategories.ELECTRICITY, expense(payee = "BESCOM").category)
        assertEquals(SmsCategories.INTERNET, expense(payee = "Jio Fiber").category)
        assertEquals(SmsCategories.PHONE, expense(payee = "Airtel").category)
        assertEquals(SmsCategories.LEISURE, expense(payee = "Netflix").category)
        assertEquals(SmsCategories.HEALTHCARE, expense(payee = "Apollo Pharmacy").category)
        assertEquals(SmsCategories.PUBLIC_TRANSIT, expense(payee = "Namma Metro").category)
        assertEquals(SmsCategories.TRAVEL, expense(payee = "MakeMyTrip").category)
        assertEquals(SmsCategories.EDUCATION, expense(payee = "Udemy").category)
        assertEquals(SmsCategories.FOOD_COFFEE, expense(payee = "Starbucks").category)
        assertEquals(SmsCategories.FOOD_DINING_OUT, expense(payee = "Dominos").category)
    }

    @Test
    fun genericKeywordsStillWorkWhenNoBrandMatches() {
        assertEquals(SmsCategories.FUEL, expense(payee = "Rao Filling Station", body = "petrol purchase").category)
        assertEquals(SmsCategories.HEALTHCARE, expense(payee = "City Hospital").category)
        assertEquals(SmsCategories.INSURANCE, expense(payee = "Star Health Insurance").category)
        assertEquals(SmsCategories.WATER, expense(body = "water bill paid").category)
        assertEquals(SmsCategories.GAS, expense(body = "gas bill paid").category)
        assertEquals(SmsCategories.FOOD, expense(body = "Grocery payment").category)
    }

    // ---- transfers ---------------------------------------------------------------------------

    @Test
    fun bankTransferIsRecognisedFromNeftImpsRtgsAndBankPayees() {
        assertEquals(SmsCategories.BANK_TRANSFER, expense(body = "IMPS transferred to account").category)
        assertEquals(SmsCategories.BANK_TRANSFER, expense(payee = "Ravi Kumar", body = "NEFT payment Rs 5000").category)
        assertEquals(SmsCategories.BANK_TRANSFER, expense(body = "RTGS of Rs 200000 sent").category)
        assertEquals(SmsCategories.BANK_TRANSFER, expense(payee = "HDFC Bank").category)
    }

    @Test
    fun maskedAccountPayeeIsABankTransferWhenTheAccountIsNotKnownToBeOwn() {
        val result = expense(payee = "ICICI Bank XX450", body = "Rs 4842 debited. UPI:1")
        assertEquals(SmsCategories.BANK_TRANSFER, result.category)
        assertEquals(SmsPaymentKind.BANK_TRANSFER, result.kind)
    }

    @Test
    fun maskedAccountPayeeIsAnOwnAccountTransferWhenTheAccountWasSeenInTheUsersOwnSms() {
        val result = expense(payee = "ICICI Bank XX450", own = setOf("icici:450"))
        assertEquals(SmsCategories.OWN_ACCOUNT_TRANSFER, result.category)
        assertEquals(SmsPaymentKind.OWN_ACCOUNT, result.kind)
        // A different account at the same bank, or the same digits at another bank, is not own.
        assertEquals(SmsCategories.BANK_TRANSFER, expense(payee = "ICICI Bank XX999", own = setOf("icici:450")).category)
        assertEquals(SmsCategories.BANK_TRANSFER, expense(payee = "HDFC Bank XX450", own = setOf("icici:450")).category)
    }

    @Test
    fun ownAccountKeyIsLearnedFromTheUsersOwnBankSms() {
        assertEquals(
            "icici:450",
            SmsCategorizer.ownAccountKeyFrom("ICICI Bank Acct XX450 debited for Rs 1000.00 on 04-Nov-26; AKASH S credited. UPI:667400510260.")
        )
        assertEquals("hdfc:1234", SmsCategorizer.ownAccountKeyFrom("Rs 500 debited from HDFC Bank A/c XX1234 on 01-Nov-26"))
        assertEquals("sbi:9876", SmsCategorizer.ownAccountKeyFrom("Your SBI A/c X9876 is credited with Rs 100"))
        assertNull(SmsCategorizer.ownAccountKeyFrom("Your OTP is 123456"))
        assertNull(SmsCategorizer.ownAccountKeyFrom("Rs 200 debited from your account"))
    }

    @Test
    fun ownAccountIsRecognisedFromPayeeWordsAndFromDescription() {
        assertEquals(SmsCategories.OWN_ACCOUNT_TRANSFER, expense(payee = "your account").category)
        assertEquals(SmsCategories.OWN_ACCOUNT_TRANSFER, expense(payee = "Own Account").category)
        assertEquals(SmsCategories.OWN_ACCOUNT_TRANSFER, expense(payee = "self").category)
        assertEquals(SmsCategories.OWN_ACCOUNT_TRANSFER, expense(body = "Self transfer of Rs 5000 done").category)
        assertEquals(SmsCategories.OWN_ACCOUNT_TRANSFER, expense(body = "Rs 5000 transferred to own account").category)
    }

    @Test
    fun routineDebitWordingIsNotMistakenForAnOwnAccountTransfer() {
        // "debited from your account" is boilerplate in nearly every debit SMS.
        val result = expense(payee = "Unknown Merchant", body = "Rs 200 debited from your account at Unknown Merchant")
        assertEquals(SmsCategories.OTHER, result.category)
    }

    @Test
    fun incomingCreditedToYourAccountIsNotAnOwnAccountTransfer() {
        assertEquals(SmsCategories.OTHER_INCOME, income(payee = "your account", body = "Rs 9559 credited to your account").category)
        assertEquals(SmsCategories.OTHER_INCOME, income(payee = "mobile number XXXXX52400").category)
    }

    @Test
    fun incomeFromAnOwnAccountIsRecognised() {
        assertEquals(SmsCategories.OWN_ACCOUNT_TRANSFER, income(payee = "ICICI Bank XX450", own = setOf("icici:450")).category)
        assertEquals(SmsCategories.OWN_ACCOUNT_TRANSFER, income(body = "Self transfer received").category)
    }

    @Test
    fun personToPersonUpiPaymentsAreDistinguishedFromMerchantsAndBanks() {
        val upi = "Rs 10000 debited. UPI:123456789"
        val person = expense(payee = "VEERAMREDDY NAG", body = upi)
        assertEquals(SmsCategories.PERSON_TO_PERSON, person.category)
        assertEquals(SmsPaymentKind.PERSON_TO_PERSON, person.kind)
        assertEquals(SmsCategories.PERSON_TO_PERSON, expense(payee = "AKASH S", body = upi).category)
        assertEquals(SmsCategories.PERSON_TO_PERSON, expense(payee = "mobile number XXXXX52400", body = upi).category)
        assertEquals(SmsCategories.PERSON_TO_PERSON, expense(payee = "9876543210@ybl", body = upi).category)
        // Businesses, even with person-like shapes, are not person-to-person.
        assertEquals(SmsCategories.OTHER, expense(payee = "Sri Lakshmi Stores", body = upi).category)
        assertEquals(SmsCategories.OTHER, expense(payee = "Unknown Merchant", body = upi).category)
        assertEquals(SmsCategories.OTHER, expense(payee = "Dreamplug Servi".replace("Dreamplug", "Acme"), body = upi).category)
    }

    @Test
    fun personToPersonNeedsUpiEvidenceWhenTheSmsTextIsAvailable() {
        assertEquals(SmsCategories.OTHER, expense(payee = "VEERAMREDDY NAG", body = "Rs 10000 debited at VEERAMREDDY NAG").category)
        // Without any SMS text (stored transactions) the payee shape is the only evidence.
        assertEquals(SmsCategories.PERSON_TO_PERSON, expense(payee = "VEERAMREDDY NAG").category)
    }

    @Test
    fun creditCardPaymentsToACardAreCardBillsButCardSpendIsNot() {
        assertEquals(SmsCategories.CREDIT_CARD_BILL, expense(payee = "HDFC Bank Credit Card XX1234").category)
        assertEquals(SmsCategories.CREDIT_CARD_BILL, expense(body = "Credit card bill payment of Rs 15000").category)
        // Spending ON a credit card at an unknown merchant is not a bill payment.
        val spend = expense(payee = "Unknown Merchant", body = "Spent Rs 500 on ICICI Bank Credit Card XX1234 at Unknown Merchant")
        assertEquals(SmsCategories.OTHER, spend.category)
        // ...and a known merchant on a card still wins.
        assertEquals(SmsCategories.SHOPPING, expense(payee = "Amazon", body = "Spent Rs 500 on HDFC Credit Card at Amazon").category)
    }

    @Test
    fun merchantsBeatTransferHeuristics() {
        assertEquals(SmsCategories.SHOPPING, expense(payee = "Amazon", body = "IMPS payment").category)
    }

    // ---- income ------------------------------------------------------------------------------

    @Test
    fun incomeKeepsSalaryAndOtherIncomeCategories() {
        assertEquals(SmsCategories.SALARY, income(payee = "ACME Payroll", body = "Salary credited").category)
        assertEquals(SmsCategories.OTHER_INCOME, income(payee = "Someone", body = "Rs 100 credited").category)
    }

    // ---- sender handling and the default category list ---------------------------------------

    @Test
    fun senderPrefixAndSuffixAreStripped() {
        assertEquals("AMAZON", SmsCategorizer.normalizeSender("AX-AMAZON-S"))
        assertEquals("ICICIB", SmsCategorizer.normalizeSender(" VM-ICICIB "))
        assertEquals("919876543210", SmsCategorizer.normalizeSender("919876543210"))
    }

    @Test
    fun everyCategoryTheEngineCanReturnExistsInTheDefaultCategoryList() {
        val picker = DefaultFinanceCategories.MONEY + listOf(SmsCategories.SALARY, SmsCategories.OTHER_INCOME)
        SmsCategories.ALL.forEach { category ->
            assertTrue("$category is not offered by the default category list", category in picker)
        }
    }

    @Test
    fun addedDefaultsDoNotDuplicateExistingOnesAndKeepTheOriginalOrder() {
        assertEquals(DefaultFinanceCategories.MONEY.size, DefaultFinanceCategories.MONEY.toSet().size)
        assertEquals(DefaultFinanceCategories.EXISTING, DefaultFinanceCategories.MONEY.take(DefaultFinanceCategories.EXISTING.size))
        assertTrue(SmsCategories.ADDED_DEFAULTS.none { it in DefaultFinanceCategories.EXISTING })
    }
    @Test
    fun everyRuleIdHasAtLeastOneCoveringCase() {
        val upi = "Rs 100 debited. UPI:123456789"
        val cases: Map<String, SmsCategoryInput> = mapOf(
            "groceries" to SmsCategoryInput(payee = "BigBasket", income = false),
            "streaming-entertainment" to SmsCategoryInput(payee = "Netflix", income = false),
            "cred" to SmsCategoryInput(payee = "CRED Club", income = false),
            "swiggy" to SmsCategoryInput(payee = "Swiggy", income = false),
            "zomato" to SmsCategoryInput(payee = "Zomato", income = false),
            "coffee" to SmsCategoryInput(payee = "Starbucks", income = false),
            "restaurant-chains" to SmsCategoryInput(payee = "Dominos", income = false),
            "flipkart" to SmsCategoryInput(payee = "Flipkart", income = false),
            "amazon" to SmsCategoryInput(payee = "Amazon", income = false),
            "online-shopping" to SmsCategoryInput(payee = "Myntra", income = false),
            "taxi" to SmsCategoryInput(payee = "Ola", income = false),
            "public-transit" to SmsCategoryInput(payee = "Namma Metro", income = false),
            "travel-booking" to SmsCategoryInput(payee = "MakeMyTrip", income = false),
            "fuel" to SmsCategoryInput(payee = "Indian Oil", income = false),
            "internet" to SmsCategoryInput(payee = "Jio Fiber", income = false),
            "electricity" to SmsCategoryInput(payee = "BESCOM", income = false),
            "phone" to SmsCategoryInput(payee = "Airtel", income = false),
            "health-brands" to SmsCategoryInput(payee = "Apollo Pharmacy", income = false),
            "education-brands" to SmsCategoryInput(payee = "Udemy", income = false),
            "kw-taxi" to SmsCategoryInput(payee = "Unknown Merchant", body = "cab ride booked", income = false),
            "kw-transit" to SmsCategoryInput(payee = "Unknown Merchant", body = "metro card top-up", income = false),
            "kw-dining" to SmsCategoryInput(payee = "Unknown Merchant", body = "restaurant bill", income = false),
            "kw-groceries" to SmsCategoryInput(payee = "Unknown Merchant", body = "grocery items", income = false),
            "kw-fuel" to SmsCategoryInput(payee = "Unknown Merchant", body = "diesel refill", income = false),
            "kw-electricity" to SmsCategoryInput(payee = "Unknown Merchant", body = "electricity charges", income = false),
            "kw-water" to SmsCategoryInput(payee = "Unknown Merchant", body = "water bill", income = false),
            "kw-gas" to SmsCategoryInput(payee = "Unknown Merchant", body = "gas bill", income = false),
            "kw-internet" to SmsCategoryInput(payee = "Unknown Merchant", body = "broadband plan", income = false),
            "kw-phone" to SmsCategoryInput(payee = "Unknown Merchant", body = "mobile recharge", income = false),
            "kw-health" to SmsCategoryInput(payee = "Unknown Merchant", body = "pharmacy order", income = false),
            "kw-leisure" to SmsCategoryInput(payee = "Unknown Merchant", body = "movie tickets", income = false),
            "kw-education" to SmsCategoryInput(payee = "Unknown Merchant", body = "tuition fee", income = false),
            "kw-insurance" to SmsCategoryInput(payee = "Unknown Merchant", body = "insurance renewal", income = false),
            "kw-shopping" to SmsCategoryInput(payee = "Unknown Merchant", body = "purchase", income = false),
            "own-account" to SmsCategoryInput(payee = "your account", income = false),
            "salary" to SmsCategoryInput(body = "Salary credited", income = true),
            "credit-card-bill" to SmsCategoryInput(body = "Credit card bill payment", income = false),
            "bank-transfer" to SmsCategoryInput(body = "IMPS transfer", income = false),
            "person-to-person" to SmsCategoryInput(payee = "AKASH S", body = upi, income = false)
        )
        assertEquals("Add a covering case for every rule id", SmsCategorizer.ruleIds, cases.keys)
        cases.forEach { (id, input) ->
            assertEquals("Case for $id matched a different rule", id, SmsCategorizer.categorize(input).rule)
        }
    }

    @Test
    fun editingACategoryMarksItManualButEditingOtherFieldsDoesNot() {
        assertEquals(CategoryOrigin.MANUAL, CategoryOrigin.afterEdit("Other", CategoryOrigin.AUTO, "Household"))
        assertEquals(CategoryOrigin.MANUAL, CategoryOrigin.afterEdit("Other", CategoryOrigin.UNKNOWN, "Shopping"))
        // Re-selecting "Other" explicitly over an automatic Other is not a change, so it stays automatic...
        assertEquals(CategoryOrigin.AUTO, CategoryOrigin.afterEdit("Other", CategoryOrigin.AUTO, "other"))
        // ...and saving an edit that leaves the category alone keeps whatever origin it had.
        assertEquals(CategoryOrigin.MANUAL, CategoryOrigin.afterEdit("Household", CategoryOrigin.MANUAL, "Household"))
        assertEquals(CategoryOrigin.UNKNOWN, CategoryOrigin.afterEdit("Other", CategoryOrigin.UNKNOWN, " Other "))
    }
}
