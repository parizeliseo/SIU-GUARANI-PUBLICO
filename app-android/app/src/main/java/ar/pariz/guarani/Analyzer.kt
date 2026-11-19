package ar.pariz.guarani

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate
import java.time.ZonedDateTime

/** Compara cada revisión con la anterior, actualiza el tablero y dispara las notificaciones. */
class Analyzer(private val c: Context, private val prof: Profile) {
    private val pid = prof.id

    data class Change(val page: String, val kind: String, val added: List<String>, val removed: List<String>)

    companion object {
        private val NOISE = listOf(
            Regex("^\\d{1,2}:\\d{2}(:\\d{2})?\\s*(hs)?$", RegexOption.IGNORE_CASE),
            Regex("ultim[oa]s? (acceso|conexi|ingreso)", RegexOption.IGNORE_CASE),
            Regex("hace \\d+ (segundo|minuto|hora)", RegexOption.IGNORE_CASE),
            Regex("hora del servidor", RegexOption.IGNORE_CASE),
            Regex("^(cargando|aguarde)", RegexOption.IGNORE_CASE)
        )
        val GRADE_WORDS = Regex("(aprobad|promocion|promoción|desaprobad|ausente|libre|regular|nota|acta|calificaci|final)", RegexOption.IGNORE_CASE)
        val INSC_WORDS = Regex("(inscripci|periodo|vencimiento|vence|mesa|turno de examen|llamado|cierre|apertura)")
        /** Frases con las que Guaraní dice «acá todavía no hay nada» (inscripción cerrada, sin mesas…). */
        val CLOSED = Regex("(no hay|no existen|no se encontr|no posee|no tenes|no tiene|no se registran|sin (mesas|inscripciones|turnos|periodos)|" +
            "fuera del? periodo|periodo.{0,25}(cerrado|finalizado|no (esta )?(vigente|habilitado))|no esta habilitad|no hay periodos)")

        /** De qué trata una página de Guaraní, según su dirección y su título. */
        fun kindOf(url: String, title: String): String {
            val u = norm(url.substringAfter("://").substringAfter('/').substringBefore('?') + " " + title)
            return when {
                Regex("historia|actuacion|\\bactas?\\b|calificacion|reporte_acta").containsMatchIn(u) -> "nota"
                Regex("mis[_ ]inscrip").containsMatchIn(u) -> "inscripciones"
                Regex("plan").containsMatchIn(u) -> "plan"
                Regex("examen|mesa|evaluac|cronograma").containsMatchIn(u) -> "examen"
                Regex("cursada|inscripcion|comision|materia").containsMatchIn(u) -> "cursada"
                Regex("mensaje").containsMatchIn(u) -> "mensaje"
                Regex("encuesta").containsMatchIn(u) -> "encuesta"
                Regex("inasistencia").containsMatchIn(u) -> "inasistencia"
                else -> "otro"
            }
        }

        /** Título, canal y tipo de novedad para un cambio, según el tema de la página. */
        fun headline(kind: String, page: String, added: List<String>, removed: List<String>, hasGradeMsgs: Boolean): Triple<String, String, String>? {
            val opened = added.isNotEmpty() && removed.any { CLOSED.containsMatchIn(norm(it)) }
            val closed = removed.isNotEmpty() && added.any { CLOSED.containsMatchIn(norm(it)) } && !opened
            return when (kind) {
                "nota" -> if (hasGradeMsgs) null else Triple("Movimiento en tus notas", Notifier.CH_NOTAS, "Nota")
                "plan" -> if (hasGradeMsgs) null else Triple("Cambió tu plan de estudios", Notifier.CH_PLAN, "Plan de estudios")
                "examen" -> Triple(when { opened -> "Se abrió la inscripción a exámenes"; closed -> "Cerró la inscripción a exámenes"
                    else -> "Novedades en mesas de examen" }, Notifier.CH_INSC, "Exámenes")
                "cursada" -> Triple(when { opened -> "Se abrió la inscripción a materias"; closed -> "Cerró la inscripción a materias"
                    else -> "Novedades en inscripción a materias" }, Notifier.CH_INSC, "Inscripciones")
                "inscripciones" -> Triple("Novedades en tus inscripciones", Notifier.CH_INSC, "Inscripciones")
                "mensaje" -> Triple("Mensaje nuevo en Guaraní", Notifier.CH_MSG, "Mensaje")
                "encuesta" -> Triple("Encuesta en Guaraní", Notifier.CH_MSG, "Encuesta")
                "inasistencia" -> Triple("Novedad en tus inasistencias", Notifier.CH_CAMBIOS, "Inasistencias")
                else -> when {
                    added.any { GRADE_WORDS.containsMatchIn(it) } && !hasGradeMsgs -> Triple("Movimiento de notas: $page", Notifier.CH_NOTAS, "Nota")
                    added.any { INSC_WORDS.containsMatchIn(norm(it)) } -> Triple("Inscripciones y fechas: $page", Notifier.CH_INSC, "Inscripciones")
                    else -> null
                }
            }
        }

        private val WORD_NUM = mapOf("uno" to 1, "dos" to 2, "tres" to 3, "cuatro" to 4, "cinco" to 5, "seis" to 6,
            "siete" to 7, "ocho" to 8, "nueve" to 9, "diez" to 10)

        fun norm(s: String): String = Lector.norm(s)

        fun lines(text: String): List<String> = text.lines()
            .map { it.replace(' ', ' ').replace(Regex("[ \\t]+"), " ").trim() }
            .filter { it.length >= 2 && NOISE.none { r -> r.containsMatchIn(norm(it)) } }
            .distinct()

        fun category(estado: String): String {
            val e = norm(estado)
            return when {
                e.contains("desaprob") -> "desaprobada"
                e.contains("promocion") -> "promocionada"
                e.contains("aprob") -> "aprobada"
                e.contains("ausente") -> "ausente"
                e.contains("libre") -> "libre"
                e.contains("regular") -> "regular"
                else -> "otra"
            }
        }

        fun label(cat: String): String = when (cat) {
            "desaprobada" -> "Desaprobada"; "promocionada" -> "Promocionada"; "aprobada" -> "Aprobada"
            "ausente" -> "Ausente"; "libre" -> "Libre"; "regular" -> "Regular, falta final"; else -> "En curso"
        }

        /** Línea tipo "Base de Datos (GSTI-107)": así lista Guaraní cada actividad en la historia académica. */
        private val ACTIVITY = Regex("^(.{3,120}?)\\s*\\(([A-Za-z0-9][A-Za-z0-9.\\-_/]{0,15})\\)\\s*$")

        /** Respaldo para instituciones con otro formato: materias que aparecen en la historia académica. */
        fun discoverActivities(pages: List<Pair<String, List<String>>>): List<Pair<String, String>> {
            val out = linkedMapOf<String, String>()
            for ((url, ls) in pages) {
                val isHist = norm(url).contains("historia") || ls.take(5).any { norm(it).contains("historia academica") }
                if (!isHist) continue
                for (l in ls) {
                    val m = ACTIVITY.find(l) ?: continue
                    val name = m.groupValues[1].trim().trimEnd('-', ':', '·').trim()
                    if (name.length < 3 || Regex("^\\d").containsMatchIn(name)) continue
                    if (GRADE_WORDS.matches(name)) continue
                    out.putIfAbsent(name, m.groupValues[2])
                }
            }
            return out.map { it.key to it.value }
        }

        /** Respaldo: busca estado y nota de una materia dentro del texto de Guaraní. */
        fun findGrade(all: List<String>, name: String, others: List<String>): Pair<String, String?>? {
            val n = norm(name)
            val otherN = others.map { norm(it) }.filter { it != n && !n.contains(it) }
            val idx = all.indexOfFirst { norm(it).contains(n) }
            if (idx < 0) return null
            val window = StringBuilder(all[idx])
            for (j in idx + 1 until minOf(all.size, idx + 5)) {
                val ln = norm(all[j])
                if (otherN.any { ln.contains(it) }) break
                window.append(' ').append(all[j])
            }
            val w = norm(window.toString()).replace(n, " ")
            val cat = category(w)
            if (cat == "otra") return null
            val cleaned = w.replace(Regex("\\d{1,2}/\\d{1,2}/\\d{2,4}"), " ")
                .replace(Regex("[a-z]+-\\d+"), " ").replace(Regex("\\d+[.,]?\\d*\\s?%"), " ")
            var nota: String? = Regex("\\b(10|[1-9])\\s*\\((uno|dos|tres|cuatro|cinco|seis|siete|ocho|nueve|diez)\\)").find(cleaned)?.groupValues?.get(1)
            if (nota == null) nota = Regex("\\((uno|dos|tres|cuatro|cinco|seis|siete|ocho|nueve|diez)\\)").find(cleaned)?.groupValues?.get(1)?.let { WORD_NUM[it].toString() }
            if (nota == null) nota = Regex("(nota|calificacion)[^0-9]{0,15}\\b(10|[1-9])\\b").find(cleaned)?.groupValues?.get(2)
            if (nota == null && cat in setOf("aprobada", "promocionada", "desaprobada", "regular"))
                nota = Regex("(?<![\\d.,])\\b(10|[1-9])\\b(?![\\d.,])").find(cleaned)?.groupValues?.get(1)
            if (nota == null) nota = Regex("(nota|calificacion|concepto)[: ]+([a-e])\\b|\\(([a-e])\\)").find(cleaned)
                ?.groupValues?.let { (it[2].ifEmpty { it[3] }).uppercase() }?.ifEmpty { null }
            return cat to nota
        }

        /** Renglones de nota de todas las páginas, según el formato oficial de Guaraní 3. */
        fun registros(pages: List<Scraper.Page>): List<Lector.Reg> {
            val out = mutableListOf<Lector.Reg>()
            for (p in pages) when (kindOf(p.url, p.title)) {
                "plan" -> out += Lector.dePlan(p)
                "nota" -> { out += Lector.deHistoria(p); out += Lector.deTablas(p) }
            }
            return out
        }

        private val SECCIONES = mapOf("historia_academica" to "Historia académica", "plan_estudio" to "Plan de estudio",
            "actuacion_provisoria" to "Actuación provisoria", "mis_inscripciones" to "Mis inscripciones", "cursada" to "Inscripción a materias",
            "examen" to "Inscripción a exámenes", "datos_censales" to "Datos personales", "cronograma_evaluaciones" to "Cronograma de evaluaciones",
            "certificados" to "Constancias y certificados", "inasistencias" to "Inasistencias", "agenda" to "Agenda", "mensajes" to "Mensajes",
            "encuestas" to "Encuestas")

        /** Nombre de una sección para mostrar: el título que pone Guaraní («SIU Guaraní - Historia académica») o, si no, la dirección. */
        fun nombreDePagina(url: String, title: String): String {
            val t = title.substringAfter(" - ", "").trim()
            if (t.length in 3..44 && !norm(t).contains("guarani")) return t
            val slug = url.trimEnd('/').substringAfterLast('/').substringBefore('?')
            SECCIONES[slug.lowercase()]?.let { return it }
            val s = slug.replace('_', ' ').replace('-', ' ')
            return (if (s.isNotBlank() && s.length < 40) s.replaceFirstChar { it.uppercase() } else title.trim()).ifBlank { "Guaraní" }
        }

        private val CALIFICA = setOf("aprobada", "promocionada", "regular", "desaprobada", "ausente", "libre")
        private val FUENTE = listOf("historia", "provisoria", "tabla", "plan", "texto")

        /** De los renglones, los que son una calificación: (clave para no repetir el aviso, renglón). */
        fun calificaciones(regs: List<Lector.Reg>): List<Pair<String, Lector.Reg>> {
            val clave = Lector.claves(regs)
            return regs.filter { it.cat in CALIFICA }
                .groupBy { "${clave(it)}|${it.cat}|${it.nota ?: ""}" }
                .map { (k, g) -> k to g.minByOrNull { FUENTE.indexOf(it.fuente).let { i -> if (i < 0) 99 else i } }!! }
        }
    }

