package com.openfinanceai

import android.content.SharedPreferences
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class FinanceManagementStoreTest {
    private lateinit var store: FinanceManagementStore
    private lateinit var preferences: SharedPreferences
    private val defaults = listOf("Food", "Transport")
    private val defaultTags = listOf("Credit Card", "Cash")

    @Before
    fun setUp() {
        preferences = InMemorySharedPreferences()
        store = FinanceManagementStore(preferences, defaults, defaultTags)
    }

    @Test
    fun firstRunLoadsDefaultCategoriesAndTags() {
        assertEquals(defaults, store.loadCategories())
        assertEquals(defaultTags, store.loadTags())
    }

    @Test
    fun categoryCreateRenameAndDeletePersistAcrossReload() {
        assertTrue(store.addCategory("Fuel"))
        assertFalse(store.addCategory(" fuel "))
        store.saveCategoryAppearance("Fuel", "fuel", 2)

        assertTrue(store.renameCategory("Fuel", "Petrol"))
        assertEquals(defaults + "Petrol", store.loadCategories())

        assertEquals("fuel", preferences.getString("category_icon_petrol", null))
        assertEquals(2, preferences.getInt("category_color_index_petrol", -1))
        assertFalse(preferences.contains("category_icon_fuel"))

        store.deleteCategory("Petrol")
        assertEquals(defaults, store.loadCategories())
        assertFalse(preferences.contains("category_icon_petrol"))
    }

    @Test
    fun deletingEveryCategoryPersistsAnEmptyListInsteadOfRestoringDefaults() {
        defaults.forEach(store::deleteCategory)
        assertTrue(store.loadCategories().isEmpty())
        assertTrue(store.loadCategories().isEmpty())
    }

    @Test
    fun tagCreateRenameAndDeletePersistAcrossReload() {
        assertTrue(store.addTag("#Amazon"))
        assertFalse(store.addTag(" amazon "))
        store.saveTagAppearance("Amazon", "brand_amazon", 4)

        assertTrue(store.renameTag("Amazon", "Amazon Pay"))
        assertEquals(defaultTags + "Amazon Pay", store.loadTags())

        assertEquals("brand_amazon", preferences.getString("tag_icon_amazon pay", null))
        assertEquals(4, preferences.getInt("tag_color_index_amazon pay", -1))

        store.deleteTag("Amazon Pay")
        assertEquals(defaultTags, store.loadTags())
        assertFalse(preferences.contains("tag_icon_amazon pay"))
    }

    @Test
    fun deletingEveryTagPersistsAnEmptyListInsteadOfRestoringDefaults() {
        defaultTags.forEach(store::deleteTag)
        assertTrue(store.loadTags().isEmpty())
        assertTrue(store.loadTags().isEmpty())
    }

    @Test
    fun brandAndCategoryNamesResolveToSpecificOfflineIconKeys() {
        assertEquals("brand_cred", financeIconKeyForName("CRED"))
        assertEquals("brand_phonepe", financeIconKeyForName("PhonePe"))
        assertEquals("brand_swiggy", financeIconKeyForName("Swiggy"))
        assertEquals("brand_zomato", financeIconKeyForName("Zomato"))
        assertEquals("brand_google_pay", financeIconKeyForName("Google Pay"))
        assertEquals("brand_amazon", financeIconKeyForName("Amazon"))
        assertEquals("brand_amazon_pay", financeIconKeyForName("Amazon Pay"))
        assertEquals("brand_paypal", financeIconKeyForName("PayPal"))
        assertEquals("brand_apple_pay", financeIconKeyForName("Apple Pay"))
        assertEquals("brand_google_play", financeIconKeyForName("Google Play"))
        assertEquals("brand_myntra", financeIconKeyForName("MYNTRA"))
        assertEquals("pets", financeIconKeyForName("Pet Care"))
        assertEquals("food", financeIconKeyForName("Restaurant"))
        assertEquals("flight", financeIconKeyForName("Flight"))
        assertEquals("fuel", financeIconKeyForName("Fuel"))
        assertEquals("health", financeIconKeyForName("Medical"))
        assertEquals("app", financeIconKeyForName("Unknown Merchant"))
    }

    @Test
    fun renameRejectsDuplicateNamesWithoutChangingStoredItems() {
        assertFalse(store.renameCategory("Food", "Transport"))
        assertEquals(defaults, store.loadCategories())
        assertFalse(store.renameTag("Cash", "Credit Card"))
        assertEquals(defaultTags, store.loadTags())
    }
}


/** Small deterministic SharedPreferences fake; these tests verify store semantics, not Android runtime behavior. */
private class InMemorySharedPreferences : SharedPreferences {
    private val values = mutableMapOf<String, Any?>()

    override fun getAll(): MutableMap<String, *> = values.filterValues { it != null }.toMutableMap()
    override fun getString(key: String?, defValue: String?): String? = (values[key] as? String) ?: defValue
    @Suppress("UNCHECKED_CAST")
    override fun getStringSet(key: String?, defValues: MutableSet<String>?): MutableSet<String>? =
        (values[key] as? Set<String>)?.toMutableSet() ?: defValues?.toMutableSet()
    override fun getInt(key: String?, defValue: Int): Int = values[key] as? Int ?: defValue
    override fun getLong(key: String?, defValue: Long): Long = values[key] as? Long ?: defValue
    override fun getFloat(key: String?, defValue: Float): Float = values[key] as? Float ?: defValue
    override fun getBoolean(key: String?, defValue: Boolean): Boolean = values[key] as? Boolean ?: defValue
    override fun contains(key: String?): Boolean = values.containsKey(key)
    override fun edit(): SharedPreferences.Editor = MemoryEditor()
    override fun registerOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener?) = Unit
    override fun unregisterOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener?) = Unit

    private inner class MemoryEditor : SharedPreferences.Editor {
        private val updates = mutableMapOf<String, Any?>()
        private val removals = mutableSetOf<String>()
        private var clearRequested = false

        override fun putString(key: String?, value: String?): SharedPreferences.Editor = apply { updates[key!!] = value; removals.remove(key) }
        override fun putStringSet(key: String?, values: MutableSet<String>?): SharedPreferences.Editor = apply { updates[key!!] = values?.toSet(); removals.remove(key) }
        override fun putInt(key: String?, value: Int): SharedPreferences.Editor = apply { updates[key!!] = value; removals.remove(key) }
        override fun putLong(key: String?, value: Long): SharedPreferences.Editor = apply { updates[key!!] = value; removals.remove(key) }
        override fun putFloat(key: String?, value: Float): SharedPreferences.Editor = apply { updates[key!!] = value; removals.remove(key) }
        override fun putBoolean(key: String?, value: Boolean): SharedPreferences.Editor = apply { updates[key!!] = value; removals.remove(key) }
        override fun remove(key: String?): SharedPreferences.Editor = apply { key?.let { removals.add(it); updates.remove(it) } }
        override fun clear(): SharedPreferences.Editor = apply { clearRequested = true; updates.clear(); removals.clear() }
        override fun commit(): Boolean { apply(); return true }
        override fun apply() {
            if (clearRequested) values.clear()
            removals.forEach(values::remove)
            updates.forEach { (key, value) -> if (value == null) values.remove(key) else values[key] = value }
        }
    }
}
