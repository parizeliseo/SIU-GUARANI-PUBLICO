# Novedades

Las fechas son de publicación. La app siempre se firma con **la misma clave**, así que cada versión
se instala encima de la anterior y conserva cuentas, datos y ajustes.

---

## Guaraní al día 1.4 — 13 de noviembre de 2026

Se instala encima de la 1.3.2. La primera revisión después de actualizar vuelve a leer todo y no
manda avisos, para no dar novedades falsas.

- **Sonido propio para las calificaciones nuevas** (tres notas, distinto del resto) con vibración,
  en un canal nuevo *Calificaciones nuevas*. Botón en Ajustes para cambiar el tono.
- **Reportes completos:** la app pide *Historia completa* y expande el plan de estudio, y entiende
  el formato oficial de SIU Guaraní 3 (plan, historia académica, actuación provisoria, mis
  inscripciones). El tablero muestra **todas las materias del plan**, incluidas las pendientes, con
  la historia de cada una (cursada, exámenes, promoción, equivalencia) y la marca *provisoria*.
- **Avisos de calificación más claros:** «examen aprobado con 8», «cursada aprobada con 7 (queda
  el final)», «nota provisoria…». Cada nota se avisa una sola vez.
- **Compartir calificaciones** por WhatsApp, mail, etc.: todas, las de un filtro o la de un aviso.
- **Exámenes y Google Calendar:** las fechas de *Mis inscripciones* aparecen en *Próximos
  exámenes*, con aviso de fecha nueva y recordatorios una semana antes, el día anterior y el mismo
  día. *Agendar* las pasa al calendario del teléfono, sin pedir permisos.
- **PDF originales:** sección *Documentos oficiales* con los PDF y comprobantes que ofrece
  Guaraní; se descargan del sitio sin modificar y se pueden abrir o compartir. Botón para guardar
  cualquier pantalla de Guaraní como PDF.
- **Carrera e institución siempre a la vista:** la app lee la propuesta elegida y la muestra en el
  tablero, el menú, la pestaña Guaraní y los avisos. Se puede escribir otro nombre en Ajustes, y
  avisa si el usuario tiene más de una propuesta.
- **Actualización automática al abrir la app** si la última lectura tiene más de 10 minutos.
- **Diseño nuevo del tablero:** encabezado con institución y carrera, indicadores con su origen,
  buscador y filtros de materias, modo oscuro, y redibujo solo cuando cambian los datos.
- Seguridad: los datos y contraseñas tampoco viajan al pasar las apps a un teléfono nuevo.
- 29 pruebas automáticas (antes 7), con páginas de prueba según la documentación del SIU.

---

## Manual de uso — 12 de noviembre de 2026

- Nuevo **manual de uso completo** en PDF (`publicado/Manual-Guarani-al-dia.pdf`) y para leer en
  línea (`docs/manual/MANUAL.md`): instalación paso a paso, cuentas, las cuatro pestañas, avisos,
  sesión abierta, contraseña y privacidad, ajustes uno por uno y problemas frecuentes. La app no
  cambia (sigue en 1.3.2).

---

## Guaraní al día 1.3.2 — 12 de noviembre de 2026

- **Arreglo importante: no se podía escribir en Ajustes.** La lista de cuentas recargaba los
  campos sin parar y borraba lo que se iba escribiendo, así que no se podía cargar ninguna cuenta.
- La pantalla se acomoda al teclado y a los bordes del teléfono (Android 15).
- La app abre aunque falle algo del arranque en segundo plano.
- Prueba automática nueva que abre la app como recién instalada y escribe en Ajustes, para que
  esta falla no vuelva.

---

## Guaraní al día 1.3.1 — 12 de noviembre de 2026

- Se suma el **IUPA** (Instituto Universitario Patagónico de las Artes) a la lista: ya son 27
  instituciones comprobadas.

---

## Guaraní al día 1.3 — 12 de noviembre de 2026

- **Sesión siempre abierta:** la app toca Guaraní cada 8 minutos para que la sesión no venza. Si
  igual vence y la contraseña está guardada, vuelve a entrar en el momento; si no, avisa una vez.
- **Vigilancia permanente** (opcional): notificación fija que impide que Android cierre la app,
  para teléfonos con ahorro de batería agresivo. Se retoma sola al prender el teléfono.
- **Avisos por tema**, con título claro y canal propio: calificaciones nuevas, cambios en el plan
  de estudios, inscripción a exámenes abierta o cerrada, mesas de examen nuevas, inscripción a
  materias, mensajes y encuestas. Estos temas se avisan siempre; el interruptor de Ajustes solo
  apaga «otros cambios».

---

## Guaraní al día 1.2 — 12 de noviembre de 2026

La app deja de ser solo para el IUGNA: sirve para **cualquier institución que use SIU Guaraní**.
Se instala encima de la 1.1 y la cuenta que ya tenías pasa sola, con sus notas y novedades, a ser la
primera cuenta.

- **Varias cuentas:** una por facultad o institución, o más de un usuario en la misma. Cada una
  tiene sus datos, su menú y sus avisos; con varias, arriba del tablero aparece un selector.
- **Lista de 26 instituciones** comprobadas contra el sitio real, más la opción de pegar la
  dirección de cualquier otro Guaraní. Formulario en GitHub para pedir que se sume una.
- **Guardar la contraseña es opcional** y queda cifrada con el Android Keystore. Si entrás a mano
  desde la pestaña Guaraní, la app pregunta si querés guardarla.
- **Ingreso único institucional** (SAML/CAS, el botón «Acceder» de UTN-FRBA o UNQ): la app lo
  sigue sola, en segundo plano y en la pestaña Guaraní.
- Las **materias se leen solas** de la historia académica, para cualquier carrera.
- El menú público (fechas de examen, horarios, validador, recuperar contraseña) se lee de la
  portada de cada institución.
- Si dos cuentas son del mismo Guaraní, la app cierra la sesión de una antes de abrir la otra.

---

## Guaraní al día 1.1 — 12 de noviembre de 2026

Primera versión publicada: aplicación Android para el SIU Guaraní del IUGNA.

- Tablero con promedio, avance, novedades, fechas, bandeja y estado de cada materia.
- Pestaña **Menú** con todas las secciones de Guaraní: las del alumno (se leen del menú real) y
  las públicas (Fechas de Examen, Horarios de Cursadas, Validador de Certificados, ayuda y acceso).
- Guaraní completo dentro de la app, con ingreso automático, descargas y subida de archivos.
- Revisión en segundo plano cada 15 minutos a 6 horas, y avisos en cuatro canales: notas y actas;
  inscripciones, períodos y vencimientos; otros cambios; y estado de la sesión.
- Comprobación automática en GitHub que compila la app y prueba el lector de notas.
