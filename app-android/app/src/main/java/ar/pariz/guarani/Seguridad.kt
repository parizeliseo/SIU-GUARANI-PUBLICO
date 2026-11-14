package ar.pariz.guarani

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.webkit.WebSettings
import java.security.SecureRandom

/**
 * Reglas de seguridad de la app, juntas en un solo lugar:
 * - la contraseña solo se escribe en páginas https;
 * - las órdenes que llegan con un Intent (botones de las notificaciones) solo valen si las armó esta app;
 * - los visores web no pueden leer archivos del teléfono;
 * - desde Guaraní solo se abren afuera enlaces de correo, teléfono, SMS y mapas.
 */
object Seguridad {
    private const val PREFS = "seguridad"
    const val EXTRA_TOKEN = "tok"

    /** Clave aleatoria de esta instalación. Va en los avisos que arma la app; otra app no la conoce. */
    @Synchronized
    fun token(c: Context): String {
        val p = c.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        p.getString("token", null)?.let { return it }
        val b = ByteArray(16).also { SecureRandom().nextBytes(it) }
        val t = b.joinToString("") { "%02x".format(it) }
        p.edit().putString("token", t).apply()
        return t
    }

    /** ¿Este Intent lo armó la app (por ejemplo, un botón de una notificación)? */
    fun esPropio(c: Context, i: Intent?): Boolean {
        val t = i?.getStringExtra(EXTRA_TOKEN) ?: return false
        return t.isNotEmpty() && java.security.MessageDigest.isEqual(t.toByteArray(), token(c).toByteArray())
    }

    fun firmar(c: Context, i: Intent): Intent = i.putExtra(EXTRA_TOKEN, token(c))

    /** Solo se completa o se captura la contraseña en páginas cifradas. */
    fun esHttps(url: String?): Boolean = url != null && Uri.parse(url).scheme.equals("https", true)

    private val EXTERNOS = setOf("mailto", "tel", "sms", "smsto", "geo")
    /** Enlaces de Guaraní que se pueden abrir con otra app del teléfono. */
    fun externoPermitido(u: Uri): Boolean = u.scheme?.lowercase() in EXTERNOS

    /** Ajustes comunes a todos los visores web de la app. */
    fun endurecer(s: WebSettings) {
        s.allowFileAccess = false
        s.allowContentAccess = false
        @Suppress("DEPRECATION")
        run { s.allowFileAccessFromFileURLs = false; s.allowUniversalAccessFromFileURLs = false }
        s.mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
        if (Build.VERSION.SDK_INT >= 26) s.safeBrowsingEnabled = true
    }
}
