package ar.pariz.guarani

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import javax.crypto.KeyGenerator

/** Usuario y contraseña cifrados (AES-256-GCM). Robolectric no tiene Keystore: se usa una clave común. */
@RunWith(RobolectricTestRunner::class)
class BovedaTest {
    private val c: Context get() = ApplicationProvider.getApplicationContext()

    @Before fun clave() {
        Boveda.reiniciar()
        Boveda.claveDe = { KeyGenerator.getInstance("AES").apply { init(256) }.generateKey() }
    }
    @After fun fin() = Boveda.reiniciar()

    @Test fun cifraYDescifra() {
        val a = Boveda.cifrar(c, "secreta ñandú 123"); val b = Boveda.cifrar(c, "secreta ñandú 123")
        assertNotEquals("cada cifrado usa un vector distinto", a, b)
        assertFalse(a.contains("secreta"))
        assertEquals("secreta ñandú 123", Boveda.descifrar(c, a))
        assertNull("un dato alterado no se acepta", Boveda.descifrar(c, a.dropLast(4) + "AAAA"))
    }

    @Test fun nadaEnTextoPlano() {
        val prof = Profile("b1", "UNDEF", "UNDEF", "https://autogestion.ejemplo.edu.ar/3w/", true)
        Profiles.save(c, prof, "30123456", "mi-clave")
        assertEquals("30123456", Profiles.user(c, "b1"))
        assertEquals("mi-clave", Profiles.pass(c, "b1"))
        val xml = java.io.File(c.applicationInfo.dataDir, "shared_prefs").listFiles()!!.joinToString { it.readText() }
        assertFalse(xml.contains("mi-clave")); assertFalse(xml.contains("30123456"))
    }

    @Test fun sinGuardarContraseñaYBorrado() {
        val prof = Profile("b2", "X", "X", "https://g.ejemplo.edu.ar/", true)
        Profiles.save(c, prof, "u", "p1")
        Profiles.save(c, prof.copy(savePass = false), "u", null)
        assertEquals("", Profiles.pass(c, "b2"))
        Profiles.delete(c, "b2")
        assertEquals("", Profiles.user(c, "b2"))
    }
}
