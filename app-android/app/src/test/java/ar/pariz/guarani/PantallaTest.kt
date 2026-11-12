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
}
