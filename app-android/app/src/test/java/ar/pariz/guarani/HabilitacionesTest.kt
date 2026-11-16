package ar.pariz.guarani

import android.Manifest
import android.app.Application
import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.json.JSONArray
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config as RConfig

/**
 * Inscripción a exámenes con la lista de materias y el detalle de cada una (como en la UNDEF):
 * se avisa cuando una materia pasa de «No hay mesas habilitadas» a tener mesa.
 * Las líneas salen de app/src/test/guarani/examen*.html leídas con el JavaScript EXPLORAR (probar.js).
 */
@RunWith(RobolectricTestRunner::class)
@RConfig(sdk = [34])
class HabilitacionesTest {
    private lateinit var c: Context
    private lateinit var prof: Profile
    private val nm get() = c.getSystemService(NotificationManager::class.java)
    private fun avisos(): List<Notification> = shadowOf(nm).allNotifications
    private fun titulo(n: Notification) = n.extras.getCharSequence(Notification.EXTRA_TITLE).toString()
    private val URL = "https://guarani.ejemplo.edu.ar/3w/examen"

    private val leidas: List<Pair<String, List<String>?>> by lazy {
        Habilitaciones.leer(JSONArray(javaClass.getResource("/guarani/examen_actividades.json")!!.readText()))
    }

    @Before fun prep() {
        c = ApplicationProvider.getApplicationContext()
        shadowOf(c as Application).grantPermissions(Manifest.permission.POST_NOTIFICATIONS)
        Notifier.createChannels(c)
        prof = Profile("h1", "Ciberdefensa", "Universidad de Ejemplo", "https://guarani.ejemplo.edu.ar/3w/", true)
        Profiles.save(c, prof, "30123456", "secreta"); Profiles.setActive(c, prof.id)
    }

    @Test fun tipoDePagina() {
        assertEquals("examen", Habilitaciones.tipoDePagina("https://x.edu.ar/3w/examen", "Inscripción a exámenes"))
        assertEquals("examen", Habilitaciones.tipoDePagina("https://x.edu.ar/autogestion/inscripcion_examen", ""))
        assertEquals("cursada", Habilitaciones.tipoDePagina("https://x.edu.ar/3w/cursada", "Inscripción a materias"))
        assertNull(Habilitaciones.tipoDePagina("https://x.edu.ar/3w/mis_inscripciones", "Mis inscripciones"))
        assertNull(Habilitaciones.tipoDePagina("https://x.edu.ar/3w/historia_academica", ""))
        assertNull(Habilitaciones.tipoDePagina("https://x.edu.ar/3w/cronograma_evaluaciones", ""))
    }

    @Test fun leeElDetalleDeCadaMateria() {
        val e = leidas.map { Habilitaciones.estado("examen", it.first, it.second) }.associateBy { it.materia }
        assertEquals(setOf("Ética Profesional", "Geopolítica", "Inglés I"), e.keys)
        assertEquals(Habilitaciones.CERRADA, e["Ética Profesional"]!!.estado)
        assertEquals(Habilitaciones.CERRADA, e["Inglés I"]!!.estado)
        val g = e["Geopolítica"]!!
        assertEquals(Habilitaciones.ABIERTA, g.estado)
        assertEquals(listOf("10/12/2026"), g.fechas)
        assertEquals("Turno Diciembre - Llamado 1 · 10/12/2026 · 09:00 · Campus virtual", g.detalle.first())
    }

    @Test fun detalleSinTituloYLecturaFallida() {
        // Sin el nombre de la materia como título y con avisos de otra parte de la página.
        val e = Habilitaciones.estado("cursada", "Inglés II", listOf("Comisión A · Lunes 18 a 21 hs · Aula 4 · Inscribirse"))
        assertEquals(Habilitaciones.ABIERTA, e.estado)
        assertEquals(Habilitaciones.CERRADA, Habilitaciones.estado("cursada", "Inglés II", listOf("No hay comisiones habilitadas para esta actividad")).estado)
        assertEquals("?", Habilitaciones.estado("examen", "Inglés II", null).estado)
        assertEquals("?", Habilitaciones.estado("examen", "Inglés II", listOf("Inglés II")).estado)
    }

