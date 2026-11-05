package com.openfinanceai

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TransactionCsvImportTest {
    @Test
    fun recognizesDateHeaderWithUtf8BomAndPreservesActualDates() {
        val csv = "\uFEFFDate,Description,Amount,Type,Category\n" +
            "2026-05-01,Salary,50000,Credit,Income\n" +
            "2026-05-02,Coffee,250,Debit,Food"

        val transactions = parseTransactionCsv(csv)

        assertEquals(2, transactions.size)
        assertEquals("2026-05-01", transactions[0].date)
        assertEquals("2026-05-02", transactions[1].date)
        assertTrue(transactions[0].income)
        assertFalse(transactions[1].income)
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsCsvWithoutDateColumnInsteadOfShowingImportedAsTheDate() {
        parseTransactionCsv("Description,Amount,Type\nCoffee,250,Debit")
    }
}
