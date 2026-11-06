package com.openfinanceai

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class BankSmsParserTest {
    private val received = LocalDate.parse("2026-11-05")

    @Test
    fun parsesDebitMerchantAfterSemicolonEvenWhenPayeeIsSaidToBeCredited() {
        val parsed = BankSmsParser.parse(
            "ICICI Bank Acct XX450 debited for Rs 1000.00 on 04-Nov-26; AKASH S credited. UPI:667400510260. Call 18002662 for dispute.",
            received
        )

        assertEquals(1000L, parsed?.amount)
        assertFalse(parsed!!.income)
        assertEquals("2026-11-04", parsed.date)
        assertEquals("AKASH S", parsed.merchant)
    }

    @Test
    fun parsesCreditMerchantAndDateFromCreditAlert() {
        val parsed = BankSmsParser.parse(
            "Dear Customer, Acct XX450 is credited with Rs 13725.00 on 04-Nov-26 from Pocketly. UPI:630872836559-ICICI Bank.",
            received
        )

        assertEquals(13725L, parsed?.amount)
        assertTrue(parsed!!.income)
        assertEquals("2026-11-04", parsed.date)
        assertEquals("Pocketly", parsed.merchant)
    }

    @Test
    fun extractsUnlistedMerchantNamesWithoutMerchantWhitelist() {
        val parsed = BankSmsParser.parse(
            "ICICI Bank Acct XX450 debited for Rs 108.00 on 05-Nov-26; North Star Books credited. UPI:667522627906.",
            received
        )

        assertEquals("North Star Books", parsed?.merchant)
        assertFalse(parsed!!.income)
    }

    @Test
    fun extractsMerchantFromToPattern() {
        val parsed = BankSmsParser.parse(
            "Your account was debited for INR 245.50 on 05-Nov-26 to Metro Cafe. UPI:123456.",
            received
        )

        assertEquals(246L, parsed?.amount)
        assertEquals("245.5", parsed?.exactAmount)
        assertEquals("Metro Cafe", parsed?.merchant)
        assertFalse(parsed!!.income)
    }

    @Test
    fun ignoresScheduledFutureAutopayMandateNotice() {
        val parsed = BankSmsParser.parse(
            "ICICI Bank SAVINGS Account XX450 will be debited for Rs 4842.00 on 05-Nov-26 towards Autopay for Finnable, Create+Mandate, Unique Mandate Number abc123@ibl",
            LocalDate.parse("2026-11-04")
        )

        assertNull(parsed)
    }

    @Test
    fun ignoresOtpAndFailedMessages() {
        assertNull(BankSmsParser.parse("Your OTP is 123456 for Rs 1000.00", received))
        assertNull(BankSmsParser.parse("Transaction failed. Rs 250.00 not debited.", received))
    }

    @Test
    fun rejectsAmbiguousMessagesWithoutClearDebitOrCreditDirection() {
        assertNull(BankSmsParser.parse("ICICI Bank message: Rs 500.00 reference 12345", received))
    }

    @Test
    fun categorizesSmsMerchantsInsteadOfDefaultingEveryExpenseToBankTransfer() {
        assertEquals("Shopping", SmsTransactionImporter.categoryForSms("Amazon", "Paid to Amazon", false))
        assertEquals("Shopping", SmsTransactionImporter.categoryForSms("Flipkart", "UPI purchase", false))
        assertEquals("Taxi", SmsTransactionImporter.categoryForSms("Ola", "Ride payment", false))
        assertEquals("Taxi", SmsTransactionImporter.categoryForSms("Uber", "Trip payment", false))
        assertEquals("Food & Dining", SmsTransactionImporter.categoryForSms("Swiggy", "Order payment", false))
        assertEquals("Bank transfer", SmsTransactionImporter.categoryForSms(null, "IMPS transferred to account", false))
        assertEquals("Shopping", SmsTransactionImporter.categoryForSms("Amazon", "Transferred to Amazon by UPI", false))
        assertEquals("Other", SmsTransactionImporter.categoryForSms("Unknown Merchant", "Transferred to merchant by UPI", false))
        assertEquals("Other", SmsTransactionImporter.categoryForSms("Unknown Merchant", "Payment successful", false))
        assertEquals("Salary", SmsTransactionImporter.categoryForSms("ACME Payroll", "Salary credited", true))
    }

    @Test
    fun preservesExactPaiseValueForReviewWhileLegacyModelUsesRoundedRupees() {
        val parsed = BankSmsParser.parse(
            "ICICI Bank Acct XX450 debited for Rs 268.33 on 04-Nov-26; New Merchant credited. UPI:123456.",
            received
        )

        assertEquals(268L, parsed?.amount)
        assertEquals("268.33", parsed?.exactAmount)
        assertEquals("New Merchant", parsed?.merchant)
    }
}