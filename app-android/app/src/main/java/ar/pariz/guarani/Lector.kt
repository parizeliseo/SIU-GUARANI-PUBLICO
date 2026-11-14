package ar.pariz.guarani

import java.text.Normalizer
import java.time.LocalDate

/**
 * Interpreta lo que muestra SIU Guaraní 3 (autogestión de alumnos). No toca Android: recibe las
 * páginas ya leídas y devuelve materias, notas, fechas de examen y la carrera.
 *
 * Los formatos salen de la documentación oficial del SIU (Reportes de Autogestión, v3.18):
 *  - Plan de estudio: tabla «Actividad | Tipo | Año | Período | Nota | Origen | …», con la nota como
 *    «8,0 (Promocionado)» y el origen «Promoción», «Examen», «En Curso»…
 *  - Historia académica: por cada actividad, un título «Materia MAM 1 (MAM-1)» y debajo renglones
 *    «Promoción - 8,0 (OCHO) Promocionado 05/07/2019».
 *  - Actuación provisoria: tablas «Cursadas», «Promociones» y «Exámenes» con Actividad, Nota y Resultado.
 *  - Mis inscripciones: tabla de exámenes con «Actividad» y «Fecha de examen».
 */
object Lector {

    /** Un renglón de nota tal como lo informa Guaraní. */
    data class Reg(
        val nombre: String, val codigo: String,
        /** cursada | promocion | examen | equivalencia | plan | "" */
        val tipo: String,
        /** aprobada | promocionada | regular | en_curso | desaprobada | ausente | libre | pendiente */
        val cat: String,
        val nota: String?, val fecha: String?,
        /** plan | historia | provisoria | tabla | texto */
        val fuente: String,
        val tipoAct: String = "", val anio: String = "", val periodo: String = ""
    )

    /** Una materia ya consolidada (su mejor estado entre todos los renglones). */
    data class Materia(
        val codigo: String, val nombre: String, val cat: String, val nota: String?, val fecha: String?,
        val detalle: String, val tipoAct: String, val anio: String, val periodo: String, val provisoria: Boolean
    )

    /** Un examen o evaluación con fecha. */
    data class Evento(val fecha: String, val hora: String?, val titulo: String, val tipo: String,
                      val materia: String, val detalle: String) {
        val id: String get() = norm("$tipo|$materia|$fecha|${hora ?: ""}")
    }

    data class Carrera(val nombre: String, val plan: String, val todas: List<String>)

    // ------------------------------------------------------------------ utilidades

    fun norm(s: String): String = Normalizer.normalize(s, Normalizer.Form.NFD)
        .replace(Regex("\\p{M}+"), "").lowercase().replace(Regex("\\s+"), " ").trim()

    private val WORD_NUM = mapOf("cero" to 0, "uno" to 1, "dos" to 2, "tres" to 3, "cuatro" to 4, "cinco" to 5, "seis" to 6,
        "siete" to 7, "ocho" to 8, "nueve" to 9, "diez" to 10)
    private val FECHA = Regex("(?<!\\d)(\\d{1,2})/(\\d{1,2})/(\\d{4})(?!\\d)")
    private val HORA = Regex("(?<![\\d:])([01]?\\d|2[0-3]):([0-5]\\d)(?!\\d)")
    private val COD_FIN = Regex("^(.{2,140}?)\\s*\\(([^()]{1,20})\\)\\s*$")
    private val COD_INI = Regex("^\\(([^()]{1,20})\\)\\s*(.{2,140})$")
    private val RESULTADO = Regex("aprob|promoc|regular|reprob|ausente|libre|insuf|curso")

    private fun esCodigo(c: String): Boolean {
        val t = c.trim()
        if (!Regex("^[A-Za-z0-9][A-Za-z0-9.\\-_/ ]{0,19}$").matches(t)) return false
        val n = norm(t)
        if (n in WORD_NUM || RESULTADO.containsMatchIn(n)) return false
        return t.any { it.isDigit() } || (t == t.uppercase() && !t.contains(' '))
    }

