package ar.pariz.guarani

import android.content.Context
import androidx.work.*
import java.util.concurrent.TimeUnit

object Scheduler {
    private const val PERIODIC = "guarani-periodico"
    private const val NOW = "guarani-ahora"

    private fun constraints(c: Context) = Constraints.Builder()
        .setRequiredNetworkType(if (Config.wifiOnly(c)) NetworkType.UNMETERED else NetworkType.CONNECTED)
        .build()

    fun schedule(c: Context, replace: Boolean) {
        if (!Config.configured(c)) return
        val req = PeriodicWorkRequestBuilder<CheckWorker>(Config.interval(c).toLong(), TimeUnit.MINUTES)
            .setConstraints(constraints(c))
            .addTag(CheckWorker.TAG)
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 10, TimeUnit.MINUTES)
            .build()
        WorkManager.getInstance(c).enqueueUniquePeriodicWork(
            PERIODIC,
            if (replace) ExistingPeriodicWorkPolicy.UPDATE else ExistingPeriodicWorkPolicy.KEEP,
            req
        )
    }

    /** Revisa ya una cuenta ([pid]) o todas (null). */
    fun checkNow(c: Context, pid: String? = null) {
        val req = OneTimeWorkRequestBuilder<CheckWorker>()
            .setInputData(workDataOf("pid" to pid))
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .addTag(CheckWorker.TAG)
            .build()
        WorkManager.getInstance(c).enqueueUniqueWork(NOW + (pid ?: ""), ExistingWorkPolicy.KEEP, req)
    }

    // ----- Sesión abierta -----
    private const val KA_TAG = "guarani-keepalive"

    /** (Re)inicia la cadena que toca Guaraní cada pocos minutos para que la sesión no venza. */
    fun keepAlive(c: Context) {
        val wm = WorkManager.getInstance(c)
        wm.cancelAllWorkByTag(KA_TAG)
        if (!Config.keepAlive(c) || !Config.configured(c)) return
        wm.enqueue(nextKeepAlive())
    }

    fun nextKeepAlive(): OneTimeWorkRequest = OneTimeWorkRequestBuilder<KeepAliveWorker>()
        .setInitialDelay(Config.KEEPALIVE_MIN, TimeUnit.MINUTES)
        .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
        .addTag(KA_TAG)
        .build()

    /** Un toque ya mismo (lo usa la vigilancia permanente). */
    fun pingNow(c: Context) {
        val req = OneTimeWorkRequestBuilder<KeepAliveWorker>()
            .setInputData(workDataOf("once" to true))
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .build()
        WorkManager.getInstance(c).enqueueUniqueWork("guarani-ping", ExistingWorkPolicy.KEEP, req)
    }
}
