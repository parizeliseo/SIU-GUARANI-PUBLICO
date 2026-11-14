// Datos de ejemplo para las capturas del manual: un alumno ficticio, con el formato que guarda la app 1.4.
const B = 'https://guarani.frba.utn.edu.ar/autogestion/grado/';
const hoy = new Date(); const iso = (d) => { const x = new Date(hoy); x.setDate(x.getDate() + d); return x.toISOString().slice(0, 10); };
const M = (codigo, materia, cat, nota, fecha, detalle, anio, tipo, prov) => ({ codigo, materia, cat, estado: { aprobada: 'Aprobada', promocionada: 'Promocionada', regular: 'Regular, falta final', en_curso: 'En curso', pendiente: 'Pendiente', desaprobada: 'Desaprobada' }[cat], nota, fecha, detalle, anio, tipo: tipo || 'Materia', periodo: '', provisoria: !!prov });
exports.una = [{ id: 'c1', nombre: 'UTN Sistemas', institucion: 'UTN — Facultad Regional Buenos Aires (grado)', url: B, carrera: 'Ingeniería en Sistemas de Información', plan: 'Plan 2023', propuestas: [], usuario: '30123456' }];
exports.dos = exports.una.concat([{ id: 'c2', nombre: 'UBA CBC', institucion: 'UBA — Ciclo Básico Común (CBC)' }]);
exports.datos = {
  resumen: JSON.stringify([{ promedio: 7.67, promedio_origen: 'calculado', avance_pct: 27.3, avance_origen: 'plan', aprobadas: 3, pendientes_final: 1, en_curso: 2, pendientes: 5, total: 11, examenes_inscriptos: 2, ultima_revision: new Date(Date.now() - 4 * 60000).toISOString(), ultima_ok_ms: Date.now() - 4 * 60000, sesion: 'ok', propuesta: 'Ingeniería en Sistemas de Información', plan: 'Plan 2023' }]),
  materias: JSON.stringify([
    M('95-1121', 'Análisis Matemático I', 'aprobada', 7, '2026-07-12', 'Examen aprobado con 7 (12/07/2026) · Cursada aprobada con 6 (queda el final) (28/11/2025)', '1'),
    M('95-1124', 'Física I', 'aprobada', 8, iso(-1), 'Examen aprobado con 8 — provisoria, acta sin cerrar', '1', null, true),
    M('95-1112', 'Álgebra y Geometría Analítica', 'promocionada', 8, '2026-07-20', 'Promocionada con 8 (20/07/2026)', '1'),
    M('95-1128', 'Algoritmos y Estructuras de Datos', 'regular', 7, '2026-11-01', 'Cursada aprobada con 7 (queda el final) (01/11/2026)', '1'),
    M('95-1122', 'Análisis Matemático II', 'en_curso', null, '', 'En curso', '2'),
    M('95-1130', 'Sistemas y Organizaciones', 'en_curso', null, '', 'En curso', '1'),
    M('95-1140', 'Ingeniería y Sociedad', 'pendiente', null, '', 'Todavía no la cursaste', '2'),
    M('95-1201', 'Paradigmas de Programación', 'pendiente', null, '', 'Todavía no la cursaste', '2'),
    M('95-1205', 'Sistemas Operativos', 'pendiente', null, '', 'Todavía no la cursaste', '2'),
    M('95-1310', 'Bases de Datos', 'pendiente', null, '', 'Todavía no la cursaste', '3'),
    M('95-1599', 'Proyecto Final', 'pendiente', null, '', 'Todavía no la cursaste', '5', 'Tesis')]),
  calendario: JSON.stringify([
    { id: 'a', fecha: iso(1), hora: '18:00', evento: 'Examen: Algoritmos y Estructuras de Datos', tipo: 'examen', materia: 'Algoritmos y Estructuras de Datos', detalle: 'Llamado: Llamado 01 · Mesa: A · Lugar: Aula 214' },
    { id: 'b', fecha: iso(9), hora: '', evento: 'Evaluación: Análisis Matemático II', tipo: 'evaluacion', materia: 'Análisis Matemático II', detalle: 'Evaluación: 2.º parcial' },
    { id: 'c', fecha: iso(34), hora: '09:30', evento: 'Examen: Física I', tipo: 'examen', materia: 'Física I', detalle: 'Llamado: Llamado 02' }]),
  novedades: JSON.stringify([
    { fecha: new Date(Date.now() - 4 * 60000).toISOString(), tipo: 'Nota', detalle: 'Física I: nota provisoria, examen aprobado con 8.' },
    { fecha: new Date(Date.now() - 26 * 3600000).toISOString(), tipo: 'Exámenes', detalle: 'Examen: Algoritmos y Estructuras de Datos — ' + iso(1).split('-').reverse().slice(0, 2).join('/') + ' a las 18:00' },
    { fecha: new Date(Date.now() - 3 * 86400000).toISOString(), tipo: 'Inscripciones', detalle: 'Se abrió la inscripción a exámenes: Análisis Matemático II' },
    { fecha: new Date(Date.now() - 5 * 86400000).toISOString(), tipo: 'Mensaje', detalle: 'Mensaje nuevo en Guaraní: Cambio de aula para el segundo parcial' },
    { fecha: new Date(Date.now() - 6 * 86400000).toISOString(), tipo: 'Plan de estudios', detalle: 'Cambió tu plan de estudios: Ingeniería y Sociedad (95-1140)' },
    { fecha: new Date(Date.now() - 8 * 86400000).toISOString(), tipo: 'Inicio', detalle: 'La app tomó la foto inicial de 14 páginas de Guaraní. Desde ahora avisa cada cambio.' }]),
  documentos: JSON.stringify([
    { titulo: 'Historia académica (PDF)', url: B + 'historia_academica/generar_pdf', pagina: B + 'historia_academica', seccion: 'Historia académica', tipo: 'pdf', directo: true },
    { titulo: 'Plan de estudio (PDF)', url: B + 'plan_estudio/generar_pdf', pagina: B + 'plan_estudio', seccion: 'Plan de estudio', tipo: 'pdf', directo: true },
    { titulo: 'Actuación provisoria (PDF)', url: B + 'actuacion_provisoria/pdf', pagina: B + 'actuacion_provisoria', seccion: 'Actuación provisoria', tipo: 'pdf', directo: true },
    { titulo: 'Comprobante: Algoritmos y Estructuras de Datos (95-1128)', url: '', pagina: B + 'mis_inscripciones', seccion: 'Mis inscripciones', tipo: 'doc', directo: false }]),
  diplomatura: '[]', mesas: '[]',
  menu: JSON.stringify(['Inscripción a Materias|cursada', 'Inscripción a Exámenes|examen', 'Actuación provisoria|actuacion_provisoria', 'Agenda|agenda', 'Historia académica|historia_academica',
    'Inasistencias|inasistencias', 'Mis inscripciones|mis_inscripciones', 'Plan de estudios|plan_estudio', 'Mis datos personales|datos_censales', 'Solicitar Constancias y Certificados|certificados']
    .map(x => ({ titulo: x.split('|')[0], url: B + x.split('|')[1] }))),
  menu_publico: JSON.stringify([{ titulo: 'Fechas de Examen', url: B + 'fecha_examen' }, { titulo: 'Horarios de Cursadas', url: B + 'horarios_cursadas' },
    { titulo: 'Validador de Certificados', url: B + 'validador_certificados' }, { titulo: '¿Olvidaste tu contraseña?', url: B + 'acceso/recuperar' }, { titulo: 'Ayuda', url: B + 'ayuda_menu' }]),
};
exports.mock = `(function(accs,data){ window.__calls=[]; function rec(n){ return function(){ window.__calls.push([n].concat([].slice.call(arguments))); }; }
  window.Android={ accounts:()=>JSON.stringify(accs.map(a=>({id:a.id,nombre:a.nombre}))), account:()=>JSON.stringify(accs[0]||{}), get:n=>data[n]||'[]',
    status:()=>'13/11 10:40 · 14 páginas revisadas · 1 novedad(es)', base:()=>accs.length?accs[0].url:'', setActive:rec('setActive'), newAccount:rec('newAccount'), checkNow:rec('checkNow'),
    openGuarani:rec('openGuarani'), openSettings:rec('openSettings'), open:rec('open'), share:rec('share'), calendar:rec('calendar'), reminder:rec('reminder'), doc:rec('doc'), soundSettings:rec('soundSettings') }; })`;