    fun process(r: Scraper.Result) {
        val res = Store.resumen(c, pid)
        res.put("ultima_revision", Store.nowIso())

        if (r.menuPublico.isNotEmpty()) Store.put(c, pid, "menu_publico", menuJson(r.menuPublico))
        if (r.state != Scraper.State.OK) {
            val msg = when (r.state) {
                Scraper.State.OFFLINE -> if (r.message.startsWith("Guaraní está fuera")) "${r.message}. Se reintenta en la próxima revisión."
                    else "Sin conexión con Guaraní (${r.message}). Se reintenta en la próxima revisión."
                Scraper.State.NEED_LOGIN -> "La sesión de Guaraní venció y la contraseña no está guardada: entrá desde la pestaña Guaraní."
                Scraper.State.LOGIN_FAILED -> "Guaraní rechazó el usuario o la clave. ${r.message}".trim()
                Scraper.State.CAPTCHA -> "Guaraní pide un captcha: entrá una vez desde la pestaña Guaraní."
                else -> ""
            }
            Store.setStatus(c, pid, "${Store.shortNow()} · $msg")
            if (r.state != Scraper.State.OFFLINE) {
                res.put("sesion", "vencida"); Store.putResumen(c, pid, res)
                if (!Store.flag(c, pid, "session_warned")) {
                    Store.setFlag(c, pid, "session_warned", true)
                    notify(Notifier.CH_ESTADO, "Guaraní: hay que volver a entrar", msg, tab = "web")
                }
            }
            return
        }
        Store.setFlag(c, pid, "session_warned", false)
        res.put("sesion", "ok")
        res.put("ultima_ok_ms", System.currentTimeMillis())

        // Páginas vigiladas: suma lo que se descubrió en el menú.
        val w = Store.watched(c, pid).toMutableList()
        r.discovered.forEach { if (it !in w) w.add(it) }
        Store.setWatched(c, pid, w)
        if (r.menu.isNotEmpty()) Store.put(c, pid, "menu", menuJson(r.menu))

        // Desde la versión 1.4 la app lee más de cada página (historia completa, plan expandido): la
        // primera revisión con esta versión vuelve a tomar la foto, sin avisar, para no dar falsas novedades.
        if (!Store.flag(c, pid, "lectura_v2")) Store.clearSnapshots(c, pid)

        // 1) Cambios página por página.
        val changes = mutableListOf<Change>()
        var baseline = 0
        for (p in r.pages) {
            val now = lines(p.text)
            val old = Store.snapshot(c, pid, p.url)
            Store.setSnapshot(c, pid, p.url, now)
            if (old == null) { baseline++; continue }
            val oldSet = old.toHashSet(); val nowSet = now.toHashSet()
            val added = now.filter { it !in oldSet }
            val removed = old.filter { it !in nowSet }
            if (added.isNotEmpty() || removed.isNotEmpty()) changes.add(Change(pageName(p), kindOf(p.url, p.title), added, removed))
        }

        // 2) Materias y calificaciones.
        val regs = registros(r.pages)
        val gradeMsgs = mutableListOf<String>()
        if (regs.isNotEmpty()) {
            guardarMaterias(Lector.consolidar(regs), regs.any { it.fuente == "plan" })
            gradeMsgs += nuevasCalificaciones(regs)
        } else {
            // Respaldo para instituciones cuyo Guaraní muestra las notas con otro formato.
            val allLines = r.pages.flatMap { lines(it.text) }
            val firstRun = !Store.flag(c, pid, "first_ok")
            gradeMsgs += updateGrades("materias", "materia", allLines)
            gradeMsgs += addDiscovered(r, firstRun)
        }
        gradeMsgs += updateGrades("diplomatura", "actividad", r.pages.flatMap { lines(it.text) })

        // 3) Exámenes y evaluaciones con fecha.
        val nuevosEventos = guardarEventos(Lector.eventos(r.pages),
            r.pages.any { kindOf(it.url, it.title) in setOf("inscripciones", "examen") })

        // 3b) Inscripciones habilitadas, materia por materia.
        val habilitadas = habilitaciones(r.pages)

        // 4) Carrera, documentos oficiales y resumen.
        Lector.carrera(r.pages)?.let { ca ->
            if (ca.nombre.isNotBlank()) res.put("propuesta", ca.nombre)
            if (ca.plan.isNotBlank()) res.put("plan", ca.plan)
            res.put("propuestas", JSONArray(ca.todas))
        }
        guardarDocumentos(r.pages)
        resumir(res, r.pages)
        Store.putResumen(c, pid, res)

        // 5) Notificaciones.
        if (gradeMsgs.isNotEmpty()) {
            gradeMsgs.forEach { Store.addNovedad(c, pid, "Nota", it) }
            notify(Notifier.CH_NOTAS,
                if (gradeMsgs.size == 1) "Nueva calificación en Guaraní" else "${gradeMsgs.size} calificaciones nuevas",
                gradeMsgs.first(), gradeMsgs,
                acciones = listOf(Notifier.Accion("Compartir", "compartir", gradeMsgs.joinToString("\n"))))
        }
        for (e in nuevosEventos) {
            val cuando = fechaLarga(e.fecha) + (e.hora?.let { " a las $it" } ?: "")
            Store.addNovedad(c, pid, "Exámenes", "${e.titulo} — $cuando")
            notify(Notifier.CH_INSC, if (e.tipo == "examen") "Nueva fecha de examen" else "Nueva evaluación", "${e.materia} — $cuando",
                listOfNotNull("${e.materia} — $cuando", e.detalle.ifBlank { null }),
                acciones = listOf(Notifier.Accion("Agendar", "agendar", eventoJson(e).toString())))
        }
        for ((avisos, url) in habilitadas) {
            val (t, txt, ls) = Habilitaciones.mensaje(avisos)
            avisos.forEach { Store.addNovedad(c, pid, "Inscripciones", (if (it.tipo == "examen") "Inscripción a examen abierta: " else "Inscripción a materia abierta: ") +
                it.materia + (it.detalle.firstOrNull()?.let { d -> " — $d" } ?: "")) }
            notify(Notifier.CH_INSC, t, txt, ls, tab = "web", url = url)
        }
        val notifyAll = Config.notifyAll(c)
        val materiasConFecha = nuevosEventos.map { norm(it.materia) }
        for (ch in changes) {
            val first = ch.added.firstOrNull() ?: "Se quitó: ${ch.removed.first()}"
            val detail = ch.added.map { "+ $it" } + ch.removed.take(4).map { "− $it" }
            val h = headline(ch.kind, ch.page, ch.added, ch.removed, gradeMsgs.isNotEmpty())
            // Si el cambio es el mismo examen que ya se avisó con su fecha, no se repite el aviso.
            val yaAvisado = materiasConFecha.isNotEmpty() && ch.added.any { l -> materiasConFecha.any { norm(l).contains(it) } }
            if (h != null) {
                Store.addNovedad(c, pid, h.third, "${h.first}: ${first.take(160)}")
                if (!yaAvisado) notify(h.second, h.first, first, detail, tab = if (h.second == Notifier.CH_NOTAS) "dash" else "web")
            } else {
                Store.addNovedad(c, pid, "Cambio", "${ch.page}: ${first.take(160)}")
                if (notifyAll && !yaAvisado && !(ch.kind in setOf("nota", "plan") && gradeMsgs.isNotEmpty()))
                    notify(Notifier.CH_CAMBIOS, "Cambió algo en ${ch.page}", first, detail, tab = "web")
            }
        }
        if (baseline > 0 && changes.isEmpty() && gradeMsgs.isEmpty() && !Store.flag(c, pid, "baseline_done")) {
            Store.setFlag(c, pid, "baseline_done", true)
            Store.addNovedad(c, pid, "Inicio", "La app tomó la foto inicial de ${r.pages.size} páginas de Guaraní. Desde ahora avisa cada cambio.")
        }
        Store.setFlag(c, pid, "first_ok", true)
        Store.setFlag(c, pid, "lectura_v2", true)
        val total = changes.size + gradeMsgs.size + nuevosEventos.size
        Store.setStatus(c, pid, "${Store.shortNow()} · ${r.pages.size} páginas revisadas · " +
            if (total == 0) "sin cambios" else "$total novedad(es)")
    }

