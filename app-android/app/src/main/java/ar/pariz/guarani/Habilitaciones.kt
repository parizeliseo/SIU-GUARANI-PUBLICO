package ar.pariz.guarani

import org.json.JSONArray
import org.json.JSONObject

/**
 * Inscripciones habilitadas por materia.
 *
 * En «Inscripción a exámenes» e «Inscripción a materias», Guaraní lista todas las actividades y, al
 * elegir una, dice si hay mesas o comisiones habilitadas («No hay mesas habilitadas para esta
 * actividad»). La app lee ese detalle de cada materia en cada revisión y avisa cuando una pasa a tener
 * inscripción abierta, o cuando aparece una mesa nueva en una que ya estaba abierta.
 */
object Habilitaciones {

    data class Estado(val tipo: String, val materia: String, val estado: String, val detalle: List<String>, val fechas: List<String>)
    data class Aviso(val tipo: String, val materia: String, val detalle: List<String>, val nuevaMesa: Boolean)

    const val ABIERTA = "abierta"
    const val CERRADA = "cerrada"
    private const val DUDA = "?"

    private fun norm(s: String) = Lector.norm(s)

    /** "examen" o "cursada" si la página es la de inscribirse; null para el resto (incluida «Mis inscripciones»). */
    fun tipoDePagina(url: String, title: String): String? {
        val u = norm(url.substringAfter("://").substringAfter('/').substringBefore('?') + " " + title)
        return when {
            Regex("mis[_ ]inscrip|cronograma|evaluac|agenda|fecha_examen|comprobante|historia|plan").containsMatchIn(u) -> null
            Regex("examen|mesa").containsMatchIn(u) -> "examen"
            Regex("cursada|inscripcion[_ ]a?[_ ]?materia|comision").containsMatchIn(u) -> "cursada"
            else -> null
        }
    }

    /** Resultado del JavaScript EXPLORAR: [{t: materia, l: [líneas] | null}]. */
    fun leer(a: JSONArray?): List<Pair<String, List<String>?>> {
        if (a == null) return emptyList()
        return (0 until a.length()).mapNotNull { a.optJSONObject(it) }.map { o ->
            val l = o.optJSONArray("l")
            o.optString("t").trim() to l?.let { arr -> (0 until arr.length()).map { arr.optString(it) } }
        }.filter { it.first.isNotEmpty() }
    }

    private val FECHA = Regex("\\b\\d{1,2}/\\d{1,2}/\\d{2,4}\\b")
    private val SEÑALES = Regex("\\b(llamado|turno|inscribir|inscribite|inscribirme|inscribirse|comision|docente|horario|aula|cupo|ubicacion|sede|mesa \\d|fecha)")
    private val CERRADO = Regex("(no hay|no existen|no se encontr|no posee|no tiene|no se registran|no esta habilitad|no estan habilitad|sin (mesas|comisiones|inscripciones|turnos|periodos)|" +
        "fuera del? periodo|periodo.{0,25}(cerrado|finalizado|no (esta )?(vigente|habilitado))|no hay periodos|ya (estas|se encuentra) inscript)")
    private val BOTON = Regex("(\\s*·\\s*)?\\b(inscribirse|inscribirme|inscribir|ver detalle|seleccionar)\\s*$", RegexOption.IGNORE_CASE)
    private val RELLENO = Regex("^(ver|todas|solo inscriptas|buscar actividad.*|volver|cerrar|seleccione.*|elegi.*|mesas?|comisiones|inscripcion(es)?|inscripcion a (examenes|materias))$")

    /** Lee el detalle de una materia: ¿hay inscripción abierta? */
    fun estado(tipo: String, materia: String, lineas: List<String>?): Estado {
        if (lineas == null) return Estado(tipo, materia, DUDA, emptyList(), emptyList())
        val m = norm(materia)
        val utiles = lineas.map { it.trim() }.filter { it.length > 1 && norm(it) != m && !RELLENO.matches(norm(it)) }
        val cerradas = utiles.filter { CERRADO.containsMatchIn(norm(it)) }
        val resto = utiles - cerradas.toSet()
        val fechas = resto.flatMap { l -> FECHA.findAll(l).map { it.value } }.distinct()
        val abierta = fechas.isNotEmpty() || resto.any { SEÑALES.containsMatchIn(norm(it)) }
        val e = when { abierta -> ABIERTA; cerradas.isNotEmpty() -> CERRADA; else -> DUDA }
        val det = (resto.filter { FECHA.containsMatchIn(it) } + resto.filter { !FECHA.containsMatchIn(it) })
            .map { it.replace(BOTON, "").trim().take(120) }.filter { it.length > 1 }.distinct().take(5)
        return Estado(tipo, materia, e, if (e == ABIERTA) det else cerradas.take(1), fechas)
    }

