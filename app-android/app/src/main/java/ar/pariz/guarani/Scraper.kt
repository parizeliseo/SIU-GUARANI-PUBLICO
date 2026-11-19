package ar.pariz.guarani

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.os.SystemClock
import android.webkit.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.suspendCancellableCoroutine
import org.json.JSONArray
import java.io.ByteArrayInputStream
import org.json.JSONObject
import kotlin.coroutines.resume

/**
 * Recorre SIU Guaraní con un WebView invisible (comparte las cookies con la pestaña Guaraní).
 * Debe usarse en el hilo principal.
 *
 * Solo mira: los únicos clics que hace son los que muestran información («Historia completa» en la
 * historia académica y «Expandir» en el plan de estudio). Nunca inscribe, da de baja ni confirma nada.
 */
class Scraper(private val ctx: Context) {

    enum class State { OK, NEED_LOGIN, LOGIN_FAILED, CAPTCHA, OFFLINE }

    /** Una tabla de Guaraní: el título de la sección donde está, los encabezados y las filas. */
    data class Table(val sec: String, val head: List<String>, val rows: List<List<String>>)

    /** Un documento oficial que Guaraní ofrece en una página (PDF, comprobante…). */
    data class Doc(val titulo: String, val url: String, val tipo: String, val contexto: String)

    data class Page(
        val url: String, val title: String, val text: String,
        val tables: List<Table> = emptyList(),
        /** Texto de toda la pantalla (incluye barra superior y paneles laterales). */
        val full: String = "",
        /** Propuesta elegida en el selector de Guaraní, y todas las que ofrece. */
        val propSel: String = "", val props: List<String> = emptyList(),
        val docs: List<Doc> = emptyList(),
        /** Detalle de cada actividad de una página de inscripción (materia → líneas del detalle; null si no se pudo leer). */
        val actividades: List<Pair<String, List<String>?>> = emptyList()
    ) {
        companion object {
            private fun strs(a: JSONArray?): List<String> = if (a == null) emptyList() else (0 until a.length()).map { a.optString(it) }
            fun fromJson(e: JSONObject, urlFinal: String? = null): Page {
                val ts = e.optJSONArray("tables") ?: JSONArray()
                val tables = (0 until ts.length()).mapNotNull { ts.optJSONObject(it) }.map { t ->
                    val rs = t.optJSONArray("rows") ?: JSONArray()
                    Table(t.optString("sec"), strs(t.optJSONArray("head")), (0 until rs.length()).map { strs(rs.optJSONArray(it)) })
                }
                val ds = e.optJSONArray("docs") ?: JSONArray()
                val docs = (0 until ds.length()).mapNotNull { ds.optJSONObject(it) }
                    .map { Doc(it.optString("t"), it.optString("h"), it.optString("tipo"), it.optString("ctx")) }
                return Page(urlFinal ?: e.optString("url"), e.optString("title"), e.optString("text"), tables,
                    e.optString("full"), e.optString("propSel"), strs(e.optJSONArray("props")), docs)
            }
        }
    }

    data class Result(val state: State, val pages: List<Page>, val discovered: List<String>, val message: String = "",
                      val menu: List<Pair<String, String>> = emptyList(),
                      val menuPublico: List<Pair<String, String>> = emptyList())

    private lateinit var wv: WebView
    @Volatile private var loading = false
    @Volatile private var lastEvent = 0L
    @Volatile private var mainError: String? = null

