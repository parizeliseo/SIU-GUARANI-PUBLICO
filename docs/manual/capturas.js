// Genera las capturas del manual. Uso: node capturas.js
// Tablero, menú y bienvenida son las pantallas reales de la app (assets) con datos de ejemplo.
// Ajustes y avisos son esquemas dibujados con los mismos textos de la app.
const { chromium } = require('playwright');
const path = require('path');
const A = path.resolve(__dirname, '../../app-android/app/src/main/assets') + '/';
const OUT = path.resolve(__dirname, 'img') + '/';
const W = 400, SCALE = 2;

const una = [{ id: 'c1', nombre: 'UTN Sistemas', institucion: 'UTN — Facultad Regional Buenos Aires (grado)', url: 'https://guarani.frba.utn.edu.ar/autogestion/grado/' }];
const dos = una.concat([{ id: 'c2', nombre: 'UBA CBC', institucion: 'UBA — Ciclo Básico Común (CBC)' }]);
const B = 'https://guarani.frba.utn.edu.ar/autogestion/grado/';
const datos = {
  resumen: JSON.stringify([{ promedio: 7.67, avance_pct: 18, aprobadas: 3, pendientes_final: 1, en_curso: 2, mensajes: 1, encuestas_pendientes: 0, examenes_inscriptos: 1, ultima_revision: '2026-11-12T16:40:00-03:00', sesion: 'ok' }]),
  novedades: JSON.stringify([
    { fecha: '2026-11-12T16:40:00-03:00', tipo: 'Nota', detalle: 'Física I: aprobada con 8.' },
    { fecha: '2026-11-11T09:10:00-03:00', tipo: 'Exámenes', detalle: 'Se abrió la inscripción a exámenes: Análisis Matemático II - 15/12/2026' },
    { fecha: '2026-11-09T18:25:00-03:00', tipo: 'Mensaje', detalle: 'Mensaje nuevo en Guaraní: Cambio de aula para el segundo parcial' },
    { fecha: '2026-11-05T12:00:00-03:00', tipo: 'Inicio', detalle: 'La app tomó la foto inicial de 14 páginas de Guaraní. Desde ahora avisa cada cambio.' }]),
  materias: JSON.stringify([
    { codigo: '95-1121', materia: 'Análisis Matemático I', estado: 'Aprobada', nota: 7, fecha: '2026-07-12', detalle: 'Leída de la historia académica' },
    { codigo: '95-1124', materia: 'Física I', estado: 'Aprobada', nota: 8, fecha: '2026-11-12', detalle: 'Actualizado por la app: Aprobada con 8' },
    { codigo: '95-1112', materia: 'Álgebra y Geometría Analítica', estado: 'Promocionada', nota: 8, fecha: '2026-07-20', detalle: 'Leída de la historia académica' },
    { codigo: '95-1128', materia: 'Algoritmos y Estructuras de Datos', estado: 'Regular, falta final', nota: null, fecha: '2026-11-01', detalle: 'Leída de la historia académica' },
    { codigo: '95-1122', materia: 'Análisis Matemático II', estado: 'En curso', nota: null, fecha: '2026-11-05', detalle: 'Leída de la historia académica' },
    { codigo: '95-1130', materia: 'Sistemas y Organizaciones', estado: 'En curso', nota: null, fecha: '2026-11-05', detalle: 'Leída de la historia académica' }]),
  calendario: '[]', mesas: '[]', diplomatura: '[]',
  menu: JSON.stringify(['Inscripción a Materias|cursada', 'Inscripción a Exámenes|examen', 'Plan de estudios|plan_estudio', 'Historia académica|historia_academica',
    'Mis inscripciones|mis_inscripciones', 'Mensajes|mensajes', 'Encuestas|encuestas', 'Certificados|certificados', 'Mis datos personales|datos_censales']
    .map(x => ({ titulo: x.split('|')[0], url: B + x.split('|')[1] }))),
  menu_publico: JSON.stringify([{ titulo: 'Fechas de Examen', url: B + 'fecha_examen' }, { titulo: 'Horarios de Cursadas', url: B + 'horarios_cursadas' },
    { titulo: 'Validador de Certificados', url: B + 'validador_certificados' }, { titulo: '¿Olvidaste tu contraseña?', url: B + 'acceso/recuperar' }, { titulo: 'Ayuda', url: B + 'ayuda_menu' }]),
};

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
<div class="btn">Guardar</div><div class="btn o">Revisar todas ahora</div><div class="btn o">Probar notificación</div><div class="btn o">Permitir que funcione en segundo plano</div>
<div class="sec">Avanzado (cuenta elegida)</div>
<div class="lab">Páginas de Guaraní que se vigilan (una por línea)</div>
<div class="in" style="font-size:12px;line-height:1.5">…/autogestion/grado/historia_academica<br>…/autogestion/grado/plan_estudio<br>…/autogestion/grado/examen<br>…/autogestion/grado/mensajes</div>
<div class="btn x">Guardar páginas</div><div class="btn x">Volver a tomar la foto inicial (sin avisar)</div></div>`;
const noti = (t, tx, hora, canal) => `<div style="background:#fff;border-radius:18px;padding:12px 14px;margin:0 0 8px;box-shadow:0 1px 2px rgba(0,0,0,.08)">
<div style="display:flex;gap:6px;align-items:center;font-size:11.5px;color:#5c5c57"><span style="width:16px;height:16px;border-radius:50%;background:#1F5C4A;display:inline-block"></span>Guaraní al día · ${canal} · ${hora}</div>
<div style="font-weight:600;font-size:14.5px;margin-top:5px">${t}</div><div style="font-size:13.5px;color:#3c3c38;margin-top:2px">${tx}</div></div>`;
const avisosHTML = `<div style="background:#E9E7E0;padding:14px;min-height:100%">
<div style="font-size:12px;color:#5c5c57;margin:2px 4px 10px">Notificaciones</div>
${noti('Nueva calificación en Guaraní', 'Física I: aprobada con 8.', 'ahora', 'Notas y actas')}
${noti('Se abrió la inscripción a exámenes', 'Análisis Matemático II - 15/12/2026 18:00', '9:10', 'Inscripciones y mesas de examen')}
${noti('Cambió tu plan de estudios', 'Ingeniería y Sociedad (95-1140)', 'ayer', 'Plan de estudios')}
${noti('Mensaje nuevo en Guaraní', 'Cambio de aula para el segundo parcial', 'lun', 'Mensajes y encuestas')}
${noti('Guaraní: hay que volver a entrar', 'La sesión venció. Entrá desde la pestaña Guaraní (o guardá la contraseña) para seguir recibiendo avisos.', 'dom', 'Estado de la sesión')}</div>`;

const NAV = ['Tablero', 'Menú', 'Guaraní', 'Ajustes'];
const frame = (imgPath, active, toolbar) => `<html><body style="margin:0;background:transparent;font-family:Roboto,system-ui,sans-serif">
<div style="width:${W}px;border-radius:26px;overflow:hidden;border:6px solid #1c1c1a;background:#F7F6F2">
<div style="background:#143D31;color:#fff;font-size:11px;padding:5px 16px;display:flex;justify-content:space-between"><span>16:40</span><span>▮▮▮ 86 %</span></div>
${toolbar ? `<div style="background:#1F5C4A;color:#fff;padding:12px 14px;font-size:14px;display:flex;justify-content:space-between"><span>←&nbsp;&nbsp; ${toolbar}</span><span>⌂ &nbsp;⟳</span></div>` : ''}
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
      await p.addInitScript(([accs, data]) => { window.Android = { accounts: () => JSON.stringify(accs), account: () => JSON.stringify(accs[0] || {}), get: n => data[n] || '[]',
        status: () => '12/11 16:40 · 14 páginas revisadas · 1 novedad(es)', base: () => accs.length ? accs[0].url : '', setActive() {}, newAccount() {}, checkNow() {}, openGuarani() {}, openSettings() {}, open() {} }; }, [accs, data]);
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
  await shot('01-bienvenida', 0, { file: 'dashboard.html', accs: [], data: {}, height: 420 });
  await shot('02-cuenta', 3, { html: cuentaHTML, height: 760 });
  await shot('03-tablero', 0, { file: 'dashboard.html', height: 760 });
  await shot('04-materias', 0, { file: 'dashboard.html', height: 560, scrollTo: '#materias' });
  await shot('05-menu', 1, { file: 'menu.html', height: 760 });
  await shot('06-guarani', 2, { url: 'https://siu.iugna.edu.ar/autogestion/', height: 560, toolbar: 'SIU Guaraní - Acceso' });
  await shot('07-avisos', -1, { html: avisosHTML, height: 560 });
  await shot('08-ajustes', 3, { html: generalHTML, height: 980 });
  await shot('09-dos-cuentas', 0, { file: 'dashboard.html', accs: dos, height: 330 });
  await b.close();
})();
