package ar.pariz.guarani

import android.Manifest
import android.app.Application
import android.app.Notification
import android.app.NotificationManager
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config as RConfig
import java.time.LocalDate
import java.time.ZonedDateTime

/**
 * Recorrido completo: lo que la app lee de Guaraní → tablero → notificaciones.
 * Primera revisión (no avisa nada) y segunda revisión con una nota nueva y un examen nuevo.
 */
@RunWith(RobolectricTestRunner::class)
@RConfig(sdk = [34])
class FlujoTest {
    private lateinit var c: Context
    private lateinit var prof: Profile
    private val nm get() = c.getSystemService(NotificationManager::class.java)
    private fun avisos(): List<Notification> = shadowOf(nm).allNotifications
    private fun titulo(n: Notification) = n.extras.getCharSequence(Notification.EXTRA_TITLE).toString()
    private fun texto(n: Notification) = n.extras.getCharSequence(Notification.EXTRA_BIG_TEXT).toString()

    @Before fun prep() {
        c = ApplicationProvider.getApplicationContext()
        shadowOf(c as Application).grantPermissions(Manifest.permission.POST_NOTIFICATIONS)
        Notifier.createChannels(c)
        prof = Profile("t1", "UTN Sistemas", "Universidad de Ejemplo", "https://guarani.ejemplo.edu.ar/autogestion/", true)
        Profiles.save(c, prof, "30123456", "secreta")
        Profiles.setActive(c, prof.id)
    }

    private fun resultado(pages: List<Scraper.Page>) = Scraper.Result(Scraper.State.OK, pages, pages.drop(1).map { it.url },
        menu = listOf("Historia académica" to "https://guarani.ejemplo.edu.ar/autogestion/historia_academica"))

    /** Segunda visita: Guaraní carga una nota de examen provisoria y aparece una inscripción a examen nueva. */
    private fun conNovedades(pages: List<Scraper.Page>) = pages.map { p ->
        when {
            p.url.endsWith("actuacion_provisoria") -> p.copy(
                text = p.text.replace("No hay información sobre actuaciones provisorias en exámenes", "Programación II 9 (Nueve) Aprobado"),
                tables = p.tables + Scraper.Table("Exámenes", listOf("Propuesta", "Actividad", "Fecha", "Nota", "Resultado"),
                    listOf(listOf("Lic. en Sistemas", "Programación II", "12/11/2026", "9 (Nueve)", "Aprobado"))))
            p.url.endsWith("mis_inscripciones") -> p.copy(
                text = p.text + "\n10/11/2026 Diciembre 2026 Llamado 02 Programación II (SI-201) A Sede Única 10/07/2027 09:30 Aceptada",
                tables = p.tables.map { t -> if (!t.sec.startsWith("Exámenes")) t else t.copy(rows = t.rows + listOf(listOf(
                    "10/11/2026 08:00:00", "2026", "Julio 2027", "Llamado 02", "Lic. en Sistemas", "Programación II (SI-201)", "A", "Sede Única", "", "10/07/2027 09:30", "Aceptada", "Generar"))) })
            else -> p
        }
    }