    companion object {
        private val EXCLUDE = Regex(
            "(logout|salir|cerrar|login|/acceso|recuperar|alta_usuario|validador_certificados|ayuda_menu|fecha_examen$|horarios_cursadas$|olvid|password|clave|cambiar_perfil|imprimir|descargar|" +
            "\\.pdf|\\.zip|\\.xls|\\.jpg|\\.png|mailto:|javascript:|tel:|ayuda|manual|tutorial|faq|baja|eliminar|confirmar|generar|exportar|" +
            "cambiar_carrera|seleccionar_propuesta)", RegexOption.IGNORE_CASE
        )
        /** Rutas típicas de SIU Guaraní 3 autogestión, por si el menú no expone enlaces directos. */
        val FALLBACK = listOf(
            "historia_academica", "plan_estudio", "actuacion_provisoria", "mis_inscripciones", "cursada", "examen",
            "inscripcion_cursada", "inscripcion_examen", "agenda", "cronograma_evaluaciones", "inasistencias",
            "mensajes", "encuestas", "certificados", "datos_censales"
        )
        const val MAX_PAGES = 40

        /**
         * Estado de la página: "listo|cosas pendientes|largo del texto". Pendientes = consultas XHR/fetch en curso más
         * scripts y estilos que todavía se descargan (Guaraní 3 dibuja el formulario en etapas después de cargar).
         */
        const val QUIETA = """(function(){
  if(!window.__gadRed){ window.__gadRed={n:0};
    var S=XMLHttpRequest.prototype.send;
    XMLHttpRequest.prototype.send=function(){ var r=window.__gadRed, fin=false; r.n++;
      function f(){ if(!fin){ fin=true; r.n--; } } this.addEventListener('loadend',f);
      try{ return S.apply(this,arguments); }catch(e){ f(); throw e; } };
    if(window.fetch){ var F=window.fetch; window.fetch=function(){ var r=window.__gadRed; r.n++;
      return F.apply(this,arguments).finally(function(){ r.n--; }); }; } }
  // Scripts y estilos agregados que todavía se están bajando (no figuran aún entre los recursos cargados).
  var hechos={}; (performance.getEntriesByType?performance.getEntriesByType('resource'):[]).forEach(function(e){ hechos[e.name]=1; });
  var bajando=0; document.querySelectorAll('script[src],link[rel=stylesheet][href]').forEach(function(e){
    var u=e.src||e.href; if(/^https?:/.test(u)&&!hechos[u]) bajando++; });
  return document.readyState+'|'+(Math.max(0,window.__gadRed.n)+bajando)+'|'+(document.body?document.body.innerText.length:0);
})()"""

        internal val FUERA_DE_HORARIO = Regex("(disponibilidad horaria|fuera de (este|ese|el) (rango )?horario|disponible para su uso .{0,60}horario|sistema (se encuentra )?(fuera de servicio|en mantenimiento)|no estara habilitado)")
        private val NO_HACE_FALTA = Regex("\\.(woff2?|ttf|otf|eot|mp4|webm|mp3|ogg|wav|m4a)$")
        private val RASTREO = listOf("google-analytics.com", "googletagmanager.com", "doubleclick.net", "hotjar.com",
            "facebook.net", "clarity.ms", "stats.g.doubleclick.net")

        /**
         * Lee la pantalla actual: texto, tablas visibles, menú, propuesta elegida y documentos.
         * (Es JavaScript; se prueba aparte contra páginas de ejemplo, ver app/src/test/guarani/.)
         */
        const val EXTRACT = """
(function(){
  function tx(e){ return ((e&&(e.innerText||e.textContent))||'').replace(/\s+/g,' ').trim(); }
  function vis(e){ return !!(e&&(e.offsetParent!==null||e.getClientRects().length)); }
  var pw=document.querySelector('input[type=password]');
  var main=document.querySelector('#kernel_contenido')||document.querySelector('#contenido')||document.querySelector('main')||document.querySelector('.contenido')||document.body;
  var links=[];
  document.querySelectorAll('a[href]').forEach(function(a){ links.push([a.href,(a.innerText||a.title||'').trim().slice(0,80)]); });
  var menu=[]; document.querySelectorAll('li.js-menuitem-root a, .navbar .nav a, .dropdown-menu a').forEach(function(a){
    var t=(a.innerText||'').trim(); if(t && a.href && a.href.slice(-1)!=='#') menu.push([t,a.href]); });
  if(pw){ (document.querySelectorAll('#kernel_contenido a[href]')||[]).forEach(function(a){
    var t=(a.innerText||'').trim(); if(t.length>3 && a.href.slice(-1)!=='#') menu.push([t,a.href]); }); }
  var cap=!!document.querySelector('img[src*=captcha i],input[name*=captcha i],.g-recaptcha,iframe[src*=recaptcha]');
  var err=(document.querySelector('.alert-error,.alert-danger,.error,.mensaje_error')||{}).innerText||'';
  var ssoA=document.querySelector('a[href*="auth=saml"],a[href*="auth=cas"],a[href*="auth=oidc"],a[href*="auth=arai"]');
  var sso=(!pw||pw.offsetParent===null)&&ssoA?ssoA.href:'';

  var tables=[];
  main.querySelectorAll('table').forEach(function(t){
    if(!vis(t)||tables.length>=40) return;
    var rs=[].slice.call(t.rows||[]); if(rs.length<2) return;
    var head=[].slice.call(rs[0].cells).map(tx);
    if(!head.join('').length) return;
    var rows=[];
    for(var i=1;i<rs.length&&rows.length<400;i++){ if(!vis(rs[i])) continue;
      var cs=[].slice.call(rs[i].cells).map(tx); if(cs.join('').length) rows.push(cs); }
    var sec='', e=t, g=0;
    while(e&&e!==main&&!sec&&g++<6){ var p=e.previousElementSibling, g2=0;
      while(p&&!sec&&g2++<8){
        if(/^(H[1-6]|LEGEND|CAPTION)$/.test(p.tagName)) sec=tx(p);
        else if(p.querySelectorAll){ var hs=p.querySelectorAll('h1,h2,h3,h4,h5,legend'); if(hs.length) sec=tx(hs[hs.length-1]); }
        p=p.previousElementSibling; }
      e=e.parentElement; }
    tables.push({sec:sec.slice(0,80),head:head,rows:rows});
  });

  var propSel='', props=[], lab=null;
  document.querySelectorAll('span,label,div,li,a,strong,b,p').forEach(function(e){ if(lab) return; var own='';
    e.childNodes.forEach(function(n){ if(n.nodeType===3) own+=n.nodeValue; });
    if(/^\s*Propuesta:?\s*$/i.test(own)) lab=e; });
  if(lab){ var box=lab.parentElement, k=0;
    while(box&&k++<3&&!box.querySelector('.dropdown-menu a, select, .dropdown-toggle, button')) box=box.parentElement;
    if(box){ var sel=box.querySelector('select, .dropdown-toggle, button, .btn');
      if(sel) propSel = sel.tagName==='SELECT' ? tx(sel.options[sel.selectedIndex]) : tx(sel);
      box.querySelectorAll('.dropdown-menu a, select option').forEach(function(o){ var t=tx(o); if(t&&props.indexOf(t)<0) props.push(t); }); } }

  var docs=[], here=location.href.replace(/#.*/,'');
  main.querySelectorAll('a,button,input[type=button],input[type=submit]').forEach(function(el){
    if(!vis(el)||docs.length>=60) return;
    var h=el.getAttribute('href')||'', label=tx(el)||el.title||el.value||'';
    var blob=[h,label,el.title||'',el.className||'',el.id||'',(el.innerHTML||'').slice(0,300)].join(' ');
    var tipo=/pdf/i.test(blob)?'pdf':(/xls|excel/i.test(blob)?'excel':(/comprobante|imprimir|constancia|descargar|generar/i.test(blob)?'doc':''));
    if(!tipo||tipo==='excel') return;
    var abs=el.href||'';
    var directo=!!abs&&h!==''&&h.charAt(0)!=='#'&&abs.replace(/#.*/,'')!==here&&!/^\s*javascript:/i.test(h);
    var tr=el.closest?el.closest('tr'):null, ctx='';
    if(tr){ var tb=tr.closest('table'), hi=-1;
      if(tb&&tb.rows.length) [].slice.call(tb.rows[0].cells).forEach(function(c,i){ if(hi<0&&/actividad|materia|asignatura/i.test(tx(c))) hi=i; });
      ctx=(hi>=0&&tr.cells[hi])?tx(tr.cells[hi]):tx(tr).slice(0,140); }
    docs.push({t:label.slice(0,60),h:directo?abs:'',tipo:tipo,ctx:ctx});
  });

  return JSON.stringify({url:location.href,title:document.title,login:!!(pw&&pw.offsetParent!==null),sso:sso,captcha:cap,error:err,
     text:(main&&main.innerText)||'',full:((document.body&&document.body.innerText)||'').slice(0,40000),
     links:links,menu:menu,tables:tables,propSel:propSel,props:props,docs:docs});
})()"""

        /**
         * Clics que solo muestran información. FASE 1: pedir la historia completa / expandir el plan.
         * FASE 2: en la historia, ordenar por actividad. Devuelve el texto del botón tocado, o vacío.
         */
        const val ACTIONS = """
(function(fase){
  var main=document.querySelector('#kernel_contenido')||document.body;
  function click(re){ var els=main.querySelectorAll('a,button,input[type=button]');
    for(var i=0;i<els.length;i++){ var e=els[i], t=((e.innerText||e.value||'')+'').replace(/\s+/g,' ').trim();
      if(re.test(t)&&(e.offsetParent!==null||e.getClientRects().length)){ e.click(); return t; } }
    return ''; }
  var u=(location.pathname+' '+document.title).toLowerCase();
  if(fase===1){
    if(/historia/.test(u)) return click(/^historia completa$/i);
    if(/plan/.test(u)) return click(/^expandir( todo)?$/i);
  } else if(fase===2){
    if(/historia/.test(u)) return click(/^actividad\s*\/\s*reconocimiento$/i);
  }
  return '';
})(FASE)"""

        /**
         * En «Inscripción a exámenes» y «Inscripción a materias», Guaraní lista las actividades y muestra
         * las mesas o comisiones recién al elegir una. Esto lee, sin tocar nada, el detalle de cada actividad
         * (pide cada página con la sesión, como si se la abriera) y deja el resultado en window.__gad.
         * Devuelve cuántas actividades va a leer.
         */
        const val EXPLORAR = """
(function(){
  var main=document.querySelector('#kernel_contenido')||document.querySelector('#contenido')||document.body;
  function tx(e){ return ((e&&(e.innerText||e.textContent))||'').replace(/\s+/g,' ').trim(); }
  function nm(s){ return (s||'').toLowerCase().normalize('NFD').replace(/[̀-ͯ]/g,'').replace(/\s+/g,' ').trim(); }
  var RE=/\/(elegir_materia|elegir_actividad|seleccionar_materia|seleccionar_actividad|ver_materia|elegir_comision)\//i;
  var vistos={}, lista=[];
  main.querySelectorAll('a[href]').forEach(function(a){ var t=tx(a), h=a.href||'';
    if(t.length<3||!RE.test(h)||h.slice(-1)==='#'||/^javascript:/i.test(h)||vistos[nm(t)]) return;
    vistos[nm(t)]=1; lista.push([t,h]); });
  lista=lista.slice(0,150);
  var nombres={}; lista.forEach(function(x){ nombres[nm(x[0])]=1; });
  function lineas(root){
    var c=root.cloneNode(true);
    c.querySelectorAll('script,style,noscript,select,option,input,button,nav,.navbar,.dropdown-menu').forEach(function(e){ e.remove(); });
    c.querySelectorAll('br').forEach(function(e){ e.replaceWith('\n'); });
    c.querySelectorAll('td,th').forEach(function(e){ e.append(' · '); });
    c.querySelectorAll('p,div,li,tr,h1,h2,h3,h4,h5,h6,legend,table,dt,dd,section,article').forEach(function(e){ e.append('\n'); });
    var out=[], ya={};
    (c.textContent||'').split('\n').forEach(function(l){ l=l.replace(/\s+/g,' ').replace(/(\s·\s*)+$/,'').trim();
      if(l.length>1&&!ya[l]){ ya[l]=1; out.push(l); } });
    return out; }
  function detalle(doc, nombre){
    var m=doc.querySelector('#kernel_contenido')||doc.querySelector('#contenido')||doc.body; if(!m) return null;
    var n=nm(nombre), hs=m.querySelectorAll('h1,h2,h3,h4,h5,legend,.titulo,strong,b'), cab=null;
    for(var i=0;i<hs.length&&!cab;i++){ if(nm(hs[i].textContent)===n && !hs[i].closest('a')) cab=hs[i]; }
    if(cab){ var box=cab.parentElement, k=0;
      while(box&&box!==m&&k++<4&&(box.textContent||'').replace(/\s+/g,' ').trim().length<(cab.textContent||'').trim().length+15) box=box.parentElement;
      if(box&&!box.querySelector('a[href*="elegir_"]')) return lineas(box); }
    return lineas(m).filter(function(l){ return !nombres[nm(l)]; }); }
  window.__gad={listo:false, items:[]};
  // Cuatro lecturas a la vez (rápido y sin cargar de más al servidor); el resultado sale en el orden de la lista.
  var res=new Array(lista.length), sig=0;
  async function obrero(){
    while(sig<lista.length){ var i=sig++;
      try{
        var r=await fetch(lista[i][1],{credentials:'include'});
        var d=new DOMParser().parseFromString(await r.text(),'text/html');
        var pw=d.querySelector('input[type=password]');
        res[i]={t:lista[i][0], l:pw?null:(detalle(d,lista[i][0])||[]).slice(0,40)};
      }catch(e){ res[i]={t:lista[i][0], l:null}; }
      window.__gad.items=res.filter(function(x){ return x; });
      await new Promise(function(ok){ setTimeout(ok,60); });
    }
  }
  Promise.all([obrero(),obrero(),obrero(),obrero()]).then(function(){ window.__gad.items=res; window.__gad.listo=true; });
  return String(lista.length);
})()"""
        const val EXPLORADO = """JSON.stringify(window.__gad||null)"""
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun build() {
        wv = WebView(ctx.applicationContext)
        Seguridad.endurecer(wv.settings)
        with(wv.settings) {
            javaScriptEnabled = true
            domStorageEnabled = true
            loadsImagesAutomatically = false
            blockNetworkImage = true
            userAgentString = userAgentString.replace("; wv", "")
        }
        CookieManager.getInstance().setAcceptCookie(true)
        CookieManager.getInstance().setAcceptThirdPartyCookies(wv, true)
        wv.webViewClient = object : WebViewClient() {
            override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) { loading = true; lastEvent = SystemClock.elapsedRealtime() }
            override fun onPageFinished(view: WebView?, url: String?) { loading = false; lastEvent = SystemClock.elapsedRealtime() }
            override fun onReceivedError(view: WebView?, req: WebResourceRequest?, err: WebResourceError?) {
                if (req?.isForMainFrame == true) mainError = err?.description?.toString() ?: "error"
            }
            override fun shouldInterceptRequest(view: WebView?, req: WebResourceRequest?): WebResourceResponse? {
                val u = req?.url ?: return null
                if (req.isForMainFrame || !innecesario(u)) return null
                return WebResourceResponse("text/plain", "utf-8", 204, "No Content", emptyMap(), ByteArrayInputStream(ByteArray(0)))
            }
        }
    }

