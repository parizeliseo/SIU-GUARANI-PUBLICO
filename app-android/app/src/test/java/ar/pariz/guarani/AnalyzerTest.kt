package ar.pariz.guarani
import org.junit.Assert.*
import org.junit.Test
class AnalyzerTest {
    private val txt = """
Historia Académica
Promedio general con aplazos: 9,60
Instrumentos y Mediciones (GSTI-106)
14/11/2026 Examen 8 (Ocho) Aprobado
Legislación en Tecnologías de la Información y Comunicaciones (GSTI-104)
En curso
Estadísticas y Probabilidad (GSTI-103)
20/06/2026 Regularidad 10 (Diez) Regular
Base de Datos (GSTI-107)
11/10/2026 Promoción 10 (Diez) Promocionado
Fronteras y Desarrollo (DDF-4)
15/11/2026 Aprobado Nota: A
Última conexión 12/11/2026 13:10
""".trimIndent()
    private val names = listOf("Instrumentos y Mediciones","Legislación en Tecnologías de la Información y Comunicaciones",
        "Estadísticas y Probabilidad","Base de Datos","Fronteras y Desarrollo")
    @Test fun grades() {
        val l = Analyzer.lines(txt)
        assertFalse(l.any { it.contains("conexión") })
        assertEquals("aprobada" to "8", Analyzer.findGrade(l, "Instrumentos y Mediciones", names))
        assertNull(Analyzer.findGrade(l, "Legislación en Tecnologías de la Información y Comunicaciones", names))
        assertEquals("regular" to "10", Analyzer.findGrade(l, "Estadísticas y Probabilidad", names))
        assertEquals("promocionada" to "10", Analyzer.findGrade(l, "Base de Datos", names))
        assertEquals("aprobada" to "A", Analyzer.findGrade(l, "Fronteras y Desarrollo", names))
    }
}

class DiscoverTest {
    @Test fun activities() {
        val hist = """
Historia Académica
Análisis Matemático I (95-1121)
12/07/2026 Examen 7 (Siete) Aprobado
Física I (95-1124)
En curso
Promedio general: 7,50
""".trimIndent()
        val found = Analyzer.discoverActivities(listOf("https://x.edu.ar/autogestion/historia_academica" to Analyzer.lines(hist)))
        assertEquals(listOf("Análisis Matemático I" to "95-1121", "Física I" to "95-1124"), found)
        val other = Analyzer.discoverActivities(listOf("https://x.edu.ar/autogestion/mensajes" to Analyzer.lines("Aviso (URGENTE)")))
        assertTrue(other.isEmpty())
        assertEquals("aprobada" to "7", Analyzer.findGrade(Analyzer.lines(hist), "Análisis Matemático I", found.map { it.first }))
    }
    @Test fun urls() {
        assertEquals("https://siu.iugna.edu.ar/autogestion/", Profiles.normalizeUrl("siu.iugna.edu.ar/autogestion/acceso?auth=form"))
        assertEquals("https://www.fie.undef.edu.ar/siu/3w/", Profiles.normalizeUrl("https://www.fie.undef.edu.ar/siu/3w/acceso?auth=form"))
        assertEquals("https://guarani.unt.edu.ar/autogestion/", Profiles.normalizeUrl("https://guarani.unt.edu.ar/autogestion"))
    }
}

class AvisosTest {
    private val B = "https://siu.x.edu.ar/autogestion/"
    @Test fun temas() {
        assertEquals("nota", Analyzer.kindOf(B + "historia_academica", "SIU Guaraní - Historia académica"))
        assertEquals("plan", Analyzer.kindOf(B + "plan_estudio", "Plan de estudios"))
        assertEquals("examen", Analyzer.kindOf(B + "inscripcion_examen", "Inscripción a exámenes"))
        assertEquals("examen", Analyzer.kindOf(B + "fecha_examen", "Fechas de examen"))
        assertEquals("cursada", Analyzer.kindOf(B + "cursada", "Inscripción a materias"))
        assertEquals("mensaje", Analyzer.kindOf(B + "mensajes", "Mensajes"))
        assertEquals("otro", Analyzer.kindOf(B + "datos_censales", "Datos"))
    }
    @Test fun titulos() {
        val abre = Analyzer.headline("examen", "Examen", listOf("Base de Datos - 15/12/2026 18:00 - Regular"), listOf("No hay mesas de examen disponibles"), false)
        assertEquals("Se abrió la inscripción a exámenes", abre!!.first)
        assertEquals(Notifier.CH_INSC, abre.second)
        val mesa = Analyzer.headline("examen", "Examen", listOf("Electromagnetismo - 20/12/2026"), emptyList(), false)
        assertEquals("Novedades en mesas de examen", mesa!!.first)
        val cierra = Analyzer.headline("cursada", "Cursada", listOf("No hay períodos de inscripción vigentes"), listOf("Taller de Programación - Comisión A"), false)
        assertEquals("Cerró la inscripción a materias", cierra!!.first)
        assertEquals("Cambió tu plan de estudios", Analyzer.headline("plan", "Plan", listOf("Informática Forense (GSTI-301)"), emptyList(), false)!!.first)
        assertEquals(Notifier.CH_PLAN, Analyzer.headline("plan", "Plan", listOf("x"), emptyList(), false)!!.second)
        assertNull(Analyzer.headline("nota", "Historia", listOf("x"), emptyList(), true))
        assertNull(Analyzer.headline("otro", "Datos", listOf("Domicilio: Av. Siempreviva 742"), emptyList(), false))
    }
    @Test fun sesion() {
        val ingreso = "kernel.renderer.on_arrival({\"content\":\"<input id=\\\"password\\\" name=\\\"password\\\" type=\\\"password\\\" value=\\\"\\\"/>\"})"
        assertTrue(KeepAliveWorker.looksLoggedOut(B, ingreso))
        assertTrue(KeepAliveWorker.looksLoggedOut(B, "<a href=\"https://x/unq/acceso?auth=saml\">Ingresar</a>"))
        assertTrue(KeepAliveWorker.looksLoggedOut(B + "acceso", "<html></html>"))
        assertFalse(KeepAliveWorker.looksLoggedOut(B, "<div id='kernel_contenido'>Bienvenido Juan. Períodos de inscripción</div>"))
    }
}