    @Test fun dosRevisiones() {
        val pages = LectorTest.TODAS
        Analyzer(c, prof).process(resultado(pages))

        // ---- Primera revisión: llena el tablero y NO avisa (son las notas que ya tenías)
        assertTrue("la primera revisión no debe notificar", avisos().isEmpty())
        val r = Store.resumen(c, prof.id)
        assertEquals("Licenciatura en Sistemas de Información", r.getString("propuesta"))
        assertEquals("Plan 2019", r.getString("plan"))
        assertEquals(2, r.getJSONArray("propuestas").length())
        assertEquals("ok", r.getString("sesion"))
        assertEquals(3, r.getInt("aprobadas")); assertEquals(2, r.getInt("pendientes_final")); assertEquals(1, r.getInt("en_curso"))
        assertEquals(7.83, r.getDouble("promedio"), 0.001); assertEquals("calculado", r.getString("promedio_origen"))
        assertEquals(30.0, r.getDouble("avance_pct"), 0.001); assertEquals("plan", r.getString("avance_origen"))   // 3 de 10
        assertEquals(2, r.getInt("examenes_inscriptos"))
        val mats = Store.arr(c, prof.id, "materias")
        assertEquals(10, mats.length())
        val bd = (0 until mats.length()).map { mats.getJSONObject(it) }.first { it.getString("codigo") == "SI-202" }
        assertEquals("regular", bd.getString("cat")); assertEquals(7, bd.getInt("nota")); assertTrue(bd.getBoolean("provisoria"))
        assertEquals(2, Store.arr(c, prof.id, "calendario").length())
        val docs = Store.arr(c, prof.id, "documentos")
        assertTrue(docs.length() >= 6)
        assertTrue((0 until docs.length()).any { docs.getJSONObject(it).getString("titulo") == "Historia académica (PDF)" && docs.getJSONObject(it).getBoolean("directo") })
        assertTrue((0 until docs.length()).any { docs.getJSONObject(it).getString("titulo") == "Comprobante: Álgebra y Geometría (SI-103)" && !docs.getJSONObject(it).getBoolean("directo") })
        assertEquals("Licenciatura en Sistemas de Información", Profiles.carreraDe(c, prof))

        // ---- Misma lectura otra vez: sin cambios, sin avisos
        Analyzer(c, prof).process(resultado(pages))
        assertTrue(avisos().isEmpty())
        assertTrue(Store.status(c, prof.id).endsWith("sin cambios"))

        // ---- Tercera: aparece una nota y un examen
        Analyzer(c, prof).process(resultado(conNovedades(pages)))
        val ns = avisos()
        val nota = ns.single { titulo(it) == "Nueva calificación en Guaraní" }
        assertEquals("Programación II: nota provisoria, examen aprobado con 9.", texto(nota))
        assertEquals(Notifier.CH_NOTAS, nota.channelId)
        assertEquals("Compartir", nota.actions.single().title.toString())
        val ex = ns.single { titulo(it) == "Nueva fecha de examen" }
        assertEquals(Notifier.CH_INSC, ex.channelId)
        assertTrue(texto(ex).contains("Programación II — sábado 10/07 a las 09:30"))
        assertEquals("Agendar", ex.actions.single().title.toString())
        // El mismo examen no se avisa dos veces (como «novedad en tus inscripciones» y como fecha nueva)
        assertEquals("avisos: " + ns.map { titulo(it) }, 2, ns.size)

        val m2 = Store.arr(c, prof.id, "materias")
        val p2 = (0 until m2.length()).map { m2.getJSONObject(it) }.first { it.getString("codigo") == "SI-201" }
        assertEquals("aprobada", p2.getString("cat")); assertEquals(9, p2.getInt("nota"))
        assertEquals(10, m2.length())                                    // la materia no se duplica
        assertEquals(4, Store.resumen(c, prof.id).getInt("aprobadas"))
        assertEquals(3, Store.arr(c, prof.id, "calendario").length())
        val nov = Store.arr(c, prof.id, "novedades")
        val historial = (0 until nov.length()).map { nov.getJSONObject(it) }
        assertTrue(historial.any { it.getString("tipo") == "Nota" && it.getString("detalle").startsWith("Programación II: nota provisoria") })
        assertTrue(historial.any { it.getString("tipo") == "Exámenes" && it.getString("detalle").contains("Examen: Programación II") })

        // ---- Cuarta, igual a la tercera: no repite avisos
        Analyzer(c, prof).process(resultado(conNovedades(pages)))
        assertEquals(2, avisos().size)
    }

    @Test fun canalDeCalificacionesConSonido() {
        val ch = nm.getNotificationChannel(Notifier.CH_NOTAS)
        assertEquals(NotificationManager.IMPORTANCE_HIGH, ch.importance)
        assertEquals("android.resource://${c.packageName}/${R.raw.calificacion}", ch.sound.toString())
        assertTrue(ch.shouldVibrate())
        assertNull("el canal viejo sin sonido propio se retira", nm.getNotificationChannel("notas"))
        assertNotNull(nm.getNotificationChannel(Notifier.CH_REC))
        // El archivo de sonido existe y es un Ogg
        val cab = c.resources.openRawResource(R.raw.calificacion).use { it.readNBytes(4) }
        assertEquals("OggS", String(cab))
    }

