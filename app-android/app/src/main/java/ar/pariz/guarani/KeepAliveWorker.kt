package ar.pariz.guarani

import android.content.Context
import android.webkit.CookieManager
import androidx.work.CoroutineWorker
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL

/**
 * Mantiene abierta la sesión de Guaraní: cada pocos minutos pide la portada con las cookies de la
 * sesión (un pedido liviano, sin navegador). Si Guaraní contesta con la pantalla de ingreso, la
 * sesión venció: con contraseña guardada se lanza una revisión completa, que vuelve a entrar;
 * sin contraseña, se avisa una sola vez.
 */
class KeepAliveWorker(ctx: Context, params: WorkerParameters) : CoroutineWorker(ctx, params) {

    enum class Ping { ALIVE, EXPIRED, ERROR }

    companion object {
        /** ¿La respuesta de Guaraní es la pantalla de ingreso? */
        fun looksLoggedOut(finalUrl: String, body: String): Boolean {
            val b = body.lowercase()
            return Regex("type=\\\\?[\"']password").containsMatchIn(b) ||
                Regex("auth=(saml|cas|oidc|arai|form)").containsMatchIn(b) ||
                Regex("/(acceso|login)(/login)?(\\?|$)").containsMatchIn(finalUrl.lowercase())
        }
    }

    override suspend fun doWork(): Result {
        val c = applicationContext
        if (Config.keepAlive(c)) {
            for (prof in Profiles.all(c)) {
                if (!Profiles.ownsSession(c, prof)) continue   // el sitio tiene abierta otra cuenta: la renueva su revisión
                when (withContext(Dispatchers.IO) { ping(prof.url) }) {
                    Ping.ALIVE -> Store.setFlag(c, prof.id, "session_warned", false)
                    Ping.EXPIRED ->
                        if (Profiles.pass(c, prof.id).isNotEmpty()) Scheduler.checkNow(c, prof.id)
                        else warnExpired(c, prof)
                    Ping.ERROR -> {}
                }
            }
        }
        if (!inputData.getBoolean("once", false) && Config.keepAlive(c) && Config.configured(c))
            WorkManager.getInstance(c).enqueue(Scheduler.nextKeepAlive())
        return Result.success()
    }

    private fun warnExpired(c: Context, prof: Profile) {
        val res = Store.resumen(c, prof.id)
        res.put("sesion", "vencida"); Store.putResumen(c, prof.id, res)
        Store.setStatus(c, prof.id, "${Store.shortNow()} · La sesión de Guaraní venció y la contraseña no está guardada: entrá desde la pestaña Guaraní.")
        if (Store.flag(c, prof.id, "session_warned")) return
        Store.setFlag(c, prof.id, "session_warned", true)
        val t = if (Profiles.all(c).size > 1) "${prof.nombre} · " else ""
        Notifier.notify(c, Notifier.CH_ESTADO, "${t}Guaraní: hay que volver a entrar",
            "La sesión venció. Entrá desde la pestaña Guaraní (o guardá la contraseña) para seguir recibiendo avisos.", tab = "web", pid = prof.id)
    }

    private fun ping(base: String): Ping {
        val cm = CookieManager.getInstance()
        var url = base
        return try {
            var body = ""
            repeat(5) {
                val cookie = cm.getCookie(url) ?: if (url == base) return Ping.EXPIRED else ""
                val con = (URL(url).openConnection() as HttpURLConnection).apply {
                    instanceFollowRedirects = false
                    connectTimeout = 15_000; readTimeout = 20_000
                    setRequestProperty("Cookie", cookie)
                    setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android) AppleWebKit/537.36 Chrome/124 Mobile Safari/537.36")
                    setRequestProperty("Accept", "text/html")
                }
                val code = con.responseCode
                con.headerFields["Set-Cookie"]?.forEach { cm.setCookie(url, it) }
                if (code in 300..399) {
                    val loc = con.getHeaderField("Location") ?: return Ping.ERROR
                    con.disconnect()
                    url = URL(URL(url), loc).toString()
                    return@repeat
                }
                if (code >= 500) { con.disconnect(); return Ping.ERROR }
                body = con.inputStream.use { it.readNBytesCompat(300_000) }.toString(Charsets.ISO_8859_1)
                con.disconnect()
                cm.flush()
                return if (looksLoggedOut(url, body)) Ping.EXPIRED else Ping.ALIVE
            }
            Ping.ERROR
        } catch (e: Exception) { Ping.ERROR }
    }

    private fun java.io.InputStream.readNBytesCompat(max: Int): ByteArray {
        val out = java.io.ByteArrayOutputStream()
        val buf = ByteArray(8192)
        while (out.size() < max) { val n = read(buf); if (n < 0) break; out.write(buf, 0, n) }
        return out.toByteArray()
    }
}
