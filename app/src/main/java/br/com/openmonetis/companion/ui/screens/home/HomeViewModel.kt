package br.com.openmonetis.companion.ui.screens.home

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.drawable.Drawable
import android.net.Uri
import android.os.PowerManager
import android.provider.Settings
import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import br.com.openmonetis.companion.data.local.dao.AppConfigDao
import br.com.openmonetis.companion.data.local.dao.NotificationDao
import br.com.openmonetis.companion.data.local.entities.NotificationEntity
import br.com.openmonetis.companion.data.local.entities.SyncStatus
import br.com.openmonetis.companion.service.CaptureNotificationListenerService
import br.com.openmonetis.companion.service.SyncWorker
import br.com.openmonetis.companion.util.SecureStorage
import br.com.openmonetis.companion.util.UpdateChecker
import androidx.core.content.FileProvider
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import javax.inject.Inject

@Immutable
data class NotificationUiItem(
    val id: String,
    val appName: String,
    val appIcon: Drawable?,
    val title: String?,
    val text: String,
    val parsedAmount: String?,
    val parsedName: String?,
    val syncStatus: SyncStatus,
    val syncError: String?,
    val timestamp: String,
    val timestampFull: String
)

enum class SyncStatusFilter {
    PENDING, SENT
}

data class MonitoredAppIcon(
    val packageName: String,
    val displayName: String,
    val icon: Drawable?
)

data class HomeUiState(
    val pendingCount: Int = 0,
    val syncedToday: Int = 0,
    val lastSyncTime: String? = null,
    val hasNotificationPermission: Boolean = false,
    val hasBatteryOptimizationExemption: Boolean = true,
    val enabledAppsCount: Int = 0,
    val monitoredApps: List<MonitoredAppIcon> = emptyList(),
    val isRefreshing: Boolean = false,
    val availableUpdateSha: String? = null,
    val isDownloadingUpdate: Boolean = false,
    // History
    val notifications: List<NotificationUiItem> = emptyList(),
    val selectedFilter: SyncStatusFilter = SyncStatusFilter.SENT,
    val isLoadingNotifications: Boolean = true
)