    /** «Materia MAM 1 (MAM-1)» o «(153) Sistemas de Información I» → (nombre, código). Sin código: (texto, ""). */
    fun nombreCodigo(raw: String): Pair<String, String> {
        val s = raw.replace(' ', ' ').trim()
        COD_FIN.find(s)?.let { m -> if (esCodigo(m.groupValues[2])) return m.groupValues[1].trim() to m.groupValues[2].trim() }
        COD_INI.find(s)?.let { m -> if (esCodigo(m.groupValues[1])) return m.groupValues[2].trim() to m.groupValues[1].trim() }
        return s to ""
    }

    /** «05/07/2019» → «2019-07-05». */
    fun fechaIso(s: String?): String? {
        val m = FECHA.find(s ?: return null) ?: return null
        val d = m.groupValues[1].toInt(); val mo = m.groupValues[2].toInt(); val y = m.groupValues[3].toInt()
        return runCatching { LocalDate.of(y, mo, d).toString() }.getOrNull()
    }

    fun hora(s: String?): String? {
        val m = HORA.find(s ?: return null) ?: return null
        return "%02d:%s".format(m.groupValues[1].toInt(), m.groupValues[2])
    }

    /** «8,0 (OCHO)» → «8»; «7,50» → «7,5»; «9 (nueve)» → «9»; «(Siete)» → «7»; «A» → «A». */
    fun nota(raw: String?): String? {
        val s = (raw ?: return null).replace(FECHA, " ").replace(HORA, " ").trim()
        if (s.isEmpty()) return null
        Regex("(?<![\\d.,/])(\\d{1,3}(?:[.,]\\d{1,2})?)(?![\\d/])").find(s)?.let { m ->
            val v = m.groupValues[1].replace(',', '.').toDoubleOrNull()
            if (v != null && v <= 100) return if (v == Math.floor(v)) v.toInt().toString() else v.toString().replace('.', ',')
        }
        Regex("\\(([\\p{L}]+)\\)").findAll(s).forEach { m -> WORD_NUM[norm(m.groupValues[1])]?.let { return it.toString() } }
        Regex("^([A-Ea-e][+-]?)(?:\\s|$)").find(s)?.let { return it.groupValues[1].uppercase() }
        return null
    }

    /** Categoría de un renglón según el tipo de instancia y el resultado que informa Guaraní. */
    fun cat(tipo: String, resultado: String): String? {
        val t = norm(tipo); val r = norm(resultado)
        val cursada = Regex("regular|cursad").containsMatchIn(t)
        return when {
            Regex("desaprob|reprob|insuf|no aprob").containsMatchIn(r) -> "desaprobada"
            r.contains("ausente") -> "ausente"
            Regex("\\blibre\\b").containsMatchIn(r) -> "libre"
            r.contains("promoc") -> "promocionada"
            r.contains("aprob") -> if (cursada) "regular" else if (t.contains("promoc")) "promocionada" else "aprobada"
            Regex("\\bregular").containsMatchIn(r) -> "regular"
            r.contains("en curso") || t.contains("en curso") -> "en_curso"
            Regex("equivalen|resoluc|reconoc").containsMatchIn(t) -> "aprobada"
            else -> null
        }
    }

    private val CON_NOTA = setOf("aprobada", "promocionada", "regular", "desaprobada")

    fun etiqueta(cat: String): String = when (cat) {
        "aprobada" -> "Aprobada"; "promocionada" -> "Promocionada"; "regular" -> "Regular, falta final"
        "en_curso" -> "En curso"; "desaprobada" -> "Desaprobada"; "ausente" -> "Ausente"; "libre" -> "Libre"
        else -> "Pendiente"
    }

    /** Categoría a partir de una etiqueta guardada (para datos de versiones anteriores). */
    fun catDeEtiqueta(estado: String): String {
        val e = norm(estado)
        return when {
            e.contains("desaprob") -> "desaprobada"; e.contains("promoc") -> "promocionada"
            e.contains("regular") -> "regular"; e.contains("aprob") -> "aprobada"
            e.contains("ausente") -> "ausente"; e.contains("libre") -> "libre"
            e.contains("pendiente") -> "pendiente"; else -> "en_curso"
        }
    }

