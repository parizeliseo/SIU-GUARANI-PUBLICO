package ar.pariz.guarani

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.io.File
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Guarda usuario y contraseña cifrados con AES-256-GCM. La clave vive en el Android Keystore del
 * teléfono (no se puede sacar del aparato) y se crea una sola vez.
 *
 * Reemplaza a EncryptedSharedPreferences, que Google declaró obsoleto. Lo guardado por versiones
 * anteriores se pasa solo a este formato la primera vez que se lee.
 */
object Boveda {
    private const val ALIAS = "guarani_credenciales_v2"
    private const val PREFS = "cred2"

    /** De dónde sale la clave. En las pruebas se reemplaza por una clave común (Robolectric no tiene Keystore). */
    @Volatile internal var claveDe: (Context) -> SecretKey = ::claveKeystore
    @Volatile private var clave: SecretKey? = null

    private fun claveKeystore(c: Context): SecretKey {
        val ks = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (ks.getEntry(ALIAS, null) as? KeyStore.SecretKeyEntry)?.let { return it.secretKey }
        val g = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        g.init(KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(256)
            .build())
        return g.generateKey()
    }

    @Synchronized
    private fun key(c: Context): SecretKey = clave ?: claveDe(c.applicationContext).also { clave = it }

    /** Para las pruebas: olvida la clave en memoria. */
    internal fun reiniciar() { clave = null }

    private fun p(c: Context) = c.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun cifrar(c: Context, texto: String): String {
        val ci = Cipher.getInstance("AES/GCM/NoPadding")
        ci.init(Cipher.ENCRYPT_MODE, key(c))
        val out = ci.iv + ci.doFinal(texto.toByteArray(Charsets.UTF_8))
        return Base64.encodeToString(out, Base64.NO_WRAP)
    }

    fun descifrar(c: Context, b64: String): String? = runCatching {
        val all = Base64.decode(b64, Base64.NO_WRAP)
        val ci = Cipher.getInstance("AES/GCM/NoPadding")
        ci.init(Cipher.DECRYPT_MODE, key(c), GCMParameterSpec(128, all, 0, 12))
        String(ci.doFinal(all, 12, all.size - 12), Charsets.UTF_8)
    }.getOrNull()

    fun get(c: Context, nombre: String): String {
        p(c).getString(nombre, null)?.let { return descifrar(c, it) ?: "" }
        // ¿Lo guardó una versión anterior? Se pasa al formato nuevo y se borra de allá.
        val viejo = Legado.leer(c, nombre) ?: return ""
        put(c, nombre, viejo); Legado.borrar(c, nombre)
        return viejo
    }

    fun put(c: Context, nombre: String, valor: String) {
        runCatching { p(c).edit().putString(nombre, cifrar(c, valor)).apply() }
    }

    fun remove(c: Context, vararg nombres: String) {
        val e = p(c).edit(); nombres.forEach { e.remove(it) }; e.apply()
        nombres.forEach { Legado.borrar(c, it) }
    }

    /** Lectura de lo guardado con EncryptedSharedPreferences (versiones 1.0 a 1.5). */
    private object Legado {
        @Suppress("DEPRECATION")
        private fun prefs(c: Context) = runCatching {
            if (!File(c.applicationInfo.dataDir, "shared_prefs/cred.xml").exists()) return@runCatching null
            val k = androidx.security.crypto.MasterKey.Builder(c).setKeyScheme(androidx.security.crypto.MasterKey.KeyScheme.AES256_GCM).build()
            androidx.security.crypto.EncryptedSharedPreferences.create(c, "cred", k,
                androidx.security.crypto.EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                androidx.security.crypto.EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM)
        }.getOrNull()

        fun leer(c: Context, nombre: String): String? = runCatching { prefs(c)?.getString(nombre, null) }.getOrNull()
        fun borrar(c: Context, nombre: String) { runCatching { prefs(c)?.edit()?.remove(nombre)?.apply() } }
    }
}
