package com.openfinanceai

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SmsTransactionImporterTest {
    @Test
    fun detectsSameSmsBodyEvenWhenInboxAndReceiverTimestampsDiffer() {
        val hash = "To SAI SRINIVAS NE Rs 14".hashCode()

        assertTrue(
            SmsTransactionImporter.isSameSmsMessage(
                "sms-import:ax-icicib-s:1794391200000:$hash",
                "AX-ICICIB-S",
                hash
            )
        )
    }

    @Test
    fun doesNotTreatDifferentMessagesOrSendersAsDuplicates() {
        val hash = "To SAI SRINIVAS NE Rs 14".hashCode()

        assertFalse(
            SmsTransactionImporter.isSameSmsMessage(
                "sms-import:ax-icicib-s:1794391200000:${hash + 1}",
                "AX-ICICIB-S",
                hash
            )
        )
        assertFalse(
            SmsTransactionImporter.isSameSmsMessage(
                "sms-import:other-bank:1794391200000:$hash",
                "AX-ICICIB-S",
                hash
            )
        )
    }

    @Test
    fun ignoresNonSmsNotificationKeys() {
        assertFalse(SmsTransactionImporter.isSameSmsMessage("notification:abc", "AX-ICICIB-S", 123))
    }
}