    /**
     * Espera a que la página termine de cargar y quede quieta: sin navegación en curso, sin consultas
     * de red pendientes (XHR/fetch) y con el texto sin cambiar en dos lecturas seguidas.
     * Antes se esperaban 2,2 s fijos por página; ahora se lee apenas está lista.
     */
    private suspend fun waitIdle(expectNavigation: Boolean) {
        val start = SystemClock.elapsedRealtime()
        if (expectNavigation) { loading = true }
        lastEvent = start
        while (SystemClock.elapsedRealtime() - start < 45_000) {
            delay(150)
            if (!loading && wv.progress >= 100) break
        }
        waitStable(maxOf(4_000L, 45_000L - (SystemClock.elapsedRealtime() - start)), minQuietas = 2)
    }

    /** Espera a que la pantalla deje de cambiar (también después de un clic que trae datos sin recargar). */
    private suspend fun waitStable(maxMs: Long = 20_000, minQuietas: Int = 3) {
        val start = SystemClock.elapsedRealtime()
        delay(250)
        var last = ""; var same = 0
        var lastTexto = ""; var textoIgual = 0
        while (SystemClock.elapsedRealtime() - start < maxMs) {
            val s = if (loading) "" else eval(QUIETA)
            // "listo|pendientes|largo del texto": con algo pendiente no se da por terminada...
            val quieta = s.isNotEmpty() && s.startsWith("complete|0|") && !s.endsWith("|0")
            if (quieta && s == last) { if (++same >= minQuietas) return } else same = 0
            // ...salvo que el texto no cambie en 3 segundos (un recurso trabado no frena la lectura).
            val texto = s.substringAfterLast('|')
            if (s.startsWith("complete|") && texto != "0" && texto == lastTexto) { if (++textoIgual >= 10) return } else textoIgual = 0
            last = s; lastTexto = texto
            delay(300)
        }
    }

