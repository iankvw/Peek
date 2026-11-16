package com.iank.im.peek.wearbridge

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.iank.im.peek.R
import com.iank.im.peek.domain.repository.ConversationRepository
import com.iank.im.peek.domain.usecase.LocalizedResourceProvider
import com.iank.im.peek.ui.common.AppVisibilityTracker
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class WearReportDispatcher @Inject constructor(
    @param:ApplicationContext private val appContext: Context,
    private val conversationRepository: ConversationRepository,
    private val wearStateSyncManager: WearStateSyncManager,
    private val appVisibilityTracker: AppVisibilityTracker,
    private val resources: LocalizedResourceProvider
) {
    suspend fun dispatchReport(messageId: String, reason: String): WearSimpleAckResponse {
        val message = conversationRepository.getMessage(messageId)
            ?: return WearSimpleAckResponse(
                success = false,
                errorCode = "NOT_FOUND",
                errorMessage = "Message not found"
            )

        // Immediately mask in Room DB and notify both phone & watch UI
        conversationRepository.markMessageReported(messageId, isReported = true)
        wearStateSyncManager.notifyMessageUpdated(message.conversationId, messageId)

        val modelLabel = listOfNotNull(
            message.servedGroupName?.takeIf { it.isNotBlank() },
            message.servedPresetName?.takeIf { it.isNotBlank() },
            (message.servedModelName ?: message.modelIdUsed)?.takeIf { it.isNotBlank() }
        ).joinToString(" / ").ifBlank { "Unknown" }

        val email = resources.getString(R.string.developer_report_email)
        val subject = resources.getString(R.string.report_email_subject)
        val body = resources.getString(
            R.string.report_email_body_template,
            reason,
            modelLabel,
            message.id,
            message.textContent.take(4000)
        )

        val emailIntent = Intent(Intent.ACTION_SENDTO).apply {
            data = Uri.parse("mailto:")
            putExtra(Intent.EXTRA_EMAIL, arrayOf(email))
            putExtra(Intent.EXTRA_SUBJECT, subject)
            putExtra(Intent.EXTRA_TEXT, body)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }

        val launchedForeground = if (appVisibilityTracker.isAppInForeground) {
            runCatching { appContext.startActivity(emailIntent) }.isSuccess
        } else {
            false
        }

        if (!launchedForeground) {
            postReportEmailNotificationIfPermitted(messageId, emailIntent)
        }

        return WearSimpleAckResponse(success = true)
    }

    private fun postReportEmailNotificationIfPermitted(messageId: String, emailIntent: Intent) {
        ensureReportChannel()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val granted = ContextCompat.checkSelfPermission(
                appContext,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
            if (!granted) return
        }

        val chooser = Intent.createChooser(
            emailIntent,
            resources.getString(R.string.report_dialog_title)
        ).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }

        val requestCode = messageId.hashCode()
        val pendingIntent = PendingIntent.getActivity(
            appContext,
            requestCode,
            chooser,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(appContext, CHANNEL_ID_REPORT)
            .setSmallIcon(android.R.drawable.ic_dialog_email)
            .setContentTitle(resources.getString(R.string.notif_report_email_title))
            .setContentText(resources.getString(R.string.notif_report_email_body))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()

        try {
            NotificationManagerCompat.from(appContext)
                .notify(REPORT_NOTIFICATION_BASE_ID + (requestCode and 0x0FFF), notification)
        } catch (_: SecurityException) {
        }
    }

    private fun ensureReportChannel() {
        val manager = appContext.getSystemService(NotificationManager::class.java) ?: return
        val channel = NotificationChannel(
            CHANNEL_ID_REPORT,
            resources.getString(R.string.notif_channel_report_name),
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = resources.getString(R.string.notif_channel_report_desc)
        }
        manager.createNotificationChannel(channel)
    }

    companion object {
        private const val CHANNEL_ID_REPORT = "peek_ai_report_action"
        private const val REPORT_NOTIFICATION_BASE_ID = 6000
    }
}
