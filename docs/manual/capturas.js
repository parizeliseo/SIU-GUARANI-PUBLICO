// Genera las capturas del manual. Uso: node capturas.js
// Tablero, menú y bienvenida son las pantallas reales de la app (assets) con datos de ejemplo.
// Ajustes y avisos son esquemas dibujados con los mismos textos de la app.
const { chromium } = require('playwright');
const path = require('path');
const A = path.resolve(__dirname, '../../app-android/app/src/main/assets') + '/';
const OUT = path.resolve(__dirname, 'img') + '/';
const W = 400, SCALE = 2;

const { una, dos, datos, mock } = require('./datos-ejemplo.js');

const CSS = `
*{box-sizing:border-box} body{margin:0;font-family:Roboto,system-ui,sans-serif;background:#F7F6F2;color:#1E1E1C}
.p{padding:20px} h1{font-family:"Noto Serif",Georgia,serif;font-weight:400;font-size:26px;margin:0}
.sec{color:#1F5C4A;font-size:18px;margin:22px 0 4px} .lab{font-weight:700;font-size:14px;margin:14px 0 2px}
.help{font-size:12px;opacity:.65;line-height:1.35} .in{border-bottom:1px solid #8a8a84;padding:9px 2px;font-size:15px;min-height:38px}
.in.ph{color:#9a9a94} .row{display:flex;gap:8px;align-items:center} .sp{flex:1;border-bottom:1px solid #8a8a84;padding:10px 2px;font-size:15px;display:flex;justify-content:space-between}
.btn{background:#1F5C4A;color:#fff;border-radius:999px;text-align:center;padding:11px;font-size:14px;font-weight:500;margin-top:10px}
.btn.o{background:transparent;color:#1F5C4A;border:1px solid #9bb5ab} .btn.t{background:#DCEAE4;color:#143D31;padding:9px 16px;margin:0}
.btn.x{background:transparent;color:#1F5C4A;text-align:left;padding:8px 0}
.sw{display:flex;justify-content:space-between;align-items:center;padding:10px 0;font-size:14.5px;gap:12px}
.tg{width:46px;height:26px;border-radius:13px;background:#cfcfc8;position:relative;flex:none} .tg:after{content:"";position:absolute;top:4px;left:4px;width:18px;height:18px;border-radius:50%;background:#fff}
.tg.on{background:#1F5C4A} .tg.on:after{left:24px}
.ck{display:flex;gap:10px;align-items:center;font-size:14.5px;padding:10px 0} .bx{width:20px;height:20px;border-radius:4px;background:#1F5C4A;color:#fff;font-size:14px;display:flex;align-items:center;justify-content:center}
.num{display:inline-flex;width:22px;height:22px;border-radius:50%;background:#C8501E;color:#fff;font-size:12px;font-weight:700;align-items:center;justify-content:center;margin-right:6px;vertical-align:middle}
`;
const cuentaHTML = `<div class="p"><h1>Ajustes</h1>
<div class="sec">Mis cuentas de SIU Guaraní</div>
<div class="help">Podés tener varias: una por institución o facultad, o más de un usuario en la misma. La app las revisa a todas.</div>
<div class="row" style="margin-top:8px"><div class="sp"><span>(cuenta nueva)</span><span>▾</span></div><div class="btn t">+ Nueva</div></div>
<div class="help" style="margin-top:6px">Elegí tu institución, poné tu usuario y tocá «Agregar cuenta».</div>
<div class="lab"><span class="num">1</span>Institución o facultad</div><div class="in">UTN — Facultad Regional Buenos Aires (grado)</div>
<div class="lab"><span class="num">2</span>Dirección de SIU Guaraní (autogestión)</div><div class="in" style="font-size:13.5px">https://guarani.frba.utn.edu.ar/autogestion/grado/</div>
<div class="lab"><span class="num">3</span>Nombre de esta cuenta</div><div class="in">UTN Sistemas</div>
<div class="lab">Carrera o propuesta</div><div class="in ph">Se completa sola al leer Guaraní</div>
<div class="lab"><span class="num">4</span>Usuario</div><div class="in">30123456</div>
<div class="lab"><span class="num">5</span>Contraseña</div><div class="in">••••••••••</div>
<div class="ck"><div class="bx">✓</div><span><span class="num">6</span>Guardar la contraseña en este teléfono</span></div>
<div class="btn"><span class="num">7</span>Agregar cuenta y revisar</div></div>`;
const generalHTML = `<div class="p" style="padding-top:6px">
<div class="sec">Revisiones y avisos (todas las cuentas)</div>
<div class="help">Siempre se avisa: calificaciones nuevas, cambios en el plan de estudios, inscripción a exámenes y a materias, mesas de examen, mensajes y encuestas.</div>
<div class="lab">Revisar Guaraní cada</div><div class="sp" style="flex:none"><span>30 minutos</span><span>▾</span></div>
<div class="sw"><span>Avisarme también de cualquier otro cambio</span><div class="tg on"></div></div>
<div class="sw"><span>Revisar solo con Wi-Fi</span><div class="tg"></div></div>
<div class="sw"><span>Mantener la sesión abierta</span><div class="tg on"></div></div>
<div class="help">La app toca Guaraní cada 8 minutos para que la sesión no venza. Si igual vence y la contraseña está guardada, vuelve a entrar sola.</div>
<div class="sw"><span>Vigilancia permanente (notificación fija)</span><div class="tg"></div></div>
<div class="help">Activala si los avisos te llegan tarde o no llegan: deja una notificación fija que impide que Android cierre la app. Gasta un poco más de batería.</div>
<div class="btn">Guardar</div><div class="btn o">Revisar todas ahora</div><div class="btn o">Probar notificación</div><div class="btn o">Sonido de las calificaciones</div><div class="btn o">Permitir que funcione en segundo plano</div>
<div class="sec">Avanzado (cuenta elegida)</div>
<div class="lab">Páginas de Guaraní que se vigilan (una por línea)</div>
<div class="in" style="font-size:12px;line-height:1.5">…/autogestion/grado/historia_academica<br>…/autogestion/grado/plan_estudio<br>…/autogestion/grado/examen<br>…/autogestion/grado/mensajes</div>
<div class="btn x">Guardar páginas</div><div class="btn x">Volver a tomar la foto inicial (sin avisar)</div></div>`;
const noti = (t, tx, hora, canal, accion) => `<div style="background:#fff;border-radius:18px;padding:12px 14px;margin:0 0 8px;box-shadow:0 1px 2px rgba(0,0,0,.08)">
<div style="display:flex;gap:6px;align-items:center;font-size:11.5px;color:#5c5c57"><span style="width:16px;height:16px;border-radius:50%;background:#1F5C4A;display:inline-block"></span>Guaraní al día · ${canal} · ${hora}</div>
<div style="font-weight:600;font-size:14.5px;margin-top:5px">${t}</div><div style="font-size:13.5px;color:#3c3c38;margin-top:2px">${tx}</div>
${accion ? `<div style="margin-top:8px;font-size:13px;font-weight:600;color:#1F5C4A">${accion}</div>` : ''}</div>`;
const avisosHTML = `<div style="background:#E9E7E0;padding:14px;min-height:100%">
<div style="font-size:12px;color:#5c5c57;margin:2px 4px 10px">Notificaciones</div>
${noti('Nueva calificación en Guaraní 🔔', 'Física I: nota provisoria, examen aprobado con 8.', 'ahora', 'Calificaciones nuevas', 'COMPARTIR')}
${noti('Nueva fecha de examen', 'Algoritmos y Estructuras de Datos — sábado 14/11 a las 18:00', '9:10', 'Inscripciones y mesas de examen', 'AGENDAR')}
${noti('Mañana: Examen: Algoritmos y Estructuras de Datos', 'sábado 14/11 a las 18:00 · Lugar: Aula 214', '8:31', 'Recordatorios de examen', 'AGENDAR')}
${noti('Se abrió la inscripción a exámenes', 'Análisis Matemático II - 15/12/2026 18:00', 'ayer', 'Inscripciones y mesas de examen')}
${noti('Cambió tu plan de estudios', 'Ingeniería y Sociedad (95-1140)', 'lun', 'Plan de estudios')}
${noti('Guaraní: hay que volver a entrar', 'La sesión venció. Entrá desde la pestaña Guaraní (o guardá la contraseña) para seguir recibiendo avisos.', 'dom', 'Estado de la sesión')}</div>`;

