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

    /** Pure exact-title lookup. Manual choices and income are never overwritten. */
    internal fun categoryFor(
        title: String,
        automaticCategory: String,
        learnedCategories: Map<String, String>,
        categoryOrigin: String = CategoryOrigin.AUTO,
        income: Boolean = false
    ): String {
        if (income || categoryOrigin == CategoryOrigin.MANUAL) return automaticCategory
        return learnedCategories[normalize(title)]?.takeIf { it.isNotBlank() } ?: automaticCategory
    }

    private fun mappingMap(context: Context): Map<String, String> {
        val json = mappings(context)
        return buildMap {
            val keys = json.keys()
            while (keys.hasNext()) {
                val key = keys.next()
                json.optString(key).takeIf { it.isNotBlank() }?.let { put(key, it) }
            }
        }
    }

    /** Resolves a new row's automatic category using the user's saved exact-title preference. */
    fun categoryFor(context: Context, title: String, automaticCategory: String, income: Boolean): String =
        categoryFor(title, automaticCategory, mappingMap(context), CategoryOrigin.AUTO, income)

    /** Applies previously learned exact-title categories to newly imported transactions. */
    fun apply(context: Context, transactions: List<Transaction>): List<Transaction> {
        val learned = mappingMap(context)
        if (learned.isEmpty()) return transactions
        return transactions.map { row ->
            val category = categoryFor(row.title, row.category, learned, row.categoryOrigin, row.income)
            if (category == row.category) row else row.copy(category = category, categoryOrigin = CategoryOrigin.MANUAL)
        }
    }
}
