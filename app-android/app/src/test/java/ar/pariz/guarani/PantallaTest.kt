package ar.pariz.guarani

import android.os.Looper
import android.widget.EditText
import android.widget.Spinner
import androidx.test.core.app.ApplicationProvider
import androidx.work.Configuration
import androidx.work.testing.WorkManagerTestInitHelper
import com.google.android.material.bottomnavigation.BottomNavigationView
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config as RConfig
import java.util.concurrent.TimeUnit

/** Abre la app como en un teléfono recién instalado y comprueba que se pueda escribir en Ajustes. */
@RunWith(RobolectricTestRunner::class)
@RConfig(sdk = [34])
class PantallaTest {
    @Before fun prep() {
        WorkManagerTestInitHelper.initializeTestWorkManager(ApplicationProvider.getApplicationContext(), Configuration.Builder().build())
    }

    private fun idle() { repeat(6) { shadowOf(Looper.getMainLooper()).idleFor(300, TimeUnit.MILLISECONDS) } }

    /** Simula varios redibujados de pantalla, como hace un teléfono real en cada cuadro. */
    private fun frames(act: MainActivity) {
        val root = act.window.decorView
        repeat(5) {
            root.measure(android.view.View.MeasureSpec.makeMeasureSpec(1080, android.view.View.MeasureSpec.EXACTLY),
                android.view.View.MeasureSpec.makeMeasureSpec(2200, android.view.View.MeasureSpec.EXACTLY))
            root.layout(0, 0, 1080, 2200)
            idle()
        }
    }

    @Test(timeout = 120_000) fun seEscribeEnAjustes() {
        val act = Robolectric.buildActivity(MainActivity::class.java).setup().get()
        frames(act)
        assertEquals(R.id.nav_set, act.findViewById<BottomNavigationView>(R.id.nav).selectedItemId)
        val url = act.findViewById<EditText>(R.id.setUrl)
        val user = act.findViewById<EditText>(R.id.setUser)
        url.setText("https://siu.iugna.edu.ar/autogestion/")
        user.setText("12345678")
        frames(act)
        assertEquals("lo escrito en Dirección se borró", "https://siu.iugna.edu.ar/autogestion/", url.text.toString())
        assertEquals("lo escrito en Usuario se borró", "12345678", user.text.toString())
        // Cambiar de pestaña y volver no debe trabar la pantalla
        act.findViewById<Spinner>(R.id.accSpinner).setSelection(0)
        frames(act)
        user.setText("87654321"); frames(act)
        assertEquals("87654321", user.text.toString())
    }

    private fun conCuenta(): Profile {
        val c = ApplicationProvider.getApplicationContext<android.content.Context>()
        val p = Profile("t9", "UTN Sistemas", "Universidad de Ejemplo", "https://guarani.ejemplo.edu.ar/autogestion/", true, "Ingeniería en Sistemas")
        Profiles.save(c, p, "30123456", "secreta"); Profiles.setActive(c, p.id)
        return p
    }

    @Test(timeout = 120_000) fun agendarDesdeLaNotificacion() {
        conCuenta()
        val ev = org.json.JSONObject().put("evento", "Examen: Álgebra").put("fecha", "2027-12-15").put("hora", "18:00").put("detalle", "Lugar: Aula 12")
        val i = android.content.Intent(ApplicationProvider.getApplicationContext(), MainActivity::class.java)
            .putExtra("hacer", "agendar").putExtra("datos", ev.toString())
        val act = Robolectric.buildActivity(MainActivity::class.java, i).setup().get()
        val abierto = shadowOf(act).nextStartedActivity
        assertEquals(android.content.Intent.ACTION_INSERT, abierto.action)
        assertEquals(android.provider.CalendarContract.Events.CONTENT_URI, abierto.data)
        assertEquals("Examen: Álgebra", abierto.getStringExtra(android.provider.CalendarContract.Events.TITLE))
        val esperado = java.time.LocalDateTime.of(2027, 12, 15, 18, 0).atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli()
        assertEquals(esperado, abierto.getLongExtra(android.provider.CalendarContract.EXTRA_EVENT_BEGIN_TIME, 0))
        assertEquals(esperado + 2 * 3600_000L, abierto.getLongExtra(android.provider.CalendarContract.EXTRA_EVENT_END_TIME, 0))
        val desc = abierto.getStringExtra(android.provider.CalendarContract.Events.DESCRIPTION)!!
        assertTrue(desc.contains("Lugar: Aula 12") && desc.contains("Universidad de Ejemplo · Ingeniería en Sistemas"))
    }

    @Test(timeout = 120_000) fun examenSinHoraEsDeTodoElDia() {
        val c = ApplicationProvider.getApplicationContext<android.app.Application>()
        assertTrue(Compartir.agendar(c, "Examen: Ética", "2027-05-27", null, ""))
        val abierto = shadowOf(c).nextStartedActivity
        assertTrue(abierto.getBooleanExtra(android.provider.CalendarContract.EXTRA_EVENT_ALL_DAY, false))
        val ini = java.time.LocalDate.of(2027, 5, 27).atStartOfDay(java.time.ZoneOffset.UTC).toInstant().toEpochMilli()
        assertEquals(ini, abierto.getLongExtra(android.provider.CalendarContract.EXTRA_EVENT_BEGIN_TIME, 0))
    }

    @Test(timeout = 120_000) fun compartirDesdeLaNotificacion() {
        conCuenta()
        val i = android.content.Intent(ApplicationProvider.getApplicationContext(), MainActivity::class.java)
            .putExtra("hacer", "compartir").putExtra("datos", "Física I: examen aprobado con 8.")
        val act = Robolectric.buildActivity(MainActivity::class.java, i).setup().get()
        val selector = shadowOf(act).nextStartedActivity
        assertEquals(android.content.Intent.ACTION_CHOOSER, selector.action)
        @Suppress("DEPRECATION")
        val envio = selector.getParcelableExtra<android.content.Intent>(android.content.Intent.EXTRA_INTENT)!!
        assertEquals("text/plain", envio.type)
        assertEquals("Universidad de Ejemplo · Ingeniería en Sistemas\nFísica I: examen aprobado con 8.", envio.getStringExtra(android.content.Intent.EXTRA_TEXT))
    }

    @Test(timeout = 120_000) fun carreraEnAjustesYEnLaBarra() {
        val p = conCuenta()
        val act = Robolectric.buildActivity(MainActivity::class.java).setup().get()
        frames(act)
        act.findViewById<BottomNavigationView>(R.id.nav).selectedItemId = R.id.nav_set; frames(act)
        assertEquals("Ingeniería en Sistemas", act.findViewById<EditText>(R.id.setCarrera).text.toString())
        act.findViewById<BottomNavigationView>(R.id.nav).selectedItemId = R.id.nav_web; frames(act)
        assertEquals("Universidad de Ejemplo · Ingeniería en Sistemas", act.findViewById<android.widget.TextView>(R.id.webSub).text.toString())
        // Sin carrera escrita a mano, se usa la que la app leyó de Guaraní
        val c = ApplicationProvider.getApplicationContext<android.content.Context>()
        Profiles.save(c, p.copy(carrera = ""), null, null)
        Store.putResumen(c, p.id, Store.resumen(c, p.id).put("propuesta", "Licenciatura leída de Guaraní"))
        assertEquals("Licenciatura leída de Guaraní", Profiles.carreraDe(c, Profiles.get(c, p.id)!!))
    }
}