    private suspend fun load(url: String) {
        mainError = null
        wv.loadUrl(url)
        waitIdle(true)
    }

    private suspend fun eval(js: String): String = suspendCancellableCoroutine { cont ->
        wv.evaluateJavascript(js) { raw ->
            val s = runCatching { JSONArray("[$raw]").getString(0) }.getOrDefault("")
            if (cont.isActive) cont.resume(s)
        }
    }

    /** Bloquea lo que no hace falta para leer Guaraní: fuentes, audio/video y estadísticas de terceros. */
    private fun innecesario(u: Uri): Boolean {
        val path = (u.path ?: "").lowercase()
        val host = (u.host ?: "").lowercase()
        return NO_HACE_FALTA.containsMatchIn(path) || RASTREO.any { host == it || host.endsWith(".$it") }
    }

    private suspend fun extract(): JSONObject = runCatching { JSONObject(eval(EXTRACT)) }.getOrDefault(JSONObject())

    /** Pide a la página que muestre todo lo que tiene (historia completa, plan expandido). Devuelve si tocó algo. */
    private suspend fun reveal(): Boolean {
        if (eval(ACTIONS.replace("FASE", "1")).isEmpty()) return false
        waitStable()
        if (eval(ACTIONS.replace("FASE", "2")).isNotEmpty()) waitStable()
        return true
    }