    private fun col(head: List<String>, vararg claves: String): Int {
        val h = head.map { norm(it) }
        for (k in claves) { val i = h.indexOfFirst { it == k }; if (i >= 0) return i }
        for (k in claves) { val i = h.indexOfFirst { it.contains(k) }; if (i >= 0) return i }
        return -1
    }
    private fun cel(r: List<String>, i: Int): String = if (i in r.indices) r[i].trim() else ""
    private fun vacio(s: String) = s.isBlank() || norm(s) in setOf("-", "--", "sin definir", "no tiene", "s/d")

    private val ACT = arrayOf("actividad", "materia", "asignatura", "espacio curricular")

    // ------------------------------------------------------------------ plan de estudio

    /** Todas las actividades del plan, con su nota y origen cuando ya tienen. */
    fun dePlan(p: Scraper.Page): List<Reg> {
        val out = mutableListOf<Reg>()
        for (t in p.tables) {
            val a = col(t.head, *ACT); val o = col(t.head, "origen"); val n = col(t.head, "nota")
            if (a < 0 || (o < 0 && n < 0)) continue
            if (o < 0 && !norm(p.url + " " + p.title).contains("plan")) continue
            val ti = col(t.head, "tipo"); val an = col(t.head, "ano"); val pe = col(t.head, "periodo")
            for (r in t.rows) {
                if (r.size < 2) continue
                val (nombre, codigo) = nombreCodigo(cel(r, a))
                if (nombre.length < 2 || norm(nombre) in ACT) continue
                val notaCel = cel(r, n); val origen = cel(r, o)
                val c = cat(origen, "$notaCel $origen") ?: when {
                    vacio(notaCel) && vacio(origen) -> "pendiente"
                    // Nota cargada sin la palabra del resultado: el plan solo muestra la nota que cuenta.
                    nota(notaCel) != null -> if (Regex("regular|cursad").containsMatchIn(norm(origen))) "regular" else "aprobada"
                    else -> "en_curso"
                }
                out += Reg(nombre, codigo, "plan", c, if (c in CON_NOTA) nota(notaCel) else null, fechaIso(notaCel), "plan",
                    cel(r, ti), cel(r, an), cel(r, pe))
            }
        }
        return out
    }

    // ------------------------------------------------------------------ historia académica

    private val TIPOS = Regex("promoci|examen|regular|cursad|equivalen|resoluci|reconoc|en curso|aprobaci|credito|final|coloquio|libre")
    private val COLA = Regex("\\s*[-–]\\s*(cerrar|ver|detalle|detalles|mostrar|ocultar)\\s*$", RegexOption.IGNORE_CASE)

    private fun tipoDe(t: String): String {
        val n = norm(t)
        return when {
            n.contains("promoc") -> "promocion"
            Regex("regular|cursad").containsMatchIn(n) -> "cursada"
            Regex("examen|final|coloquio|libre").containsMatchIn(n) -> "examen"
            Regex("equivalen|resoluc|reconoc|credito").containsMatchIn(n) -> "equivalencia"
            else -> ""
        }
    }

    /** Renglones «Promoción - 8,0 (OCHO) Promocionado 05/07/2019» bajo cada título de actividad. */
    fun deHistoria(p: Scraper.Page): List<Reg> {
        val out = mutableListOf<Reg>()
        var cur: Pair<String, String>? = null
        for (raw in p.text.lines()) {
            val l = raw.replace(' ', ' ').replace(Regex("[ \\t]+"), " ").trim()
            if (l.length < 3) continue
            val i = l.indexOf(" - ").takeIf { it > 0 } ?: l.indexOf(" – ")
            if (i > 0 && cur != null) {
                val tipo = l.substring(0, i)
                if (tipo.length <= 40 && TIPOS.containsMatchIn(norm(tipo))) {
                    val resto = l.substring(i + 3).replace(COLA, "")
                    val c = cat(tipo, resto)
                    if (c != null) {
                        out += Reg(cur.first, cur.second, tipoDe(tipo), c, if (c in CON_NOTA) nota(resto) else null, fechaIso(resto), "historia")
                        continue
                    }
                }
            }
            val nc = nombreCodigo(l)
            if (nc.second.isNotEmpty() && !FECHA.containsMatchIn(nc.first)) cur = nc
        }
        return out
    }

