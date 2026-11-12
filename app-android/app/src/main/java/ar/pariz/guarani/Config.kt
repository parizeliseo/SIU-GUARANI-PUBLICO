package ar.pariz.guarani

import android.content.Context

/** Ajustes generales (valen para todas las cuentas). */
object Config {
    val INTERVALS = listOf(15, 30, 60, 120, 360)
    val INTERVAL_LABELS = listOf("15 minutos", "30 minutos", "1 hora", "2 horas", "6 horas")

    private fun p(c: Context) = c.getSharedPreferences("config", Context.MODE_PRIVATE)

    fun interval(c: Context): Int = p(c).getInt("interval", 30)
    fun notifyAll(c: Context): Boolean = p(c).getBoolean("all", true)
    fun wifiOnly(c: Context): Boolean = p(c).getBoolean("wifi", false)
    /** Tocar Guaraní cada pocos minutos para que la sesión no venza. */
    fun keepAlive(c: Context): Boolean = p(c).getBoolean("keepalive", true)
    /** Vigilancia permanente con notificación fija (para teléfonos que cierran las apps). */
    fun fixed(c: Context): Boolean = p(c).getBoolean("fixed", false)
    const val KEEPALIVE_MIN = 8L

    fun configured(c: Context): Boolean = Profiles.all(c).isNotEmpty()

    fun saveGeneral(c: Context, interval: Int, all: Boolean, wifi: Boolean, keepAlive: Boolean, fixed: Boolean) {
        p(c).edit().putInt("interval", interval).putBoolean("all", all).putBoolean("wifi", wifi)
            .putBoolean("keepalive", keepAlive).putBoolean("fixed", fixed).apply()
    }

    // ----- Lo que guardaba la versión de una sola cuenta (para migrar) -----
    fun legacyUrl(c: Context): String? = if (p(c).getBoolean("configured", false)) p(c).getString("url", null) else null
    fun clearLegacy(c: Context) { p(c).edit().remove("url").remove("configured").apply() }
}