    /** Lee el detalle de cada actividad de la página de inscripción abierta (hasta 3 minutos). */
    private suspend fun explorar(): List<Pair<String, List<String>?>> {
        val n = eval(EXPLORAR).toIntOrNull() ?: 0
        if (n == 0) return emptyList()
        val start = SystemClock.elapsedRealtime()
        var o: JSONObject? = null
        while (SystemClock.elapsedRealtime() - start < 180_000) {
            delay(400)
            o = runCatching { JSONObject(eval(EXPLORADO)) }.getOrNull() ?: continue
            if (o.optBoolean("listo")) break
        }
        return Habilitaciones.leer(o?.optJSONArray("items"))
    }

    private suspend fun login(user: String, pass: String): Boolean {
        val js = """
(function(u,p){
  var pw=document.querySelector('input[type=password]'); if(!pw) return 'nologin';
  var f=pw.form||document;
  var us=f.querySelector('input[name=usuario],input[name=user],input[name=username],input[name=email],input[type=email],input[type=text]');
  function set(el,v){ el.focus(); el.value=v; el.dispatchEvent(new Event('input',{bubbles:true})); el.dispatchEvent(new Event('change',{bubbles:true})); }
  if(us) set(us,u); set(pw,p);
  var btn=f.querySelector('button[type=submit],input[type=submit],#login,button');
  if(btn) btn.click(); else if(pw.form) pw.form.submit();
  return 'ok';
})(${JSONObject.quote(user)},${JSONObject.quote(pass)})"""
        mainError = null
        val r = eval(js)
        if (r != "ok") return false
        delay(800)
        waitIdle(false)
        return true
    }