    // ------------------------------------------------------------------ tablas de notas (actuación provisoria)

    /** Tablas con Actividad + Nota/Resultado, como las de «Actuación provisoria». */
    fun deTablas(p: Scraper.Page): List<Reg> {
        val out = mutableListOf<Reg>()
        val provisoria = norm(p.url + " " + p.title).contains("provisoria")
        for (t in p.tables) {
            val a = col(t.head, *ACT); val n = col(t.head, "nota", "calificacion"); val re = col(t.head, "resultado", "condicion")
            if (a < 0 || (n < 0 && re < 0) || col(t.head, "origen") >= 0) continue
            val f = t.head.indexOfFirst { norm(it).contains("fecha") && !norm(it).contains("inscrip") }
            val ti = col(t.head, "tipo", "instancia")
            for (r in t.rows) {
                if (r.size < 2) continue
                val (nombre, codigo) = nombreCodigo(cel(r, a))
                if (nombre.length < 2) continue
                val tipoTxt = (t.sec + " " + cel(r, ti)).trim()
                val c = cat(tipoTxt, cel(r, re) + " " + cel(r, n)) ?: continue
                out += Reg(nombre, codigo, tipoDe(tipoTxt), c, if (c in CON_NOTA) nota(cel(r, n)) else null, fechaIso(cel(r, f)),
                    if (provisoria) "provisoria" else "tabla")
            }
        }
        return out
    }

    // ------------------------------------------------------------------ consolidación

    private fun rango(c: String) = when (c) {
        "aprobada", "promocionada" -> 6; "regular" -> 4; "en_curso" -> 3
        "desaprobada", "ausente", "libre" -> 2; else -> 1
    }

    /** Clave de cada materia: el código si alguna fuente lo trae para ese nombre; si no, el nombre. */
    fun claves(regs: List<Reg>): (Reg) -> String {
        val codigoDe = regs.filter { it.codigo.isNotBlank() }.associate { norm(it.nombre) to norm(it.codigo) }
        return { r -> (if (r.codigo.isNotBlank()) norm(r.codigo) else codigoDe[norm(r.nombre)])?.let { "c:$it" } ?: "n:${norm(r.nombre)}" }
    }

    private fun frase(r: Reg): String {
        val n = r.nota?.let { " con $it" } ?: ""
        return when (r.tipo) {
            "cursada" -> when (r.cat) {
                "regular" -> "cursada aprobada$n (queda el final)"; "desaprobada" -> "cursada desaprobada$n"
                "ausente" -> "ausente en la cursada"; "libre" -> "libre en la cursada"; "en_curso" -> "en curso"
                else -> etiqueta(r.cat).lowercase() + n
            }
            "examen" -> when (r.cat) {
                "aprobada" -> "examen aprobado$n"; "desaprobada" -> "examen desaprobado$n"; "ausente" -> "ausente en el examen"
                else -> etiqueta(r.cat).lowercase() + n
            }
            "equivalencia" -> "aprobada por equivalencia$n"
            else -> etiqueta(r.cat).lowercase() + n
        }
    }

    /** Texto del aviso de una nota: «Física I: examen aprobado con 8.» */
    fun aviso(r: Reg): String = "${r.nombre}: ${if (r.fuente == "provisoria") "nota provisoria, " else ""}${frase(r)}."

    private fun fechaCorta(iso: String?): String =
        iso?.let { runCatching { LocalDate.parse(it) }.getOrNull() }?.let { "%02d/%02d/%d".format(it.dayOfMonth, it.monthValue, it.year) } ?: ""

