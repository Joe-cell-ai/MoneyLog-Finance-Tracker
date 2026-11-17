package com.openfinanceai

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

data class OneDriveBackupItem(val id: String, val name: String, val modified: String)

object OneDriveGraph {
    private const val BASE = "https://graph.microsoft.com/v1.0"
    private const val FOLDER_NAME = "MoneyLog Backups"

    fun uploadBackup(context: Context, fileName: String, json: String) {
        val token = OneDriveAuth.accessToken(context)
        val folderId = getOrCreateBackupFolder(token)
        val encodedName = encode(fileName)
        val response = request(
            "PUT",
            "$BASE/me/drive/items/$folderId:/$encodedName:/content",
            token,
            "application/json",
            json.toByteArray(Charsets.UTF_8)
        )
        check(response.status in 200..201) { graphError(response, "OneDrive upload failed") }
        pruneOldBackups(token, folderId, 30)
    }

    fun listBackups(context: Context): List<OneDriveBackupItem> {
        val token = OneDriveAuth.accessToken(context)
        val folderId = getOrCreateBackupFolder(token)
        val response = request(
            "GET",
            "$BASE/me/drive/items/$folderId/children?\$select=id,name,lastModifiedDateTime&\$top=200",
            token
        )
        check(response.status in 200..299) { graphError(response, "Could not list OneDrive backups") }
        val items = JSONObject(response.body).optJSONArray("value") ?: JSONArray()
        return (0 until items.length()).mapNotNull { index ->
            val item = items.optJSONObject(index) ?: return@mapNotNull null
            val name = item.optString("name")
            if (!name.startsWith("MoneyLog-backup-") || !name.endsWith(".json")) return@mapNotNull null
            OneDriveBackupItem(item.optString("id"), name, item.optString("lastModifiedDateTime"))
        }.sortedByDescending { it.modified }
    }

    fun downloadBackup(context: Context, itemId: String): String {
        val token = OneDriveAuth.accessToken(context)
        val response = request("GET", "$BASE/me/drive/items/$itemId/content", token)
        if (response.status == HttpURLConnection.HTTP_MOVED_TEMP ||
            response.status == HttpURLConnection.HTTP_MOVED_PERM ||
            response.status == 303) {
            val location = response.location ?: error("OneDrive did not provide a download link.")
            val download = (URL(location).openConnection() as HttpURLConnection).apply {
                connectTimeout = 20000
                readTimeout = 60000
                instanceFollowRedirects = true
            }
            return try {
                check(download.responseCode in 200..299) { "Could not download the selected OneDrive backup." }
                download.inputStream.bufferedReader().use { it.readText() }
            } finally {
                download.disconnect()
            }
        }
        check(response.status in 200..299) { graphError(response, "Could not download the selected OneDrive backup") }
        return response.body
    }

    private fun getOrCreateBackupFolder(token: String): String {
        val path = "$BASE/me/drive/root:/MoneyLog%20Backups"
        val existing = request("GET", path, token)
        if (existing.status in 200..299) return JSONObject(existing.body).getString("id")
        check(existing.status == 404) { graphError(existing, "Could not access the OneDrive backup folder") }

        val createBody = JSONObject()
            .put("name", FOLDER_NAME)
            .put("folder", JSONObject())
            .put("@microsoft.graph.conflictBehavior", "fail")
            .toString().toByteArray(Charsets.UTF_8)
        val created = request("POST", "$BASE/me/drive/root/children", token, "application/json", createBody)
        if (created.status in 200..201) return JSONObject(created.body).getString("id")
        if (created.status == 409) {
            val retry = request("GET", path, token)
            check(retry.status in 200..299) { graphError(retry, "Could not open the OneDrive backup folder") }
            return JSONObject(retry.body).getString("id")
        }
        error(graphError(created, "Could not create the OneDrive backup folder"))
    }

    private fun pruneOldBackups(token: String, folderId: String, retention: Int) {
        val response = request(
            "GET",
            "$BASE/me/drive/items/$folderId/children?\$select=id,name,lastModifiedDateTime&\$top=200",
            token
        )
        check(response.status in 200..299) { graphError(response, "Could not check OneDrive backup retention") }
        val items = JSONObject(response.body).optJSONArray("value") ?: JSONArray()
        val backups = (0 until items.length()).mapNotNull { index ->
            val item = items.optJSONObject(index) ?: return@mapNotNull null
            val name = item.optString("name")
            if (!name.startsWith("MoneyLog-backup-") || !name.endsWith(".json")) return@mapNotNull null
            item.optString("id").takeIf { it.isNotBlank() }?.let { it to item.optString("lastModifiedDateTime") }
        }.sortedByDescending { it.second }
        backups.drop(retention).forEach { (id, _) ->
            val deleted = request("DELETE", "$BASE/me/drive/items/$id", token)
            check(deleted.status == 204 || deleted.status in 200..299) {
                graphError(deleted, "Could not remove an expired backup")
            }
        }
    }

    private data class GraphResponse(val status: Int, val body: String, val location: String?)

    private fun request(
        method: String,
        endpoint: String,
        token: String,
        contentType: String? = null,
        body: ByteArray? = null
    ): GraphResponse {
        val connection = (URL(endpoint).openConnection() as HttpURLConnection).apply {
            requestMethod = method
            connectTimeout = 20000
            readTimeout = 60000
            instanceFollowRedirects = false
            setRequestProperty("Authorization", "Bearer $token")
            setRequestProperty("Accept", "application/json")
            if (contentType != null) setRequestProperty("Content-Type", contentType)
            if (body != null) doOutput = true
        }
        return try {
            if (body != null) connection.outputStream.use { it.write(body) }
            val status = connection.responseCode
            val stream = if (status in 200..299) connection.inputStream else connection.errorStream
            val text = stream?.bufferedReader()?.use { it.readText() }.orEmpty()
            GraphResponse(status, text, connection.getHeaderField("Location"))
        } finally {
            connection.disconnect()
        }
    }

    private fun graphError(response: GraphResponse, fallback: String): String {
        val detail = runCatching {
            JSONObject(response.body).optJSONObject("error")?.optString("message").orEmpty()
        }.getOrDefault("")
        return if (detail.isBlank()) "$fallback (HTTP " + response.status + ")" else "$fallback: $detail"
    }

    private fun encode(value: String): String = URLEncoder.encode(value, "UTF-8").replace("+", "%20")
}