    @Test fun recordatoriosDeExamen() {
        val hoy = LocalDate.now(Store.TZ)
        fun ev(d: LocalDate, m: String) = JSONObject().put("id", "examen|$m|$d").put("fecha", d.toString()).put("hora", "18:00")
            .put("evento", "Examen: $m").put("tipo", "examen").put("materia", m).put("detalle", "Aula 12")
        Store.put(c, prof.id, "calendario", JSONArray().put(ev(hoy.plusDays(1), "Álgebra")).put(ev(hoy.plusDays(7), "Física")).put(ev(hoy.plusDays(3), "Química")))
        val mediodia = ZonedDateTime.now(Store.TZ).withHour(12)
        // De madrugada no suena
        Analyzer(c, prof).recordatorios(mediodia.withHour(3))
        assertTrue(avisos().isEmpty())
        Analyzer(c, prof).recordatorios(mediodia)
        assertEquals(setOf("Mañana: Examen: Álgebra", "En una semana: Examen: Física"), avisos().map { titulo(it) }.toSet())
        assertTrue(avisos().all { it.channelId == Notifier.CH_REC && it.actions.single().title == "Agendar" })
        // No se repiten
        Analyzer(c, prof).recordatorios(mediodia)
        assertEquals(2, avisos().size)
    }

    @Test fun sesionVencida() {
        Analyzer(c, prof).process(Scraper.Result(Scraper.State.NEED_LOGIN, emptyList(), emptyList()))
        Analyzer(c, prof).process(Scraper.Result(Scraper.State.NEED_LOGIN, emptyList(), emptyList()))
        assertEquals("avisa una sola vez", listOf("Guaraní: hay que volver a entrar"), avisos().map { titulo(it) })
        assertEquals("vencida", Store.resumen(c, prof.id).getString("sesion"))
        // Sin conexión no es sesión vencida
        Analyzer(c, prof).process(resultado(LectorTest.TODAS))
        Analyzer(c, prof).process(Scraper.Result(Scraper.State.OFFLINE, emptyList(), emptyList(), "net::ERR"))
        assertEquals("ok", Store.resumen(c, prof.id).getString("sesion"))
    }

    @Test fun datosDeVersionesAnteriores() {
        // Una instalación 1.3 con materias cargadas (sin categoría) y fotos viejas: al actualizar no se pierden ni disparan avisos.
        Store.put(c, prof.id, "materias", JSONArray()
            .put(JSONObject().put("codigo", "SI-101").put("materia", "Análisis Matemático I").put("estado", "Regular, falta final").put("nota", 7))
            .put(JSONObject().put("codigo", "X-9").put("materia", "Materia de otro plan").put("estado", "Aprobada").put("nota", 10)))
        Store.setSnapshot(c, prof.id, "https://guarani.ejemplo.edu.ar/autogestion/historia_academica", listOf("Historia académica", "Consultas habituales:"))
        Store.setFlag(c, prof.id, "first_ok", true); Store.setFlag(c, prof.id, "baseline_done", true)
        Analyzer(c, prof).process(resultado(LectorTest.TODAS))
        assertTrue("actualizar la app no debe generar avisos", avisos().isEmpty())
        val m = Store.arr(c, prof.id, "materias")
        val l = (0 until m.length()).map { m.getJSONObject(it) }
        assertEquals(11, l.size)                                                  // las 10 del plan + la que Guaraní no nombró
        assertEquals("aprobada", l.first { it.getString("codigo") == "SI-101" }.getString("cat"))
        assertEquals("aprobada", l.first { it.getString("codigo") == "X-9" }.getString("cat"))
    }

    @Test fun compartirCalificaciones() {
        Analyzer(c, prof).process(resultado(LectorTest.TODAS))
        val t = Compartir.texto("Universidad de Ejemplo", "Licenciatura en Sistemas de Información", Store.resumen(c, prof.id), Store.arr(c, prof.id, "materias"))
        val lineas = t.lines()
        assertEquals("Mis calificaciones — Licenciatura en Sistemas de Información", lineas[0])
        assertEquals("Universidad de Ejemplo", lineas[1])
        assertEquals("Promedio: 7,83 · Aprobadas: 3 de 10", lineas[2])
        assertTrue(t.contains("Aprobadas (3)"))
        assertTrue(t.contains("• Análisis Matemático I — 8 · 20/02/2026"))
        assertTrue(t.contains("• Programación I — 8 (promoción) · 05/07/2026"))
        assertTrue(t.contains("Regulares (falta el final) (2)"))
        assertTrue(t.contains("• Bases de Datos — 7 (provisoria)"))
        assertTrue(t.contains("En curso (1)"))
        assertTrue(t.contains("Pendientes del plan: 3"))
        assertFalse(t.contains("Trabajo Final"))                                  // las pendientes no se listan una por una
        val solo = Compartir.texto("U", "C", Store.resumen(c, prof.id), Store.arr(c, prof.id, "materias"), "aprobada")
        assertTrue(solo.contains("Aprobadas (3)")); assertFalse(solo.contains("En curso"))
    }
}