    fun consolidar(regs: List<Reg>): List<Materia> {
        val clave = claves(regs)
        return regs.groupBy(clave).map { (_, g) ->
            val mejor = g.sortedWith(compareByDescending<Reg> { rango(it.cat) }
                .thenByDescending { if (it.fuente == "plan") 0 else 1 }.thenByDescending { it.fecha ?: "" }).first()
            val plan = g.firstOrNull { it.fuente == "plan" }
            val conCodigo = g.firstOrNull { it.codigo.isNotBlank() }
            val hechos = g.filter { it.fuente != "plan" && it.cat != "pendiente" }.distinctBy { "${it.tipo}|${it.cat}|${it.nota}|${it.fecha}" }
                .sortedByDescending { it.fecha ?: "" }
            val detalle = hechos.joinToString(" · ") { r ->
                (frase(r).replaceFirstChar { it.uppercase() } + fechaCorta(r.fecha).let { if (it.isEmpty()) "" else " ($it)" } +
                    if (r.fuente == "provisoria") " — provisoria, acta sin cerrar" else "")
            }.ifEmpty { if (mejor.cat == "pendiente") "Todavía no la cursaste" else "Según el plan de estudio" }
            Materia(conCodigo?.codigo ?: "", (plan ?: conCodigo ?: mejor).nombre, mejor.cat,
                mejor.nota ?: g.firstOrNull { it.cat == mejor.cat && it.nota != null }?.nota,
                mejor.fecha ?: g.filter { it.cat == mejor.cat }.mapNotNull { it.fecha }.maxOrNull(),
                detalle, plan?.tipoAct ?: "", plan?.anio ?: "", plan?.periodo ?: "",
                hechos.isNotEmpty() && hechos.filter { it.cat == mejor.cat }.all { it.fuente == "provisoria" })
        }
    }

    /** Promedio de las notas numéricas de las materias aprobadas o promocionadas (sin aplazos). */
    fun promedio(ms: List<Materia>): Double? {
        val ns = ms.filter { it.cat == "aprobada" || it.cat == "promocionada" }
            .mapNotNull { it.nota?.replace(',', '.')?.toDoubleOrNull() }.filter { it in 1.0..10.0 }
        return if (ns.isEmpty()) null else Math.round(ns.average() * 100) / 100.0
    }

    // ------------------------------------------------------------------ exámenes y evaluaciones

    private val EXAMEN_LATERAL = Regex("^\\W{0,3}\\(([^()]{1,20})\\)\\s*(.{2,120}?)\\s+[-–]\\s+(\\d{1,2}/\\d{1,2}/\\d{4})(?:\\s+(\\d{1,2}:\\d{2}))?\\s*$")

