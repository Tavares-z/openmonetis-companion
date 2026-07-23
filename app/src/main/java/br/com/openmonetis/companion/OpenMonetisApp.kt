package br.com.openmonetis.companion

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import br.com.openmonetis.companion.service.SyncWorker
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

@HiltAndroidApp
class OpenMonetisApp : Application(), Configuration.Provider {

    @Inject
    lateinit var workerFactory: HiltWorkerFactory

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .build()

    override fun onCreate() {
        super.onCreate()
        createNotificationChannels()
        // Safety-net periodic sync to drain notifications left pending after
        // their one-time backoff was exhausted. KEEP means this is a no-op if
        // already scheduled.
        SyncWorker.enqueuePeriodic(this)
    }

    private fun createNotificationChannels() {
        val notificationManager = getSystemService(NotificationManager::class.java)

        // Sync notification channel
        val syncChannel = NotificationChannel(
            CHANNEL_SYNC,
            getString(R.string.notification_channel_sync),
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = getString(R.string.notification_channel_sync_description)
        }

        val syncResultsChannel = NotificationChannel(
            CHANNEL_SYNC_RESULTS,
            getString(R.string.notification_channel_sync_results),
            NotificationManager.IMPORTANCE_DEFAULT
        ).apply {
            description = getString(R.string.notification_channel_sync_results_description)
        }

        notificationManager.createNotificationChannel(syncChannel)
        notificationManager.createNotificationChannel(syncResultsChannel)
    }

    companion object {
        const val CHANNEL_SYNC = "sync_channel"
        const val CHANNEL_SYNC_RESULTS = "sync_results_channel"
    }
}