    private fun needsLogin(d: JSONObject) = d.optBoolean("login") || d.optString("sso").isNotEmpty()

    /**
     * Entra a Guaraní. Sirve para el formulario propio de Guaraní y para el ingreso único de la
     * institución (SAML/CAS: botón «Acceder» que lleva a otra página donde se pone la contraseña).
     * Devuelve null si entró, o el estado de falla.
     */
    private suspend fun signIn(home: JSONObject, user: String, pass: String): State? {
        var d = home
        val sso = d.optString("sso")
        if (sso.isNotEmpty()) {
            load(sso)
            d = extract()
            if (!needsLogin(d)) return null           // el ingreso único ya tenía la sesión abierta
        }
        if (user.isEmpty() || pass.isEmpty()) return State.NEED_LOGIN
        if (d.optBoolean("captcha")) return State.CAPTCHA
        if (!d.optBoolean("login")) return State.LOGIN_FAILED
        // La contraseña nunca se escribe en una página sin cifrar.
        if (!Seguridad.esHttps(wv.url)) { lastError = "La página de ingreso no es segura (no usa https)"; return State.LOGIN_FAILED }
        login(user, pass)
        val after = extract()
        lastError = after.optString("error")
        if (needsLogin(after)) return if (after.optBoolean("captcha")) State.CAPTCHA else State.LOGIN_FAILED
        return null
    }
    private var lastError = ""