    fun eventos(pages: List<Scraper.Page>): List<Evento> {
        val out = linkedMapOf<String, Evento>()
        fun add(e: Evento) { out.putIfAbsent(e.id, e) }
        for (p in pages) {
            val pag = norm(p.url + " " + p.title)
            for (t in p.tables) {
                val h = t.head.map { norm(it) }
                val a = col(t.head, *ACT); if (a < 0) continue
                val fx = h.indexOfFirst { it.contains("fecha") && it.contains("exam") }
                val esExamen = fx >= 0 || (h.any { Regex("mesa|llamado").containsMatchIn(it) } && h.any { it.contains("fecha") && !it.contains("inscrip") })
                val esEval = !esExamen && (h.any { it.contains("evaluac") } || Regex("evaluac|cronograma").containsMatchIn(pag))
                if (!esExamen && !esEval) continue
                val f = if (fx >= 0) fx else h.indexOfFirst { it.contains("fecha") && !it.contains("inscrip") }
                if (f < 0) continue
                val est = col(t.head, "estado"); val ho = col(t.head, "hora", "horario")
                for (r in t.rows) {
                    val iso = fechaIso(cel(r, f)) ?: continue
                    if (Regex("baja|rechaz|anulad").containsMatchIn(norm(cel(r, est)))) continue
                    val (nombre, _) = nombreCodigo(cel(r, a)); if (nombre.length < 2) continue
                    val det = t.head.indices.filter { it != a && it != f && it != est }.mapNotNull { i ->
                        val k = norm(t.head[i])
                        if (Regex("llamado|mesa|turno|ubicacion|lugar|aula|evaluac|descripcion|instancia|tipo").containsMatchIn(k) && !vacio(cel(r, i)))
                            "${t.head[i].trim()}: ${cel(r, i)}" else null
                    }.joinToString(" · ")
                    val tipo = if (esExamen) "examen" else "evaluacion"
                    add(Evento(iso, hora(cel(r, f)) ?: hora(cel(r, ho)), (if (esExamen) "Examen: " else "Evaluación: ") + nombre, tipo, nombre, det))
                }
            }
            // Panel lateral de Guaraní: «(180) Ética Profesional - 27/05/2022»
            for (l in p.full.lines()) {
                val m = EXAMEN_LATERAL.find(l.replace(' ', ' ').trim()) ?: continue
                if (!esCodigo(m.groupValues[1])) continue
                val iso = fechaIso(m.groupValues[3]) ?: continue
                val nombre = m.groupValues[2].trim()
                add(Evento(iso, m.groupValues[4].ifEmpty { null }?.let { hora(it) }, "Examen: $nombre", "examen", nombre, ""))
            }
        }
        // Un mismo examen puede venir de la tabla (con detalle) y del panel lateral (sin detalle): gana el que tiene más datos.
        return out.values.groupBy { norm("${it.tipo}|${it.materia}|${it.fecha}") }
            .map { (_, g) -> g.maxByOrNull { (if (it.hora != null) 1000 else 0) + it.detalle.length }!! }
            .sortedWith(compareBy({ it.fecha }, { it.hora ?: "" }))
    }

    // ------------------------------------------------------------------ carrera

    private val PROP = Regex("Propuesta:[ \\t]*\\n?[ \\t]*([^\\n\\t]{3,160})", RegexOption.IGNORE_CASE)
    private val PLAN = Regex("Plan:[ \\t]*\\n?[ \\t]*([^\\n\\t]{2,160})", RegexOption.IGNORE_CASE)
    private fun limpio(s: String): String = s.substringBefore("Plan:").substringBefore("Versión:").substringBefore("Version:")
        .substringBefore("Perfil:").replace(Regex("^\\([^()]{1,20}\\)\\s*"), "").replace(Regex("[▾▼▸»]+$"), "").trim()

    /** Carrera (propuesta) elegida en Guaraní: del plan de estudio o del selector de arriba. */
    fun carrera(pages: List<Scraper.Page>): Carrera? {
        var nombre = ""; var plan = ""; val todas = linkedSetOf<String>()
        val orden = pages.sortedByDescending { if (norm(it.url + " " + it.title).contains("plan")) 1 else 0 }
        for (p in orden) {
            p.props.map { limpio(it) }.filter { it.length > 2 }.forEach { todas.add(it) }
            if (nombre.isEmpty() && p.propSel.isNotBlank()) limpio(p.propSel).takeIf { it.length > 2 && !norm(it).startsWith("propuesta") }?.let { nombre = it }
            if (nombre.isEmpty()) PROP.find(p.full)?.let { m -> limpio(m.groupValues[1]).takeIf { it.length > 2 }?.let { nombre = it } }
            if (plan.isEmpty() && norm(p.url + " " + p.title).contains("plan")) PLAN.find(p.full)?.let { plan = limpio(it.groupValues[1]) }
        }
        // En la página del plan, «Propuesta:» es más completa que el botón de arriba.
        orden.firstOrNull()?.takeIf { norm(it.url + " " + it.title).contains("plan") }?.let { p ->
            PROP.find(p.text.ifBlank { p.full })?.let { m -> limpio(m.groupValues[1]).takeIf { it.length > 2 }?.let { nombre = it } }
        }
        return if (nombre.isEmpty() && todas.isEmpty()) null else Carrera(nombre, plan, todas.toList())
    }
}