    // ------------------------------------------------------------------ materias

    private fun materiaJson(m: Lector.Materia) = JSONObject()
        .put("codigo", m.codigo).put("materia", m.nombre).put("cat", m.cat).put("estado", Lector.etiqueta(m.cat))
        .put("nota", m.nota?.let { it.toIntOrNull() ?: it } ?: JSONObject.NULL)
        .put("fecha", m.fecha ?: "").put("detalle", m.detalle).put("tipo", m.tipoAct)
        .put("anio", m.anio).put("periodo", m.periodo).put("provisoria", m.provisoria)

    /** Reemplaza la lista de materias por lo que informa Guaraní; conserva lo que Guaraní no nombró. */
    private fun guardarMaterias(ms: List<Lector.Materia>, hayPlan: Boolean) {
        val viejas = Store.arr(c, pid, "materias")
        val vistas = hashSetOf<String>()
        val out = JSONArray()
        for (m in ms) {
            out.put(materiaJson(m))
            if (m.codigo.isNotBlank()) vistas += "c:" + norm(m.codigo)
            vistas += "n:" + norm(m.nombre)
        }
        for (i in 0 until viejas.length()) {
            val o = viejas.getJSONObject(i)
            val cod = o.optString("codigo"); val nom = o.optString("materia")
            if ((cod.isNotBlank() && "c:" + norm(cod) in vistas) || "n:" + norm(nom) in vistas) continue
            if (!o.has("cat")) o.put("cat", Lector.catDeEtiqueta(o.optString("estado")))
            out.put(o)
        }
        Store.put(c, pid, "materias", out)
        Store.setFlag(c, pid, "hay_plan", hayPlan || Store.flag(c, pid, "hay_plan"))
    }

