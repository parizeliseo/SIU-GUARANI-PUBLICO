package ar.pariz.guarani

import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat

/**
 * Vigilancia permanente (opcional): un servicio con notificación fija que mantiene la app despierta
 * y toca Guaraní cada pocos minutos. Sirve en teléfonos que cierran las apps en segundo plano.
 */
class WatchService : Service() {
    private val h = Handler(Looper.getMainLooper())
    private val tick = object : Runnable {
        override fun run() {
            Scheduler.pingNow(this@WatchService)
            h.postDelayed(this, Config.KEEPALIVE_MIN * 60_000L)
        }
    }

    override fun onBind(i: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        val n = Profiles.all(this).size
        val pi = PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE)
        val notif = NotificationCompat.Builder(this, Notifier.CH_VIG)
            .setSmallIcon(R.drawable.ic_notif)
            .setContentTitle("Guaraní al día está vigilando")
            .setContentText(if (n == 1) "Sesión abierta · 1 cuenta" else "Sesión abierta · $n cuentas")
            .setOngoing(true).setContentIntent(pi)
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .build()
        runCatching {
            ServiceCompat.startForeground(this, 77, notif,
                if (Build.VERSION.SDK_INT >= 34) ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE else 0)
        }.onFailure { stopSelf(); return }
        h.postDelayed(tick, 60_000L)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int = START_STICKY

    override fun onDestroy() { h.removeCallbacks(tick); super.onDestroy() }

    companion object {
        /** Enciende o apaga el servicio según el ajuste. */
        fun sync(c: Context) {
            val i = Intent(c, WatchService::class.java)
            if (Config.fixed(c) && Config.configured(c)) runCatching { ContextCompat.startForegroundService(c, i) }
            else c.stopService(i)
        }
    }
}

/** Al prender el teléfono o actualizar la app, retoma la vigilancia. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(c: Context, i: Intent) {
        if (i.action != Intent.ACTION_BOOT_COMPLETED && i.action != Intent.ACTION_MY_PACKAGE_REPLACED) return
        Scheduler.schedule(c, replace = false)
        Scheduler.keepAlive(c)
        WatchService.sync(c)
    }
}
