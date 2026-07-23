package br.com.openmonetis.companion.service

import android.content.Context
import android.util.Log
import androidx.hilt.work.HiltWorker
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import br.com.openmonetis.companion.data.local.dao.NotificationDao
import br.com.openmonetis.companion.data.local.dao.SyncLogDao
import br.com.openmonetis.companion.data.local.entities.SyncLogEntity
import br.com.openmonetis.companion.data.local.entities.SyncLogType
import br.com.openmonetis.companion.data.local.entities.SyncStatus
import br.com.openmonetis.companion.data.remote.OpenMonetisApi
import br.com.openmonetis.companion.data.remote.dto.InboxBatchRequest
import br.com.openmonetis.companion.data.remote.dto.InboxRequest
import br.com.openmonetis.companion.util.SecureStorage
import br.com.openmonetis.companion.util.SyncResultNotifier
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

@HiltWorker
class SyncWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val notificationDao: NotificationDao,
    private val syncLogDao: SyncLogDao,
    private val api: OpenMonetisApi,
    private val secureStorage: SecureStorage
) : CoroutineWorker(context, params) {

    private val dateFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US)
    private val syncResultNotifier = SyncResultNotifier(applicationContext, secureStorage)

    override suspend fun doWork(): Result {
        Log.d(TAG, "Starting sync work")

        // Recover notifications stuck in SYNCING from a run that was killed
        // mid-flight. Safe because ExistingWorkPolicy.REPLACE + a unique work
        // name guarantee only one sync executes at a time, so anything still
        // SYNCING here must be orphaned from an interrupted run.
        notificationDao.resetStaleSyncing()

        // Clean old logs (older than 7 days)
        cleanOldLogs()

        // Check if configured
        if (!secureStorage.isConfigured()) {
            Log.w(TAG, "Not configured, skipping sync")
            log(SyncLogType.WARNING, "Sincronização ignorada: app não configurado")
            return Result.failure()
        }

        // If a previous sync hit 401, the token is expired/revoked and every
        // request will keep failing until the user re-enters a token. Don't
        // hammer the server on each captured notification - just hold. The flag
        // is cleared by saveCredentials() on successful re-setup, and the
        // periodic sync will pick pending items back up afterwards.
        if (secureStorage.needsReauth) {
            Log.w(TAG, "Re-auth required, skipping sync until reconfigured")
            log(SyncLogType.WARNING, "Sincronização pausada: reconfigure o token de acesso")
            return Result.failure()
        }

        // Get pending notifications
        val pending = notificationDao.getPendingSync(limit = BATCH_SIZE)

        if (pending.isEmpty()) {
            Log.d(TAG, "No pending notifications to sync")
            return Result.success()
        }

        Log.d(TAG, "Syncing ${pending.size} notifications")
        log(SyncLogType.INFO, "Iniciando sincronização de ${pending.size} notificações")

        return try {
            // Mark as syncing
            pending.forEach { notification ->
                notificationDao.updateStatus(notification.id, SyncStatus.SYNCING)
            }

            // Build batch request
            val items = pending.map { notification ->
                InboxRequest(
                    sourceApp = notification.sourceApp,
                    sourceAppName = notification.sourceAppName,
                    originalTitle = notification.originalTitle,
                    originalText = notification.originalText,
                    notificationTimestamp = dateFormat.format(Date(notification.notificationTimestamp)),
                    parsedName = notification.parsedName,
                    parsedAmount = notification.parsedAmount,
                    clientId = notification.id
                )
            }

            val response = api.submitBatch(InboxBatchRequest(items))

            if (response.isSuccessful) {
                val body = response.body()
                var successCount = 0
                var failCount = 0

                body?.results?.forEach { result ->
                    val clientId = result.clientId ?: return@forEach
                    val notification = pending.firstOrNull { it.id == clientId } ?: return@forEach

                    if (result.success && result.serverId != null) {
                        notificationDao.markSynced(clientId, result.serverId)
                        syncResultNotifier.notifySuccess(notification)
                        log(
                            SyncLogType.SUCCESS,
                            "Lançamento enviado com sucesso",
                            clientId
                        )
                        successCount++
                    } else {
                        notificationDao.markSyncFailed(clientId, result.error)
                        syncResultNotifier.notifyError(notification, result.error)
                        log(
                            SyncLogType.ERROR,
                            "Falha ao sincronizar notificação",
                            clientId,
                            result.error
                        )
                        failCount++
                    }
                }

                Log.d(TAG, "Sync completed: ${body?.success}/${body?.total} successful")
                log(
                    SyncLogType.SUCCESS,
                    "Sincronização concluída: $successCount enviadas, $failCount falhas"
                )

                // Update last sync time
                secureStorage.lastSyncTime = System.currentTimeMillis()

                // If there are more pending, schedule another sync
                val remainingCount = notificationDao.countPending()
                if (remainingCount > 0) {
                    enqueue(applicationContext)
                }

                Result.success()
            } else {
                val errorCode = response.code()

                if (errorCode == 401) {
                    Log.w(TAG, "Token rejected (401), pausing sync until re-auth")
                    log(SyncLogType.ERROR, "Token expirado", details = "HTTP 401")
                    // Backend uses a single 1-year opaque token with no refresh
                    // flow, so a 401 means expired/revoked - the user must enter
                    // a new one. Keep the notifications PENDING (don't burn them
                    // into permanent failures) and raise a single app-level
                    // re-auth flag + notification instead of N per-entry errors.
                    notificationDao.resetStaleSyncing()
                    secureStorage.needsReauth = true
                    syncResultNotifier.notifyReauthRequired()
                    Result.failure()
                } else if (errorCode == 429) {
                    // Rate limited, retry later
                    Log.w(TAG, "Rate limited, will retry")
                    log(SyncLogType.WARNING, "Limite de requisições atingido, tentando novamente...")
                    pending.forEach { notification ->
                        notificationDao.updateStatus(notification.id, SyncStatus.PENDING_SYNC)
                    }
                    Result.retry()
                } else {
                    Log.e(TAG, "Sync failed with code $errorCode")
                    log(SyncLogType.ERROR, "Falha na sincronização", details = "HTTP $errorCode")
                    pending.forEach { notification ->
                        notificationDao.markSyncFailed(notification.id, "HTTP $errorCode")
                        syncResultNotifier.notifyError(notification, "HTTP $errorCode")
                    }
                    Result.retry()
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Sync failed with exception", e)
            log(SyncLogType.ERROR, "Erro na sincronização", details = e.message)
            pending.forEach { notification ->
                notificationDao.markSyncFailed(notification.id, e.message)
                syncResultNotifier.notifyError(notification, e.message)
            }
            Result.retry()
        }
    }

    private suspend fun log(
        type: SyncLogType,
        message: String,
        notificationId: String? = null,
        details: String? = null
    ) {
        syncLogDao.insert(
            SyncLogEntity(
                type = type,
                message = message,
                notificationId = notificationId,
                details = details
            )
        )
    }

    private suspend fun cleanOldLogs() {
        val sevenDaysAgo = Calendar.getInstance().apply {
            add(Calendar.DAY_OF_YEAR, -7)
        }.timeInMillis
        syncLogDao.deleteOlderThan(sevenDaysAgo)
    }

    companion object {
        private const val TAG = "SyncWorker"
        private const val WORK_NAME = "sync_notifications"
        private const val PERIODIC_WORK_NAME = "sync_notifications_periodic"
        private const val BATCH_SIZE = 50
        private const val PERIODIC_INTERVAL_HOURS = 6L

        fun enqueue(context: Context) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()

            val request = OneTimeWorkRequestBuilder<SyncWorker>()
                .setConstraints(constraints)
                .setBackoffCriteria(
                    BackoffPolicy.EXPONENTIAL,
                    30,
                    TimeUnit.SECONDS
                )
                .build()

            WorkManager.getInstance(context)
                .enqueueUniqueWork(WORK_NAME, ExistingWorkPolicy.REPLACE, request)
        }

        /**
         * Safety-net sync. Capture is event-driven (one-time work per captured
         * notification), so if a batch is left in PENDING/SYNC_FAILED after its
         * backoff is exhausted, nothing re-triggers it until a *new* notification
         * arrives. This periodic drains those stragglers. KEEP so we don't reset
         * the interval on every app start; it no-ops when there's nothing pending
         * and self-skips while needsReauth is set.
         */
        fun enqueuePeriodic(context: Context) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()

            val request = PeriodicWorkRequestBuilder<SyncWorker>(
                PERIODIC_INTERVAL_HOURS,
                TimeUnit.HOURS
            )
                .setConstraints(constraints)
                .setBackoffCriteria(
                    BackoffPolicy.EXPONENTIAL,
                    30,
                    TimeUnit.SECONDS
                )
                .build()

            WorkManager.getInstance(context)
                .enqueueUniquePeriodicWork(
                    PERIODIC_WORK_NAME,
                    ExistingPeriodicWorkPolicy.KEEP,
                    request
                )
        }
    }
}
