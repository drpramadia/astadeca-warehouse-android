package com.astadeca.warehouse.data

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.astadeca.warehouse.BuildConfig
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/** Sesi tidak bisa dipulihkan (refresh token kedaluwarsa/ditolak). UI harus kembali ke login. */
class SessionExpiredException : Exception("Sesi berakhir. Silakan masuk lagi.")

/**
 * Klien Supabase via REST. Tidak pakai SDK Supabase agar APK ringan dan
 * tidak ada shared build config dengan aplikasi web.
 *
 * Kontrak server (identik dengan web):
 *   - POST /rest/v1/rpc/login_with_username  { p_username } -> { success, email }
 *   - POST /auth/v1/token?grant_type=password { email, password } -> access_token
 *   - GET  /rest/v1/<table>?...             dengan header Authorization
 *   - POST /rest/v1/rpc/<fn>                untuk fungsi
 */
class SupabaseClient(context: Context) {

    private val baseUrl = BuildConfig.SUPABASE_URL.trimEnd('/')
    private val anonKey = BuildConfig.SUPABASE_ANON_KEY

    private val http = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    private val prefs = run {
        val key = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        EncryptedSharedPreferences.create(
            context,
            "astadeca_session",
            key,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
        )
    }

    var accessToken: String? = null
        private set

    var refreshToken: String? = null
        private set

    var userId: String? = null
        private set

    val isLoggedIn: Boolean get() = !accessToken.isNullOrEmpty()

    init {
        restoreSession()
    }

    private fun restoreSession() {
        accessToken = prefs.getString(KEY_ACCESS, null)
        refreshToken = prefs.getString(KEY_REFRESH, null)
        userId = prefs.getString(KEY_USER, null)
    }

    fun saveSession(access: String, refresh: String?, user: String?) {
        accessToken = access
        refreshToken = refresh
        userId = user
        prefs.edit()
            .putString(KEY_ACCESS, access)
            .putString(KEY_REFRESH, refresh)
            .putString(KEY_USER, user)
            .apply()
    }

    fun clearSession() {
        accessToken = null
        refreshToken = null
        userId = null
        prefs.edit().clear().apply()
    }

    /** Langkah 1: ubah username jadi email (lewat RPC yang sudah ada di web). */
    fun resolveUsername(username: String): String? {
        val body = JSONObject().put("p_username", username).toString()
        val json = postJson("$baseUrl/rest/v1/rpc/login_with_username", body, authed = false)
        return if (json.optBoolean("success")) json.optString("email").takeIf { it.isNotBlank() } else null
    }

    fun usernameError(username: String): String? {
        val body = JSONObject().put("p_username", username).toString()
        val json = postJson("$baseUrl/rest/v1/rpc/login_with_username", body, authed = false)
        return if (json.optBoolean("success")) null else json.optString("error", "Username tidak ditemukan.")
    }

    /** Langkah 2: tukar email+password jadi token. */
    fun signInWithPassword(email: String, password: String): Result<Unit> {
        val body = JSONObject()
            .put("email", email)
            .put("password", password)
            .toString()
        return try {
            val req = Request.Builder()
                .url("$baseUrl/auth/v1/token?grant_type=password")
                .header("apikey", anonKey)
                .header("Content-Type", "application/json")
                .post(body.toRequestBody(JSON))
                .build()
            http.newCall(req).execute().use { resp ->
                val text = resp.body?.string().orEmpty()
                if (!resp.isSuccessful) {
                    val msg = runCatching { JSONObject(text).optString("error_description") }
                        .getOrNull()?.takeIf { it.isNotBlank() }
                        ?: runCatching { JSONObject(text).optString("msg") }.getOrNull()
                        ?: "Login gagal (${resp.code})."
                    return Result.failure(Exception(msg))
                }
                val json = JSONObject(text)
                val access = json.optString("access_token")
                if (access.isBlank()) return Result.failure(Exception("Token tidak diterima."))
                val refresh = json.optString("refresh_token").takeIf { it.isNotBlank() }
                val uid = json.optJSONObject("user")?.optString("id")?.takeIf { it.isNotBlank() }
                saveSession(access, refresh, uid)
                Result.success(Unit)
            }
        } catch (e: Exception) {
            Result.failure(Exception("Gagal terhubung: ${e.message}"))
        }
    }

