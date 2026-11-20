package com.meshlink.app.notification

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import com.meshlink.app.MainActivity

object AssignmentNotificationHelper {
    const val CHANNEL_ID = "meshlink_emergency_assignments"
    private const val CHANNEL_NAME = "Tactical Emergency Assignments"
    private const val NOTIFICATION_ID_BASE = 8000

    internal var sdkIntOverride: Int? = null
    internal fun getSdkInt(): Int = sdkIntOverride ?: Build.VERSION.SDK_INT

    fun createNotificationChannel(context: Context) {
        if (getSdkInt() >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                CHANNEL_NAME,
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Heads-up alerts for urgent search & rescue dispatches"
                enableVibration(true)
                setShowBadge(true)
            }
            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
            manager?.createNotificationChannel(channel)
        }
    }

    fun showAssignmentHeadsUpNotification(
        context: Context,
        assignmentId: String,
        title: String,
        notes: String
    ) {
        try {
            createNotificationChannel(context)

            if (getSdkInt() >= Build.VERSION_CODES.TIRAMISU) {
                if (androidx.core.content.ContextCompat.checkSelfPermission(
                        context,
                        android.Manifest.permission.POST_NOTIFICATIONS
                    ) != android.content.pm.PackageManager.PERMISSION_GRANTED
                ) {
                    timber.log.Timber.w("POST_NOTIFICATIONS permission not granted. Skipping heads-up notification.")
                    return
                }
            }

            val intent = Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                putExtra("NAVIGATE_TO", "assignments")
                putExtra("ASSIGNMENT_ID", assignmentId)
            }

            val pendingIntent = PendingIntent.getActivity(
                context,
                assignmentId.hashCode(),
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            val notification = NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.ic_dialog_alert)
                .setContentTitle("DISPATCH ASSIGNMENT: $title")
                .setContentText(notes.ifBlank { "Immediate response required" })
                .setStyle(NotificationCompat.BigTextStyle().bigText("Incident: $title\nDetails: $notes\nTap to review and respond."))
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setCategory(NotificationCompat.CATEGORY_ALARM)
                .setAutoCancel(true)
                .setContentIntent(pendingIntent)
                .build()

            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
            manager?.notify(NOTIFICATION_ID_BASE + Math.abs(assignmentId.hashCode() % 1000), notification)
        } catch (e: Exception) {
            timber.log.Timber.w(e, "Could not post heads-up assignment notification")
        }
    }
}
