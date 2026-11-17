package com.openfinanceai

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CreditCardSmsParserTest {
    @Test
    fun parsesAxisBankStatementWithoutLosingPaise() {
        val sms = "Your statement for Axis Bank Credit Card no. XX3810 is generated. " +
            "Due on: 30-11-26 Total amt: INR Dr. 7641.48 Min amt due: INR Dr. 7641.48"

        val statement = CreditCardSmsParser.parseStatement(sms)

        assertEquals("3810", statement?.last4)
        assertEquals("2026-11-30", statement?.dueDate)
        assertEquals(764148L, statement?.totalPaise)
        assertEquals(764148L, statement?.minimumPaise)
    }

    @Test
    fun rejectsInvalidStatementAmounts() {
        val sms = "Your statement for Axis Bank Credit Card no. XX3810 is generated. " +
            "Due on: 30-11-26 Total amt: INR Dr. 7641.48 Min amt due: INR Dr. 9000.00"
        assertNull(CreditCardSmsParser.parseStatement(sms))
    }

    @Test
    fun rejectsFailedCreditCardPaymentSms() {
        val sms = "Payment failed for Axis Bank Credit Card XX3810. INR 500.00"
        assertNull(CreditCardSmsParser.parsePayment(sms))
    }
    @Test
    fun parsesSuccessfulCreditCardPaymentWithoutLosingPaise() {
        val sms = "Payment of INR 1,250.75 received for Axis Bank Credit Card XX3810"
        val payment = CreditCardSmsParser.parsePayment(sms)
        assertEquals("3810", payment?.last4)
        assertEquals(125075L, payment?.amountPaise)
    }

}
