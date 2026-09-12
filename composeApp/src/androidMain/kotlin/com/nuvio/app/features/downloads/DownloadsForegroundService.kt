package com.nuvio.app.features.downloads

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.nuvio.app.core.deeplink.buildDownloadsDeepLinkUrl
import com.nuvio.app.features.settings.AppIconPlatform
import kotlinx.coroutines.runBlocking
import kotlin.math.roundToInt
import nuvio.composeapp.generated.resources.Res
import nuvio.composeapp.generated.resources.downloads_foreground_count
import nuvio.composeapp.generated.resources.downloads_foreground_summary
import org.jetbrains.compose.resources.getString

/**
 * Foreground service that keeps the app process alive while downloads run.
 *
 * Before this service existed the download coroutines lived in the plain app
 * process: as soon as the user left the app for a while, Android froze or killed
 * the process (Doze / cached-app limits) and every in-flight download — and its
 * progress notification — died with it. A `dataSync` foreground service holds a
 * foreground priority for exactly as long as at least one download is active,
 * which keeps the existing downloader + live status notifications running.
 *
 * The service itself never touches the network: it only keeps the process alive
 * and mirrors an aggregate progress summary. Downloads continue to run through
 * [DownloadsRepository] / [DownloadsPlatformDownloader], exactly as before.
 *
 * Process-death recovery: the service is START_STICKY. When Android restarts it
 * after the process was killed mid-download, [onStartCommand] receives a null
 * intent; the service then resumes every download that [DownloadsLiveStatusPlatform]
 * recorded as actively downloading before the death (user-paused items stay paused).
 */
class DownloadsForegroundService : Service() {

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        DownloadsStorage.initialize(applicationContext)
        DownloadsPlatformDownloader.initialize(applicationContext)
        DownloadsLiveStatusPlatform.initialize(applicationContext)
        DownloadsRepository.ensureLoaded()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ActionStop -> {
                ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
                stopSelf()
                return START_NOT_STICKY
            }
            else -> {
                val downloadingItems = DownloadsRepository.uiState.value.items
                    .filter { it.status == DownloadStatus.Downloading }
                ServiceCompat.startForeground(
                    this,
                    NotificationId,
                    buildSummaryNotification(this, downloadingItems),
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
                    } else {
                        0
                    },
                )
                // Null intent = START_STICKY restart after the process was killed
                // mid-download. Bring the interrupted downloads back up.
                if (intent == null) {
                    resumeInterruptedDownloads()
                }
            }
        }
        return START_STICKY
    }

    override fun onDestroy() {
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        super.onDestroy()
    }

    private fun resumeInterruptedDownloads() {
        val interruptedIds = DownloadsLiveStatusPlatform.consumeInterruptedDownloadIds()
        if (interruptedIds.isEmpty()) return
        DownloadsRepository.ensureLoaded()
        val resumable = DownloadsRepository.uiState.value.items
            .filter { it.id in interruptedIds }
            .filter { it.status == DownloadStatus.Paused || it.status == DownloadStatus.Failed }
            .map { it.id }
        resumable.forEach(DownloadsRepository::resumeDownload)
    }

    companion object {
        private const val ActionStart = "com.nuvio.app.downloads.service.START"
        private const val ActionStop = "com.nuvio.app.downloads.service.STOP"
        internal const val NotificationId = 4711
        internal const val ChannelId = "downloads_foreground"

        @Volatile
        private var running = false

        /** Starts the foreground service while at least one download is active. */
        fun sync(context: Context, activeDownloadCount: Int) {
            if (activeDownloadCount > 0) {
                if (running) return
                val intent = Intent(context, DownloadsForegroundService::class.java).apply {
                    action = ActionStart
                }
                try {
                    ContextCompat.startForegroundService(context, intent)
                    running = true
                } catch (_: Exception) {
                    // Background start restrictions (Android 12+) — the process will
                    // simply keep running as a cached process for as long as Android
                    // allows it; downloads continue meanwhile.
                    running = false
                }
            } else {
                if (!running) return
                running = false
                // stopService is allowed from any app state; the service removes its
                // own notification in onDestroy().
                runCatching {
                    context.stopService(Intent(context, DownloadsForegroundService::class.java))
                }
            }
        }

        /** Refreshes the foreground summary notification with the current progress. */
        fun notifyProgress(context: Context, downloadingItems: List<DownloadItem>) {
            if (!running || downloadingItems.isEmpty()) return
            runCatching {
                NotificationManagerCompat.from(context)
                    .notify(NotificationId, buildSummaryNotification(context, downloadingItems))
            }
        }

        private fun buildSummaryNotification(
            context: Context,
            downloadingItems: List<DownloadItem>,
        ): android.app.Notification {
            ensureChannel(context)
            val percents = downloadingItems.mapNotNull { item ->
                item.totalBytes?.takeIf { it > 0L }?.let { total ->
                    ((item.downloadedBytes.coerceAtLeast(0L).toDouble() / total) * 100.0).roundToInt()
                        .coerceIn(0, 100)
                }
            }
            val overallPercent = if (percents.size == downloadingItems.size && percents.isNotEmpty()) {
                (percents.sum() / percents.size).coerceIn(0, 100)
            } else {
                -1
            }

            val launchIntent = Intent().apply {
                component = AppIconPlatform.currentLauncherComponent(context)
                action = Intent.ACTION_VIEW
                data = android.net.Uri.parse(buildDownloadsDeepLinkUrl())
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_CLEAR_TOP or
                    Intent.FLAG_ACTIVITY_SINGLE_TOP
            }
            val contentIntent = PendingIntent.getActivity(
                context,
                NotificationId,
                launchIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )

            val title = if (downloadingItems.size == 1) {
                downloadingItems.first().title
            } else {
                runBlocking { getString(Res.string.downloads_foreground_count, downloadingItems.size) }
            }

            val builder = NotificationCompat.Builder(context, ChannelId)
                .setSmallIcon(com.nuvio.app.R.drawable.ic_notification_small)
                .setContentTitle(title)
                .setContentText(runBlocking { getString(Res.string.downloads_foreground_summary) })
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .setSilent(true)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .setCategory(NotificationCompat.CATEGORY_PROGRESS)
                .setContentIntent(contentIntent)

            if (overallPercent >= 0) {
                builder.setProgress(100, overallPercent, false)
            } else {
                builder.setProgress(100, 0, true)
            }
            return builder.build()
        }

        private fun ensureChannel(context: Context) {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
            val manager = context.getSystemService(NotificationManager::class.java) ?: return
            if (manager.getNotificationChannel(ChannelId) != null) return
            manager.createNotificationChannel(
                NotificationChannel(
                    ChannelId,
                    "Background downloads",
                    NotificationManager.IMPORTANCE_LOW,
                ).apply {
                    description = "Keeps downloads running while NetMax is in the background."
                    setShowBadge(false)
                },
            )
        }
    }
}