    private fun conjunto(nombre: String): MutableSet<String> {
        val a = Store.arr(c, pid, nombre)
        return (0 until a.length()).map { a.optString(it) }.toMutableSet()
    }
    private fun guardarConjunto(nombre: String, s: Set<String>) = Store.put(c, pid, nombre, JSONArray(s.toList().takeLast(4000)))

    /** Calificaciones que no se habían visto antes. La primera vez no avisa: son las que ya tenías. */
    private fun nuevasCalificaciones(regs: List<Lector.Reg>): List<String> {
        val vistas = conjunto("avisadas")
        val primera = !Store.flag(c, pid, "notas_init")
        val msgs = mutableListOf<String>()
        for ((k, reg) in calificaciones(regs)) {
            if (!vistas.add(k)) continue
            if (!primera) msgs += Lector.aviso(reg)
        }
        guardarConjunto("avisadas", vistas)
        Store.setFlag(c, pid, "notas_init", true)
        return msgs
    }

    // ------------------------------------------------------------------ exámenes

    private fun eventoJson(e: Lector.Evento) = JSONObject().put("id", e.id).put("fecha", e.fecha)
        .put("hora", e.hora ?: "").put("evento", e.titulo).put("tipo", e.tipo).put("materia", e.materia).put("detalle", e.detalle)

