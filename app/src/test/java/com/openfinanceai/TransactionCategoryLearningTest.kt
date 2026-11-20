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

    @Test
    fun learnedMerchantCategoryOverridesAutomaticCategoryForExactTitle() {
        val learned = mapOf(TransactionCategoryLearning.normalize("To APSRTC") to "Transport")
        assertEquals(
            "Transport",
            TransactionCategoryLearning.categoryFor("To APSRTC", SmsCategories.PUBLIC_TRANSIT, learned)
        )
    }

    @Test
    fun learnedCategoryDoesNotMatchDifferentMerchantOrSubstring() {
        val learned = mapOf(TransactionCategoryLearning.normalize("To APSRTC") to "Transport")
        assertEquals(
            SmsCategories.PUBLIC_TRANSIT,
            TransactionCategoryLearning.categoryFor("To APSRTC Express", SmsCategories.PUBLIC_TRANSIT, learned)
        )
    }

    @Test
    fun manualCategoryAndIncomeAreNeverOverwrittenByLearnedMerchantMapping() {
        val learned = mapOf(TransactionCategoryLearning.normalize("To APSRTC") to "Transport")
        assertEquals(
            "Healthcare",
            TransactionCategoryLearning.categoryFor("To APSRTC", "Healthcare", learned, CategoryOrigin.MANUAL)
        )
        assertEquals(
            SmsCategories.OTHER_INCOME,
            TransactionCategoryLearning.categoryFor("To APSRTC", SmsCategories.OTHER_INCOME, learned, income = true)
        )
    }

    private fun assertNotEquals(expected: String, actual: String) {
        org.junit.Assert.assertNotEquals(expected, actual)
    }
}
