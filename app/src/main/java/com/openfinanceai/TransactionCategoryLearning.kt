package com.openfinanceai

import android.content.Context
import org.json.JSONObject
import java.text.Normalizer
import java.util.Locale

/**
 * Learns only from an explicit manual category change and applies the learned
 * category to exact normalized transaction titles. It deliberately avoids
 * substring/fuzzy matching so unrelated merchants are never recategorized.
 */
internal object TransactionCategoryLearning {
    private const val PREFS = "open_finance_data"
    private const val KEY = "learned_transaction_categories"

    internal fun normalize(title: String): String =
        Normalizer.normalize(title, Normalizer.Form.NFKC)
            .trim()
            .replace(Regex("\\s+"), " ")
            .lowercase(Locale.ROOT)

    private fun mappings(context: Context): JSONObject {
        val raw = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY, "{}") ?: "{}"
        return runCatching { JSONObject(raw) }.getOrDefault(JSONObject())
    }

    /** Saves a user's explicit category choice and updates exact-title matches already stored. */
    fun learn(context: Context, title: String, category: String, transactions: List<Transaction>): List<Transaction> {
        val key = normalize(title)
        val value = category.trim()
        if (key.isBlank() || value.isBlank()) return transactions
        val learned = mappings(context).put(key, value)
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(KEY, learned.toString()).apply()
        return transactions.map { row ->
            if (!row.income && normalize(row.title) == key && row.category != value) {
                row.copy(category = value, categoryOrigin = CategoryOrigin.MANUAL)
            } else row
        }
    }

    /** Applies previously learned exact-title categories to newly imported transactions. */
    fun apply(context: Context, transactions: List<Transaction>): List<Transaction> {
        val learned = mappings(context)
        if (learned.length() == 0) return transactions
        return transactions.map { row ->
            if (row.income) row else learned.optString(normalize(row.title)).takeIf { it.isNotBlank() }?.let { category ->
                if (row.category == category) row else row.copy(category = category, categoryOrigin = CategoryOrigin.MANUAL)
            } ?: row
        }
    }
}
