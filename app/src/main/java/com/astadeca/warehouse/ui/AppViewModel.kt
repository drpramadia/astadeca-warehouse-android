package com.astadeca.warehouse.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.astadeca.warehouse.data.SessionExpiredException
import com.astadeca.warehouse.data.SupabaseClient
import com.astadeca.warehouse.data.WarehouseRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject

data class SessionInfo(
    val authenticated: Boolean = false,
    val userId: String? = null,
    val fullName: String? = null,
    val roleCode: String? = null,
    val roleName: String? = null,
    val organizationId: String? = null,
    val organizationName: String? = null,
    val permissions: Set<String> = emptySet(),
)

sealed interface AppState {
    data object Loading : AppState
    data object LoggedOut : AppState
    data class LoggedIn(val session: SessionInfo) : AppState
    data class Error(val message: String) : AppState
}

class AppViewModel(app: Application) : AndroidViewModel(app) {

    val api = SupabaseClient(app)
    val repo = WarehouseRepository(api)

    private val _state = MutableStateFlow<AppState>(AppState.Loading)
    val state: StateFlow<AppState> = _state.asStateFlow()

    private val _busy = MutableStateFlow(false)
    val busy: StateFlow<Boolean> = _busy.asStateFlow()

    init { bootstrap() }

    fun bootstrap() {
        viewModelScope.launch {
            if (!api.isLoggedIn) {
                _state.value = AppState.LoggedOut
                return@launch
            }
            loadSession()
        }
    }

    fun login(username: String, password: String, onError: (String) -> Unit) {
        viewModelScope.launch {
            _busy.value = true
            _state.value = AppState.Loading
            try {
                val email = withContext(Dispatchers.IO) { api.resolveUsername(username.trim()) }
                if (email.isNullOrBlank()) {
                    val why = withContext(Dispatchers.IO) { api.usernameError(username.trim()) }
                    throw Exception(why ?: "Username tidak ditemukan.")
                }
                val res = withContext(Dispatchers.IO) { api.signInWithPassword(email, password) }
                res.getOrThrow()
                loadSession()
            } catch (e: Exception) {
                api.clearSession()
                _state.value = AppState.LoggedOut
                onError(e.message ?: "Login gagal.")
            } finally {
                _busy.value = false
            }
        }
    }

    fun logout() {
        api.clearSession()
        _state.value = AppState.LoggedOut
    }

    private suspend fun loadSession() {
        try {
            val json = withContext(Dispatchers.IO) { repo.mySession() }
            val authenticated = json.optBoolean("authenticated", false)
            if (!authenticated) {
                api.clearSession()
                _state.value = AppState.LoggedOut
                return
            }
            val permsFromSession = json.optJSONArray("permissions")
            val perms: List<String> = if (permsFromSession != null) {
                (0 until permsFromSession.length()).mapNotNull { permsFromSession.optString(it).takeIf { p -> p.isNotBlank() } }
            } else {
                withContext(Dispatchers.IO) { runCatching { repo.myPermissions() }.getOrDefault(emptyList()) }
            }
            _state.value = AppState.LoggedIn(
                SessionInfo(
                    authenticated = true,
                    userId = json.optString("user_id").ifBlank { api.userId.orEmpty() }.takeIf { it.isNotBlank() },
                    fullName = json.optString("name").takeIf { it.isNotBlank() },
                    roleCode = json.optString("role_code").takeIf { it.isNotBlank() },
                    roleName = json.optString("role_name").takeIf { it.isNotBlank() },
                    organizationId = json.optString("organization_id").takeIf { it.isNotBlank() },
                    organizationName = json.optString("organization_name").takeIf { it.isNotBlank() },
                    permissions = perms.toSet(),
                ),
            )
        } catch (e: SessionExpiredException) {
            _state.value = AppState.LoggedOut
        } catch (e: Exception) {
            _state.value = AppState.Error(e.message ?: "Gagal memuat sesi.")
        }
    }

    /** Sesi terkini, atau null bila belum login. */
    fun sessionOrNull(): SessionInfo? = (_state.value as? AppState.LoggedIn)?.session

    /** Jalankan pekerjaan IO, laporkan error ke callback; hasil null bila gagal. */
    fun <T> runIo(onError: (String) -> Unit, block: suspend () -> T): kotlinx.coroutines.Job =
        viewModelScope.launch {
            try {
                block()
            } catch (e: SessionExpiredException) {
                // Sesi mati di tengah aksi: paksa kembali ke login, bukan error mentah.
                _state.value = AppState.LoggedOut
            } catch (e: Exception) {
                onError(e.message ?: "Terjadi kesalahan.")
            }
        }

    companion object {
        fun roleAllowedForWarehouse(session: SessionInfo?): Boolean {
            val role = session?.roleCode ?: return false
            return role in setOf("WAREHOUSE", "ADMIN", "DIRECTOR", "SYSTEM_ADMIN")
        }

        fun parse(json: JSONObject, key: String): String? =
            json.optString(key).takeIf { it.isNotBlank() && it != "null" }
    }
}