const NAV = ['Tablero', 'Menú', 'Guaraní', 'Ajustes'];
const frame = (imgPath, active, toolbar) => `<html><body style="margin:0;background:transparent;font-family:Roboto,system-ui,sans-serif">
<div style="width:${W}px;border-radius:26px;overflow:hidden;border:6px solid #1c1c1a;background:#F7F6F2">
<div style="background:#143D31;color:#fff;font-size:11px;padding:5px 16px;display:flex;justify-content:space-between"><span>16:40</span><span>▮▮▮ 86 %</span></div>
${toolbar ? `<div style="background:#1F5C4A;color:#fff;padding:8px 14px;font-size:14px;display:flex;justify-content:space-between;align-items:center"><span>←&nbsp;&nbsp; <span style="display:inline-block;vertical-align:middle">${toolbar}<br><span style="font-size:11px;color:#CFE3DA">UTN — Facultad Regional Buenos Aires (grado) · Ingeniería en Sistemas de Información</span></span></span><span>⤓ ⌂ ⟳</span></div>` : ''}
<img src="data:image/png;base64,${require('fs').readFileSync(imgPath).toString('base64')}" style="display:block;width:100%">
<div style="display:${active < 0 ? 'none' : 'flex'};background:#EFEDE6;border-top:1px solid #dcd9cf">${NAV.map((n, i) => `<div style="flex:1;text-align:center;padding:9px 0 10px;font-size:11.5px;${i === active ? 'color:#143D31;font-weight:700' : 'color:#6b6a64'}"><div style="width:44px;height:22px;border-radius:11px;margin:0 auto 3px;${i === active ? 'background:#CFE3DA' : ''}"></div>${n}</div>`).join('')}</div>
</div></body></html>`;

