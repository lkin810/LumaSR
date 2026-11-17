package io.lumasr.app.batch

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import io.lumasr.app.MainActivity
import io.lumasr.app.R

object BatchNotifications {
    const val CHANNEL_ID = "batch_progress"
    const val PROGRESS_ID = 1001
    const val DONE_ID = 1002

    fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "批量超分",
                NotificationManager.IMPORTANCE_LOW,
            ).apply {
                description = "批量图片超分进度"
                setShowBadge(false)
            }
            context.getSystemService(NotificationManager::class.java)
                ?.createNotificationChannel(channel)
        }
    }

    fun progress(context: Context, done: Int, total: Int, text: String): Notification {
        val safeTotal = total.coerceAtLeast(1)
        return NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setContentTitle("批量超分")
            .setContentText(text)
            .setProgress(safeTotal, done.coerceIn(0, safeTotal), false)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(contentIntent(context))
            .build()
    }

    fun updateProgress(context: Context, done: Int, total: Int, text: String) {
        notify(context, PROGRESS_ID, progress(context, done, total, text))
    }

    fun notifyDone(context: Context, success: Int, failed: Int, enabled: Boolean = true) {
        if (!enabled) return
        val text = if (failed > 0) "完成 $success 张，失败 $failed 张" else "完成 $success 张"
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_download_done)
            .setContentTitle("批量超分完成")
            .setContentText(text)
            .setAutoCancel(true)
            .setContentIntent(contentIntent(context))
            .build()
        notify(context, DONE_ID, notification)
    }

    private fun contentIntent(context: Context): PendingIntent {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        return PendingIntent.getActivity(
            context,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private fun notify(context: Context, id: Int, notification: Notification) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            return
        }
        runCatching {
            NotificationManagerCompat.from(context).notify(id, notification)
        }
    }
}
