package ar.pariz.guarani

import android.Manifest
import android.annotation.SuppressLint
import android.app.DownloadManager
import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.os.PowerManager
import android.print.PrintAttributes
import android.print.PrintManager
import android.provider.Settings
import android.view.View
import android.webkit.*
import android.widget.*
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import androidx.work.WorkInfo
import androidx.work.WorkManager
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.android.material.materialswitch.MaterialSwitch
import org.json.JSONArray
import org.json.JSONObject

class MainActivity : AppCompatActivity() {

    private lateinit var dashWeb: WebView
    private lateinit var dashSwipe: SwipeRefreshLayout
    private lateinit var gWeb: WebView
    private lateinit var menuWeb: WebView
    private lateinit var webPane: View
    private lateinit var setPane: View
    private lateinit var nav: BottomNavigationView
    private var gLoadedFor: String? = null        // cuenta cargada en la pestaña Guaraní
    private var lastAutoLogin = 0L
    private var askingSave = false
    private var ssoUntil = 0L                    // venimos del botón de ingreso único de la cuenta
    private var editingId: String? = null         // cuenta que se edita en Ajustes (null = nueva)
    private var fileCb: ValueCallback<Array<Uri>>? = null

    private val pickFile = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { r ->
        fileCb?.onReceiveValue(WebChromeClient.FileChooserParams.parseResult(r.resultCode, r.data))
        fileCb = null
    }
    private val notifPerm = registerForActivityResult(ActivityResultContracts.RequestPermission()) {}

