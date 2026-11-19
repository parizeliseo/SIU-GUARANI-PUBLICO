# Novedades

Las fechas son de publicación. La app siempre se firma con **la misma clave**, así que cada versión
se instala encima de la anterior y conserva cuentas, datos y ajustes.

---

## Guaraní al día 1.6 — 18 de noviembre de 2026

Se instala encima de la 1.5 y conserva cuentas, contraseñas y datos. Revisión completa: más rápida,
con más capacidad y al día con Android 16.

**Más rápida**
- Cada página se lee apenas termina de cargar y queda quieta, en vez de esperar 2,2 segundos fijos.
  La app sabe cuándo terminó porque cuenta las consultas, scripts y estilos que todavía se están
  descargando (Guaraní 3 dibuja el formulario en etapas después de cargar).
- Las materias de *Inscripción a exámenes / a materias* se leen de a cuatro a la vez: 40 materias
  pasan de unos 22 segundos a unos 4.
- El visor oculto ya no descarga fuentes, audio, video ni estadísticas de terceros.
- Una lectura menos por página cuando no hay nada que desplegar.
- La clave de cifrado de la contraseña se prepara una sola vez (antes, en cada lectura).

**Más capacidad**
- Hasta 40 páginas por cuenta (antes 30) y 150 materias por página de inscripción (antes 60).
- Historial de novedades de hasta 600 entradas (antes 300).
- Los datos de cada cuenta van en archivos propios con escritura atómica y copia en memoria, en vez
  de un único archivo de preferencias que se reescribía entero en cada cambio.
- Con muchas cuentas, se reparte el tiempo: primero la que hace más tiempo que no se revisa, y
  ninguna se empieza si no alcanza el tiempo que Android da a un trabajo en segundo plano.

**Al día**
- Destino Android 16 (API 36), el que pide Google Play desde agosto de 2026; compila con Android 17.
- Android Gradle Plugin 9.5, Gradle 9.9, AndroidX Core 1.19, Material 1.14, WorkManager 2.12,
  corrutinas 1.11, Robolectric 4.18.
- La contraseña se cifra con AES-256-GCM directo sobre el Android Keystore. Reemplaza a
  *EncryptedSharedPreferences*, que Google declaró obsoleto; lo guardado se pasa solo al formato nuevo.

**Más instituciones y mejor diagnóstico**
- Se agregó **UNDEF — Facultad de la Defensa Nacional (FADENA)**. Las 28 instituciones de la lista se
  probaron contra los sitios reales.
- Si Guaraní está fuera del horario de atención (como la UNRN de noche), la app lo dice así y no lo
  toma como un cambio.
- 8 pruebas nuevas (51 en total); la compilación automática ahora también corre el análisis de código.

---

## Guaraní al día 1.5 — 15 de noviembre de 2026

Se instala encima de la 1.4.2.

- **Aviso cuando se habilita una inscripción en cualquier materia.** En *Inscripción a exámenes* e
  *Inscripción a materias*, la app ahora entra a leer cada materia (como hace la UNDEF, que lista
  todas y dice «No hay mesas habilitadas para esta actividad») y avisa apenas una tiene mesa o
  comisión: *«Inscripción a examen abierta: Geopolítica»*, con el llamado y la fecha. También avisa
  si aparece otra mesa en una materia que ya tenía. Al tocar el aviso se abre esa inscripción.
- La app solo lee: nunca inscribe ni da de baja. Si una lectura falla, conserva el estado anterior
  y no da avisos falsos.
- **Tablero:** recuadro *Inscripciones abiertas ahora* con el botón *Inscribirme*.
- 6 pruebas nuevas (43 en total), con páginas de ejemplo armadas como la inscripción a exámenes real.

---

## Guaraní al día 1.4.2 — 14 de noviembre de 2026

Se instala encima de la 1.4.1. Es una versión de seguridad, para que la app sea de confianza en
cualquier Android.

- **Solo https en toda la app**, con certificados del sistema. Las direcciones `http://` se rechazan
  en Ajustes.
- **La contraseña se completa solo en páginas https** de la institución o en el ingreso único al que
  Guaraní redirige en ese momento (antes valía cualquier página abierta en los 2 minutos siguientes).
- **Los botones de las notificaciones van firmados** con una clave aleatoria de la instalación: otra
  app no puede abrir Guaraní al día para compartir o agendar textos propios.
- Visores web sin acceso a archivos, con *Navegación segura* y sin contenido mixto; desde Guaraní
  solo se abren afuera enlaces de correo, teléfono, SMS y mapas.
- Firma con los esquemas v2 y v3 de Android.
- README con la huella del certificado y la suma SHA-256 del APK; sección *Seguridad*.
- 7 pruebas automáticas nuevas de seguridad (37 en total).

---

## Guaraní al día 1.4.1 — 14 de noviembre de 2026

Se instala encima de la 1.4. Es una versión de diseño: todo se ve más ordenado y más claro.

- **Ajustes en tarjetas:** Cuenta, Datos de la cuenta, Revisiones, Avisos y Avanzado. Cada campo
  tiene ícono y una ayuda corta debajo, y la carrera leída de Guaraní se ve en el campo.
- **Las opciones de Revisiones y Avisos se guardan solas.** Ya no hay un botón «Guardar» aparte.
- Botones con nombres más claros: *Guardar cambios y revisar*, *Probar una notificación*,
  *Revisar todas las cuentas ahora*, *Empezar de cero sin avisar*.
- **Tablero:** barra fija para saltar a Resumen, Exámenes, Materias, Novedades y Documentos, con
  la cantidad de cada uno; cada sección con su ícono.
- **Menú:** mismo encabezado que el tablero, con institución y carrera; buscador; las secciones
  del alumno agrupadas en Inscripciones, Reportes, Trámites y Comunicación, cada una con ícono.
- Colores propios de la app en claro y oscuro, y barra inferior con los nombres siempre visibles.
- Manual actualizado con capturas reales de Ajustes.

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
