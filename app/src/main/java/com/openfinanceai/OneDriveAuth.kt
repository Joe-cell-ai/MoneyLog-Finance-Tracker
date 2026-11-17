package com.openfinanceai

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey

data class OneDriveDeviceCode(
    val deviceCode: String,
    val userCode: String,
    val verificationUri: String,
    val message: String,
    val expiresInSeconds: Long,
    val intervalSeconds: Long
)

data class OneDriveToken(val accessToken: String, val refreshToken: String)

object OneDriveAuth {
    private const val PREFS = "open_finance_data"
    private const val TOKEN_KEY = "onedrive_refresh_token_encrypted"
    private const val KEY_ALIAS = "moneylog_onedrive_refresh_v1"
    private const val DEVICE_CODE_URL = "https://login.microsoftonline.com/consumers/oauth2/v2.0/devicecode"
    private const val TOKEN_URL = "https://login.microsoftonline.com/consumers/oauth2/v2.0/token"
    private const val SCOPE = "Files.ReadWrite offline_access"

    fun hasRefreshToken(context: Context): Boolean =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).contains(TOKEN_KEY)

    fun requestDeviceCode(clientId: String): OneDriveDeviceCode {
        require(clientId.isNotBlank()) { "Microsoft app Client ID is not configured in this build." }
        val json = postForm(DEVICE_CODE_URL, mapOf("client_id" to clientId, "scope" to SCOPE))
        check(!json.has("error")) { json.optString("error_description", json.optString("error", "Microsoft sign-in could not start.")) }
        return OneDriveDeviceCode(
            deviceCode = json.getString("device_code"),
            userCode = json.getString("user_code"),
            verificationUri = json.optString("verification_uri", "https://microsoft.com/devicelogin"),
            message = json.optString("message", "Open the Microsoft sign-in page and enter this code."),
            expiresInSeconds = json.optLong("expires_in", 900L),
            intervalSeconds = json.optLong("interval", 5L)
        )
    }

    /** Returns null while Microsoft is still waiting for the user to approve the device. */
    fun pollForToken(clientId: String, deviceCode: String): OneDriveToken? {
        val json = postForm(TOKEN_URL, mapOf(
            "grant_type" to "urn:ietf:params:oauth:grant-type:device_code",
            "client_id" to clientId,
            "device_code" to deviceCode
        ))
        when (json.optString("error")) {
            "authorization_pending" -> return null
            "slow_down" -> throw IllegalStateException("slow_down")
            "authorization_declined" -> throw IllegalStateException("Microsoft sign-in was declined.")
            "expired_token" -> throw IllegalStateException("The Microsoft sign-in code expired. Please try again.")
            "" -> Unit
            else -> throw IllegalStateException(json.optString("error_description", json.optString("error", "Microsoft sign-in failed.")))
        }
        return OneDriveToken(json.getString("access_token"), json.getString("refresh_token"))
    }

    fun saveRefreshToken(context: Context, refreshToken: String) {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, getOrCreateKey())
        val encrypted = cipher.doFinal(refreshToken.toByteArray(Charsets.UTF_8))
        val packed = cipher.iv + encrypted
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString(TOKEN_KEY, Base64.encodeToString(packed, Base64.NO_WRAP))
            .apply()
    }

    fun accessToken(context: Context): String {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val packed = Base64.decode(prefs.getString(TOKEN_KEY, null) ?: error("Connect OneDrive before backing up."), Base64.NO_WRAP)
        require(packed.size > 12) { "Saved OneDrive sign-in is invalid. Reconnect OneDrive." }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, getOrCreateKey(), javax.crypto.spec.GCMParameterSpec(128, packed.copyOfRange(0, 12)))
        val refreshToken = String(cipher.doFinal(packed.copyOfRange(12, packed.size)), Charsets.UTF_8)
        val json = postForm(TOKEN_URL, mapOf(
            "client_id" to BuildConfig.MICROSOFT_CLIENT_ID,
            "grant_type" to "refresh_token",
            "refresh_token" to refreshToken,
            "scope" to SCOPE
        ))
        check(!json.has("error")) {
            if (json.optString("error") == "invalid_grant") "OneDrive sign-in expired. Reconnect OneDrive."
            else json.optString("error_description", json.optString("error", "Could not refresh OneDrive sign-in."))
        }
        json.optString("refresh_token").takeIf { it.isNotBlank() }?.let { saveRefreshToken(context, it) }
        return json.getString("access_token")
    }

    fun disconnect(context: Context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().remove(TOKEN_KEY).apply()
        runCatching {
            val store = KeyStore.getInstance("AndroidKeyStore")
            store.load(null)
            store.deleteEntry(KEY_ALIAS)
        }
    }

    private fun getOrCreateKey(): SecretKey {
        val store = KeyStore.getInstance("AndroidKeyStore")
        store.load(null)
        if (store.containsAlias(KEY_ALIAS)) return store.getKey(KEY_ALIAS, null) as SecretKey
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        generator.init(KeyGenParameterSpec.Builder(
            KEY_ALIAS,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
        ).setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setRandomizedEncryptionRequired(true)
            .build())
        return generator.generateKey()
    }

    private fun postForm(endpoint: String, fields: Map<String, String>): JSONObject {
        val body = fields.entries.joinToString("&") {
            URLEncoder.encode(it.key, "UTF-8") + "=" + URLEncoder.encode(it.value, "UTF-8")
        }
        val connection = (URL(endpoint).openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = 20000
            readTimeout = 20000
            doOutput = true
            setRequestProperty("Content-Type", "application/x-www-form-urlencoded")
            setRequestProperty("Accept", "application/json")
        }
        return try {
            connection.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
            val stream = if (connection.responseCode in 200..299) connection.inputStream else connection.errorStream
            val raw = stream?.bufferedReader()?.use { it.readText() }.orEmpty()
            JSONObject(raw.ifBlank { "{}" })
        } finally {
            connection.disconnect()
        }
    }
}
