package com.openfinanceai

import org.junit.Assert.assertEquals
import org.junit.Test

class TransactionCategoryLearningTest {
    @Test
    fun merchantTitleNormalizationIsCaseAndWhitespaceInsensitive() {
        assertEquals(
            TransactionCategoryLearning.normalize("  APSRTC   BUS  "),
            TransactionCategoryLearning.normalize("apsrtc bus")
        )
    }

    @Test
    fun normalizationPreservesDistinctMerchantNames() {
        assertEquals("amazon fresh", TransactionCategoryLearning.normalize("Amazon Fresh"))
        assertEquals("amazon", TransactionCategoryLearning.normalize("Amazon"))
        assertNotEquals(TransactionCategoryLearning.normalize("Amazon Fresh"), TransactionCategoryLearning.normalize("Amazon"))
    }

    private fun assertNotEquals(expected: String, actual: String) {
        org.junit.Assert.assertNotEquals(expected, actual)
    }
}
