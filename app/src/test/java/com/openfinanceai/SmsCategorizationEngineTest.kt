package com.openfinanceai

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SmsCategorizationEngineTest {
    private val defaults = listOf(
        "Food", "Food/Dining out", "Healthcare", "Income", "Leisure/Travel",
        "Savings/Emergency", "Transport/Fuel", "Transport/Taxi", "Utilities/Gas",
        "Utilities/Phone", "Food/Coffee", "Food/Takeout", "Household", "Leisure",
        "Other", "Savings/Investment", "Transport", "Transport/Public transit",
        "Utilities/Electricity", "Utilities/Internet", "Utilities/Water"
    )

    private fun category(sender: String? = null, payee: String? = null, description: String? = null) =
        SmsCategorizationEngine.categorize(sender, payee, description, false, defaults)

    @Test fun matchesCredFromSender() {
        val result = category(sender = "CREDCLUB")
        assertEquals("Other", result.category)
        assertEquals("brand_cred", result.iconKey)
        assertEquals("#64748B", result.colorHex)
    }

    @Test fun matchesCredFromPayee() {
        assertEquals("Other", category(payee = "CRED").category)
    }

    @Test fun matchesCredFromDescription() {
        assertEquals("Other", category(description = "Payment processed by CRED").category)
    }

    @Test fun matchesDreamplugFromSender() {
        assertEquals("Other", category(sender = "DREAMPLUG").category)
    }

    @Test fun matchesDreamplugFromPayee() {
        assertEquals("Other", category(payee = "Dreamplug Technologies").category)
    }

    @Test fun matchesDreamplugFromDescription() {
        assertEquals("Other", category(description = "UPI payment to Dreamplug").category)
    }

    @Test fun matchesAmazonFromSenderAndUsesExistingHouseholdCategory() {
        val result = category(sender = "AMAZON")
        assertEquals("Household", result.category)
        assertEquals("brand_amazon", result.iconKey)
        assertEquals("#DB4765", result.colorHex)
    }

    @Test fun matchesAmazonFromPayee() {
        assertEquals("Household", category(payee = "Amazon India").category)
    }

    @Test fun matchesAmazonFromDescription() {
        assertEquals("Household", category(description = "Purchase at Amazon").category)
    }

    @Test fun matchesFlipkartFromSender() {
        assertEquals("Household", category(sender = "FLIPKART").category)
    }

    @Test fun matchesFlipkartFromPayee() {
        assertEquals("Household", category(payee = "Flipkart Internet").category)
    }

    @Test fun matchesFlipkartFromDescription() {
        assertEquals("Household", category(description = "UPI payment on Flipkart").category)
    }

    @Test fun matchesSwiggyFromSender() {
        val result = category(sender = "SWIGGY")
        assertEquals("Food/Takeout", result.category)
        assertEquals("food", result.iconKey)
        assertEquals("#148D91", result.colorHex)
    }

    @Test fun matchesSwiggyFromPayee() {
        assertEquals("Food/Takeout", category(payee = "Swiggy Instamart").category)
    }

    @Test fun matchesSwiggyFromDescription() {
        assertEquals("Food/Takeout", category(description = "Order paid to Swiggy").category)
    }

    @Test fun matchesZomatoFromSender() {
        assertEquals("Food/Takeout", category(sender = "ZOMATO").category)
    }

    @Test fun matchesZomatoFromPayee() {
        assertEquals("Food/Takeout", category(payee = "Zomato").category)
    }

    @Test fun matchesZomatoFromDescription() {
        assertEquals("Food/Takeout", category(description = "UPI debit at Zomato").category)
    }

    @Test fun distinguishesOwnAccountTransfers() {
        val result = category(description = "IMPS transfer to my own account")
        assertEquals("OWN_ACCOUNT_TRANSFER", result.transferKind)
        assertEquals("Other", result.category)
    }

    @Test fun distinguishesBankTransfers() {
        val result = category(description = "NEFT fund transfer completed")
        assertEquals("BANK_TRANSFER", result.transferKind)
        assertEquals("Other", result.category)
    }

    @Test fun distinguishesPersonToPersonUpiTransfers() {
        val result = category(payee = "Rahul Kumar", description = "UPI transfer to Rahul Kumar")
        assertEquals("PERSON_UPI_TRANSFER", result.transferKind)
        assertEquals("Other", result.category)
    }

    @Test fun merchantRuleTakesPrecedenceOverGenericUpiTransferWords() {
        assertEquals("Household", category(payee = "Amazon", description = "UPI payment").category)
    }

    @Test fun usesTheUsersExistingCategorySpellingCaseInsensitively() {
        val result = SmsCategorizationEngine.categorize(null, "SWIGGY", "UPI payment", false, listOf("food/takeout", "Other"))
        assertEquals("food/takeout", result.category)
    }

    @Test fun fallsBackToOtherWhenSpecializedCategoryIsUnavailable() {
        val result = SmsCategorizationEngine.categorize(null, "Swiggy", "Paid", false, listOf("Food", "Other"))
        assertEquals("Other", result.category)
    }

    @Test fun classifiesSalaryIntoAnExistingIncomeCategory() {
        val result = SmsCategorizationEngine.categorize("BANK", "ACME Payroll", "Salary credited", true, defaults)
        assertEquals("Income", result.category)
    }

    @Test fun returnsStableCategoryIconAndColorForFood() {
        val result = category(payee = "Swiggy")
        assertTrue(result.iconKey.isNotBlank())
        assertTrue(result.colorHex.startsWith("#"))
    }
}
