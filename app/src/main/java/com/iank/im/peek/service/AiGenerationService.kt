package com.iank.im.peek.service

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.iank.im.peek.MainActivity
import com.iank.im.peek.R

class AiGenerationService : Service() {

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        ensureChannels(this)

        if (intent?.action == ACTION_STOP) {
            try {
                stopForeground(STOP_FOREGROUND_REMOVE)
            } catch (_: Exception) {
            }
            stopSelf()
            return START_NOT_STICKY
        }

        val notification = buildProgressNotification(this)
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                startForeground(
                    PROGRESS_NOTIFICATION_ID,
                    notification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
                )
            } else {
                startForeground(PROGRESS_NOTIFICATION_ID, notification)
            }
        } catch (_: SecurityException) {
            // Continue safely even if notification/FGS permission is restricted
        } catch (_: Exception) {
        }

        return START_NOT_STICKY
    }

    companion object {
        const val EXTRA_CONVERSATION_ID = "extra_conversation_id"

        private const val ACTION_START = "com.iank.im.peek.action.START_AI_GEN"
        private const val ACTION_STOP = "com.iank.im.peek.action.STOP_AI_GEN"

        private const val CHANNEL_ID_PROGRESS = "peek_ai_generation_progress"
        private const val CHANNEL_ID_COMPLETE = "peek_ai_generation_complete"
        private const val PROGRESS_NOTIFICATION_ID = 4101

        fun startForGeneration(context: Context) {
            runCatching {
                val appContext = context.applicationContext
                ensureChannels(appContext)
                val intent = Intent(appContext, AiGenerationService::class.java).apply {
                    action = ACTION_START
                }
                ContextCompat.startForegroundService(appContext, intent)
            }
        }

        fun stopService(context: Context) {
            runCatching {
                val appContext = context.applicationContext
                val intent = Intent(appContext, AiGenerationService::class.java).apply {
                    action = ACTION_STOP
                }
                appContext.startService(intent)
            }
        }

        fun postCompletionNotificationIfPermitted(
            context: Context,
            conversationId: String,
            conversationTitle: String,
            responsePreview: String
        ) {
            val appContext = context.applicationContext
            ensureChannels(appContext)

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                val granted = ContextCompat.checkSelfPermission(
                    appContext,
                    Manifest.permission.POST_NOTIFICATIONS
                ) == PackageManager.PERMISSION_GRANTED
                if (!granted) return
            }

            val launchIntent = Intent(appContext, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
                putExtra(EXTRA_CONVERSATION_ID, conversationId)
            }
            val requestCode = conversationId.hashCode()
            val pendingIntent = PendingIntent.getActivity(
                appContext,
                requestCode,
                launchIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            val title = conversationTitle.ifBlank {
                appContext.getString(R.string.notif_ai_complete_default_title)
            }
            val body = responsePreview.ifBlank {
                appContext.getString(R.string.notif_ai_complete_default_body)
            }

            val notification = NotificationCompat.Builder(appContext, CHANNEL_ID_COMPLETE)
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .setContentTitle(title)
                .setContentText(body)
                .setStyle(NotificationCompat.BigTextStyle().bigText(body))
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .setAutoCancel(true)
                .setContentIntent(pendingIntent)
                .build()

            try {
                NotificationManagerCompat.from(appContext)
                    .notify(5000 + (requestCode and 0x0FFF), notification)
            } catch (_: SecurityException) {
            }
        }

        private fun buildProgressNotification(context: Context): Notification {
            val openIntent = Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
            val pendingIntent = PendingIntent.getActivity(
                context,
                0,
                openIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            return NotificationCompat.Builder(context, CHANNEL_ID_PROGRESS)
                .setSmallIcon(android.R.drawable.ic_popup_sync)
                .setContentTitle(context.getString(R.string.notif_ai_generating_title))
                .setContentText(context.getString(R.string.notif_ai_generating_body))
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .setContentIntent(pendingIntent)
                .build()
        }

        private fun ensureChannels(context: Context) {
            val manager = context.getSystemService(NotificationManager::class.java) ?: return
            val progressChannel = NotificationChannel(
                CHANNEL_ID_PROGRESS,
                context.getString(R.string.notif_channel_progress_name),
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = context.getString(R.string.notif_channel_progress_desc)
                setShowBadge(false)
            }

            val completeChannel = NotificationChannel(
                CHANNEL_ID_COMPLETE,
                context.getString(R.string.notif_channel_complete_name),
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = context.getString(R.string.notif_channel_complete_desc)
            }

            manager.createNotificationChannels(listOf(progressChannel, completeChannel))
        }
    }
}
