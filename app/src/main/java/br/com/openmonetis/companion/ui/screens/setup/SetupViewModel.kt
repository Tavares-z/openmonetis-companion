package br.com.openmonetis.companion.ui.screens.setup

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import br.com.openmonetis.companion.data.remote.OpenMonetisApi
import br.com.openmonetis.companion.service.SyncWorker
import br.com.openmonetis.companion.util.SecureStorage
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import java.time.Instant
import javax.inject.Inject

data class SetupUiState(
    val step: SetupStep = SetupStep.SERVER_URL,
    val serverUrl: String = "",
    val token: String = "",
    val isLoading: Boolean = false,
    val error: String? = null,
    val serverName: String? = null,
    val serverVersion: String? = null
)

enum class SetupStep {
    SERVER_URL,
    TOKEN
}

@HiltViewModel
class SetupViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val secureStorage: SecureStorage,
    private val api: OpenMonetisApi
) : ViewModel() {

    private val _uiState = MutableStateFlow(SetupUiState())
    val uiState: StateFlow<SetupUiState> = _uiState.asStateFlow()

    private val _isConfigured = MutableStateFlow(secureStorage.isConfigured())
    val isConfigured: StateFlow<Boolean> = _isConfigured.asStateFlow()

    fun updateServerUrl(url: String) {
        _uiState.value = _uiState.value.copy(serverUrl = url, error = null)
    }

    fun updateToken(token: String) {
        _uiState.value = _uiState.value.copy(token = token, error = null)
    }

    fun verifyServerConnection() {
        val url = _uiState.value.serverUrl.trim()

        // Validate URL format
        val httpUrl = url.toHttpUrlOrNull()
        if (httpUrl == null) {
            _uiState.value = _uiState.value.copy(error = "URL inválida")
            return
        }

        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)

            try {
                // Temporarily set the server URL for the API call
                secureStorage.serverUrl = url

                val response = api.healthCheck()

                if (response.isSuccessful && response.body()?.status == "ok") {
                    val body = response.body()
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        step = SetupStep.TOKEN,
                        serverName = body?.name,
                        serverVersion = body?.version
                    )
                } else {
                    secureStorage.serverUrl = null
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        error = response.body()?.message ?: "Servidor não disponível"
                    )
                }
            } catch (e: Exception) {
                secureStorage.serverUrl = null
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    error = "Não foi possível conectar: ${e.message}"
                )
            }
        }
    }

    fun verifyToken() {
        val token = _uiState.value.token.trim()

        if (token.isBlank()) {
            _uiState.value = _uiState.value.copy(error = "Token não pode estar vazio")
            return
        }

        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)

            try {
                // Temporarily set the token for verification
                secureStorage.accessToken = token

                val response = api.verifyToken()

                if (response.isSuccessful && response.body()?.valid == true) {
                    val body = response.body()

                    // Save credentials permanently
                    secureStorage.saveCredentials(
                        serverUrl = _uiState.value.serverUrl,
                        accessToken = token,
                        refreshToken = null, // Backend uses a single 1-year opaque token, no refresh flow
                        tokenId = body?.tokenId,
                        tokenName = body?.tokenName
                    )

                    // Persist token expiry so the "expiring soon" warning works
                    // from setup, not only after the user opens Settings. The
                    // verify endpoint already returns expiresAt (ISO-8601).
                    secureStorage.tokenExpiresAt = runCatching {
                        body?.expiresAt?.let { Instant.parse(it).toEpochMilli() }
                    }.getOrNull() ?: -1L

                    // saveCredentials() cleared needsReauth; kick a sync so any
                    // notifications held back during re-auth drain immediately.
                    SyncWorker.enqueue(context)

                    _uiState.value = _uiState.value.copy(isLoading = false)
                    _isConfigured.value = true
                } else {
                    secureStorage.accessToken = null
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        error = response.body()?.error ?: "Token inválido"
                    )
                }
            } catch (e: Exception) {
                secureStorage.accessToken = null
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    error = "Erro ao validar token: ${e.message}"
                )
            }
        }
    }

    fun goBackToServerStep() {
        secureStorage.serverUrl = null
        _uiState.value = _uiState.value.copy(
            step = SetupStep.SERVER_URL,
            error = null
        )
    }
}
