// Ejecuta sobre las páginas de prueba el MISMO JavaScript que la app corre dentro de Guaraní
// (lo saca de Scraper.kt) y guarda lo leído como JSON para las pruebas de Kotlin.
//   node probar.js        (necesita playwright)
const { chromium } = require('playwright');
const fs = require('fs'), path = require('path');
const src = fs.readFileSync(path.resolve(__dirname, '../../main/java/ar/pariz/guarani/Scraper.kt'), 'utf8');
const js = (n) => new RegExp('const val ' + n + ' = """([\\s\\S]*?)"""').exec(src)[1];
const EXTRACT = js('EXTRACT'), ACTIONS = js('ACTIONS'), EXPLORAR = js('EXPLORAR'), EXPLORADO = js('EXPLORADO');
const OUT = path.resolve(__dirname, '../resources/guarani');
const estable = async (p) => { let last = -1, same = 0; for (let i = 0; i < 40; i++) { const n = await p.evaluate(() => document.body.innerText.length);
  if (n === last) { if (++same >= 3) return; } else same = 0; last = n; await p.waitForTimeout(500); } };
(async () => {
  const b = await chromium.launch();
  const p = await b.newPage({ viewport: { width: 412, height: 900 } });
  for (const name of ['inicio', 'historia_academica', 'plan_estudio', 'actuacion_provisoria', 'mis_inscripciones']) {
    await p.goto('file://' + path.resolve(__dirname, name + '.html'));
    const antes = JSON.parse(await p.evaluate(EXTRACT));
    const c1 = await p.evaluate(ACTIONS.replace('FASE', '1'));
    if (c1) { await p.waitForTimeout(700); await estable(p); const c2 = await p.evaluate(ACTIONS.replace('FASE', '2')); if (c2) await estable(p); }
    const d = JSON.parse(await p.evaluate(EXTRACT));
    d.url = 'https://guarani.ejemplo.edu.ar/autogestion/' + name; d.links = []; d.menu = d.menu.map(m => [m[0], m[1].replace(/^file:.*\//, 'https://guarani.ejemplo.edu.ar/autogestion/').replace('.html', '')]);
    d.docs.forEach(x => { if (x.h) x.h = x.h.replace(/^file:.*\/guarani\//, 'https://guarani.ejemplo.edu.ar/autogestion/'); });
    fs.writeFileSync(path.join(OUT, name + '.json'), JSON.stringify(d, null, 1));
    console.log(name.padEnd(22), 'clic:', (c1 || '-').padEnd(18), '| líneas antes/después:', antes.text.split('\n').length, '/', d.text.split('\n').length,
      '| tablas:', d.tables.map(t => `${t.sec.slice(0,18)}[${t.rows.length}]`).join(', ') || '-', '| docs:', d.docs.length, '| propuesta:', d.propSel || '-');
  }

  // Inscripción a exámenes: la lista de materias y el detalle de cada una se piden por http (como en Guaraní).
  const http = require('http');
  const srv = http.createServer((q, r) => {
    const m = /\/elegir_materia\/(\w+)/.exec(q.url), f = m ? 'examen_' + m[1] + '.html' : 'examen.html';
    r.writeHead(200, { 'content-type': 'text/html; charset=utf-8' }); r.end(fs.readFileSync(path.resolve(__dirname, f)));
  }).listen(0);
  await new Promise(ok => srv.on('listening', ok));
  await p.goto('http://127.0.0.1:' + srv.address().port + '/3w/examen');
  const n = await p.evaluate(EXPLORAR);
  let g; for (let i = 0; i < 40; i++) { g = JSON.parse(await p.evaluate(EXPLORADO)); if (g && g.listo) break; await p.waitForTimeout(250); }
  fs.writeFileSync(path.join(OUT, 'examen_actividades.json'), JSON.stringify(g.items, null, 1));
  console.log('examen'.padEnd(22), 'actividades:', n, g.items.map(x => x.t + ' → ' + (x.l || []).join(' | ')).join('\n' + ' '.repeat(36)));
  srv.close();
  await b.close();
})();
