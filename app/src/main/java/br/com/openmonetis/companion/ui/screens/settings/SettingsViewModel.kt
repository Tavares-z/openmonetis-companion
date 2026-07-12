package br.com.openmonetis.companion.ui.screens.settings

import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import br.com.openmonetis.companion.data.local.dao.AppConfigDao
import br.com.openmonetis.companion.data.local.dao.NotificationDao
import br.com.openmonetis.companion.data.local.entities.AppConfigEntity
import br.com.openmonetis.companion.data.remote.OpenMonetisApi
import br.com.openmonetis.companion.util.NotificationsExporter
import br.com.openmonetis.companion.util.SecureStorage
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.temporal.ChronoUnit
import javax.inject.Inject

data class MonitoredAppUi(
    val packageName: String,
    val displayName: String,
    val isEnabled: Boolean,
    val icon: Drawable? = null
)

data class InstalledAppUi(
    val packageName: String,
    val displayName: String,
    val icon: Drawable?
)

data class SettingsUiState(
    val serverUrl: String = "",
    val tokenName: String = "",
    val isConnected: Boolean = false,
    val monitoredApps: List<MonitoredAppUi> = emptyList(),
    val appVersion: String = "",
    val showDisconnectDialog: Boolean = false,
    val showClearDataDialog: Boolean = false,
    val showAddAppDialog: Boolean = false,
    val showEditServerDialog: Boolean = false,
    val editServerUrl: String = "",
    val editToken: String = "",
    val installedApps: List<InstalledAppUi> = emptyList(),
    val appSearchQuery: String = "",
    val isLoadingApps: Boolean = false,
    val isExportingNotifications: Boolean = false,
    val exportMessage: String? = null,
    val notifySyncSuccess: Boolean = true,
    val notifySyncError: Boolean = true,
    val tokenExpiresInDays: Long? = null
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val secureStorage: SecureStorage,
    private val appConfigDao: AppConfigDao,
    private val notificationDao: NotificationDao,
    private val notificationsExporter: NotificationsExporter,
    private val api: OpenMonetisApi
) : ViewModel() {

    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    private var allInstalledApps: List<InstalledAppUi> = emptyList()

    init {
        loadSettings()
    }

    private fun loadSettings() {
        viewModelScope.launch {
            val serverUrl = secureStorage.serverUrl ?: ""
            val tokenName = secureStorage.tokenName ?: ""
            val hasToken = secureStorage.accessToken != null
            val appVersion = getAppVersion()

            _uiState.value = _uiState.value.copy(
                serverUrl = serverUrl,
                tokenName = tokenName,
                isConnected = hasToken && serverUrl.isNotEmpty(),
                appVersion = appVersion,
                notifySyncSuccess = secureStorage.notifySyncSuccess,
                notifySyncError = secureStorage.notifySyncError,
                tokenExpiresInDays = daysUntilStoredExpiry()
            )

            loadMonitoredApps()

            if (hasToken && serverUrl.isNotEmpty()) {
                refreshTokenExpiry()
            }
        }
    }

    private fun daysUntilStoredExpiry(): Long? {
        val expiresAt = secureStorage.tokenExpiresAt
        if (expiresAt < 0) return null
        return ChronoUnit.DAYS.between(Instant.now(), Instant.ofEpochMilli(expiresAt))
    }

    /**
     * Asks the server for the token's expiration and caches it locally, so we
     * can warn the user before sync silently starts failing. Best-effort: any
     * failure here just leaves the previously cached value (or null) in place.
     */
    private fun refreshTokenExpiry() {
        viewModelScope.launch {
            runCatching {
                val response = api.verifyToken()
                val expiresAt = response.body()?.expiresAt ?: return@runCatching
                val epochMillis = Instant.parse(expiresAt).toEpochMilli()
                secureStorage.tokenExpiresAt = epochMillis
                _uiState.value = _uiState.value.copy(tokenExpiresInDays = daysUntilStoredExpiry())
            }
        }
    }

    private suspend fun loadMonitoredApps() {
        val apps = appConfigDao.getAll()
        val pm = context.packageManager
        val uiApps = apps.map { app ->
            val icon = try {
                pm.getApplicationIcon(app.packageName)
            } catch (e: Exception) {
                null
            }
            MonitoredAppUi(
                packageName = app.packageName,
                displayName = app.displayName,
                isEnabled = app.isEnabled,
                icon = icon
            )
        }
        _uiState.value = _uiState.value.copy(monitoredApps = uiApps)
    }

    fun toggleApp(packageName: String, enabled: Boolean) {
        viewModelScope.launch {
            appConfigDao.setEnabled(packageName, enabled)
            loadMonitoredApps()
        }
    }

    fun removeApp(packageName: String) {
        viewModelScope.launch {
            appConfigDao.delete(packageName)
            loadMonitoredApps()
        }
    }

    fun showAddAppDialog() {
        _uiState.value = _uiState.value.copy(
            showAddAppDialog = true,
            appSearchQuery = "",
            isLoadingApps = true
        )
        loadInstalledApps()
    }

    fun hideAddAppDialog() {
        _uiState.value = _uiState.value.copy(
            showAddAppDialog = false,
            appSearchQuery = "",
            installedApps = emptyList()
        )
    }

    fun updateAppSearchQuery(query: String) {
        _uiState.value = _uiState.value.copy(appSearchQuery = query)
        filterInstalledApps(query)
    }

    private fun loadInstalledApps() {
        viewModelScope.launch {
            val apps = withContext(Dispatchers.IO) {
                val pm = context.packageManager
                val monitoredPackages = appConfigDao.getAll().map { it.packageName }.toSet()
                
                // Query apps that have a launcher activity (user-visible apps)
                val launcherIntent = Intent(Intent.ACTION_MAIN).apply {
                    addCategory(Intent.CATEGORY_LAUNCHER)
                }
                
                pm.queryIntentActivities(launcherIntent, PackageManager.MATCH_ALL)
                    .mapNotNull { resolveInfo ->
                        val packageName = resolveInfo.activityInfo.packageName
                        // Exclude already monitored and our own app
                        if (packageName in monitoredPackages || packageName == context.packageName) {
                            null
                        } else {
                            try {
                                val appInfo = pm.getApplicationInfo(packageName, 0)
                                InstalledAppUi(
                                    packageName = packageName,
                                    displayName = pm.getApplicationLabel(appInfo).toString(),
                                    icon = try { pm.getApplicationIcon(appInfo) } catch (e: Exception) { null }
                                )
                            } catch (e: Exception) {
                                null
                            }
                        }
                    }
                    .distinctBy { it.packageName }
                    .sortedBy { it.displayName.lowercase() }
            }
            
            allInstalledApps = apps
            _uiState.value = _uiState.value.copy(
                installedApps = apps,
                isLoadingApps = false
            )
        }
    }

    private fun filterInstalledApps(query: String) {
        val filtered = if (query.isBlank()) {
            allInstalledApps
        } else {
            allInstalledApps.filter { app ->
                app.displayName.contains(query, ignoreCase = true) ||
                app.packageName.contains(query, ignoreCase = true)
            }
        }
        _uiState.value = _uiState.value.copy(installedApps = filtered)
    }

    fun addApp(packageName: String, displayName: String) {
        viewModelScope.launch {
            val config = AppConfigEntity(
                packageName = packageName,
                displayName = displayName,
                isEnabled = true
            )
            appConfigDao.insert(config)
            loadMonitoredApps()
            hideAddAppDialog()
        }
    }

    fun showDisconnectDialog() {
        _uiState.value = _uiState.value.copy(showDisconnectDialog = true)
    }

    fun hideDisconnectDialog() {
        _uiState.value = _uiState.value.copy(showDisconnectDialog = false)
    }

    fun showClearDataDialog() {
        _uiState.value = _uiState.value.copy(showClearDataDialog = true)
    }

    fun hideClearDataDialog() {
        _uiState.value = _uiState.value.copy(showClearDataDialog = false)
    }

    fun disconnect() {
        viewModelScope.launch {
            secureStorage.clear()
            _uiState.value = _uiState.value.copy(
                serverUrl = "",
                tokenName = "",
                isConnected = false,
                showDisconnectDialog = false,
                tokenExpiresInDays = null
            )
        }
    }

    fun showEditServerDialog() {
        _uiState.value = _uiState.value.copy(
            showEditServerDialog = true,
            editServerUrl = _uiState.value.serverUrl,
            editToken = secureStorage.accessToken ?: ""
        )
    }

    fun hideEditServerDialog() {
        _uiState.value = _uiState.value.copy(
            showEditServerDialog = false,
            editServerUrl = "",
            editToken = ""
        )
    }

    fun updateEditServerUrl(url: String) {
        _uiState.value = _uiState.value.copy(editServerUrl = url)
    }

    fun updateEditToken(token: String) {
        _uiState.value = _uiState.value.copy(editToken = token)
    }

    fun saveServerSettings() {
        viewModelScope.launch {
            val url = _uiState.value.editServerUrl.trim()
            val token = _uiState.value.editToken.trim()

            if (url.isNotEmpty()) {
                secureStorage.serverUrl = url
            }
            if (token.isNotEmpty()) {
                secureStorage.accessToken = token
                secureStorage.tokenExpiresAt = -1L
            }

            _uiState.value = _uiState.value.copy(
                serverUrl = url,
                isConnected = url.isNotEmpty() && token.isNotEmpty(),
                showEditServerDialog = false,
                editServerUrl = "",
                editToken = "",
                tokenExpiresInDays = null
            )

            if (url.isNotEmpty() && token.isNotEmpty()) {
                refreshTokenExpiry()
            }
        }
    }

    fun clearAllData() {
        viewModelScope.launch {
            notificationDao.deleteAll()
            hideClearDataDialog()
        }
    }

    fun exportNotifications() {
        if (_uiState.value.isExportingNotifications) return

        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isExportingNotifications = true)

            try {
                val result = withContext(Dispatchers.IO) {
                    notificationsExporter.exportToDownloads()
                }
                val message = if (result.notificationCount > 0) {
                    "${result.notificationCount} notificacoes exportadas para Downloads/${result.fileName}"
                } else {
                    "Arquivo criado em Downloads/${result.fileName}, mas nao havia notificacoes salvas"
                }
                _uiState.value = _uiState.value.copy(
                    isExportingNotifications = false,
                    exportMessage = message
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isExportingNotifications = false,
                    exportMessage = "Falha ao exportar notificacoes: ${e.message ?: "erro desconhecido"}"
                )
            }
        }
    }

    fun clearExportMessage() {
        _uiState.value = _uiState.value.copy(exportMessage = null)
    }

    fun setNotifySyncSuccess(enabled: Boolean) {
        secureStorage.notifySyncSuccess = enabled
        _uiState.value = _uiState.value.copy(notifySyncSuccess = enabled)
    }

    fun setNotifySyncError(enabled: Boolean) {
        secureStorage.notifySyncError = enabled
        _uiState.value = _uiState.value.copy(notifySyncError = enabled)
    }

    private fun getAppVersion(): String {
        return try {
            val packageInfo = context.packageManager.getPackageInfo(context.packageName, 0)
            packageInfo.versionName ?: "1.0.0"
        } catch (e: PackageManager.NameNotFoundException) {
            "1.0.0"
        }
    }
}