    suspend fun run(baseIn: String, user: String, pass: String, watched: List<String>): Result {
        build()
        try {
            load(baseIn)
            if (mainError != null) return Result(State.OFFLINE, emptyList(), emptyList(), mainError ?: "")
            var home = extract()
            // Algunas instituciones apagan Guaraní de noche y muestran un cartel en lugar del sistema.
            if (!needsLogin(home) && FUERA_DE_HORARIO.containsMatchIn(Lector.norm(home.optString("text").take(1500))))
                return Result(State.OFFLINE, emptyList(), emptyList(), "Guaraní está fuera del horario de atención de la institución")
            // Si Guaraní redirige a otra dirección (otro servidor o carpeta), se toma esa como base.
            val eff = clean(home.optString("url", baseIn))
            // (Si nos mandó a la página de ingreso de otro servidor, la base sigue siendo la de la cuenta.)
            val otroServidor = !Uri.parse(eff).host.equals(Uri.parse(baseIn).host, true)
            val base = if (otroServidor && !home.optBoolean("login")) Profiles.normalizeUrl(eff) else baseIn
            val publico = if (needsLogin(home)) menuOf(home) else emptyList()
            fun fail(st: State, msg: String = "") = Result(st, emptyList(), emptyList(), msg, menuPublico = publico)
            if (needsLogin(home)) {
                signIn(home, user, pass)?.let { return fail(it, lastError) }
                // Tras entrar, Guaraní suele dejarnos en la portada; la volvemos a cargar para leer el menú completo.
                load(base); home = extract()
                if (needsLogin(home)) return fail(State.LOGIN_FAILED, home.optString("error"))
            }
            val homeUrl = clean(home.optString("url", base))
            val pages = mutableListOf(Page.fromJson(home, homeUrl))

            val baseUri = Uri.parse(base)
            val discovered = linkedSetOf<String>()
            val links = home.optJSONArray("links") ?: JSONArray()
            for (i in 0 until links.length()) {
                val href = clean(links.optJSONArray(i)?.optString(0) ?: continue)
                if (isInternal(href, baseUri) && !EXCLUDE.containsMatchIn(href) && !sameDoc(href, homeUrl)) discovered.add(href)
            }
            if (discovered.size < 3) FALLBACK.forEach { discovered.add(base + it) }

            val toVisit = (watched + discovered).map { clean(it) }.distinct().filterNot { sameDoc(it, homeUrl) }.take(MAX_PAGES)
            val good = mutableListOf<String>()
            for (u in toVisit) {
                load(u)
                if (mainError != null) continue
                val d = extract()
                if (needsLogin(d)) {
                    // La sesión venció a mitad de camino: reintenta una vez con las credenciales.
                    if (signIn(d, user, pass) != null) return Result(State.NEED_LOGIN, pages, good)
                    load(u)
                }
                // Si no hubo nada que desplegar, lo leído recién ya es la página final (una lectura menos).
                val tocó = runCatching { reveal() }.getOrDefault(false)
                val e = if (tocó || needsLogin(d)) extract() else d
                val finalUrl = clean(e.optString("url", u))
                // Si una ruta de respaldo redirige a la portada, no existe en este Guaraní.
                if (sameDoc(finalUrl, homeUrl) && u !in watched) continue
                if (pages.any { sameDoc(it.url, finalUrl) }) continue
                if (e.optString("text").isBlank()) continue
                var page = Page.fromJson(e, finalUrl)
                if (Habilitaciones.tipoDePagina(finalUrl, page.title) != null) page = page.copy(actividades = runCatching { explorar() }.getOrDefault(emptyList()))
                pages.add(page)
                good.add(finalUrl)
            }
            return Result(State.OK, pages, good, menu = menuOf(home), menuPublico = publico)
        } finally {
            runCatching { wv.stopLoading(); wv.destroy() }
            CookieManager.getInstance().flush()
        }
    }

    private fun menuOf(d: JSONObject): List<Pair<String, String>> {
        val menu = mutableListOf<Pair<String, String>>()
        val mj = d.optJSONArray("menu") ?: JSONArray()
        for (i in 0 until mj.length()) mj.optJSONArray(i)?.let { menu.add(it.optString(0) to clean(it.optString(1))) }
        return menu.filter { it.second.startsWith("http") }.distinctBy { it.second }
    }

    private fun clean(u: String) = u.substringBefore('#').trim()
    private fun sameDoc(a: String, b: String) = a.trimEnd('/').equals(b.trimEnd('/'), ignoreCase = true)
    private fun isInternal(href: String, base: Uri): Boolean {
        val u = Uri.parse(href)
        if (u.scheme != "http" && u.scheme != "https") return false
        if (!u.host.equals(base.host, true)) return false
        val bp = base.path ?: "/"
        return (u.path ?: "/").startsWith(bp.trimEnd('/'))
    }
}