(async () => {
  const b = await chromium.launch();
  const shot = async (name, active, { file, html, url, accs = una, data = datos, height = 700, toolbar, scrollTo }) => {
    const ctx = await b.newContext({ viewport: { width: W, height }, deviceScaleFactor: SCALE, isMobile: true, hasTouch: true,
      userAgent: 'Mozilla/5.0 (Linux; Android 14; Pixel 7) AppleWebKit/537.36 Chrome/124 Mobile Safari/537.36' });
    const p = await ctx.newPage();
    if (file) {
      await p.addInitScript(`${mock}(${JSON.stringify(accs)},${JSON.stringify(data)})`);
      await p.goto('file://' + A + file);
    } else if (url) { await p.goto(url, { timeout: 40000 }); await p.waitForTimeout(2500); }
    else await p.setContent(`<meta name="viewport" content="width=device-width, initial-scale=1"><style>${CSS}</style>${html}`);
    await p.waitForTimeout(400);
    if (scrollTo) await p.evaluate(s => document.querySelector(s).scrollIntoView(), scrollTo);
    const tmp = OUT + '_tmp_' + name + '.png';
    await p.screenshot({ path: tmp });
    await ctx.close();
    const c2 = await b.newContext({ viewport: { width: W + 12, height: 100 }, deviceScaleFactor: SCALE });
    const f = await c2.newPage(); await f.setContent(frame(tmp, active, toolbar)); await f.waitForTimeout(200);
    await f.screenshot({ path: OUT + name + '.png', fullPage: true, omitBackground: true });
    await c2.close(); require('fs').unlinkSync(tmp);
    console.log('ok', name);
  };
  await shot('01-bienvenida', 0, { file: 'dashboard.html', accs: [], data: {}, height: 640 });
  // 02-cuenta y 08-ajustes son capturas nativas (CapturaTest).
  if (0) await shot('02-cuenta', 3, { html: cuentaHTML, height: 760 });
  await shot('03-tablero', 0, { file: 'dashboard.html', height: 760 });
  await shot('04-materias', 0, { file: 'dashboard.html', height: 640, scrollTo: '#materias' });
  await shot('05-menu', 1, { file: 'menu.html', height: 760 });
  await shot('06-guarani', 2, { url: 'https://siu.iugna.edu.ar/autogestion/', height: 560, toolbar: 'SIU Guaraní - Acceso' });
  await shot('07-avisos', -1, { html: avisosHTML, height: 640 });
  if (0) await shot('08-ajustes', 3, { html: generalHTML, height: 1080 });
  await shot('09-dos-cuentas', 0, { file: 'dashboard.html', accs: dos, height: 470 });
  await shot('10-examenes', 0, { file: 'dashboard.html', height: 560, scrollTo: '#fechas' });
  await shot('11-documentos', 0, { file: 'dashboard.html', height: 520, scrollTo: '#docs' });
  await b.close();
})();
