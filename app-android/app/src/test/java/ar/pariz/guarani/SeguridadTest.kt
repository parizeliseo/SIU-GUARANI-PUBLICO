package ar.pariz.guarani

import android.app.PendingIntent
import android.content.Context
import android.net.Uri
import android.webkit.WebView
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf

@RunWith(RobolectricTestRunner::class)
class SeguridadTest {
    private val c: Context get() = ApplicationProvider.getApplicationContext()

    @Test fun tokenAleatorioYEstable() {
        val t = Seguridad.token(c)
        assertEquals(32, t.length)
        assertEquals(t, Seguridad.token(c))
        c.getSharedPreferences("seguridad", Context.MODE_PRIVATE).edit().clear().commit()
        assertNotEquals(t, Seguridad.token(c))
    }

    @Test fun soloHttps() {
        assertTrue(Seguridad.esHttps("https://guarani.ejemplo.edu.ar/autogestion/"))
        assertTrue(Seguridad.esHttps("HTTPS://x.edu.ar/"))
        assertFalse(Seguridad.esHttps("http://guarani.ejemplo.edu.ar/autogestion/"))
        assertFalse(Seguridad.esHttps("javascript:alert(1)"))
        assertFalse(Seguridad.esHttps(null))
    }

    @Test fun enlacesExternosLimitados() {
        for (u in listOf("mailto:alumnos@ejemplo.edu.ar", "tel:+5411", "geo:0,0")) assertTrue(u, Seguridad.externoPermitido(Uri.parse(u)))
        for (u in listOf("intent://x#Intent;end", "file:///sdcard/a", "content://x/y", "javascript:alert(1)", "market://details?id=x"))
            assertFalse(u, Seguridad.externoPermitido(Uri.parse(u)))
    }

    @Test fun visoresSinAccesoAArchivos() {
        val w = WebView(c); Seguridad.endurecer(w.settings)
        assertFalse(w.settings.allowFileAccess)
        assertFalse(w.settings.allowContentAccess)
    }

    /** Los botones de las notificaciones llevan la clave de la instalación, así la app los reconoce como propios. */
    @Test fun notificacionesFirmadas() {
        shadowOf(ApplicationProvider.getApplicationContext<android.app.Application>())
            .grantPermissions(android.Manifest.permission.POST_NOTIFICATIONS)
        Notifier.createChannels(c)
        Notifier.notify(c, Notifier.CH_NOTAS, "Nota nueva", "Física I: 8", pid = "p1",
            acciones = listOf(Notifier.Accion("Compartir", "compartir", "Física I: 8")))
        val nm = c.getSystemService(android.app.NotificationManager::class.java)
        val n = shadowOf(nm).allNotifications.single()
        for (pi in listOf(n.contentIntent) + n.actions.map { it.actionIntent }) {
            val i = shadowOf(pi as PendingIntent).savedIntent
            assertTrue(Seguridad.esPropio(c, i))
        }
    }
}
