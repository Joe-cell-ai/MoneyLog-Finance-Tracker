package com.openfinanceai

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SmsRecategorizerTest {
    private fun sms(
        title: String,
        category: String = "Other",
        income: Boolean = false,
        source: String = "SMS auto import",
        key: String = "sms-import:ax-icicib-s:1762300000000:-12345",
        origin: String = CategoryOrigin.UNKNOWN,
        amount: Long = 100L,
        date: String = "2026-11-05"
    ) = Transaction(
        title = title, category = category, date = date, amount = amount, income = income,
        source = source, notificationKey = key, notes = "Imported automatically from a bank SMS.",
        categoryOrigin = origin
    )

    @Test
    fun onlySmsImportedExpensesLabelledOtherAreEligible() {
        assertTrue(SmsRecategorizer.isEligible(sms("To CRED Club")))
        assertTrue(SmsRecategorizer.isEligible(sms("To CRED Club", source = "SMS import")))
        assertTrue(SmsRecategorizer.isEligible(sms("To CRED Club", category = " other ")))
        assertFalse(SmsRecategorizer.isEligible(sms("To CRED Club", category = "Shopping")))
        assertFalse(SmsRecategorizer.isEligible(sms("From mobile number XXXXX52400", category = "Other income", income = true)))
        assertFalse(SmsRecategorizer.isEligible(sms("To CRED Club", income = true)))
        assertFalse(SmsRecategorizer.isEligible(sms("To CRED Club", source = "")))
        assertFalse(SmsRecategorizer.isEligible(sms("To CRED Club", source = "Notification")))
        assertFalse(SmsRecategorizer.isEligible(sms("To CRED Club").copy(loanLender = "HDFC")))
        assertFalse(SmsRecategorizer.isEligible(sms("To CRED Club").copy(transactionKind = "loan_payment")))
    }

    @Test
    fun manuallySelectedCategoriesAreNeverEligibleEvenWhenTheyAreOther() {
        val manual = sms("To CRED Club", origin = CategoryOrigin.MANUAL)
        assertFalse(SmsRecategorizer.isEligible(manual))
        assertTrue(SmsRecategorizer.propose(listOf(manual)).isEmpty())
        assertEquals(listOf(manual), SmsRecategorizer.apply(listOf(manual), listOf(RecategorizeProposal(manual, "Bills/Credit card", SmsPaymentKind.MERCHANT))))
    }

    @Test
    fun parsesSenderFromNotificationKeyAndPayeeFromTitle() {
        assertEquals("ax-icicib-s", SmsRecategorizer.senderFromKey("sms-import:ax-icicib-s:1762300000000:-12345"))
        assertEquals("", SmsRecategorizer.senderFromKey("notification:abc"))
        assertEquals("", SmsRecategorizer.senderFromKey(""))
        assertEquals("CRED Club", SmsRecategorizer.payeeFromTitle("To CRED Club"))
        assertEquals("mobile number XXXXX52400", SmsRecategorizer.payeeFromTitle("From mobile number XXXXX52400"))
        assertEquals(null, SmsRecategorizer.payeeFromTitle("Bank SMS · AX-ICICIB-S"))
        assertEquals(null, SmsRecategorizer.payeeFromTitle("Bank SMS transaction"))
    }

    @Test
    fun proposesCategoriesForTheTransactionsInTheReportedScreenshot() {
        val rows = listOf(
            sms("To VEERAMREDDY NAG", amount = 10000),
            sms("To ICICI Bank XX450", amount = 4842),
            sms("To CRED Club", amount = 871),
            sms("To your account", amount = 37640),
            sms("To Dreamplug Servi", amount = 27032),
            sms("From mobile number XXXXX52400", category = "Other income", income = true, amount = 9559)
        )
        val proposals = SmsRecategorizer.propose(rows).associate { it.transaction.title to it.newCategory }
        assertEquals(SmsCategories.PERSON_TO_PERSON, proposals["To VEERAMREDDY NAG"])
        assertEquals(SmsCategories.BANK_TRANSFER, proposals["To ICICI Bank XX450"])
        assertEquals(SmsCategories.CREDIT_CARD_BILL, proposals["To CRED Club"])
        assertEquals(SmsCategories.OWN_ACCOUNT_TRANSFER, proposals["To your account"])
        assertEquals(SmsCategories.CREDIT_CARD_BILL, proposals["To Dreamplug Servi"])
        assertEquals("income rows are not proposed", 5, proposals.size)
    }

    @Test
    fun knownOwnAccountKeysTurnAMaskedBankPayeeIntoAnOwnAccountTransfer() {
        val row = sms("To ICICI Bank XX450")
        assertEquals(SmsCategories.OWN_ACCOUNT_TRANSFER, SmsRecategorizer.propose(listOf(row), setOf("icici:450")).single().newCategory)
    }

    @Test
    fun rowsWithNoBetterCategoryAreNotProposed() {
        assertTrue(SmsRecategorizer.propose(listOf(sms("To Unknown Merchant"), sms("Bank SMS · AX-ICICIB-S"))).isEmpty())
    }

    @Test
    fun applyChangesOnlyApprovedRowsAndOnlyTheirCategoryAndOrigin() {
        val cred = sms("To CRED Club", amount = 871, key = "sms-import:a:1:1")
        val person = sms("To VEERAMREDDY NAG", amount = 10000, key = "sms-import:a:2:2")
        val untouched = sms("To Unknown Merchant", key = "sms-import:a:3:3")
        val shopping = sms("To Amazon", category = "Shopping", key = "sms-import:a:4:4", origin = CategoryOrigin.AUTO)
        val all = listOf(cred, person, untouched, shopping)

        val proposals = SmsRecategorizer.propose(all)
        val approved = proposals.filter { it.transaction == cred }
        val result = SmsRecategorizer.apply(all, approved)

        assertEquals(all.size, result.size)
        // Approved row: category and origin change, nothing else.
        assertEquals(cred.copy(category = SmsCategories.CREDIT_CARD_BILL, categoryOrigin = CategoryOrigin.AUTO), result[0])
        // Row the user did not approve, row with no proposal, and an already-categorised row are identical.
        assertEquals(person, result[1])
        assertEquals(untouched, result[2])
        assertEquals(shopping, result[3])
    }

    @Test
    fun applyPreservesEveryFieldThatFeedsDuplicateDetection() {
        val original = sms("To CRED Club", amount = 871, date = "2026-11-05", key = "sms-import:ax-icicib-s:1762300000000:-12345")
        val updated = SmsRecategorizer.apply(listOf(original), SmsRecategorizer.propose(listOf(original))).single()
        assertEquals(original.title, updated.title)
        assertEquals(original.date, updated.date)
        assertEquals(original.amount, updated.amount)
        assertEquals(original.income, updated.income)
        assertEquals(original.source, updated.source)
        assertEquals(original.notificationKey, updated.notificationKey)
        assertEquals(original.notes, updated.notes)
        assertEquals(original.tag, updated.tag)
        assertEquals(original.paymentMethod, updated.paymentMethod)
        assertEquals(original.copy(category = updated.category, categoryOrigin = updated.categoryOrigin), updated)
    }

    @Test
    fun applyingWithNothingApprovedChangesNothing() {
        val rows = listOf(sms("To CRED Club"))
        assertEquals(rows, SmsRecategorizer.apply(rows, emptyList()))
    }

    @Test
    fun aProposalIsSkippedIfTheTransactionWasChangedAfterThePreviewWasOpened() {
        val original = sms("To CRED Club")
        val proposal = SmsRecategorizer.propose(listOf(original)).single()
        val editedByUser = original.copy(category = "Household", categoryOrigin = CategoryOrigin.MANUAL)
        assertEquals(listOf(editedByUser), SmsRecategorizer.apply(listOf(editedByUser), listOf(proposal)))
    }

    @Test
    fun runningTheActionTwiceIsHarmless() {
        val rows = listOf(sms("To CRED Club"), sms("To your account", key = "sms-import:a:2:2"))
        val once = SmsRecategorizer.apply(rows, SmsRecategorizer.propose(rows))
        assertTrue(SmsRecategorizer.propose(once).isEmpty())
        assertEquals(once, SmsRecategorizer.apply(once, SmsRecategorizer.propose(once)))
    }
}
