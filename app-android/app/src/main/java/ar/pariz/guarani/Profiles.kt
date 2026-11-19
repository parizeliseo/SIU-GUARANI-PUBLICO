package ar.pariz.guarani

import android.content.Context
import android.net.Uri
import android.webkit.CookieManager
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

/** Una cuenta de SIU Guaraní: una institución (o facultad) y un usuario. */
data class Profile(
    val id: String,
    val nombre: String,
    val institucion: String,
    val url: String,
    val savePass: Boolean,
    /** Carrera escrita a mano en Ajustes. Vacía = se usa la que la app lee de Guaraní. */
    val carrera: String = ""
) {
    val host: String get() = Uri.parse(url).host ?: ""
    fun json(): JSONObject = JSONObject().put("id", id).put("nombre", nombre).put("institucion", institucion)
        .put("url", url).put("savePass", savePass).put("carrera", carrera)
    companion object {
        fun from(o: JSONObject) = Profile(o.getString("id"), o.optString("nombre"), o.optString("institucion"),
            o.optString("url"), o.optBoolean("savePass", true), o.optString("carrera"))
    }
}

object Profiles {
    private fun p(c: Context) = c.getSharedPreferences("profiles", Context.MODE_PRIVATE)

    // Usuario y contraseña van cifrados con una clave del Android Keystore, nunca en texto plano (ver Boveda).

    fun normalizeUrl(url: String): String {
        var u = url.trim()
        if (u.isEmpty()) return u
        if (!u.startsWith("http")) u = "https://$u"
        // Si pegaron la dirección del formulario de ingreso, quedarse con la base.
        u = u.substringBefore('#').substringBefore('?')
        u = u.replace(Regex("/acceso(/.*)?$"), "/")
        if (!u.endsWith("/")) u += "/"
        return u
    }

    @Synchronized
    fun all(c: Context): List<Profile> {
        migrate(c)
        val a = runCatching { JSONArray(p(c).getString("list", "[]")) }.getOrDefault(JSONArray())
        return (0 until a.length()).map { Profile.from(a.getJSONObject(it)) }
    }

    fun get(c: Context, id: String?): Profile? = all(c).firstOrNull { it.id == id }

    /** Carrera de la cuenta: la escrita en Ajustes o, si no, la propuesta que la app leyó de Guaraní. */
    fun carreraDe(c: Context, p: Profile): String =
        p.carrera.ifBlank { Store.resumen(c, p.id).optString("propuesta") }

    fun active(c: Context): Profile? {
        val l = all(c)
        return l.firstOrNull { it.id == p(c).getString("active", null) } ?: l.firstOrNull()
    }

    fun setActive(c: Context, id: String) { p(c).edit().putString("active", id).apply() }

    @Synchronized
    fun save(c: Context, prof: Profile, user: String?, pass: String?) {
        val l = all(c).toMutableList()
        val i = l.indexOfFirst { it.id == prof.id }
        if (i >= 0) l[i] = prof else l.add(prof)
        p(c).edit().putString("list", JSONArray(l.map { it.json() }).toString()).apply()
        if (user != null) Boveda.put(c, "u_${prof.id}", user.trim())
        if (!prof.savePass) Boveda.remove(c, "p_${prof.id}") else if (pass != null) Boveda.put(c, "p_${prof.id}", pass)
        Store.ensureSeed(c, prof.id)
    }

    @Synchronized
    fun delete(c: Context, id: String) {
        val l = all(c).filter { it.id != id }
        p(c).edit().putString("list", JSONArray(l.map { it.json() }).toString()).apply()
        Boveda.remove(c, "u_$id", "p_$id")
        Store.deleteAll(c, id)
        if (p(c).getString("active", null) == id) l.firstOrNull()?.let { setActive(c, it.id) }
    }

    fun user(c: Context, id: String): String = Boveda.get(c, "u_$id")
    fun pass(c: Context, id: String): String {
        val prof = get(c, id) ?: return ""
        return if (prof.savePass) Boveda.get(c, "p_$id") else ""
    }
    fun savePassword(c: Context, id: String, user: String, pass: String) {
        val prof = get(c, id) ?: return
        save(c, prof.copy(savePass = true), user, pass)
    }

    fun newId(): String = "c" + UUID.randomUUID().toString().take(8)

    // ----- Sesiones: dos cuentas del mismo Guaraní no pueden estar abiertas a la vez -----
    /** Deja las cookies del sitio listas para [prof]: si eran de otra cuenta del mismo sitio, las borra. */
    fun useSessionOf(c: Context, prof: Profile): Boolean {
        val key = "owner_${prof.host}"
        val owner = p(c).getString(key, null)
        var cleared = false
        if (owner != null && owner != prof.id && all(c).any { it.id == owner && it.host == prof.host }) {
            clearCookies(prof.url); cleared = true
        }
        p(c).edit().putString(key, prof.id).apply()
        return cleared
    }

    /** ¿Las cookies del sitio son de esta cuenta? (falso si la última sesión abierta ahí fue de otra) */
    fun ownsSession(c: Context, prof: Profile): Boolean {
        val owner = p(c).getString("owner_${prof.host}", null)
        return owner == null || owner == prof.id
    }

    fun clearCookies(url: String) {
        val cm = CookieManager.getInstance()
        val raw = cm.getCookie(url) ?: return
        val path = Uri.parse(url).path?.trimEnd('/')?.ifEmpty { "/" } ?: "/"
        raw.split(";").map { it.substringBefore('=').trim() }.filter { it.isNotEmpty() }.forEach { name ->
            for (pth in setOf("/", path)) cm.setCookie(url, "$name=; Expires=Thu, 01 Jan 1970 00:00:00 GMT; Path=$pth")
        }
        cm.flush()
    }

    /** Pasa la cuenta única de las versiones 1.0/1.1 a la primera cuenta. */
    private var migrated = false
    private fun migrate(c: Context) {
        if (migrated) return
        migrated = true
        if (p(c).contains("list")) return
        val url = Config.legacyUrl(c) ?: run { p(c).edit().putString("list", "[]").apply(); return }
        val u = Boveda.get(c, "u")
        val pw = Boveda.get(c, "p")
        val id = "c1"
        val host = Uri.parse(url).host ?: url
        val inst = Instituciones.nameFor(c, url) ?: host
        val prof = Profile(id, inst, inst, normalizeUrl(url), true)
        p(c).edit().putString("list", JSONArray().put(prof.json()).toString()).putString("active", id).apply()
        Boveda.put(c, "u_$id", u); Boveda.put(c, "p_$id", pw); Boveda.remove(c, "u", "p")
        Store.migrateLegacy(c, id)
        Store.ensureSeed(c, id)
        Config.clearLegacy(c)
    }
}
