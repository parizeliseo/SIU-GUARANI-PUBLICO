package ar.pariz.guarani

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout

/** Revisa una cuenta o todas, una detrás de otra (comparten el navegador y las cookies). */
class CheckWorker(ctx: Context, params: WorkerParameters) : CoroutineWorker(ctx, params) {
    companion object {
        const val TAG = "guarani-check"
        private val lock = Mutex()
        private const val PRESUPUESTO_MS = 9 * 60_000L
        private const val MINIMO_MS = 90_000L
    }

    override suspend fun doWork(): Result {
        val c = applicationContext
        val only = inputData.getString("pid")
        // Primero la cuenta que hace más tiempo que no se revisa: si son muchas y no alcanza el tiempo,
        // las que quedan van primero la próxima vez (ninguna queda sin revisar).
        val cuentas = Profiles.all(c).filter { only == null || it.id == only }.sortedBy { Store.ultimaRevision(c, it.id) }
        val inicio = System.currentTimeMillis()
        lock.withLock {
            for (prof in cuentas) {
                // Android corta un trabajo en segundo plano a los 10 minutos: no se empieza una cuenta sin tiempo.
                val queda = PRESUPUESTO_MS - (System.currentTimeMillis() - inicio)
                if (queda < MINIMO_MS) break
                Store.ensureSeed(c, prof.id)
                Store.setUltimaRevision(c, prof.id)
                try {
                    val r = withTimeout(minOf(6 * 60_000L, queda)) {
                        withContext(Dispatchers.Main) {
                            Profiles.useSessionOf(c, prof)
                            Scraper(c).run(prof.url, Profiles.user(c, prof.id), Profiles.pass(c, prof.id), Store.watched(c, prof.id))
                        }
                    }
                    Analyzer(c, prof).process(r)
                    Analyzer(c, prof).recordatorios()
                } catch (e: Throwable) {
                    Store.setStatus(c, prof.id, "${Store.shortNow()} · Error en la revisión: ${e.javaClass.simpleName} ${e.message ?: ""}".trim())
                }
            }
        }
        Scheduler.keepAlive(c)   // la revisión ya renovó la sesión: la cadena arranca de nuevo
        return Result.success()
    }
}
