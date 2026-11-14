package ar.pariz.guarani

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.ContentResolver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioAttributes
import android.net.Uri
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat

object Notifier {
    /** Calificaciones: canal con sonido propio. (El canal viejo «notas» no tenía sonido propio y se retira.) */
    const val CH_NOTAS = "calificaciones"
    private const val CH_NOTAS_VIEJO = "notas"
    const val CH_CAMBIOS = "cambios"
    const val CH_ESTADO = "estado"
    const val CH_INSC = "inscripciones"
    const val CH_PLAN = "plan"
    const val CH_MSG = "mensajes"
    const val CH_REC = "recordatorios"
    const val CH_VIG = "vigilancia"
    private const val GROUP = "guarani"

    /** Botón de una notificación: qué dice y qué hace la app al tocarlo. */
    data class Accion(val etiqueta: String, val hacer: String, val datos: String)

    fun sonidoCalificacion(c: Context): Uri =
        Uri.parse("${ContentResolver.SCHEME_ANDROID_RESOURCE}://${c.packageName}/${R.raw.calificacion}")

    fun createChannels(c: Context) {
        val nm = c.getSystemService(NotificationManager::class.java)
        runCatching { nm.deleteNotificationChannel(CH_NOTAS_VIEJO) }
        val sonido = AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_NOTIFICATION)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build()
        nm.createNotificationChannel(NotificationChannel(CH_NOTAS, "Calificaciones nuevas", NotificationManager.IMPORTANCE_HIGH).apply {
            description = "Cuando se carga o cambia una nota, una promoción o un acta. Suena con un tono propio."
            setSound(sonidoCalificacion(c), sonido)
            enableVibration(true)
            vibrationPattern = longArrayOf(0, 180, 120, 180, 120, 320)
            enableLights(true)
        })
        nm.createNotificationChannel(NotificationChannel(CH_INSC, "Inscripciones y mesas de examen", NotificationManager.IMPORTANCE_HIGH).apply {
            description = "Apertura y cierre de inscripciones, mesas y fechas de examen nuevas"
        })
        nm.createNotificationChannel(NotificationChannel(CH_REC, "Recordatorios de examen", NotificationManager.IMPORTANCE_HIGH).apply {
            description = "Una semana antes, el día anterior y el mismo día de cada examen"
        })
        nm.createNotificationChannel(NotificationChannel(CH_PLAN, "Plan de estudios", NotificationManager.IMPORTANCE_HIGH).apply {
            description = "Cuando cambia algo en el plan de tu propuesta"
        })
        nm.createNotificationChannel(NotificationChannel(CH_MSG, "Mensajes y encuestas", NotificationManager.IMPORTANCE_DEFAULT).apply {
            description = "Mensajes de la institución y encuestas pendientes"
        })
        nm.createNotificationChannel(NotificationChannel(CH_VIG, "Vigilancia permanente", NotificationManager.IMPORTANCE_MIN).apply {
            description = "Notificación fija que mantiene la app despierta (se puede ocultar)"
            setShowBadge(false)
        })
        nm.createNotificationChannel(NotificationChannel(CH_CAMBIOS, "Otros cambios en Guaraní", NotificationManager.IMPORTANCE_DEFAULT).apply {
            description = "Inasistencias, datos y cualquier otro cambio"
        })
        nm.createNotificationChannel(NotificationChannel(CH_ESTADO, "Estado de la sesión", NotificationManager.IMPORTANCE_LOW).apply {
            description = "Avisos cuando la sesión de Guaraní vence"
        })
    }

    private var seq = 0

    fun notify(c: Context, channel: String, title: String, text: String, lines: List<String> = emptyList(), tab: String = "dash",
               pid: String? = null, acciones: List<Accion> = emptyList(), subtexto: String = "") {
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(c, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return
        val id = ((System.currentTimeMillis() % 100_000_000).toInt() * 10 + (seq++ % 10)) xor (title.hashCode() and 0xFFFF)
        fun abrir(req: Int, extra: (Intent) -> Unit): PendingIntent {
            val i = Intent(c, MainActivity::class.java).putExtra("tab", tab).putExtra("pid", pid)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            extra(i)
            return PendingIntent.getActivity(c, req, i, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        }
        val big = if (lines.isEmpty()) text else lines.take(8).joinToString("\n")
        val b = NotificationCompat.Builder(c, channel)
            .setSmallIcon(R.drawable.ic_notif)
            .setColor(0xFF1F5C4A.toInt())
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(big))
            .setAutoCancel(true)
            .setContentIntent(abrir(id) {})
            .setGroup(GROUP)
            .setCategory(if (channel == CH_REC) NotificationCompat.CATEGORY_REMINDER else NotificationCompat.CATEGORY_STATUS)
            .setPriority(if (channel in setOf(CH_NOTAS, CH_INSC, CH_PLAN, CH_REC)) NotificationCompat.PRIORITY_HIGH else NotificationCompat.PRIORITY_DEFAULT)
        if (subtexto.isNotBlank()) b.setSubText(subtexto.take(48))
        acciones.take(3).forEachIndexed { n, a ->
            b.addAction(0, a.etiqueta, abrir(id + 1 + n) { it.putExtra("hacer", a.hacer).putExtra("datos", a.datos).putExtra("nid", id) })
        }
        runCatching { NotificationManagerCompat.from(c).notify(id, b.build()) }
    }
}