@HiltViewModel
class HomeViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val notificationDao: NotificationDao,
    private val appConfigDao: AppConfigDao,
    private val secureStorage: SecureStorage,
    private val updateChecker: UpdateChecker
) : ViewModel() {

    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    private val dateFormat = SimpleDateFormat("dd/MM HH:mm", Locale.getDefault())
    private val dateFormatFull = SimpleDateFormat("dd/MM/yyyy HH:mm:ss", Locale.getDefault())

    // Cache for app icons to avoid loading during scroll
    private val iconCache = mutableMapOf<String, Drawable?>()

    init {
        loadStats()
        loadNotifications()
        checkNotificationPermission()
        checkBatteryOptimization()
        checkForUpdate()
    }

    private fun checkForUpdate() {
        viewModelScope.launch {
            val result = updateChecker.checkForUpdate()
            _uiState.value = _uiState.value.copy(availableUpdateSha = result?.shortSha)
        }
    }

    fun dismissUpdatePrompt() {
        _uiState.value = _uiState.value.copy(availableUpdateSha = null)
    }

    fun downloadAndInstallUpdate(context: Context) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isDownloadingUpdate = true)

            val file = updateChecker.downloadApk(context)

            _uiState.value = _uiState.value.copy(isDownloadingUpdate = false)

            if (file == null) {
                return@launch
            }

            _uiState.value = _uiState.value.copy(availableUpdateSha = null)

            val uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                file
            )
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "application/vnd.android.package-archive")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        }
    }

    private fun loadStats() {
        viewModelScope.launch {
            // Count pending notifications
            val pendingCount = notificationDao.countPending()

            // Count synced today
            val todayStart = Calendar.getInstance().apply {
                set(Calendar.HOUR_OF_DAY, 0)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }.timeInMillis
            val syncedToday = notificationDao.countSyncedSince(todayStart)

            // Get enabled apps with icons
            val enabledApps = appConfigDao.getEnabled()
            val pm = context.packageManager
            val appsWithIcons = enabledApps.map { app ->
                val icon = try {
                    pm.getApplicationIcon(app.packageName)
                } catch (e: Exception) {
                    null
                }
                MonitoredAppIcon(
                    packageName = app.packageName,
                    displayName = app.displayName,
                    icon = icon
                )
            }

            // Get last sync time
            val lastSyncTime = secureStorage.lastSyncTime
            val lastSyncTimeFormatted = if (lastSyncTime > 0) {
                dateFormatFull.format(Date(lastSyncTime))
            } else {
                null
            }

            _uiState.value = _uiState.value.copy(
                pendingCount = pendingCount,
                syncedToday = syncedToday,
                enabledAppsCount = enabledApps.size,
                monitoredApps = appsWithIcons,
                lastSyncTime = lastSyncTimeFormatted
            )
        }
    }

    fun loadNotifications() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoadingNotifications = true)

            val notifications = notificationDao.getRecent(100)
            val filteredNotifications = filterNotifications(notifications, _uiState.value.selectedFilter)

            // Pre-load all icons on IO thread
            val uiItems = withContext(Dispatchers.IO) {
                val pm = context.packageManager
                filteredNotifications.map { entity ->
                    val icon = iconCache.getOrPut(entity.sourceApp) {
                        try {
                            pm.getApplicationIcon(entity.sourceApp)
                        } catch (e: Exception) {
                            null
                        }
                    }
                    entity.toUiItem(icon)
                }
            }

            _uiState.value = _uiState.value.copy(
                isLoadingNotifications = false,
                notifications = uiItems
            )
        }
    }

    fun setFilter(filter: SyncStatusFilter) {
        _uiState.value = _uiState.value.copy(selectedFilter = filter)
        loadNotifications()
    }

    fun deleteNotification(id: String) {
        viewModelScope.launch {
            notificationDao.delete(id)
            loadNotifications()
            loadStats()
        }
    }

    fun retryNotification(id: String) {
        viewModelScope.launch {
            notificationDao.retrySync(id)
            SyncWorker.enqueue(context)
            loadNotifications()
            loadStats()
        }
    }

    fun discardNotification(id: String) {
        viewModelScope.launch {
            notificationDao.updateStatus(id, SyncStatus.DISCARDED)
            loadNotifications()
            loadStats()
        }
    }

    private fun filterNotifications(
        notifications: List<NotificationEntity>,
        filter: SyncStatusFilter
    ): List<NotificationEntity> {
        return when (filter) {
            SyncStatusFilter.PENDING -> notifications.filter {
                it.syncStatus == SyncStatus.PENDING_SYNC ||
                    it.syncStatus == SyncStatus.SYNCING ||
                    it.syncStatus == SyncStatus.SYNC_FAILED
            }
            SyncStatusFilter.SENT -> notifications.filter {
                it.syncStatus == SyncStatus.SYNCED || it.syncStatus == SyncStatus.PROCESSED
            }
        }
    }

    private fun NotificationEntity.toUiItem(icon: Drawable?): NotificationUiItem {
        return NotificationUiItem(
            id = id,
            appName = sourceAppName ?: sourceApp,
            appIcon = icon,
            title = originalTitle,
            text = originalText,
            parsedAmount = parsedAmount?.let { "R$ %.2f".format(it) },
            parsedName = parsedName,
            syncStatus = syncStatus,
            syncError = syncError,
            timestamp = dateFormat.format(Date(createdAt)),
            timestampFull = dateFormatFull.format(Date(createdAt))
        )
    }

    private fun checkNotificationPermission() {
        val hasPermission = isNotificationListenerEnabled()
        _uiState.value = _uiState.value.copy(hasNotificationPermission = hasPermission)
    }

    private fun isNotificationListenerEnabled(): Boolean {
        val componentName = ComponentName(context, CaptureNotificationListenerService::class.java)
        val enabledListeners = Settings.Secure.getString(
            context.contentResolver,
            "enabled_notification_listeners"
        )
        return enabledListeners?.contains(componentName.flattenToString()) == true
    }

    fun openNotificationSettings(): Intent {
        return Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
    }

    fun refreshPermissionStatus() {
        checkNotificationPermission()
        checkBatteryOptimization()
    }

    /**
     * Many OEMs (Xiaomi/MIUI, Samsung, Huawei...) aggressively kill the
     * notification listener and delay WorkManager jobs unless the app is
     * exempt from battery optimization - the most common real-world cause of
     * "stopped capturing notifications" reports for this kind of app.
     */
    private fun checkBatteryOptimization() {
        val powerManager = context.getSystemService(Context.POWER_SERVICE) as PowerManager
        val isExempt = powerManager.isIgnoringBatteryOptimizations(context.packageName)
        _uiState.value = _uiState.value.copy(hasBatteryOptimizationExemption = isExempt)
    }

    fun requestIgnoreBatteryOptimizationsIntent(): Intent {
        return Intent(
            Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
            Uri.parse("package:${context.packageName}")
        )
    }

    fun openBatteryOptimizationSettings(): Intent {
        return Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
    }

    fun refreshData() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isRefreshing = true)

            loadStats()
            loadNotifications()
            checkNotificationPermission()

            // Trigger a sync
            SyncWorker.enqueue(context)

            _uiState.value = _uiState.value.copy(isRefreshing = false)
        }
    }
}
