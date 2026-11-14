package ar.pariz.guarani

import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

/**
 * Interpretación de las pantallas de Guaraní. Los datos de entrada (los .json de src/test/resources/guarani) son lo que
 * el JavaScript de la app leyó de las páginas de prueba de src/test/guarani/, armadas según la documentación del SIU.
 */
class LectorTest {
    companion object {
        fun pagina(n: String): Scraper.Page =
            Scraper.Page.fromJson(JSONObject(LectorTest::class.java.getResource("/guarani/$n.json")!!.readText()))
        val TODAS get() = listOf("inicio", "historia_academica", "plan_estudio", "actuacion_provisoria", "mis_inscripciones").map { pagina(it) }
    }

    @Test fun notas() {
        assertEquals("8", Lector.nota("8,0 (OCHO)"))
        assertEquals("8", Lector.nota("8,0 (Promocionado)"))
        assertEquals("7,5", Lector.nota("7,50 (Siete con cincuenta) Aprobado"))
        assertEquals("10", Lector.nota("10 (Diez)"))
        assertEquals("7", Lector.nota("(Siete)"))
        assertEquals("9", Lector.nota("9 (nueve) 05/07/2026"))
        assertEquals("A", Lector.nota("A"))
        assertNull(Lector.nota("Aprobado"))
        assertNull(Lector.nota("Ausente 22/11/2025"))
        assertNull(Lector.nota(""))
    }

    @Test fun nombresYCodigos() {
        assertEquals("Materia MAM 1" to "MAM-1", Lector.nombreCodigo("Materia MAM 1 (MAM-1)"))
        assertEquals("Sistemas de Información I" to "153", Lector.nombreCodigo("(153) Sistemas de Información I"))
        assertEquals("Problemas de Historia Argentina" to "CI003", Lector.nombreCodigo("Problemas de Historia Argentina (CI003)"))
        assertEquals("Taller de Lectura y Escritura" to "", Lector.nombreCodigo("Taller de Lectura y Escritura"))
        // Una nota entre paréntesis no es un código
        assertEquals("", Lector.nombreCodigo("8,0 (OCHO)").second)
        assertEquals("", Lector.nombreCodigo("7 (Siete)").second)
        assertEquals("", Lector.nombreCodigo("8,0 (Promocionado)").second)
    }

    @Test fun categorias() {
        assertEquals("promocionada", Lector.cat("Promoción", "8,0 (OCHO) Promocionado 05/07/2019"))
        assertEquals("regular", Lector.cat("Regularidad", "7 (Siete) Aprobado"))      // cursada aprobada = falta el final
        assertEquals("regular", Lector.cat("Cursadas", "Aprobado"))
        assertEquals("aprobada", Lector.cat("Examen", "8 (Ocho) Aprobado"))
        assertEquals("desaprobada", Lector.cat("Examen", "2 (Dos) Desaprobado"))
        assertEquals("desaprobada", Lector.cat("Parcial", "3 (tres) Reprobado"))
        assertEquals("ausente", Lector.cat("Regularidad", "Ausente"))
        assertEquals("en_curso", Lector.cat("En curso", "Primer cuatrimestre 2026"))
        assertEquals("aprobada", Lector.cat("Equivalencia", "7,50"))
        assertNull(Lector.cat("Examen", "sin datos"))
    }

    @Test fun planDeEstudio() {
        val regs = Lector.dePlan(pagina("plan_estudio"))
        assertEquals(10, regs.size)
        fun r(c: String) = regs.first { it.codigo == c }
        assertEquals("aprobada" to "8", r("SI-101").cat to r("SI-101").nota)
        assertEquals("promocionada" to "8", r("SI-102").cat to r("SI-102").nota)
        assertEquals("pendiente", r("SI-103").cat)
        assertEquals("aprobada" to "7,5", r("SI-105").cat to r("SI-105").nota)
        assertEquals("en_curso", r("SI-106").cat)
        assertEquals("pendiente", r("SI-501").cat)
        assertEquals("Tesis", r("SI-501").tipoAct)
        assertEquals("Análisis Matemático I", r("SI-101").nombre)
        assertEquals("1", r("SI-101").anio)
    }

    @Test fun historiaAcademica() {
        val regs = Lector.deHistoria(pagina("historia_academica"))
        val am = regs.filter { it.codigo == "SI-101" }
        assertEquals(listOf("cursada|regular|7|2025-11-20", "examen|desaprobada|2|2025-12-12", "examen|aprobada|8|2026-02-20"),
            am.map { "${it.tipo}|${it.cat}|${it.nota}|${it.fecha}" })
        val p = regs.single { it.codigo == "SI-102" }
        assertEquals("promocion|promocionada|8|2026-07-05", "${p.tipo}|${p.cat}|${p.nota}|${p.fecha}")
        assertEquals("ausente", regs.single { it.codigo == "SI-104" }.cat)
        assertNull(regs.single { it.codigo == "SI-104" }.nota)
        assertEquals("aprobada" to "7,5", regs.single { it.codigo == "SI-105" }.let { it.cat to it.nota })
        assertEquals("equivalencia", regs.single { it.codigo == "SI-105" }.tipo)
        assertEquals("en_curso", regs.single { it.codigo == "SI-106" }.cat)
        // Las «evaluaciones parciales» del detalle (ocultas) no se confunden con notas finales
        assertEquals(8, regs.size)
    }

    @Test fun actuacionProvisoria() {
        val regs = Lector.deTablas(pagina("actuacion_provisoria"))
        val r = regs.single()
        assertEquals("Bases de Datos", r.nombre)
        assertEquals("regular|7|2026-11-10|provisoria|cursada", "${r.cat}|${r.nota}|${r.fecha}|${r.fuente}|${r.tipo}")
        // La tabla de inscripciones no es una tabla de notas
        assertTrue(Lector.deTablas(pagina("mis_inscripciones")).isEmpty())
    }