    /** Guarda las fechas que vienen y devuelve las que aparecieron ahora. */
    private fun guardarEventos(es: List<Lector.Evento>, leyoInscripciones: Boolean): List<Lector.Evento> {
        val hoy = LocalDate.now(Store.TZ)
        val futuros = es.filter { runCatching { !LocalDate.parse(it.fecha).isBefore(hoy) }.getOrDefault(false) }
        // Solo si no se pudo leer la página de inscripciones se conservan las fechas que ya se conocían.
        val previos = Store.arr(c, pid, "calendario")
        val out = JSONArray()
        val ids = hashSetOf<String>()
        futuros.forEach { out.put(eventoJson(it)); ids += it.id }
        if (futuros.isEmpty() && !leyoInscripciones) for (i in 0 until previos.length()) {
            val o = previos.getJSONObject(i)
            val f = runCatching { LocalDate.parse(o.optString("fecha")) }.getOrNull() ?: continue
            if (!f.isBefore(hoy) && ids.add(o.optString("id", o.optString("evento") + o.optString("fecha")))) out.put(o)
        }
        Store.put(c, pid, "calendario", out)

        val vistos = conjunto("fechas_avisadas")
        val primera = !Store.flag(c, pid, "fechas_init")
        val nuevos = futuros.filter { vistos.add(it.id) }
        guardarConjunto("fechas_avisadas", vistos)
        Store.setFlag(c, pid, "fechas_init", true)
        return if (primera) emptyList() else nuevos
    }