    /** Segarkan token; true bila berhasil. Refresh token Supabase single-use, jadi serialkan. */
    @Synchronized
    fun refreshSession(): Boolean {
        val rt = refreshToken ?: return false
        val body = JSONObject().put("refresh_token", rt).toString()
        val req = Request.Builder()
            .url("$baseUrl/auth/v1/token?grant_type=refresh_token")
            .header("apikey", anonKey)
            .header("Content-Type", "application/json")
            .post(body.toRequestBody(JSON))
            .build()
        return try {
            http.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return false
                val json = JSONObject(resp.body?.string().orEmpty())
                val access = json.optString("access_token")
                if (access.isBlank()) return false
                saveSession(access, json.optString("refresh_token").takeIf { it.isNotBlank() }, userId)
                true
            }
        } catch (_: Exception) {
            false
        }
    }

    /**
     * Respons 401 pada request terautentikasi: coba refresh sekali.
     * Return true bila token sudah diperbarui (pemanggil boleh retry).
     * Return false kalau refresh gagal — sesi dibersihkan supaya UI kembali ke login.
     */
    private fun recoverFrom401(retryOn401: Boolean): Boolean {
        if (!retryOn401) {
            clearSession()
            throw SessionExpiredException()
        }
        if (refreshSession()) return true
        clearSession()
        throw SessionExpiredException()
    }

    /** GET tabel dengan query PostgREST, contoh: select=*&order=created_at.desc&limit=50 */
    fun get(table: String, query: String = "", retryOn401: Boolean = true): JSONArray {
        val url = buildString {
            append("$baseUrl/rest/v1/").append(table)
            if (query.isNotBlank()) append('?').append(query)
        }
        val req = Request.Builder()
            .url(url)
            .header("apikey", anonKey)
            .header("Authorization", "Bearer ${accessToken.orEmpty()}")
            .header("Accept", "application/json")
            .get()
            .build()
        http.newCall(req).execute().use { resp ->
            val text = resp.body?.string().orEmpty()
            if (resp.code == 401 && retryOn401 && recoverFrom401(retryOn401)) return get(table, query, retryOn401 = false)
            if (!resp.isSuccessful) throw Exception(errorMessage(text, resp.code))
            return if (text.isBlank()) JSONArray() else JSONArray(text)
        }
    }

    /** RPC umum. Melempar bila gagal — pemanggil menampilkan pesannya. */
    fun rpc(fn: String, params: JSONObject, retryOn401: Boolean = true): org.json.JSONTokener {
        val json = postJson("$baseUrl/rest/v1/rpc/$fn", params.toString(), authed = true, retryOn401 = retryOn401)
        return org.json.JSONTokener(json.toString())
    }

    /** INSERT satu baris, kembalikan id-nya (kolom id harus ada di tabel). */
    fun insert(table: String, body: JSONObject): String {
        val arr = JSONArray().put(body)
        val res = requestJson(
            method = "POST",
            url = "$baseUrl/rest/v1/$table?select=id",
            body = arr.toString(),
            authed = true,
            extraHeader = "Prefer" to "return=representation",
        )
        val json = JSONArray(res)
        if (json.length() == 0) throw Exception("Server tidak mengembalikan baris yang dibuat.")
        return json.getJSONObject(0).optString("id").takeIf { it.isNotBlank() }
            ?: throw Exception("Baris dibuat tanpa id.")
    }

    /** INSERT banyak baris sekaligus. */
    fun insertMany(table: String, rows: JSONArray) {
        if (rows.length() == 0) return
        requestJson(
            method = "POST",
            url = "$baseUrl/rest/v1/$table",
            body = rows.toString(),
            authed = true,
            extraHeader = "Prefer" to "return=minimal",
        )
    }

    /**
     * PATCH dengan filter, mengembalikan baris yang berubah.
     * List kosong berarti tidak ada baris cocok (mis. guard tidak terpenuhi).
     */
    fun updateReturning(table: String, query: String, body: JSONObject): JSONArray {
        val url = "$baseUrl/rest/v1/$table" + if (query.isBlank()) "" else "?$query"
        val payload = JSONArray().put(body).toString() // PostgREST PATCH menerima objek maupun array
        val res = requestJson(
            method = "PATCH",
            url = url,
            body = payload,
            authed = true,
            extraHeader = "Prefer" to "return=representation",
        )
        return runCatching { JSONArray(res) }.getOrDefault(JSONArray())
    }

    /** DELETE dengan filter. */
    fun delete(table: String, query: String) {
        val url = "$baseUrl/rest/v1/$table" + if (query.isBlank()) "" else "?$query"
        requestJson(method = "DELETE", url = url, body = null, authed = true, extraHeader = "Prefer" to "return=minimal")
    }

    /** POST/PATCH/DELETE JSON generik. Melempar dengan pesan server bila gagal. */
    private fun requestJson(
        method: String,
        url: String,
        body: String?,
        authed: Boolean,
        extraHeader: Pair<String, String>? = null,
        retryOn401: Boolean = true,
    ): String {
        val builder = Request.Builder().url(url)
            .header("apikey", anonKey)
            .header("Accept", "application/json")
            .header("Content-Type", "application/json")
        if (authed) builder.header("Authorization", "Bearer ${accessToken.orEmpty()}")
        extraHeader?.let { builder.header(it.first, it.second) }

        val rb = (body ?: "").toRequestBody(JSON)
        when (method) {
            "POST" -> builder.post(rb)
            "PATCH" -> builder.patch(rb)
            "DELETE" -> builder.delete(if (body.isNullOrEmpty()) null else rb)
            "PUT" -> builder.put(rb)
        }

        val resp = try {
            http.newCall(builder.build()).execute()
        } catch (e: Exception) {
            throw Exception("Tidak bisa terhubung ke server: ${e.message}")
        }

        resp.use {
            val text = resp.body?.string().orEmpty()
            if (resp.code == 401 && authed && recoverFrom401(retryOn401)) {
                return requestJson(method, url, body, authed, extraHeader, retryOn401 = false)
            }
            if (!resp.isSuccessful) throw Exception(errorMessage(text, resp.code))
            return text
        }
    }

    private fun errorMessage(text: String, code: Int): String {
        val msg = runCatching { JSONObject(text).optString("message") }.getOrNull()
        val details = runCatching { JSONObject(text).optString("details") }.getOrNull()
        return listOfNotNull(msg?.takeIf { it.isNotBlank() }, details?.takeIf { it.isNotBlank() }).joinToString(" ")
            .ifBlank { "Permintaan gagal (HTTP $code)." }
    }

    /**
     * POST JSON. Melempar Exception berisi pesan server bila HTTP tidak sukses,
     * supaya UI tidak pernah "gagal diam".
     */
    private fun postJson(url: String, body: String, authed: Boolean, retryOn401: Boolean = true): JSONObject {
        val builder = Request.Builder()
            .url(url)
            .header("apikey", anonKey)
            .header("Content-Type", "application/json")
            .post(body.toRequestBody(JSON))
        if (authed) builder.header("Authorization", "Bearer ${accessToken.orEmpty()}")

        val resp = try {
            http.newCall(builder.build()).execute()
        } catch (e: Exception) {
            throw Exception("Tidak bisa terhubung ke server: ${e.message}")
        }

        resp.use {
            val text = resp.body?.string().orEmpty()
            if (resp.code == 401 && authed && recoverFrom401(retryOn401)) {
                return postJson(url, body, authed, retryOn401 = false)
            }
            if (!resp.isSuccessful) throw Exception(errorMessage(text, resp.code))
            if (text.isBlank()) return JSONObject()
            val trimmed = text.trim()
            return when {
                trimmed.startsWith("{") -> JSONObject(trimmed)
                trimmed.startsWith("[") -> {
                    val arr = JSONArray(trimmed)
                    if (arr.length() > 0 && arr.get(0) is JSONObject) arr.getJSONObject(0)
                    else JSONObject().put("value", arr)
                }
                else -> JSONObject().put("value", trimmed.trim('"'))
            }
        }
    }

    companion object {
        private val JSON = "application/json; charset=utf-8".toMediaType()
        private const val KEY_ACCESS = "access_token"
        private const val KEY_REFRESH = "refresh_token"
        private const val KEY_USER = "user_id"
    }
}
