package com.openfinanceai

import android.content.SharedPreferences
import org.json.JSONArray
import java.util.Locale

/**
 * Single source of truth for user-managed categories and tags.
 * An explicit initialized flag distinguishes a deliberately empty list from a first-run list.
 */
internal class FinanceManagementStore(
    private val preferences: SharedPreferences,
    private val defaultCategories: List<String>,
    private val defaultTags: List<String>
) {
    fun loadCategories(): List<String> =
        loadNames("custom_categories", "custom_categories_initialized", defaultCategories, stripHash = false)

    fun loadTags(): List<String> =
        loadNames("custom_tags", "custom_tags_initialized", defaultTags, stripHash = true)

    fun saveCategories(values: List<String>) =
        saveNames("custom_categories", "custom_categories_initialized", values)

    fun saveTags(values: List<String>) =
        saveNames("custom_tags", "custom_tags_initialized", values)

    fun addCategory(name: String): Boolean = addName(loadCategories(), name, ::saveCategories)
    fun addTag(name: String): Boolean = addName(loadTags(), name, ::saveTags)

    fun renameCategory(oldName: String, newName: String): Boolean =
        renameName("category", oldName, newName, loadCategories(), ::saveCategories)

    fun renameTag(oldName: String, newName: String): Boolean =
        renameName("tag", oldName, newName, loadTags(), ::saveTags)

    fun deleteCategory(name: String) {
        saveCategories(loadCategories().filterNot { it.equals(name, ignoreCase = true) })
        removeAppearance("category", name)
    }

    fun deleteTag(name: String) {
        saveTags(loadTags().filterNot { it.equals(name, ignoreCase = true) })
        removeAppearance("tag", name)
    }

    fun saveCategoryAppearance(name: String, iconKey: String, colorIndex: Int) =
        saveAppearance("category", name, iconKey, colorIndex)

    fun saveTagAppearance(name: String, iconKey: String, colorIndex: Int) =
        saveAppearance("tag", name, iconKey, colorIndex)

    private fun loadNames(
        storageKey: String,
        initializedKey: String,
        defaults: List<String>,
        stripHash: Boolean
    ): List<String> {
        val raw = preferences.getString(storageKey, null)
        if (raw.isNullOrBlank()) {
            return if (preferences.getBoolean(initializedKey, false)) emptyList() else defaults
        }
        return runCatching {
            val values = JSONArray(raw)
            (0 until values.length())
                .map { values.getString(it).trim().let { value -> if (stripHash) value.removePrefix("#").trim() else value } }
                .filter { it.isNotBlank() }
                .distinctBy { it.lowercase(Locale.ROOT) }
        }.getOrDefault(if (preferences.getBoolean(initializedKey, false)) emptyList() else defaults)
    }

    private fun saveNames(storageKey: String, initializedKey: String, values: List<String>) {
        val normalized = values.map { it.trim() }.filter { it.isNotBlank() }
            .distinctBy { it.lowercase(Locale.ROOT) }
        val array = JSONArray()
        normalized.forEach(array::put)
        preferences.edit()
            .putString(storageKey, array.toString())
            .putBoolean(initializedKey, true)
            .apply()
    }

    private fun addName(current: List<String>, rawName: String, save: (List<String>) -> Unit): Boolean {
        val name = rawName.trim().removePrefix("#").trim()
        if (name.isBlank() || current.any { it.equals(name, ignoreCase = true) }) return false
        save(current + name)
        return true
    }

    private fun renameName(
        type: String,
        oldName: String,
        rawNewName: String,
        current: List<String>,
        save: (List<String>) -> Unit
    ): Boolean {
        val newName = rawNewName.trim().removePrefix("#").trim()
        if (newName.isBlank() || current.none { it.equals(oldName, ignoreCase = true) }) return false
        if (current.any { it.equals(newName, ignoreCase = true) && !it.equals(oldName, ignoreCase = true) }) return false
        save(current.map { if (it.equals(oldName, ignoreCase = true)) newName else it })
        moveAppearance(type, oldName, newName)
        return true
    }

    private fun saveAppearance(type: String, name: String, iconKey: String, colorIndex: Int) {
        val suffix = name.trim().lowercase(Locale.ROOT)
        preferences.edit()
            .putString("${type}_icon_$suffix", iconKey)
            .putInt("${type}_color_index_$suffix", colorIndex)
            .apply()
    }

    private fun moveAppearance(type: String, oldName: String, newName: String) {
        val oldSuffix = oldName.trim().lowercase(Locale.ROOT)
        val newSuffix = newName.trim().lowercase(Locale.ROOT)
        if (oldSuffix == newSuffix) return
        val editor = preferences.edit()
        preferences.getString("${type}_icon_$oldSuffix", null)?.let { editor.putString("${type}_icon_$newSuffix", it) }
        if (preferences.contains("${type}_color_index_$oldSuffix")) {
            editor.putInt("${type}_color_index_$newSuffix", preferences.getInt("${type}_color_index_$oldSuffix", 0))
        }
        editor.remove("${type}_icon_$oldSuffix")
            .remove("${type}_color_index_$oldSuffix")
            .apply()
    }

    private fun removeAppearance(type: String, name: String) {
        val suffix = name.trim().lowercase(Locale.ROOT)
        preferences.edit()
            .remove("${type}_icon_$suffix")
            .remove("${type}_color_index_$suffix")
            .apply()
    }
}
