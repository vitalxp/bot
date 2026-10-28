package app.taxicom.commandos.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.lifecycleScope
import app.taxicom.commandos.MainActivity
import app.taxicom.commandos.R
import app.taxicom.commandos.TaxiCommandosApp
import app.taxicom.commandos.data.SyncWorker
import app.taxicom.core.model.Verdict
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

class TaxiCommandosService : LifecycleService() {
    override fun onCreate() {
        super.onCreate()
        ensureChannels()
        val graph = (application as TaxiCommandosApp).graph
        startForegroundCompat(notif(getString(R.string.notif_shift_title), getString(R.string.notif_shift_text)))
        graph.wheel.start()
        graph.bus.start()
        graph.pipeline.startShift()

        lifecycleScope.launch {
            graph.prefs.prefs.collectLatest { graph.pipeline.updatePrefs(it); graph.wheel.setEnabled(it.wheelEnabled); graph.voice.setEnabled(it.ttsEnabled) }
        }
        lifecycleScope.launch {
            combine(graph.pipeline.live, graph.pipeline.shift) { live, shift -> live to shift }.collectLatest { (live, _) ->
                if (live == null) {
                    nm().notify(NOTIF_SHIFT, notif(getString(R.string.notif_shift_title), getString(R.string.notif_shift_text)))
                    graph.wheel.updateNowPlaying("Taxi Commandos", "On shift")
                } else {
                    val v = when (live.score.verdict) {
                        Verdict.TAKE -> getString(R.string.verdict_take)
                        Verdict.MAYBE -> getString(R.string.verdict_maybe)
                        Verdict.SKIP -> getString(R.string.verdict_skip)
                    }
                    val body = "${live.offer.platform.displayName} ${live.offer.price} · ${live.score.rKm}/km · ${live.score.rH}/h"
                    nm().notify(NOTIF_SHIFT, notif(v, body))
                    graph.wheel.updateNowPlaying(v, body)
                }
            }
        }
        lifecycleScope.launch {
            graph.pipeline.alerts.collectLatest { alert ->
                val pct = "${(alert.rate * 100).toInt()}%"
                val level = "${(alert.level * 100).toInt()}%"
                val text = getString(R.string.alert_acceptance, alert.platform.displayName, pct, level)
                nm().notify(NOTIF_ALERT, alertNotif(text))
            }
        }
        lifecycleScope.launch {
            graph.bus.incoming.collectLatest { graph.pipeline.onBusMessage(it) }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                stopSelf()
                return START_NOT_STICKY
            }
        }
        return super.onStartCommand(intent, flags, startId)
    }

    override fun onDestroy() {
        val graph = (application as TaxiCommandosApp).graph
        graph.pipeline.endShift()
        graph.wheel.stop()
        graph.bus.stop()
        SyncWorker.syncNow(this)
        super.onDestroy()
    }

    private fun startForegroundCompat(notification: Notification) {
        if (Build.VERSION.SDK_INT >= 29) {
            startForeground(NOTIF_SHIFT, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK)
        } else {
            startForeground(NOTIF_SHIFT, notification)
        }
    }

    private fun notif(title: String, text: String): Notification {
        val open = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val stop = PendingIntent.getService(
            this, 1, Intent(this, TaxiCommandosService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        return NotificationCompat.Builder(this, CHANNEL_SHIFT)
            .setSmallIcon(R.drawable.ic_launcher_monochrome)
            .setContentTitle(title)
            .setContentText(text)
            .setContentIntent(open)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .addAction(0, getString(R.string.shift_stop), stop)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .build()
    }

    private fun alertNotif(text: String): Notification =
        NotificationCompat.Builder(this, CHANNEL_ALERTS)
            .setSmallIcon(R.drawable.ic_launcher_monochrome)
            .setContentTitle(getString(R.string.app_name))
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .build()

    private fun ensureChannels() {
        if (Build.VERSION.SDK_INT < 26) return
        val nm = nm()
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_SHIFT, getString(R.string.channel_shift), NotificationManager.IMPORTANCE_LOW),
        )
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_ALERTS, getString(R.string.channel_alerts), NotificationManager.IMPORTANCE_HIGH),
        )
    }

    private fun nm() = getSystemService(NOTIFICATION_SERVICE) as NotificationManager

    companion object {
        const val ACTION_STOP = "app.taxicom.commandos.STOP_SHIFT"
        private const val CHANNEL_SHIFT = "shift"
        private const val CHANNEL_ALERTS = "alerts"
        private const val NOTIF_SHIFT = 1001
        private const val NOTIF_ALERT = 1002

        fun start(context: android.content.Context) {
            val i = Intent(context, TaxiCommandosService::class.java)
            if (Build.VERSION.SDK_INT >= 26) context.startForegroundService(i) else context.startService(i)
        }

        fun stop(context: android.content.Context) {
            context.startService(Intent(context, TaxiCommandosService::class.java).setAction(ACTION_STOP))
        }
    }
}
