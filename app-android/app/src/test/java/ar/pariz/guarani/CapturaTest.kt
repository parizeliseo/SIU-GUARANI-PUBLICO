package ar.pariz.guarani

import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.Looper
import android.view.View
import androidx.test.core.app.ApplicationProvider
import androidx.work.Configuration
import androidx.work.testing.WorkManagerTestInitHelper
import com.google.android.material.bottomnavigation.BottomNavigationView
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config as RConfig
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.util.concurrent.TimeUnit

/** Saca capturas de las pantallas nativas (solo si se pide con -Dcapturas=carpeta). */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@RConfig(sdk = [34], qualifiers = "w411dp-h891dp-xxhdpi")
class CapturaTest {
    private val dir = System.getProperty("capturas")?.let { File(it) }

    @Before fun prep() {
        WorkManagerTestInitHelper.initializeTestWorkManager(ApplicationProvider.getApplicationContext(), Configuration.Builder().build())
    }
    private fun idle() { repeat(4) { shadowOf(Looper.getMainLooper()).idleFor(300, TimeUnit.MILLISECONDS) } }

    private fun shot(v: View, name: String, h: Int = 0) {
        val d = dir ?: return
        d.mkdirs()
        val w = v.width; val hh = if (h > 0) h else v.height
        val bmp = Bitmap.createBitmap(w, hh, Bitmap.Config.ARGB_8888)
        v.draw(Canvas(bmp))
        File(d, "$name.png").outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    private fun fullHeight(v: View, act: android.app.Activity, name: String) {
        // Mide la pantalla de ajustes completa (no solo lo visible) para ver todo el contenido.
        val sv = v as android.widget.ScrollView
        val child = sv.getChildAt(0)
        val w = v.width
        child.measure(View.MeasureSpec.makeMeasureSpec(w, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED))
        child.layout(0, 0, w, child.measuredHeight)
        val d = dir ?: return
        val bmp = Bitmap.createBitmap(w, child.measuredHeight, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp); c.drawColor(act.getColor(R.color.bg)); child.draw(c)
        File(d.apply { mkdirs() }, "$name.png").outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    @Test fun pantallas() {
        if (dir == null) return
        val c = ApplicationProvider.getApplicationContext<android.content.Context>()
        // Sin cuentas: Ajustes vacío
        val a0 = Robolectric.buildActivity(MainActivity::class.java).setup().visible().get(); idle()
        fullHeight(a0.findViewById(R.id.setPane), a0, "ajustes-nuevo")
        shot(a0.window.decorView, "pantalla-ajustes")
        // Con una cuenta
        val p = Profile("t9", "UTN Sistemas", "UTN — Facultad Regional Buenos Aires (grado)", "https://guarani.frba.utn.edu.ar/autogestion/grado/", true)
        Profiles.save(c, p, "30123456", "secreta"); Profiles.setActive(c, p.id)
        Store.putResumen(c, p.id, Store.resumen(c, p.id).put("propuesta", "Ingeniería en Sistemas de Información"))
        Store.setStatus(c, p.id, "14/11 08:40 · 14 páginas revisadas · sin cambios")
        val a1 = Robolectric.buildActivity(MainActivity::class.java).setup().visible().get(); idle()
        a1.findViewById<BottomNavigationView>(R.id.nav).selectedItemId = R.id.nav_set; idle()
        fullHeight(a1.findViewById(R.id.setPane), a1, "ajustes-cuenta")
        a1.findViewById<BottomNavigationView>(R.id.nav).selectedItemId = R.id.nav_web; idle()
        shot(a1.window.decorView, "pantalla-guarani")
    }
}