    /**
     * Recordatorios propios de la app: una semana antes, el día anterior y el mismo día.
     * Solo suenan de 8 a 22; si la revisión cae de madrugada, esperan a la siguiente.
     */
    fun recordatorios(ahora: ZonedDateTime = ZonedDateTime.now(Store.TZ)) {
        if (ahora.hour < 8 || ahora.hour >= 22) return
        val hoy = ahora.toLocalDate()
        val cal = Store.arr(c, pid, "calendario")
        for (i in 0 until cal.length()) {
            val o = cal.getJSONObject(i)
            val f = runCatching { LocalDate.parse(o.optString("fecha")) }.getOrNull() ?: continue
            val dias = java.time.temporal.ChronoUnit.DAYS.between(hoy, f).toInt()
            val cuando = when (dias) { 7 -> "En una semana"; 1 -> "Mañana"; 0 -> "Hoy"; else -> continue }
            val marca = "rec_${dias}_" + Integer.toHexString(o.optString("id").hashCode())
            if (Store.flag(c, pid, marca)) continue
            Store.setFlag(c, pid, marca, true)
            val hora = o.optString("hora").ifBlank { null }
            notify(Notifier.CH_REC, "$cuando: ${o.optString("evento")}",
                fechaLarga(o.optString("fecha")) + (hora?.let { " a las $it" } ?: "") + o.optString("detalle").let { if (it.isBlank()) "" else " · $it" },
                acciones = listOf(Notifier.Accion("Agendar", "agendar", o.toString())))
        }
    }

    private fun fechaLarga(iso: String): String = runCatching {
        val d = LocalDate.parse(iso)
        val dias = listOf("lunes", "martes", "miércoles", "jueves", "viernes", "sábado", "domingo")
        "${dias[d.dayOfWeek.value - 1]} %02d/%02d".format(d.dayOfMonth, d.monthValue)
    }.getOrDefault(iso)