    @Test fun reporteCompleto() {
        val regs = Analyzer.registros(TODAS)
        val ms = Lector.consolidar(regs)
        assertEquals("todas las actividades del plan, sin repetir", 10, ms.size)
        fun m(c: String) = ms.single { it.codigo == c }
        assertEquals("aprobada" to "8", m("SI-101").cat to m("SI-101").nota)          // gana el examen aprobado, no el desaprobado
        assertEquals("2026-02-20", m("SI-101").fecha)
        assertTrue(m("SI-101").detalle.contains("Examen aprobado con 8 (20/02/2026)"))
        assertTrue(m("SI-101").detalle.contains("Examen desaprobado con 2"))
        assertEquals("promocionada", m("SI-102").cat)
        assertEquals("regular" to "6", m("SI-103").cat to m("SI-103").nota)           // el plan decía pendiente; la historia, regular
        assertEquals("ausente", m("SI-104").cat)
        assertEquals("en_curso", m("SI-106").cat)
        assertEquals("pendiente", m("SI-201").cat)
        // La nota provisoria (sin código) se une a la materia del plan por el nombre
        assertEquals("regular" to "7", m("SI-202").cat to m("SI-202").nota)
        assertTrue(m("SI-202").provisoria)
        assertFalse(m("SI-101").provisoria)
        assertEquals("Seminario", m("SI-204").tipoAct)
        assertEquals(7.83, Lector.promedio(ms)!!, 0.001)                               // (8 + 8 + 7,5) / 3
    }

    @Test fun avisosDeCalificaciones() {
        val cal = Analyzer.calificaciones(Analyzer.registros(TODAS))
        val textos = cal.map { Lector.aviso(it.second) }
        assertTrue(textos.contains("Análisis Matemático I: examen aprobado con 8."))
        assertTrue(textos.contains("Análisis Matemático I: examen desaprobado con 2."))
        assertTrue(textos.contains("Programación I: promocionada con 8."))
        assertTrue(textos.contains("Álgebra y Geometría: cursada aprobada con 6 (queda el final)."))
        assertTrue(textos.contains("Bases de Datos: nota provisoria, cursada aprobada con 7 (queda el final)."))
        assertTrue(textos.contains("Sistemas y Organizaciones: ausente en la cursada."))
        // El plan y la historia informan la misma nota: un solo aviso
        assertEquals(1, textos.count { it.startsWith("Programación I:") })
        assertEquals(cal.size, cal.map { it.first }.distinct().size)
    }

    @Test fun examenesConFecha() {
        val ev = Lector.eventos(TODAS)
        assertEquals(2, ev.size)                                   // la inscripción dada de baja no cuenta
        val e = ev.first { it.materia == "Ética Profesional" }
        assertEquals("2027-05-27", e.fecha); assertNull(e.hora)    // viene de la tabla y del panel lateral: una sola vez
        val a = ev.first { it.materia == "Álgebra y Geometría" }
        assertEquals("2027-12-15" to "18:00", a.fecha to a.hora)
        assertEquals("Examen: Álgebra y Geometría", a.titulo)
        assertTrue(a.detalle.contains("Llamado: Llamado 01") && a.detalle.contains("Lugar: Aula 12"))
        assertEquals(listOf("2027-05-27", "2027-12-15"), ev.map { it.fecha })
    }

    @Test fun carreraSeleccionada() {
        val c = Lector.carrera(TODAS)!!
        assertEquals("Licenciatura en Sistemas de Información", c.nombre)
        assertEquals("Plan 2019", c.plan)
        assertEquals(listOf("Licenciatura en Sistemas de Información", "Tecnicatura en Programación"), c.todas)
        // Sin la página del plan, se toma del selector de arriba
        assertEquals("Licenciatura en Sistemas de Información", Lector.carrera(listOf(pagina("inicio")))!!.nombre)
    }

    @Test fun documentosOficiales() {
        val h = pagina("historia_academica").docs.single()
        assertEquals("pdf", h.tipo)
        assertEquals("https://guarani.ejemplo.edu.ar/autogestion/historia_academica/generar_pdf", h.url)
        val comprobantes = pagina("mis_inscripciones").docs
        assertEquals(4, comprobantes.size)
        assertTrue(comprobantes.all { it.url.isEmpty() && it.titulo == "Generar" })   // botones sin enlace propio
        assertTrue(comprobantes.any { it.contexto.contains("Álgebra y Geometría") })
    }

    @Test fun temasNuevos() {
        val b = "https://x.edu.ar/autogestion/"
        assertEquals("inscripciones", Analyzer.kindOf(b + "mis_inscripciones", "Mis inscripciones"))
        assertEquals("nota", Analyzer.kindOf(b + "actuacion_provisoria", "Actuación Provisoria"))
        assertEquals("examen", Analyzer.kindOf(b + "cronograma_evaluaciones", "Cronograma de evaluaciones"))
        assertEquals("inasistencia", Analyzer.kindOf(b + "inasistencias", "Inasistencias"))
        assertEquals("Historia académica", Analyzer.nombreDePagina(b + "historia_academica", "SIU Guaraní - Historia académica"))
        assertEquals("Plan de estudio", Analyzer.nombreDePagina(b + "plan_estudio", "SIU Guaraní"))
        assertEquals("Oferta comisiones", Analyzer.nombreDePagina(b + "oferta_comisiones", ""))
        assertEquals("Novedades en tus inscripciones", Analyzer.headline("inscripciones", "x", listOf("a"), emptyList(), false)!!.first)
    }
}
