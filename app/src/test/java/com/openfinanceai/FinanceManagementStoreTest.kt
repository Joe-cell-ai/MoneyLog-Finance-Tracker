package com.openfinanceai

import android.content.Context
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

@RunWith(RobolectricTestRunner::class)
class FinanceManagementStoreTest {
    private lateinit var store: FinanceManagementStore
    private val defaults = listOf("Food", "Transport")
    private val defaultTags = listOf("Credit Card", "Cash")

    @Before
    fun setUp() {
        val context = RuntimeEnvironment.getApplication()
        val preferences = context.getSharedPreferences("finance_management_test", Context.MODE_PRIVATE)
        preferences.edit().clear().commit()
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

        val preferences = RuntimeEnvironment.getApplication()
            .getSharedPreferences("finance_management_test", Context.MODE_PRIVATE)
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

        val preferences = RuntimeEnvironment.getApplication()
            .getSharedPreferences("finance_management_test", Context.MODE_PRIVATE)
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
