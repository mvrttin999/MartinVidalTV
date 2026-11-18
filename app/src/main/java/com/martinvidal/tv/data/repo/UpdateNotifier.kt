package com.martinvidal.tv.data.repo

import android.content.Context
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build
import com.martinvidal.tv.R
import com.martinvidal.tv.ui.update.InstallUpdateActivity
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/** Aviso del sistema cuando una actualización ya está descargada y verificada. */
@Singleton
class UpdateNotifier @Inject constructor(
    @ApplicationContext private val context: Context
) {
    companion object {
        private const val CHANNEL = "updates"
        private const val ID = 4101
    }

    fun show(version: String, notes: String) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = context.getSystemService(NotificationManager::class.java)
            val channel = NotificationChannel(
                CHANNEL,
                context.getString(R.string.update_channel),
                NotificationManager.IMPORTANCE_DEFAULT
            )
            channel.description = context.getString(R.string.update_channel_desc)
            manager?.createNotificationChannel(channel)
        }
        if (!NotificationManagerCompat.from(context).areNotificationsEnabled()) return

        val intent = InstallUpdateActivity.intent(context, version)
        val flags = PendingIntentFlags
        val pending = android.app.PendingIntent.getActivity(
            context,
            ID,
            intent,
            flags
        )
        val text = buildString {
            append(context.getString(R.string.update_notify_text, version))
            if (notes.isNotBlank()) append("\n").append(notes.take(120))
        }
        val notification = NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(R.drawable.ic_logo)
            .setContentTitle(context.getString(R.string.update_notify_title))
            .setContentText(context.getString(R.string.update_notify_text, version))
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setContentIntent(pending)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()
        @Suppress("MissingPermission")
        NotificationManagerCompat.from(context).notify(ID, notification)
    }

    /** FLAG_IMMUTABLE solo existe desde API 23. */
    private val PendingIntentFlags: Int
        get() = android.app.PendingIntent.FLAG_UPDATE_CURRENT or
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                android.app.PendingIntent.FLAG_IMMUTABLE
            } else {
                0
            }
}
