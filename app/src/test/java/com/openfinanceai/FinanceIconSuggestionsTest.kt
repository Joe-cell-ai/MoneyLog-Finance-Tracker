package com.openfinanceai

import org.junit.Assert.assertEquals
import org.junit.Test

class FinanceIconSuggestionsTest {
    @Test
    fun recognizesSupportedBrandNamesBeforeGenericKeywords() {
        val cases = mapOf(
            "CRED" to "brand_cred",
            "Google Play" to "brand_google_play",
            "Amazon" to "brand_amazon",
            "Amazon Pay" to "brand_amazon_pay",
            "Apple Pay" to "brand_apple_pay",
            "GPay" to "brand_google_pay",
            "PhonePe" to "brand_phonepe",
            "Paytm" to "brand_paytm",
            "Swiggy" to "brand_swiggy",
            "Zomato" to "brand_zomato",
            "Netflix" to "brand_netflix",
            "Spotify" to "brand_spotify",
            "Uber Eats" to "brand_uber",
            "Flipkart" to "brand_flipkart",
            "Myntra" to "brand_myntra"
        )
        cases.forEach { (name, expected) ->
            assertEquals("Wrong icon suggestion for $name", expected, financeIconKeyForName(name))
        }
    }

    @Test
    fun recognizesCommonCategoryTermsWithoutPetrolCollision() {
        assertEquals("pets", financeIconKeyForName("Pet Care"))
        assertEquals("fuel", financeIconKeyForName("Petrol"))
        assertEquals("transport", financeIconKeyForName("Public Transit"))
        assertEquals("food", financeIconKeyForName("Food / Dining"))
        assertEquals("card", financeIconKeyForName("Credit Card"))
    }

    @Test
    fun unknownNamesReceiveGenericAppIcon() {
        assertEquals("app", financeIconKeyForName("Custom label xyz"))
    }
}