    // ------------------------------------------------------------------ documentos y resumen

    /** PDF y comprobantes que Guaraní ofrece en cada página (se descargan del sitio, la app no los arma). */
    private fun guardarDocumentos(pages: List<Scraper.Page>) {
        val out = JSONArray()
        val vistos = hashSetOf<String>()
        for (p in pages) {
            val pagina = pageName(p)
            for (d in p.docs) {
                val ref = d.contexto.trim()
                val titulo = when {
                    ref.isNotBlank() && Regex("generar|comprobante|imprimir", RegexOption.IGNORE_CASE).containsMatchIn(d.titulo) -> "Comprobante: ${ref.take(70)}"
                    ref.isNotBlank() -> "${d.titulo.ifBlank { "Documento" }}: ${ref.take(70)}"
                    d.tipo == "pdf" -> "$pagina (PDF)"
                    else -> "$pagina: ${d.titulo.ifBlank { "documento" }}"
                }
                if (!vistos.add(d.url.ifBlank { p.url + "|" + titulo })) continue
                out.put(JSONObject().put("titulo", titulo).put("url", d.url).put("pagina", p.url).put("seccion", pagina)
                    .put("tipo", d.tipo).put("directo", d.url.isNotBlank()).put("ref", ref))
                if (out.length() >= 60) break
            }
        }
        // Si esta revisión no encontró ninguno (por ejemplo, falló una página), se conserva la lista anterior.
        if (out.length() > 0 || Store.arr(c, pid, "documentos").length() == 0) Store.put(c, pid, "documentos", out)
    }

    private fun resumir(res: JSONObject, pages: List<Scraper.Page>) {
        val m = Store.arr(c, pid, "materias")
        var aprob = 0; var reg = 0; var curso = 0; var pend = 0
        val notas = mutableListOf<Double>()
        for (i in 0 until m.length()) {
            val o = m.getJSONObject(i)
            when (o.optString("cat").ifBlank { Lector.catDeEtiqueta(o.optString("estado")) }) {
                "aprobada", "promocionada" -> { aprob++; o.opt("nota")?.toString()?.replace(',', '.')?.toDoubleOrNull()?.takeIf { it in 1.0..10.0 }?.let { notas += it } }
                "regular" -> reg++
                "en_curso" -> curso++
                "pendiente" -> pend++
            }
        }
        res.put("aprobadas", aprob); res.put("pendientes_final", reg); res.put("en_curso", curso); res.put("pendientes", pend)
        res.put("total", m.length())

        // Promedio: el que informa Guaraní si aparece; si no, el que resulta de tus notas aprobadas.
        val joined = norm(pages.joinToString("\n") { it.text })
        val deGuarani = Regex("promedio[^0-9\\n]{0,40}(\\d{1,2}[.,]\\d{1,3})").find(joined)?.groupValues?.get(1)?.replace(',', '.')?.toDoubleOrNull()
        when {
            deGuarani != null -> { res.put("promedio", deGuarani); res.put("promedio_origen", "guarani") }
            notas.isNotEmpty() -> { res.put("promedio", Math.round(notas.average() * 100) / 100.0); res.put("promedio_origen", "calculado") }
        }
        // Avance: materias aprobadas sobre el total del plan (solo si la app pudo leer el plan completo).
        if (Store.flag(c, pid, "hay_plan") && m.length() > 0) {
            res.put("avance_pct", Math.round(aprob * 1000.0 / m.length()) / 10.0); res.put("avance_origen", "plan")
        } else Regex("(avance|porcentaje)[^0-9\\n]{0,40}(\\d{1,3}(?:[.,]\\d+)?)").find(joined)?.groupValues?.get(2)
            ?.replace(',', '.')?.toDoubleOrNull()?.takeIf { it <= 100 }?.let { res.put("avance_pct", it); res.put("avance_origen", "guarani") }
        Regex("(\\d+)\\s*mensajes?\\s*(nuevos|sin leer|no leidos)").find(joined)?.groupValues?.get(1)?.toIntOrNull()?.let { res.put("mensajes", it) }
        Regex("(\\d+)\\s*encuestas?\\s*pendientes?").find(joined)?.groupValues?.get(1)?.toIntOrNull()?.let { res.put("encuestas_pendientes", it) }
        val cal = Store.arr(c, pid, "calendario")
        res.put("examenes_inscriptos", (0 until cal.length()).count { cal.getJSONObject(it).optString("tipo") == "examen" })
    }

