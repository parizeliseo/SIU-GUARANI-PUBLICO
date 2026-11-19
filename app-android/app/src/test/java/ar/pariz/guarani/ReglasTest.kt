package ar.pariz.guarani

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ReglasTest {
    private fun fuera(t: String) = Scraper.FUERA_DE_HORARIO.containsMatchIn(Lector.norm(t))

    /** Cartel real de la UNRN de noche: la app lo toma como «fuera de horario», no como un cambio. */
    @Test fun carteleDeHorario() {
        assertTrue(fuera("Disponibilidad horaria de sistemas SISTEMA GUARANI Autogestión, Gestión y Preinscripción Se informa que el sistema " +
            "se encuentra disponible para su uso todos los días en el horario de 07:00 a 21:30 hs. Fuera de este rango horario, el acceso al sistema no estará habilitado."))
        assertTrue(fuera("El sistema se encuentra en mantenimiento"))
        assertFalse(fuera("Acceso Fechas de examen Horarios de cursadas Validador de certificados Ayuda Ingresá tus datos Usuario Contraseña"))
        assertFalse(fuera("Horarios de cursadas Turno mañana 8 a 12 hs"))
    }
}