    private fun clave(tipo: String, materia: String) = tipo + "|" + norm(materia)

    /**
     * Compara lo leído con lo guardado. Devuelve los avisos y el estado nuevo para guardar.
     * Una lectura dudosa (no se pudo leer o no se entendió) conserva lo anterior: así no hay avisos falsos.
     */
    fun comparar(previo: JSONArray, leidas: List<Estado>, url: String = ""): Pair<List<Aviso>, JSONArray> {
        val ant = (0 until previo.length()).mapNotNull { previo.optJSONObject(it) }
            .associateBy { clave(it.optString("tipo"), it.optString("materia")) }.toMutableMap()
        val avisos = mutableListOf<Aviso>()
        for (e in leidas) {
            if (e.estado == DUDA) continue
            val k = clave(e.tipo, e.materia)
            val p = ant[k]
            if (e.estado == ABIERTA) {
                val antes = p?.optString("estado")
                val fechasAntes = p?.optJSONArray("fechas")?.let { a -> (0 until a.length()).map { a.optString(it) }.toSet() } ?: emptySet()
                when {
                    antes != ABIERTA -> avisos += Aviso(e.tipo, e.materia, e.detalle, false)
                    e.fechas.any { it !in fechasAntes } -> avisos += Aviso(e.tipo, e.materia, e.detalle, true)
                }
            }
            ant[k] = JSONObject().put("tipo", e.tipo).put("materia", e.materia).put("estado", e.estado)
                .put("detalle", JSONArray(e.detalle)).put("fechas", JSONArray(e.fechas)).put("url", url.ifEmpty { p?.optString("url").orEmpty() })
                .put("desde", if (p?.optString("estado") == e.estado) p.optString("desde") else Store.nowIso())
        }
        val out = JSONArray()
        ant.values.sortedWith(compareBy({ it.optString("estado") != ABIERTA }, { it.optString("tipo") }, { norm(it.optString("materia")) })).forEach { out.put(it) }
        return avisos to out
    }

    fun nombreTipo(tipo: String) = if (tipo == "examen") "examen" else "cursada"

    /** Título y líneas de la notificación. */
    fun mensaje(avisos: List<Aviso>): Triple<String, String, List<String>> {
        fun linea(a: Aviso) = a.materia + (a.detalle.firstOrNull()?.let { " — $it" } ?: "")
        if (avisos.size == 1) {
            val a = avisos[0]
            val t = when {
                a.nuevaMesa && a.tipo == "examen" -> "Nueva mesa de examen: ${a.materia}"
                a.nuevaMesa -> "Nueva comisión: ${a.materia}"
                a.tipo == "examen" -> "Inscripción a examen abierta: ${a.materia}"
                else -> "Inscripción a materia abierta: ${a.materia}"
            }
            val txt = a.detalle.firstOrNull() ?: if (a.tipo == "examen") "Hay mesas habilitadas. Tocá para inscribirte en Guaraní."
                else "Hay comisiones habilitadas. Tocá para inscribirte en Guaraní."
            return Triple(t, txt, listOf(a.materia) + a.detalle.take(5))
        }
        val ex = avisos.count { it.tipo == "examen" }; val cu = avisos.size - ex
        val t = when {
            cu == 0 -> "Se habilitó la inscripción a $ex exámenes"
            ex == 0 -> "Se habilitó la inscripción a $cu materias"
            else -> "Se habilitaron ${avisos.size} inscripciones"
        }
        return Triple(t, avisos.joinToString(", ") { it.materia }.take(140), avisos.map { (if (it.tipo == "examen") "Examen: " else "Cursada: ") + linea(it) })
    }
}
