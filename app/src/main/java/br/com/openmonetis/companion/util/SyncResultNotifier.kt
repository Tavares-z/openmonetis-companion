package br.com.openmonetis.companion.util

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.toBitmap
import br.com.openmonetis.companion.OpenMonetisApp
import br.com.openmonetis.companion.R
import br.com.openmonetis.companion.data.local.entities.NotificationEntity

class SyncResultNotifier(
    private val context: Context,
    private val secureStorage: SecureStorage
) {

    fun notifySuccess(notification: NotificationEntity) {
        if (!secureStorage.notifySyncSuccess) return
        notify(
            notification = notification,
            title = context.getString(R.string.notification_entry_sent_title),
            message = buildSuccessMessage(notification),
            priority = NotificationCompat.PRIORITY_DEFAULT
        )
    }

    /**
     * A single, coalesced alert that the token was rejected (401). Uses a fixed
     * notification id so repeated failed syncs don't stack N copies, and posts
     * even when per-entry error notifications are muted - re-auth is not an
     * "entry failed", it's an app-level blocker the user must act on.
     */
    fun notifyReauthRequired() {
        if (!canPostNotifications()) return

        val builder = NotificationCompat.Builder(context, OpenMonetisApp.CHANNEL_SYNC_RESULTS)
            .setSmallIcon(R.drawable.ic_notification_small)
            .setContentTitle(context.getString(R.string.notification_reauth_title))
            .setContentText(context.getString(R.string.notification_reauth_message))
            .setStyle(
                NotificationCompat.BigTextStyle()
                    .bigText(context.getString(R.string.notification_reauth_message))
            )
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_ERROR)
            .setAutoCancel(true)

        NotificationManagerCompat.from(context).notify(REAUTH_NOTIFICATION_ID, builder.build())
    }

    fun notifyError(notification: NotificationEntity, error: String?) {
        if (!secureStorage.notifySyncError) return
        notify(
            notification = notification,
            title = context.getString(R.string.notification_entry_failed_title),
            message = buildErrorMessage(notification, error),
            priority = NotificationCompat.PRIORITY_HIGH
        )
    }

    private fun notify(
        notification: NotificationEntity,
        title: String,
        message: String,
        priority: Int
    ) {
        if (!canPostNotifications()) return

        val builder = NotificationCompat.Builder(context, OpenMonetisApp.CHANNEL_SYNC_RESULTS)
            .setSmallIcon(R.drawable.ic_notification_small)
            .setContentTitle(title)
            .setContentText(message)
            .setStyle(NotificationCompat.BigTextStyle().bigText(message))
            .setPriority(priority)
            .setCategory(NotificationCompat.CATEGORY_STATUS)
            .setAutoCancel(true)
            .setOnlyAlertOnce(false)

        loadSourceAppIcon(notification.sourceApp)?.let { bitmap ->
            builder.setLargeIcon(bitmap)
        }

        NotificationManagerCompat.from(context).notify(notification.id.hashCode(), builder.build())
    }

    private fun buildSuccessMessage(notification: NotificationEntity): String {
        val merchant = notification.parsedName ?: notification.originalTitle ?: notification.sourceAppName
        val amount = notification.parsedAmount?.let { "R$ %.2f".format(it) }
        val suffix = when {
            merchant != null && amount != null -> "$merchant • $amount"
            merchant != null -> merchant
            amount != null -> amount
            else -> context.getString(R.string.notification_entry_generic_success)
        }
        return suffix
    }

    private fun buildErrorMessage(notification: NotificationEntity, error: String?): String {
        val merchant = notification.parsedName ?: notification.originalTitle ?: notification.sourceAppName
        val prefix = merchant ?: context.getString(R.string.notification_entry_generic_error)
        return if (error.isNullOrBlank()) prefix else "$prefix • $error"
    }

    private fun loadSourceAppIcon(packageName: String): Bitmap? {
        return runCatching {
            context.packageManager.getApplicationIcon(packageName).toBitmap()
        }.getOrNull()
    }

    private fun canPostNotifications(): Boolean {
        if (!NotificationManagerCompat.from(context).areNotificationsEnabled()) {
            return false
        }

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            return true
        }

        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.POST_NOTIFICATIONS
        ) == PackageManager.PERMISSION_GRANTED
    }

    companion object {
        // Fixed id so re-auth alerts coalesce instead of stacking.
        private const val REAUTH_NOTIFICATION_ID = 424242
    }
}