    @Test fun comparaYSoloAvisaLoNuevo() {
        fun ronda(prev: JSONArray, l: List<Pair<String, List<String>?>>) = Habilitaciones.comparar(prev, l.map { Habilitaciones.estado("examen", it.first, it.second) })
        val (a1, s1) = ronda(JSONArray(), leidas)
        assertEquals(listOf("Geopolítica"), a1.map { it.materia })               // ya abierta la primera vez: se avisa
        val (a2, s2) = ronda(s1, leidas)
        assertTrue(a2.isEmpty())                                                   // igual que antes: nada
        // Se habilita Ética Profesional y una lectura de Inglés I falla (no cambia su estado).
        val abreEtica = leidas.map { (m, l) -> when (m) {
            "Ética Profesional" -> m to listOf("Ética Profesional", "Llamado · Fecha", "Turno Diciembre - Llamado 1 · 15/12/2026 · Inscribirse")
            "Inglés I" -> m to null
            else -> m to l } }
        val (a3, s3) = ronda(s2, abreEtica)
        assertEquals(listOf("Ética Profesional"), a3.map { it.materia }); assertFalse(a3[0].nuevaMesa)
        assertEquals(Habilitaciones.CERRADA, (0 until s3.length()).map { s3.getJSONObject(it) }.first { it.getString("materia") == "Inglés I" }.getString("estado"))
        // Aparece un segundo llamado en Geopolítica: «nueva mesa».
        val segundo = abreEtica.map { (m, l) -> if (m == "Geopolítica") m to (l!! + "Turno Febrero - Llamado 1 · 20/02/2027 · Inscribirse") else m to l }
        val (a4, _) = ronda(s3, segundo)
        assertEquals(listOf("Geopolítica"), a4.map { it.materia }); assertTrue(a4[0].nuevaMesa)
    }

    @Test fun notificaYAbreLaPaginaDeInscripcion() {
        val pagina = Scraper.Page(URL, "Inscripción a exámenes", "Inscripción a exámenes\nÉtica Profesional\nGeopolítica\nInglés I", actividades = leidas)
        Analyzer(c, prof).process(Scraper.Result(Scraper.State.OK, listOf(pagina), listOf(URL)))
        val n = avisos().single { titulo(it).contains("Inscripción a examen abierta") }
        assertEquals("Inscripción a examen abierta: Geopolítica", titulo(n))
        assertEquals(Notifier.CH_INSC, n.channelId)
        val i = shadowOf(n.contentIntent as PendingIntent).savedIntent
        assertEquals(URL, i.getStringExtra("url"))
        assertTrue(Seguridad.esPropio(c, i))
        // Queda en las novedades y en el estado por materia (lo usa el tablero).
        assertTrue(Store.get(c, prof.id, "novedades").contains("Inscripción a examen abierta: Geopolítica"))
        assertTrue(Store.get(c, prof.id, "habilitaciones").contains("\"estado\":\"abierta\""))
        // Segunda revisión igual: no se repite.
        nm.cancelAll()
        Analyzer(c, prof).process(Scraper.Result(Scraper.State.OK, listOf(pagina), listOf(URL)))
        assertTrue(avisos().none { titulo(it).contains("Inscripción") })
    }

    @Test fun variasALaVez() {
        val (t, _, ls) = Habilitaciones.mensaje(listOf(
            Habilitaciones.Aviso("examen", "Geopolítica", listOf("10/12/2026"), false),
            Habilitaciones.Aviso("examen", "Inglés I", emptyList(), false)))
        assertEquals("Se habilitó la inscripción a 2 exámenes", t)
        assertEquals(listOf("Examen: Geopolítica — 10/12/2026", "Examen: Inglés I"), ls)
    }
}
