package ar.pariz.guarani

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.provider.CalendarContract
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZoneOffset

/** Compartir calificaciones como texto y pasar fechas al calendario del teléfono (Google Calendar u otro). */
object Compartir {

    private fun fecha(iso: String): String = runCatching { LocalDate.parse(iso) }.getOrNull()
        ?.let { "%02d/%02d/%d".format(it.dayOfMonth, it.monthValue, it.year) } ?: ""

    private fun num(d: Double): String = (if (d == Math.floor(d)) d.toInt().toString() else "%.2f".format(java.util.Locale.US, d)).replace('.', ',')

    /**
     * Resumen de calificaciones para mandar por WhatsApp, mail, etc.
     * [soloCat] limita a una categoría (por ejemplo «aprobada»); vacío = todas.
     */
    fun texto(institucion: String, carrera: String, resumen: JSONObject, materias: JSONArray, soloCat: String = ""): String {
        val grupos = listOf(
            "Aprobadas" to setOf("aprobada", "promocionada"),
            "Regulares (falta el final)" to setOf("regular"),
            "En curso" to setOf("en_curso"),
            "Desaprobadas o ausentes" to setOf("desaprobada", "ausente", "libre"),
            "Pendientes del plan" to setOf("pendiente")
        )
        val ms = (0 until materias.length()).map { materias.getJSONObject(it) }
        fun cat(o: JSONObject) = o.optString("cat").ifBlank { Lector.catDeEtiqueta(o.optString("estado")) }
        val sb = StringBuilder()
        sb.append("Mis calificaciones").append(if (carrera.isNotBlank()) " — $carrera" else "").append('\n')
        if (institucion.isNotBlank()) sb.append(institucion).append('\n')
        val aprob = ms.count { cat(it) in grupos[0].second }
        val linea = mutableListOf<String>()
        if (resumen.has("promedio") && !resumen.isNull("promedio")) linea += "Promedio: ${num(resumen.optDouble("promedio"))}"
        linea += if (resumen.optString("avance_origen") == "plan") "Aprobadas: $aprob de ${ms.size}" else "Aprobadas: $aprob"
        sb.append(linea.joinToString(" · ")).append('\n')
        for ((titulo, cats) in grupos) {
            if (soloCat.isNotBlank() && soloCat !in cats) continue
            val g = ms.filter { cat(it) in cats }
            if (g.isEmpty()) continue
            // Las pendientes solo se listan si se pidieron expresamente: suelen ser muchas.
            if (cats.contains("pendiente") && soloCat.isBlank()) { sb.append("\n$titulo: ${g.size}\n"); continue }
            sb.append("\n$titulo (${g.size})\n")
            for (o in g) {
                val nota = o.opt("nota")?.toString()?.takeIf { it != "null" && it.isNotBlank() }
                sb.append("• ").append(o.optString("materia"))
                if (nota != null) sb.append(" — ").append(nota)
                else if (cat(o) !in setOf("en_curso", "pendiente", "regular")) sb.append(" — ").append(o.optString("estado"))
                if (cat(o) == "promocionada") sb.append(" (promoción)")
                if (o.optBoolean("provisoria")) sb.append(" (provisoria)")
                fecha(o.optString("fecha")).takeIf { it.isNotEmpty() && nota != null }?.let { sb.append(" · ").append(it) }
                sb.append('\n')
            }
        }
        sb.append("\nDatos leídos de SIU Guaraní el ${Store.shortNow()} con la app Guaraní al día.")
        return sb.toString().trim()
    }

    fun enviarTexto(c: Context, texto: String, titulo: String = "Compartir calificaciones") {
        val i = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, texto)
        c.startActivity(Intent.createChooser(i, titulo).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    /**
     * Abre el calendario del teléfono con el evento ya cargado, para que lo confirmes y elijas el aviso.
     * No necesita permisos: la app nunca lee ni escribe tu calendario por su cuenta.
     * Devuelve false si el teléfono no tiene una app de calendario.
     */
    fun agendar(c: Context, titulo: String, fechaIso: String, hora: String?, detalle: String): Boolean {
        val dia = runCatching { LocalDate.parse(fechaIso) }.getOrNull() ?: LocalDate.now()
        val hm = hora?.takeIf { it.isNotBlank() }?.let { runCatching { LocalTime.parse(if (it.length == 4) "0$it" else it) }.getOrNull() }
        val i = Intent(Intent.ACTION_INSERT).setData(CalendarContract.Events.CONTENT_URI)
            .putExtra(CalendarContract.Events.TITLE, titulo)
            .putExtra(CalendarContract.Events.DESCRIPTION, (detalle.ifBlank { "" } + "\n\nAgendado desde Guaraní al día.").trim())
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        if (hm != null) {
            val ini = dia.atTime(hm).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
            i.putExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, ini).putExtra(CalendarContract.EXTRA_EVENT_END_TIME, ini + 2 * 3600_000L)
        } else {
            val ini = dia.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
            i.putExtra(CalendarContract.EXTRA_EVENT_ALL_DAY, true)
                .putExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, ini).putExtra(CalendarContract.EXTRA_EVENT_END_TIME, ini + 24 * 3600_000L)
        }
        return try { c.startActivity(i); true } catch (e: ActivityNotFoundException) { false }
    }
}