    private fun menuJson(l: List<Pair<String, String>>) = JSONArray().apply {
        l.forEach { (t, u) -> put(JSONObject().put("titulo", t).put("url", u)) }
    }

    /**
     * Lee el detalle de cada materia en las páginas de inscripción y guarda el estado. Devuelve los avisos
     * agrupados por página (para abrir esa página al tocar la notificación).
     */
    private fun habilitaciones(pages: List<Scraper.Page>): List<Pair<List<Habilitaciones.Aviso>, String>> {
        var estado = Store.arr(c, pid, "habilitaciones")
        val out = mutableListOf<Pair<List<Habilitaciones.Aviso>, String>>()
        for (p in pages) {
            if (p.actividades.isEmpty()) continue
            val tipo = Habilitaciones.tipoDePagina(p.url, p.title) ?: continue
            val (avisos, nuevo) = Habilitaciones.comparar(estado, p.actividades.map { Habilitaciones.estado(tipo, it.first, it.second) }, p.url)
            estado = nuevo
            if (avisos.isNotEmpty()) out += avisos to p.url
        }
        Store.put(c, pid, "habilitaciones", estado)
        return out
    }

    private fun notify(ch: String, title: String, text: String, lines: List<String> = emptyList(), tab: String = "dash",
                       acciones: List<Notifier.Accion> = emptyList(), url: String? = null) {
        val t = if (Profiles.all(c).size > 1) "${prof.nombre} · $title" else title
        Notifier.notify(c, ch, t, text, lines, tab, pid, acciones, Profiles.carreraDe(c, prof), url)
    }

    // ------------------------------------------------------------------ respaldo (otros formatos)

    /** Suma a la lista las materias nuevas que aparecen en la historia académica. */
    private fun addDiscovered(r: Scraper.Result, firstRun: Boolean): List<String> {
        val pages = r.pages.map { it.url to lines(it.text) }
        val found = discoverActivities(pages)
        if (found.isEmpty()) return emptyList()
        val mat = Store.arr(c, pid, "materias"); val dip = Store.arr(c, pid, "diplomatura")
        val known = (0 until mat.length()).map { norm(mat.getJSONObject(it).optString("materia")) } +
            (0 until dip.length()).map { norm(dip.getJSONObject(it).optString("actividad")) }
        val all = pages.flatMap { it.second }
        val names = found.map { it.first }
        val msgs = mutableListOf<String>()
        for ((name, code) in found) {
            if (norm(name) in known) continue
            val g = findGrade(all, name, names)
            val cat = g?.first ?: "otra"
            val nota = g?.second
            mat.put(JSONObject().put("codigo", code).put("materia", name).put("estado", label(cat))
                .put("cat", Lector.catDeEtiqueta(label(cat)))
                .put("nota", nota?.let { it.toIntOrNull() ?: it } ?: JSONObject.NULL)
                .put("fecha", Store.today()).put("detalle", "Leída de la historia académica"))
            if (!firstRun && nota != null) msgs.add("$name: ${label(cat).lowercase()} con $nota.")
        }
        Store.put(c, pid, "materias", mat)
        return msgs
    }

    private fun updateGrades(ds: String, nameField: String, all: List<String>): List<String> {
        val arr = Store.arr(c, pid, ds)
        val names = (0 until arr.length()).map { arr.getJSONObject(it).optString(nameField) }
        val msgs = mutableListOf<String>()
        for (i in 0 until arr.length()) {
            val o = arr.getJSONObject(i)
            val name = o.optString(nameField)
            if (name.isBlank()) continue
            val (cat, nota) = findGrade(all, name, names) ?: continue
            val oldCat = category(o.optString("estado"))
            val oldNota = o.opt("nota")?.toString()?.takeIf { it != "null" && it.isNotBlank() }
            val notaChanged = nota != null && nota != oldNota
            if (cat == oldCat && !notaChanged) continue
            if (cat != oldCat) { o.put("estado", label(cat)); o.put("cat", Lector.catDeEtiqueta(label(cat))) }
            if (nota != null) o.put("nota", nota.toIntOrNull() ?: nota)
            o.put("fecha", Store.today())
            o.put("detalle", "Actualizado por la app: ${label(cat)}${nota?.let { " con $it" } ?: ""}")
            msgs.add("$name: ${label(cat).lowercase()}${nota?.let { " con $it" } ?: ""}.")
        }
        if (msgs.isNotEmpty()) Store.put(c, pid, ds, arr)
        return msgs
    }

    private fun pageName(p: Scraper.Page): String = nombreDePagina(p.url, p.title)
}