    /** Descargas pedidas desde la pestaña Guaraní: id → nombre de archivo. */
    private val descargas = HashMap<Long, String>()
    private val alTerminarDescarga = object : BroadcastReceiver() {
        override fun onReceive(c: Context, i: Intent) {
            val id = i.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1)
            val nombre = descargas.remove(id) ?: return
            descargaLista(id, nombre)
        }
    }

    private fun cur(): Profile? = Profiles.active(this)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        val root = findViewById<View>(R.id.root)
        androidx.core.view.ViewCompat.setOnApplyWindowInsetsListener(root) { v, insets ->
            val b = insets.getInsets(androidx.core.view.WindowInsetsCompat.Type.systemBars() or androidx.core.view.WindowInsetsCompat.Type.ime())
            v.setPadding(b.left, b.top, b.right, b.bottom)
            androidx.core.view.WindowInsetsCompat.CONSUMED
        }
        dashWeb = findViewById(R.id.dashWeb)
        dashSwipe = findViewById(R.id.dashSwipe)
        gWeb = findViewById(R.id.guaraniWeb)
        menuWeb = findViewById(R.id.menuWeb)
        webPane = findViewById(R.id.webPane)
        setPane = findViewById(R.id.setPane)
        nav = findViewById(R.id.nav)

        editingId = cur()?.id
        setupDashboard()
        setupGuarani()
        setupMenu()
        setupSettings()

        nav.setOnItemSelectedListener { show(it.itemId); true }
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                when {
                    nav.selectedItemId == R.id.nav_web && gWeb.canGoBack() -> gWeb.goBack()
                    nav.selectedItemId != R.id.nav_dash -> nav.selectedItemId = R.id.nav_dash
                    else -> finish()
                }
            }
        })

        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            notifPerm.launch(Manifest.permission.POST_NOTIFICATIONS)
        }

        WorkManager.getInstance(this).getWorkInfosByTagLiveData(CheckWorker.TAG).observe(this) { list ->
            dashSwipe.isRefreshing = list.any { it.state == WorkInfo.State.RUNNING }
            refreshPages()
            updateStatus()
        }

        ContextCompat.registerReceiver(this, alTerminarDescarga, IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE),
            ContextCompat.RECEIVER_EXPORTED)

        if (!Config.configured(this)) { editingId = null; nav.selectedItemId = R.id.nav_set }
        handleIntent(intent)
    }

    override fun onDestroy() {
        runCatching { unregisterReceiver(alTerminarDescarga) }
        super.onDestroy()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    override fun onResume() {
        super.onResume()
        refreshPages()
        updateStatus()
        actualizarSiHaceFalta()
    }

    /** Al abrir la app, si la última lectura de Guaraní tiene más de 10 minutos, se actualiza sola. */
    private fun actualizarSiHaceFalta() {
        val p = cur() ?: return
        val ultima = Store.resumen(this, p.id).optLong("ultima_ok_ms", 0L)
        val intento = Store.resumen(this, p.id).optString("ultima_revision")
        if (System.currentTimeMillis() - ultima < 10 * 60_000L) return
        // Si la sesión está vencida y no hay contraseña guardada, insistir no sirve: no se reintenta solo.
        if (intento.isNotBlank() && Store.resumen(this, p.id).optString("sesion") == "vencida" && Profiles.pass(this, p.id).isEmpty()) return
        if (System.currentTimeMillis() - ultimoAuto < 5 * 60_000L) return
        ultimoAuto = System.currentTimeMillis()
        Scheduler.checkNow(this, p.id)
    }
    private var ultimoAuto = 0L

    private fun handleIntent(i: Intent?) {
        // Las órdenes con datos (cambiar de cuenta, compartir, agendar, ir a una pestaña) solo valen si las
        // armó esta app: otra app del teléfono no puede abrirla con textos o acciones propias.
        if (!Seguridad.esPropio(this, i)) return
        i?.getStringExtra("pid")?.let { if (Profiles.get(this, it) != null) switchTo(it) }
        // Botones de las notificaciones: «Compartir» y «Agendar».
        val hacer = i?.getStringExtra("hacer")
        if (hacer != null) {
            val datos = i.getStringExtra("datos").orEmpty()
            i.removeExtra("hacer")
            i.getIntExtra("nid", 0).takeIf { it != 0 }?.let { getSystemService(NotificationManager::class.java).cancel(it) }
            when (hacer) {
                "compartir" -> Compartir.enviarTexto(this, encabezadoCompartir() + datos, "Compartir calificación")
                "agendar" -> agendar(datos)
            }
        }
        when (i?.getStringExtra("tab")) {
            "web" -> nav.selectedItemId = R.id.nav_web
            "dash" -> nav.selectedItemId = R.id.nav_dash
            "menu" -> nav.selectedItemId = R.id.nav_menu
        }
    }

    private fun show(id: Int) {
        dashSwipe.visibility = if (id == R.id.nav_dash) View.VISIBLE else View.GONE
        menuWeb.visibility = if (id == R.id.nav_menu) View.VISIBLE else View.GONE
        webPane.visibility = if (id == R.id.nav_web) View.VISIBLE else View.GONE
        setPane.visibility = if (id == R.id.nav_set) View.VISIBLE else View.GONE
        if (id == R.id.nav_web) ensureGuaraniLoaded()
        if (id == R.id.nav_set) loadSettingsFields()
        refreshPages()
    }

    /** Cambia la cuenta activa (tablero, menú y pestaña Guaraní). */
    private fun switchTo(pid: String) {
        if (cur()?.id == pid) return
        Profiles.setActive(this, pid)
        editingId = pid
        gLoadedFor = null
        if (webPane.visibility == View.VISIBLE) ensureGuaraniLoaded()
        refreshPages()
        updateStatus()
    }

    private fun refreshPages() {
        if (::dashWeb.isInitialized) dashWeb.evaluateJavascript("window.dash && dash.refresh()", null)
        if (::menuWeb.isInitialized) menuWeb.evaluateJavascript("window.dash && dash.refresh()", null)
    }

    // ---------------- Tablero y Menú ----------------
    @SuppressLint("SetJavaScriptEnabled")
    private fun setupDashboard() {
        Seguridad.endurecer(dashWeb.settings)
        dashWeb.settings.javaScriptEnabled = true
        dashWeb.settings.domStorageEnabled = true
        dashWeb.addJavascriptInterface(Bridge(), "Android")
        dashWeb.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView?, req: WebResourceRequest?): Boolean = true
        }
        dashWeb.loadUrl("file:///android_asset/dashboard.html")
        dashSwipe.setColorSchemeColors(0xFF1F5C4A.toInt())
        dashSwipe.setOnRefreshListener { cur()?.let { Scheduler.checkNow(this, it.id) } ?: run { dashSwipe.isRefreshing = false } }
        dashSwipe.setOnChildScrollUpCallback { _, _ -> dashWeb.scrollY > 0 }
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun setupMenu() {
        Seguridad.endurecer(menuWeb.settings)
        menuWeb.settings.javaScriptEnabled = true
        menuWeb.addJavascriptInterface(Bridge(), "Android")
        menuWeb.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView?, req: WebResourceRequest?): Boolean = true
        }
        menuWeb.loadUrl("file:///android_asset/menu.html")
    }

    /** Puente con las páginas locales (tablero y menú). Solo se carga en visores de archivos propios. */
    inner class Bridge {
        @JavascriptInterface fun get(name: String): String {
            val p = cur() ?: return "[]"
            return if (name in Store.DATASETS) Store.get(this@MainActivity, p.id, name) else "[]"
        }
        @JavascriptInterface fun status(): String = cur()?.let { Store.status(this@MainActivity, it.id) } ?: ""
        @JavascriptInterface fun base(): String = cur()?.url ?: ""
        @JavascriptInterface fun account(): String {
            val p = cur() ?: return "{}"
            val r = Store.resumen(this@MainActivity, p.id)
            return p.json().put("carrera", Profiles.carreraDe(this@MainActivity, p)).put("carrera_manual", p.carrera.isNotBlank())
                .put("plan", r.optString("plan")).put("propuestas", r.optJSONArray("propuestas") ?: JSONArray())
                .put("usuario", Profiles.user(this@MainActivity, p.id)).toString()
        }
        /** Compartir calificaciones como texto. cat: "" (todas) o una categoría. */
        @JavascriptInterface fun share(cat: String) { runOnUiThread { compartirCalificaciones(cat) } }
        /** Pasar un examen al calendario del teléfono. Recibe el evento como JSON. */
        @JavascriptInterface fun calendar(json: String) { runOnUiThread { agendar(json) } }
        /** Recordatorio propio: abre el calendario con el título listo para elegir fecha. */
        @JavascriptInterface fun reminder(materia: String) {
            runOnUiThread {
                val t = if (materia.isBlank()) "Examen" else "Examen: $materia"
                if (!Compartir.agendar(this@MainActivity, t, Store.today(), null, cuentaComoTexto())) toast("No encontré una app de calendario")
            }
        }
        /** Documento oficial de Guaraní: se descarga del sitio con tu sesión. */
        @JavascriptInterface fun doc(json: String) { runOnUiThread { abrirDocumento(json) } }
        @JavascriptInterface fun soundSettings() { runOnUiThread { abrirAjustesDeSonido() } }
        @JavascriptInterface fun accounts(): String =
            JSONArray(Profiles.all(this@MainActivity).map { JSONObject().put("id", it.id).put("nombre", it.nombre) }).toString()
        @JavascriptInterface fun setActive(id: String) { runOnUiThread { switchTo(id) } }
        @JavascriptInterface fun checkNow() {
            runOnUiThread { cur()?.let { Scheduler.checkNow(this@MainActivity, it.id); toast("Revisando ${it.nombre}…") } }
        }
        @JavascriptInterface fun openGuarani() { runOnUiThread { nav.selectedItemId = R.id.nav_web } }
        @JavascriptInterface fun openSettings() { runOnUiThread { nav.selectedItemId = R.id.nav_set } }
        @JavascriptInterface fun newAccount() { runOnUiThread { editingId = null; nav.selectedItemId = R.id.nav_set; loadSettingsFields() } }
        @JavascriptInterface fun open(url: String) {
            runOnUiThread { val p = cur(); if (p != null && sameSite(url, p)) openInGuarani(url) }
        }
    }

    // ---------------- Compartir, calendario y documentos ----------------
    private fun cuentaComoTexto(): String {
        val p = cur() ?: return ""
        return listOf(p.institucion, Profiles.carreraDe(this, p)).filter { it.isNotBlank() }.joinToString(" · ")
    }
    private fun encabezadoCompartir(): String = cuentaComoTexto().let { if (it.isBlank()) "" else "$it\n" }

    private fun compartirCalificaciones(cat: String) {
        val p = cur() ?: return
        val materias = Store.arr(this, p.id, "materias")
        if (materias.length() == 0) { toast("Todavía no hay calificaciones para compartir"); return }
        Compartir.enviarTexto(this, Compartir.texto(p.institucion, Profiles.carreraDe(this, p), Store.resumen(this, p.id), materias, cat))
    }

    private fun agendar(json: String) {
        val o = runCatching { JSONObject(json) }.getOrNull() ?: return
        val detalle = listOf(o.optString("detalle"), cuentaComoTexto()).filter { it.isNotBlank() }.joinToString("\n")
        if (!Compartir.agendar(this, o.optString("evento", "Examen"), o.optString("fecha"), o.optString("hora").ifBlank { null }, detalle))
            toast("No encontré una app de calendario en el teléfono")
    }

    /** Abre un documento de Guaraní. Si tiene enlace propio se descarga; si no, se abre la página que lo ofrece. */
    private fun abrirDocumento(json: String) {
        val p = cur() ?: return
        val o = runCatching { JSONObject(json) }.getOrNull() ?: return
        val url = o.optString("url"); val pagina = o.optString("pagina")
        when {
            url.isNotBlank() && sameSite(url, p) -> { openInGuarani(url); toast("Pidiendo el documento a Guaraní…") }
            pagina.isNotBlank() && sameSite(pagina, p) -> {
                openInGuarani(pagina)
                Toast.makeText(this, "Tocá el botón de PDF o «Generar» en esta página de Guaraní", Toast.LENGTH_LONG).show()
            }
        }
    }

    /** Terminó una descarga: ofrece abrirla o compartirla. El archivo es el que entregó Guaraní, sin tocar. */
    private fun descargaLista(id: Long, nombre: String) {
        val dm = getSystemService(DownloadManager::class.java)
        val uri = runCatching { dm.getUriForDownloadedFile(id) }.getOrNull()
        val mime = runCatching { dm.getMimeTypeForDownloadedFile(id) }.getOrNull() ?: "application/pdf"
        if (uri == null) { toast("No se pudo descargar $nombre"); return }
        if (mime.startsWith("text/html")) {
            AlertDialog.Builder(this).setTitle("Guaraní no entregó un archivo")
                .setMessage("En lugar del documento devolvió una página. Abrí la sección en la pestaña Guaraní y usá el botón de PDF de esa pantalla.")
                .setPositiveButton("Entendido", null).show()
            return
        }
        if (isFinishing || isDestroyed) return
        AlertDialog.Builder(this).setTitle("Descarga lista")
            .setMessage("$nombre\nQuedó guardado en la carpeta Descargas, tal como lo emitió Guaraní.")
            .setPositiveButton("Abrir") { _, _ ->
                runCatching {
                    startActivity(Intent(Intent.ACTION_VIEW).setDataAndType(uri, mime).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION))
                }.onFailure { toast("No hay una app para abrir este archivo") }
            }
            .setNeutralButton("Compartir") { _, _ ->
                val i = Intent(Intent.ACTION_SEND).setType(mime).putExtra(Intent.EXTRA_STREAM, uri)
                    .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                runCatching { startActivity(Intent.createChooser(i, "Compartir $nombre")) }
            }
            .setNegativeButton("Cerrar", null).show()
    }

    /** Guarda en PDF la página de Guaraní que se está viendo, tal como la muestra el sitio. */
    private fun guardarPaginaComoPdf() {
        val titulo = (gWeb.title?.ifBlank { null } ?: "Guaraní").replace(Regex("[\\\\/:*?\"<>|]"), " ").take(60)
        runCatching {
            (getSystemService(Context.PRINT_SERVICE) as PrintManager)
                .print(titulo, gWeb.createPrintDocumentAdapter(titulo), PrintAttributes.Builder().build())
        }.onFailure { toast("Este teléfono no permite guardar la página como PDF") }
    }

    private fun abrirAjustesDeSonido() {
        runCatching {
            startActivity(Intent(Settings.ACTION_CHANNEL_NOTIFICATION_SETTINGS)
                .putExtra(Settings.EXTRA_APP_PACKAGE, packageName).putExtra(Settings.EXTRA_CHANNEL_ID, Notifier.CH_NOTAS))
        }.onFailure { toast("Abrí Configuración → Apps → Guaraní al día → Notificaciones") }
    }

    // ---------------- Guaraní completo ----------------
    private fun ensureGuaraniLoaded() {
        val p = cur() ?: return
        if (gLoadedFor == p.id) return
        if (Profiles.useSessionOf(this, p)) gWeb.clearHistory()
        gLoadedFor = p.id
        findViewById<TextView>(R.id.webTitle).text = p.nombre
        findViewById<TextView>(R.id.webSub).text = cuentaComoTexto()
        gWeb.loadUrl(p.url)
    }

    fun openInGuarani(url: String) {
        nav.selectedItemId = R.id.nav_web
        ensureGuaraniLoaded()
        gWeb.loadUrl(url)
    }

    /** Mismo sitio que la cuenta: mismo dominio (incluye el ingreso único de la institución). */
    private fun sameSite(url: String, p: Profile): Boolean {
        val h = Uri.parse(url).host ?: return false
        return h.equals(p.host, true) || siteOf(h) == siteOf(p.host)
    }
    private fun siteOf(host: String): String {
        val parts = host.lowercase().split('.')
        val n = if (parts.size >= 3 && parts[parts.size - 2] in setOf("edu", "gob", "gov", "com", "org", "net")) 3 else 2
        return parts.takeLast(n).joinToString(".")
    }

    private val SSO = Regex("auth=(saml|cas|oidc|arai)", RegexOption.IGNORE_CASE)

    /** Servidores de ingreso único a los que Guaraní nos redirigió (no cualquier página que se abra después). */
    private val ssoHosts = mutableSetOf<String>()

    /**
     * Al tocar «ingresar con el usuario de la institución», Guaraní lleva a otro servidor (SAML/CAS).
     * Se confía en ESE servidor —el primero fuera del sitio, o al que se llegue por redirección— y solo
     * durante 2 minutos; un enlace cualquiera abierto después no recibe la contraseña.
     */
    private fun notarIngresoUnico(u: Uri, p: Profile, redireccion: Boolean) {
        val url = u.toString()
        if (sameSite(url, p)) {
            if (SSO.containsMatchIn(url)) { ssoUntil = System.currentTimeMillis() + 120_000; ssoHosts.clear() }
            return
        }
        if (System.currentTimeMillis() >= ssoUntil) return
        val h = u.host?.lowercase() ?: return
        if (ssoHosts.isEmpty() || redireccion) ssoHosts += h
    }

    /**
     * Página donde se puede escribir la contraseña: tiene que ser https y ser de la institución, o el ingreso
     * único al que Guaraní redirigió hace instantes.
     */
    private fun trusted(url: String, p: Profile): Boolean {
        if (!Seguridad.esHttps(url)) return false
        if (sameSite(url, p)) return true
        val h = Uri.parse(url).host?.lowercase() ?: return false
        return System.currentTimeMillis() < ssoUntil && h in ssoHosts
    }

    /** Si Guaraní muestra el formulario de ingreso y la contraseña está guardada, entra solo. */
    private fun autoLogin(view: WebView?) {
        val p = cur() ?: return
        val url = view?.url ?: return
        if (!trusted(url, p)) return
        val u = Profiles.user(this, p.id); val pw = Profiles.pass(this, p.id)
        if (u.isEmpty() || pw.isEmpty()) return
        val now = System.currentTimeMillis()
        if (now - lastAutoLogin < 60_000) return
        val js = """
(function(u,p){
  var pw=document.querySelector('input[type=password]');
  if(!pw||pw.offsetParent===null){
    var s=document.querySelector('a[href*="auth=saml"],a[href*="auth=cas"],a[href*="auth=oidc"],a[href*="auth=arai"]');
    if(s){ location.href=s.href; return 'ok'; } return 'no'; }
  var f=pw.form; if(!f) return 'no';
  if(document.querySelector('.alert-error,.alert-danger')) return 'error';
  var us=f.querySelector('input[name=usuario],input[name=username],input[name=user],input[type=email],input[type=text]'); if(us) us.value=u; pw.value=p;
  var b=f.querySelector('#login,input[type=submit],button[type=submit]'); if(b) b.click(); else f.submit();
  return 'ok';
})(${JSONObject.quote(u)},${JSONObject.quote(pw)})"""
        view.evaluateJavascript(js) { r -> if (r?.contains("ok") == true && !SSO.containsMatchIn(url)) lastAutoLogin = now }
    }

    /** Engancha el formulario de ingreso para ofrecer guardar la contraseña cuando la escribís a mano. */
    private val HOOK = """
(function(){ if(window.__gadHook) return; window.__gadHook=1;
  function cap(f){ f=f||document; var pw=f.querySelector('input[type=password]'); if(!pw||!pw.value) return;
    var us=f.querySelector('input[name=usuario],input[name=username],input[name=user],input[type=email],input[type=text]');
    try{ GuaraniLogin.captured(us?us.value:'', pw.value); }catch(x){} }
  document.addEventListener('submit', function(e){ cap(e.target); }, true);
  document.addEventListener('click', function(e){ var t=e.target; if(t && (t.id==='login' || t.type==='submit')) cap(t.form); }, true);
})()"""

    inner class LoginBridge {
        @JavascriptInterface fun captured(user: String, pass: String) {
            runOnUiThread { offerSave(user, pass) }
        }
    }

    private fun offerSave(user: String, pass: String) {
        val p = cur() ?: return
        val url = gWeb.url ?: return
        if (!trusted(url, p) || pass.isEmpty() || askingSave) return
        if (Profiles.user(this, p.id) == user && Profiles.pass(this, p.id) == pass) return
        if (Store.flag(this, p.id, "never_save")) return
        askingSave = true
        AlertDialog.Builder(this)
            .setTitle("¿Guardar la contraseña?")
            .setMessage("Para «${p.nombre}». Queda cifrada en este teléfono y la app entra sola a Guaraní cuando la sesión vence, así no te perdés ningún aviso.")
            .setPositiveButton("Guardar") { _, _ ->
                Profiles.savePassword(this, p.id, user, pass); toast("Contraseña guardada"); loadSettingsFields()
            }
            .setNegativeButton("Ahora no", null)
            .setNeutralButton("Nunca para esta cuenta") { _, _ -> Store.setFlag(this, p.id, "never_save", true) }
            .setOnDismissListener { askingSave = false }
            .show()
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun setupGuarani() {
        val progress = findViewById<ProgressBar>(R.id.webProgress)
        val title = findViewById<TextView>(R.id.webTitle)
        Seguridad.endurecer(gWeb.settings)
        with(gWeb.settings) {
            javaScriptEnabled = true
            domStorageEnabled = true
            loadWithOverviewMode = true
            useWideViewPort = true
            builtInZoomControls = true
            displayZoomControls = false
            setSupportMultipleWindows(false)
            javaScriptCanOpenWindowsAutomatically = true
            userAgentString = userAgentString.replace("; wv", "")
        }
        gWeb.addJavascriptInterface(LoginBridge(), "GuaraniLogin")
        CookieManager.getInstance().setAcceptCookie(true)
        CookieManager.getInstance().setAcceptThirdPartyCookies(gWeb, true)
        gWeb.webViewClient = object : WebViewClient() {
            override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                progress.visibility = View.VISIBLE
                val p = cur()
                if (url != null && p != null) notarIngresoUnico(Uri.parse(url), p, redireccion = false)
            }
            override fun onPageFinished(view: WebView?, url: String?) {
                progress.visibility = View.GONE
                title.text = view?.title?.ifBlank { null } ?: cur()?.nombre ?: "SIU Guaraní"
                findViewById<TextView>(R.id.webSub).text = cuentaComoTexto()
                CookieManager.getInstance().flush()
                val p = cur()
                if (p != null && url != null && trusted(url, p)) view?.evaluateJavascript(HOOK, null)
                view?.postDelayed({ autoLogin(view) }, 900)
            }
            override fun shouldOverrideUrlLoading(view: WebView?, req: WebResourceRequest?): Boolean {
                val u = req?.url ?: return false
                if (u.scheme == "http" || u.scheme == "https") {
                    val p = cur()
                    if (req.isForMainFrame && p != null) notarIngresoUnico(u, p, req.isRedirect)
                    return false
                }
                if (Seguridad.externoPermitido(u)) runCatching { startActivity(Intent(Intent.ACTION_VIEW, u)) }
                return true
            }
        }
        gWeb.webChromeClient = object : WebChromeClient() {
            override fun onProgressChanged(view: WebView?, p: Int) { progress.progress = p }
            override fun onShowFileChooser(w: WebView?, cb: ValueCallback<Array<Uri>>?, params: FileChooserParams?): Boolean {
                fileCb?.onReceiveValue(null)
                fileCb = cb
                return try { pickFile.launch(params!!.createIntent()); true } catch (e: Exception) { fileCb = null; false }
            }
        }
        gWeb.setDownloadListener { url, ua, cd, mime, _ ->
            if (!url.startsWith("http")) {
                toast("Guaraní arma este archivo en la página: usá «Guardar esta página como PDF»")
                return@setDownloadListener
            }
            runCatching {
                val name = URLUtil.guessFileName(url, cd, mime)
                val req = DownloadManager.Request(Uri.parse(url))
                    .addRequestHeader("Cookie", CookieManager.getInstance().getCookie(url) ?: "")
                    .addRequestHeader("User-Agent", ua)
                    .setMimeType(mime)
                    .setTitle(name)
                    .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                    .setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, name)
                descargas[getSystemService(DownloadManager::class.java).enqueue(req)] = name
                toast("Descargando $name")
            }.onFailure { startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }
        }
        findViewById<ImageButton>(R.id.webBack).setOnClickListener { if (gWeb.canGoBack()) gWeb.goBack() }
        findViewById<ImageButton>(R.id.webHome).setOnClickListener { cur()?.let { gWeb.loadUrl(it.url) } }
        findViewById<ImageButton>(R.id.webReload).setOnClickListener { gWeb.reload() }
        findViewById<ImageButton>(R.id.webPdf).setOnClickListener { guardarPaginaComoPdf() }
    }

    // ---------------- Ajustes ----------------
    private val accSpinner by lazy { findViewById<Spinner>(R.id.accSpinner) }
    private val fInst by lazy { findViewById<AutoCompleteTextView>(R.id.setInst) }
    private val fUrl by lazy { findViewById<EditText>(R.id.setUrl) }
    private val fName by lazy { findViewById<EditText>(R.id.setName) }
    private val fCarrera by lazy { findViewById<EditText>(R.id.setCarrera) }
    private val fUser by lazy { findViewById<EditText>(R.id.setUser) }
    private val fPass by lazy { findViewById<EditText>(R.id.setPass) }
    private val fSave by lazy { findViewById<CheckBox>(R.id.setSavePass) }
    private var spinnerIds: List<String?> = emptyList()
    private var spinnerLabels: List<String> = emptyList()

    private fun setupSettings() {
        findViewById<Spinner>(R.id.setInterval).adapter = listaSimple(Config.INTERVAL_LABELS)

        val insts = Instituciones.all(this)
        fInst.setAdapter(ArrayAdapter(this, android.R.layout.simple_dropdown_item_1line, insts))
        fInst.setOnItemClickListener { parent, _, pos, _ ->
            val inst = parent.getItemAtPosition(pos) as Instituciones.Inst
            fUrl.setText(inst.url)
            if (fName.text.isBlank()) fName.setText(inst.nombre)
        }

        accSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(p: AdapterView<*>?, v: View?, pos: Int, id: Long) {
                // Solo actuar si la persona eligió OTRA cuenta. Sin esta comprobación, volver a cargar los
                // campos disparaba de nuevo este aviso y la pantalla se recargaba sin parar, borrando lo escrito.
                val pid = spinnerIds.getOrNull(pos)
                if (pid == editingId) return
                editingId = pid
                if (pid != null) switchTo(pid)
                loadSettingsFields()
            }
            override fun onNothingSelected(p: AdapterView<*>?) {}
        }
        findViewById<Button>(R.id.accNew).setOnClickListener { editingId = null; loadSettingsFields(); fInst.requestFocus() }

        findViewById<Button>(R.id.btnSave).setOnClickListener { saveAccount() }
        findViewById<Button>(R.id.btnDelete).setOnClickListener {
            val p = Profiles.get(this, editingId) ?: return@setOnClickListener
            AlertDialog.Builder(this)
                .setTitle("¿Borrar «${p.nombre}»?")
                .setMessage("Se borran del teléfono la contraseña, las notas guardadas y las novedades de esta cuenta. En Guaraní no cambia nada.")
                .setPositiveButton("Borrar") { _, _ ->
                    Profiles.delete(this, p.id)
                    editingId = cur()?.id; gLoadedFor = null
                    loadSettingsFields(); refreshPages(); toast("Cuenta borrada")
                }
                .setNegativeButton("Cancelar", null).show()
        }

        findViewById<Button>(R.id.btnGeneral).setOnClickListener { guardarGenerales(true) }
        // Revisiones y avisos se guardan solos al tocar cada opción.
        listOf(R.id.setAll, R.id.setWifi, R.id.setKeep, R.id.setFixed).forEach { id ->
            findViewById<MaterialSwitch>(id).setOnCheckedChangeListener { _, _ -> if (!llenandoAjustes) guardarGenerales(false) }
        }
        findViewById<Spinner>(R.id.setInterval).onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(p: AdapterView<*>?, v: View?, pos: Int, id: Long) { if (!llenandoAjustes) guardarGenerales(false) }
            override fun onNothingSelected(p: AdapterView<*>?) {}
        }
        findViewById<Button>(R.id.btnCheck).setOnClickListener { Scheduler.checkNow(this); toast("Revisando todas las cuentas…") }
        findViewById<Button>(R.id.btnTest).setOnClickListener {
            Notifier.notify(this, Notifier.CH_NOTAS, "Prueba: nueva calificación en Guaraní",
                "Así suena y se ve el aviso cuando cargan una nota.", subtexto = cuentaComoTexto())
        }
        findViewById<Button>(R.id.btnSound).setOnClickListener { abrirAjustesDeSonido() }
        findViewById<Button>(R.id.btnBattery).setOnClickListener {
            val pm = getSystemService(PowerManager::class.java)
            if (pm.isIgnoringBatteryOptimizations(packageName)) toast("Ya está permitido.")
            else runCatching {
                @SuppressLint("BatteryLife")
                val i = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:$packageName"))
                startActivity(i)
            }
        }
        findViewById<Button>(R.id.btnPages).setOnClickListener {
            val pid = editingId ?: return@setOnClickListener
            Store.setWatched(this, pid, findViewById<EditText>(R.id.setPages).text.toString().lines())
            toast("Páginas guardadas")
        }
        findViewById<Button>(R.id.btnReset).setOnClickListener {
            val pid = editingId ?: return@setOnClickListener
            Store.clearSnapshots(this, pid)
            Store.setFlag(this, pid, "baseline_done", false)
            Scheduler.checkNow(this, pid)
            toast("Tomando una foto nueva de Guaraní")
        }
        loadSettingsFields()
    }

    private var llenandoAjustes = false
    private var ultimoGeneral = ""

    /** Lista desplegable con el texto del mismo tamaño que el resto de la pantalla. */
    private fun listaSimple(items: List<String>): ArrayAdapter<String> =
        ArrayAdapter(this, R.layout.lista_item, items).apply { setDropDownViewResource(R.layout.lista_desplegada) }

    private fun guardarGenerales(avisar: Boolean) {
        val intervalo = Config.INTERVALS[findViewById<Spinner>(R.id.setInterval).selectedItemPosition.coerceAtLeast(0)]
        val todo = findViewById<MaterialSwitch>(R.id.setAll).isChecked; val wifi = findViewById<MaterialSwitch>(R.id.setWifi).isChecked
        val keep = findViewById<MaterialSwitch>(R.id.setKeep).isChecked; val fijo = findViewById<MaterialSwitch>(R.id.setFixed).isChecked
        val firma = "$intervalo|$todo|$wifi|$keep|$fijo"
        if (firma == ultimoGeneral && !avisar) return
        val cambioRevision = ultimoGeneral.substringBefore('|') != intervalo.toString() || ultimoGeneral.split('|').getOrNull(2) != wifi.toString()
        ultimoGeneral = firma
        Config.saveGeneral(this, intervalo, todo, wifi, keep, fijo)
        if (cambioRevision || avisar) Scheduler.schedule(this, replace = true)
        Scheduler.keepAlive(this)
        WatchService.sync(this)
        if (avisar) toast("Guardado")
    }

    private fun saveAccount() {
        val url = Profiles.normalizeUrl(fUrl.text.toString().ifBlank {
            Instituciones.all(this).firstOrNull { it.nombre.equals(fInst.text.toString().trim(), true) }?.url ?: ""
        })
        if (url.isBlank() || Uri.parse(url).host.isNullOrBlank()) {
            fUrl.error = "Elegí tu institución o escribí la dirección de tu Guaraní"; return
        }
        if (!Seguridad.esHttps(url)) {
            fUrl.error = "La dirección tiene que empezar con https://. Sin cifrado la app no envía tu contraseña."; return
        }
        val inst = Instituciones.nameFor(this, url) ?: fInst.text.toString().trim().ifBlank { Uri.parse(url).host!! }
        val nombre = fName.text.toString().trim().ifBlank { inst }
        val old = Profiles.get(this, editingId)
        val prof = Profile(old?.id ?: Profiles.newId(), nombre, inst, url, fSave.isChecked, fCarrera.text.toString().trim())
        val pass = fPass.text.toString().takeIf { it.isNotEmpty() }
        Profiles.save(this, prof, fUser.text.toString(), if (fSave.isChecked) pass else null)
        if (old != null && old.url != url) { Store.setWatched(this, prof.id, emptyList()); Store.clearSnapshots(this, prof.id) }
        editingId = prof.id
        Profiles.setActive(this, prof.id)
        gLoadedFor = null
        Scheduler.schedule(this, replace = false)
        Scheduler.checkNow(this, prof.id)
        Scheduler.keepAlive(this)
        WatchService.sync(this)
        toast(if (old == null) "Cuenta creada. Primera revisión en marcha." else "Cuenta guardada")
        loadSettingsFields(); refreshPages()
    }

    private fun loadSettingsFields() {
        val all = Profiles.all(this)
        if (editingId != null && all.none { it.id == editingId }) editingId = cur()?.id
        // Lista de cuentas
        val labels = all.map { it.nombre } + listOf("(cuenta nueva)")
        spinnerIds = all.map { it.id } + listOf(null)
        if (labels != spinnerLabels) {      // la lista solo se rearma si cambió
            spinnerLabels = labels
            accSpinner.adapter = listaSimple(labels)
        }
        val sel = spinnerIds.indexOf(editingId).coerceAtLeast(0)
        if (accSpinner.selectedItemPosition != sel) accSpinner.setSelection(sel, false)

        val p = Profiles.get(this, editingId)
        fInst.setText(p?.institucion ?: "", false)
        fUrl.setText(p?.url ?: ""); fUrl.error = null
        fName.setText(p?.nombre ?: "")
        fCarrera.setText(p?.carrera ?: "")
        findViewById<com.google.android.material.textfield.TextInputLayout>(R.id.tilCarrera).helperText =
            p?.let { Store.resumen(this, it.id).optString("propuesta") }?.takeIf { it.isNotBlank() }
                ?.let { "Leída de Guaraní: $it. Escribila solo si querés otro nombre." }
                ?: "Se completa sola al leer Guaraní. Escribila solo si querés otro nombre."
        fUser.setText(p?.let { Profiles.user(this, it.id) } ?: "")
        fPass.setText(p?.let { Profiles.pass(this, it.id) } ?: "")
        fSave.isChecked = p?.savePass ?: true
        findViewById<Button>(R.id.btnDelete).visibility = if (p != null) View.VISIBLE else View.GONE
        findViewById<Button>(R.id.btnSave).text = if (p == null) "Agregar cuenta y revisar" else "Guardar cambios y revisar"
        findViewById<EditText>(R.id.setPages).setText(p?.let { Store.watched(this, it.id).joinToString("\n") } ?: "")

        llenandoAjustes = true
        findViewById<Spinner>(R.id.setInterval).setSelection(Config.INTERVALS.indexOf(Config.interval(this)).coerceAtLeast(0), false)
        findViewById<MaterialSwitch>(R.id.setAll).isChecked = Config.notifyAll(this)
        findViewById<MaterialSwitch>(R.id.setWifi).isChecked = Config.wifiOnly(this)
        findViewById<MaterialSwitch>(R.id.setKeep).isChecked = Config.keepAlive(this)
        findViewById<MaterialSwitch>(R.id.setFixed).isChecked = Config.fixed(this)
        ultimoGeneral = "${Config.interval(this)}|${Config.notifyAll(this)}|${Config.wifiOnly(this)}|${Config.keepAlive(this)}|${Config.fixed(this)}"
        accSpinner.post { llenandoAjustes = false }
        updateStatus()
    }

    private fun updateStatus() {
        val p = Profiles.get(this, editingId)
        findViewById<TextView>(R.id.setStatus)?.text =
            if (p == null) "Elegí tu institución, poné tu usuario y tocá «Agregar cuenta»."
            else "Última revisión: " + Store.status(this, p.id)
        if (::dashWeb.isInitialized) dashWeb.evaluateJavascript("window.setAppStatus && setAppStatus()", null)
    }

    private fun toast(s: String) = Toast.makeText(this, s, Toast.LENGTH_SHORT).show()
}
