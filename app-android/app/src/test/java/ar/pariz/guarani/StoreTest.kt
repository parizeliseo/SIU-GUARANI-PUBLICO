package ar.pariz.guarani

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File

@RunWith(RobolectricTestRunner::class)
class StoreTest {
    private val c: Context get() = ApplicationProvider.getApplicationContext()

    /** Quien actualiza desde la 1.5 o anterior conserva sus datos: pasan de preferencias a archivos. */
    @Test fun pasaLosDatosViejosAArchivos() {
        val viejo = """[{"materia":"Física I","nota":"8"}]"""
        c.getSharedPreferences("store", Context.MODE_PRIVATE).edit().putString("ds_c1_materias", viejo).commit()
        assertEquals(viejo, Store.get(c, "c1", "materias"))
        assertTrue(File(c.filesDir, "datos/c1/materias.json").readText() == viejo)
        assertFalse(c.getSharedPreferences("store", Context.MODE_PRIVATE).contains("ds_c1_materias"))
    }

    @Test fun guardaLeeYBorra() {
        assertEquals("[]", Store.get(c, "c2", "calendario"))
        Store.put(c, "c2", "calendario", JSONArray().put(JSONObject().put("fecha", "2026-12-10")))
        assertEquals("2026-12-10", Store.arr(c, "c2", "calendario").getJSONObject(0).getString("fecha"))
        assertTrue(File(c.filesDir, "datos/c2/calendario.json").exists())
        Store.deleteAll(c, "c2")
        assertFalse(File(c.filesDir, "datos/c2").exists())
        assertEquals("[]", Store.get(c, "c2", "calendario"))
    }

    /** El historial de novedades guarda hasta 600 entradas, la más nueva primero. */
    @Test fun historialAmplio() {
        repeat(Store.MAX_NOVEDADES + 25) { Store.addNovedad(c, "c3", "Cambio", "n$it") }
        val n = Store.arr(c, "c3", "novedades")
        assertEquals(Store.MAX_NOVEDADES, n.length())
        assertEquals("n${Store.MAX_NOVEDADES + 24}", n.getJSONObject(0).getString("detalle"))
    }

    /** Las cuentas se revisan empezando por la que hace más tiempo que no se revisa. */
    @Test fun turnoDeRevision() {
        Store.setUltimaRevision(c, "a", 3000); Store.setUltimaRevision(c, "b", 1000)
        assertEquals(listOf("x", "b", "a"), listOf("a", "b", "x").sortedBy { Store.ultimaRevision(c, it) })
    }
}
