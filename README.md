# Gestión Integral de Espacios

Proyecto académico del ciclo de Formación Profesional de Desarrollo
de Aplicaciones Web.

La aplicación tiene como objetivo gestionar incidencias en espacios
comunitarios, coordinando a vecinos, administradores y técnicos para
registrar problemas y realizar su seguimiento hasta la resolución.

## Estado actual

El proyecto se encuentra en desarrollo. La versión actual incluye:

- Backend con Spring Boot y Maven.
- Entidades JPA del núcleo: Rol, Usuario, Comunidad, UsuarioComunidad,
  Espacio, Incidencia, HistorialIncidencia y Evidencia.
- Enumeraciones de estados, prioridades y tipos de evidencia.
- Repositorios JPA para incidencias, pertenencias, presupuestos, reuniones y costes.
- DTO de entrada y salida, con validaciones para registrar incidencias.
- Gestión de errores de validación, recursos inexistentes y operaciones
  no permitidas.
- Autenticación con usuarios de MySQL, contraseñas BCrypt y sesiones.
- Protección CSRF.
- Alta de incidencias con registro de su primer evento de historial.
- Listado, detalle e historial cronológico de las incidencias accesibles al vecino autenticado.
- Consulta de espacios de las comunidades con pertenencia activa para vecinos y administradores.
- Perfil demo para crear una cuenta de prueba.
- Pruebas de integración del servicio de alta con MySQL.

El alta está habilitada para vecinos y administradores con cuenta activa y
pertenencia activa a la comunidad del espacio. El vecino que registra el aviso
queda como verificador inicial. El administrador indica vecinoVerificadorId,
que debe corresponder a un vecino activo con pertenencia activa a esa comunidad.
El administrador queda registrado como reportante y actor del evento CREACION.

El listado, el detalle y el historial permiten consultar todas las incidencias
de las comunidades donde el vecino tiene una pertenencia activa, incluidas
las creadas por otros vecinos. También puede consultar el administrador con
pertenencia activa. La cuenta debe estar activa y tener rol USUARIO o ADMINISTRADOR.
Este permiso de consulta no concede permisos de modificación.

## Funcionalidades pendientes

- Frontend y pantallas propias de la aplicación.
- Registro y administración de usuarios.
- Integración de las operaciones implementadas con las pantallas del frontend.
- Autenticación mediante JWT, prevista inicialmente.
- Ampliación de pruebas de seguridad, API y consultas.

La autenticación actual utiliza sesiones de servidor.
Las entidades y tablas existentes no implican que todos sus procesos
funcionales estén implementados.

## Tecnologías

- Java 21.
- Spring Boot 4.1.1.
- Spring Data JPA e Hibernate.
- Spring Security.
- Jakarta Validation.
- MySQL; versión observada en las pruebas: 8.0.46.
- Maven Wrapper.
- JUnit y Spring Boot Test.
- Git y GitHub.
- Postman para las comprobaciones manuales de la API.

## Organización del proyecto

```text
backend/
  src/main/java/com/gestioncomunidad/backend/
    config/          Configuración de seguridad y datos de demostración.
    controller/      Rutas HTTP de sesión, CSRF e incidencias.
    dto/             Datos de entrada, respuestas y errores.
    entity/          Entidades JPA y enumeraciones.
    exception/       Excepciones y manejador común.
    repositories/    Acceso a datos mediante Spring Data JPA.
    service/         Reglas de negocio y carga de usuarios para autenticación.
  src/main/resources/
    application.properties
  src/test/java/com/gestioncomunidad/backend/
    BackendApplicationTests.java
    IncidenciaServiceTests.java
  pom.xml
  mvnw
  mvnw.cmd

database/
  0_crear_base_datos.sql
  01_estructura_inicial.sql
  02_integridad_evidencias.sql
  03_estado_facturacion_costes.sql
  04_historial_pertenencias.sql
  05_reuniones_actas.sql

README.md
.gitignore
```

## Requisitos previos

- JDK 21 y variable JAVA_HOME configurada.
- Servidor MySQL y un cliente como MySQL Workbench.
- Git para descargar el repositorio.
- Conexión a Internet para descargar las dependencias de Maven.
- Postman para reproducir las comprobaciones de la API.

El proyecto incluye Maven Wrapper, por lo que no es necesario instalar
Maven por separado. Las instrucciones siguientes utilizan Windows
y PowerShell.

## Descargar el proyecto

```powershell
git clone https://github.com/gabrieleo93/gestion-integral-espacios.git
cd gestion-integral-espacios
```

Si ya se dispone de una copia local, no es necesario clonarlo de nuevo.

## Preparar la base de datos

Conectarse al servidor desde MySQL Workbench y ejecutar los scripts
de la carpeta database en este orden:

1. 0_crear_base_datos.sql
2. 01_estructura_inicial.sql
3. 02_integridad_evidencias.sql
4. 03_estado_facturacion_costes.sql
5. 04_historial_pertenencias.sql
6. 05_reuniones_actas.sql

En una instalación existente, ejecutar solo los scripts que falten, sin repetir
los anteriores. El cuarto aplica la decisión de facturación en costes y el quinto
añade el historial de solicitudes de pertenencia. La migración 04 depende de las
tablas de la 01, no de la migración de costes; si la 03 necesita revisión de datos,
puede aplicarse la 04 por separado. Usar una cuenta administradora de MySQL con
permiso CREATE. La migración 05 crea reuniones y actas y depende de las tablas
iniciales, no de la 03. El backend usa ddl-auto=validate y necesita todas estas tablas al arrancar.

### Estado de facturación de los costes

La columna final es `estado_facturacion`:

| Valor en la base de datos | Etiqueta de la pantalla |
|---|---|
| PENDIENTE_FACTURAR | Pendiente de facturar |
| FACTURADO | Facturado |

El valor inicial es PENDIENTE_FACTURAR. El módulo permite registrar costes reales,
editar los pendientes y marcarlos como FACTURADO. No genera documentos de factura
ni registra pagos; los presupuestos permanecen separados de los costes reales.

La migración 03 solo convierte automáticamente una tabla de costes vacía.
Si existen registros, se detiene antes del ALTER TABLE y conserva los datos:
el antiguo estado de pago no permite determinar si hay una factura emitida.
En ese caso hay que revisar los registros y preparar su conversión explícita.
Si el cliente SQL detiene la ejecución al recibir ese error, puede quedar
creado el procedimiento auxiliar `migrar_estado_facturacion_costes`;
retirarlo con `DROP PROCEDURE IF EXISTS migrar_estado_facturacion_costes;`
antes de un nuevo intento acordado. No borrar costes para forzar la migración.

Ejecutarlos sobre una instalación nueva. Los scripts de estructura
y modificación no están preparados para repetirse sobre una base
de datos ya inicializada.

La base de datos se llama gestion_espacios. El script inicial crea
los roles ADMINISTRADOR, USUARIO y TECNICO.

El segundo script de estructura adicional vincula cada evidencia
con un evento de historial de su misma incidencia mediante una
clave foránea compuesta.

## Configurar y ejecutar el backend

La conexión está configurada para MySQL en localhost, puerto 3306.
El usuario de MySQL debe tener acceso a gestion_espacios.

Desde la raíz del proyecto, en PowerShell:

```powershell
$env:DB_USERNAME = 'tu_usuario_mysql'
$env:DB_PASSWORD = 'tu_contraseña_mysql'

cd backend
.\mvnw.cmd spring-boot:run
```

Sustituir los valores de ejemplo por las credenciales locales.
Las variables se aplican a esa terminal; deben configurarse de nuevo
si se abre otra. No guardar credenciales reales en el repositorio.

El backend utiliza el puerto 8080 de forma predeterminada.
Hibernate valida el mapeo contra las tablas existentes; no crea
automáticamente la estructura de la base de datos.

Comprobar que aparece Started BackendApplication y que el arranque
termina sin errores. Para detenerlo, pulsar Ctrl+C.

No existe una página de inicio en la ruta /. Acceder a ella puede
mostrar un 404 aunque el backend esté funcionando.

## Crear el usuario de demostración

Con MySQL preparado y las variables de conexión configuradas,
ejecutar desde backend:

```powershell
$env:DEMO_PASSWORD = 'elige_una_contraseña_de_prueba'
.\mvnw.cmd "-Dspring-boot.run.profiles=demo" spring-boot:run
```

El perfil demo crea la cuenta:

- Correo: vecino.demo@example.test
- Rol: USUARIO
- Estado: ACTIVO

La contraseña se toma de DEMO_PASSWORD y se guarda codificada con BCrypt.

Si el correo ya existe, no se modifica la cuenta ni su contraseña.
Cambiar DEMO_PASSWORD no cambia la contraseña de una cuenta existente.

El perfil demo no crea comunidades, espacios ni pertenencias.
Una vez creada la cuenta, se puede detener el backend y arrancarlo
normalmente sin activar este perfil.

## Preparar la comunidad y el espacio de demostración

Después de crear la cuenta demo, ejecutar el siguiente bloque una sola
vez desde MySQL Workbench. Los datos permanecerán guardados.

No repetirlo tras completarlo: crearía otra comunidad y otro espacio.
Si ocurre un error antes del COMMIT, detener la ejecución y ejecutar
ROLLBACK antes de revisar la causa.

```sql
USE gestion_espacios;

START TRANSACTION;

INSERT INTO comunidades (nombre, direccion, ciudad)
VALUES (
    'Comunidad de demostración',
    'Calle de prueba 1',
    'Madrid'
);

SET @comunidad_demo = LAST_INSERT_ID();

INSERT INTO espacios (nombre, tipo, descripcion, id_comunidad)
VALUES (
    'Portal principal',
    'PORTAL',
    'Espacio para las pruebas de la aplicación',
    @comunidad_demo
);

SET @espacio_demo = LAST_INSERT_ID();

INSERT INTO usuarios_comunidades (
    id_usuario,
    id_comunidad,
    estado
)
SELECT
    id_usuario,
    @comunidad_demo,
    'ACTIVO'
FROM usuarios
WHERE email = 'vecino.demo@example.test';

COMMIT;

SELECT
    u.email,
    uc.estado AS estado_pertenencia,
    c.id_comunidad,
    e.id_espacio,
    e.nombre AS espacio
FROM usuarios u
JOIN usuarios_comunidades uc
    ON uc.id_usuario = u.id_usuario
JOIN comunidades c
    ON c.id_comunidad = uc.id_comunidad
JOIN espacios e
    ON e.id_comunidad = c.id_comunidad
WHERE u.email = 'vecino.demo@example.test'
  AND e.id_espacio = @espacio_demo;
```

Anotar el id_espacio obtenido: se utilizará en la petición de alta.
Los identificadores dependen de cada instalación y no deben suponerse.

## Rutas disponibles

| Método | Ruta | Función |
|---|---|---|
| GET | /api/csrf | Obtener un token CSRF. Accesible antes del login. |
| GET | /login | Mostrar el formulario de acceso de Spring Security. |
| POST | /login | Iniciar sesión mediante formulario. Requiere CSRF. |
| GET | /api/sesion | Consultar el correo y las autoridades de la sesión. |
| POST | /api/incidencias | Crear una incidencia. Requiere sesión y CSRF. |
| GET | /api/incidencias | Listar los casos accesibles del vecino conectado. |
| GET | /api/espacios | Listar espacios de las comunidades con pertenencia activa del vecino o administrador. |
| GET | /api/incidencias/{id} | Consultar el detalle de una incidencia accesible para el vecino. |
| GET | /api/incidencias/{id}/historial | Consultar sus eventos por fecha e identificador, con los mismos permisos del detalle. |
| PUT | /api/incidencias/{id}/datos | Editar título, descripción y prioridad como creador o administrador de la comunidad. Requiere sesión y CSRF. |

La identificación del autor se obtiene de la sesión.
El cliente no envía el identificador del usuario reportante.

## Probar la API desde Postman

Utilizar siempre http://localhost:8080 y mantener habilitada la gestión
de cookies. Seleccionar No Auth: la autenticación se conserva mediante
la cookie de sesión, no mediante un token Bearer.

Desactivar Automatically follow redirects para distinguir las
redirecciones de las respuestas finales.

### 1. Obtener el token previo al login

Enviar:

```text
GET http://localhost:8080/api/csrf
```

Se espera 200 con los campos headerName, parameterName y token.
Conservar la cookie recibida y copiar el token.

### 2. Iniciar sesión

Enviar:

```text
POST http://localhost:8080/login
```

En Body, seleccionar x-www-form-urlencoded:

| Campo | Valor |
|---|---|
| username | vecino.demo@example.test |
| password | La contraseña configurada al crear la cuenta demo |
| _csrf | El token recién obtenido |

Se espera una redirección 302. Revisar el encabezado Location:
una redirección a /login?error indica que las credenciales fueron rechazadas.

Si Postman sigue automáticamente una redirección a /, puede mostrar
un 404 porque esa página no existe. Esto no basta para determinar
si la autenticación fue correcta.

### 3. Comprobar la sesión

Enviar:

```text
GET http://localhost:8080/api/sesion
```

Debe devolver 200 y un JSON con el correo del usuario y ROLE_USUARIO.

### 4. Obtener un nuevo token CSRF

Repetir:

```text
GET http://localhost:8080/api/csrf
```

Después del login hay que obtener un token nuevo.
Conservar la cookie de la sesión y utilizar este nuevo token en el POST.

### 5. Crear una incidencia

Enviar:

```text
POST http://localhost:8080/api/incidencias
```

Añadir el encabezado X-CSRF-TOKEN con el token obtenido después del login.

En Body, seleccionar raw y JSON. El siguiente ejemplo utiliza el
espacio 9 de la comprobación local; sustituirlo por el id_espacio
obtenido al preparar los datos de la instalación:

```json
{
  "titulo": "Luz del portal averiada",
  "descripcion": "La luz no se enciende al pulsar el interruptor.",
  "prioridad": "MEDIA",
  "espacioId": 9
}
```

Se espera 201 Created con la incidencia en estado PENDIENTE.
El reportante queda como verificador y no hay técnico asignado.

Cada POST correcto crea una incidencia nueva.

### 6. Consultar las incidencias

Enviar:

```text
GET http://localhost:8080/api/incidencias
```

Se espera 200 con una lista JSON. El GET no necesita token CSRF,
pero sí la cookie de una sesión autenticada.

Se devuelven los casos de todas las comunidades donde el vecino tenga
pertenencia activa, ordenados por fecha de creación e ID descendentes. Si no hay casos accesibles,
la respuesta es una lista vacía.

## Pruebas automatizadas

### Órdenes 11 y 12: reuniones, actas, costes y facturación

El administrador con cuenta y pertenencia activas crea reuniones de su comunidad
con título, descripción opcional, fecha futura y lugar. Publica un acta en texto
una vez llegada la fecha de reunión. Solo existe un acta por reunión y no se
edita ni sustituye después de publicarla. Se conservan autor y fecha.
Vecinos y administradores activos de la comunidad pueden consultar; los técnicos
y los usuarios ajenos no tienen acceso a estos módulos.

| Método | Ruta | Operación |
|---|---|---|
| GET / POST | `/api/comunidades/{id}/reuniones` | Listar / crear reunión |
| GET | `/api/reuniones/{id}` | Consultar reunión |
| GET / POST | `/api/reuniones/{id}/acta` | Consultar / publicar acta |
| GET / POST | `/api/incidencias/{id}/costes` | Listar / registrar costes reales |
| GET | `/api/incidencias/{id}/costes/resumen` | Totales, pendientes y facturados |
| PUT | `/api/costes/{id}` | Editar concepto e importe pendiente |
| POST | `/api/costes/{id}/facturacion` | Marcar FACTURADO |

Las escrituras requieren sesión administrativa y CSRF. Los costes admiten
importes no negativos con hasta dos decimales. No se modifican costes de
incidencias RECHAZADAS ni se editan costes FACTURADOS. Repetir la marca de
facturación no duplica el historial. Cada alta, edición efectiva y facturación
registra un evento con actor en el historial de la incidencia, en la misma
transacción: si falla el historial, también se revierte el cambio económico.
Los importes se suman con BigDecimal para conservar la precisión decimal.

Los bloqueos y restricciones impiden publicar actas duplicadas o duplicar el
evento de facturación ante peticiones simultáneas. Los siete nuevos recorridos
de OrdenesHttpMysqlTests comprueban permisos, CSRF, validaciones, consulta,
inmutabilidad, totales, concurrencia y reversión de costes ante fallo del historial.
Las pruebas crean datos propios en MySQL de desarrollo y los eliminan al terminar.

Validación del 12 de noviembre de 2026, después de aplicar las migraciones 03 y 05:
76 pruebas superadas, 0 fallos, 0 errores y 0 omitidas. Incluye 44 recorridos
HTTP/MySQL (7 nuevos de estos bloques), 29 pruebas de permisos y 3 del servicio
de alta. El arranque con validación del esquema y las operaciones reales sobre
reuniones, actas y costes verifican que las migraciones están aplicadas.

Este alcance no incluye borradores de actas, archivos PDF, avisos de reuniones,
emisión fiscal de facturas ni cobros.

### Orden 10: presupuestos y votación

El técnico asignado crea el presupuesto porque estima la reparación. El
administrador de la comunidad consulta esa propuesta y abre la votación.
Ambos requieren cuenta y pertenencia activas; la incidencia debe estar ASIGNADA
o EN_PROCESO al crear el presupuesto y al abrir su votación. No se añade un canal
externo de envío: el administrador recibe las propuestas mediante el listado API.

| Método y ruta | Uso y respuesta |
|---|---|
| POST /api/incidencias/{id}/presupuestos | Técnico asignado: crea propuesta PENDIENTE; 201. |
| GET /api/incidencias/{id}/presupuestos | Listado para vecinos/administradores activos de la comunidad y técnico asignado. |
| POST /api/presupuestos/{id}/votacion | Administrador: abre una votación inmediatamente y pasa presupuesto a EN_VOTACION; 201. |
| GET /api/presupuestos/{id}/votacion | Estado, plazo, totales y voto propio; 404 si todavía no hay votación. |
| POST /api/votaciones/{id}/votos | Vecino activo de la comunidad: emite un único voto; 204. |
| POST /api/votaciones/{id}/cierre | Administrador: cierra tras el plazo y devuelve el resultado; 200. |

Creación por el técnico:

```json
{
  "titulo": "Sustitución del interruptor",
  "descripcion": "Material y mano de obra incluidos.",
  "proveedor": "Servicio técnico",
  "importe": 123.45
}
```

Título y proveedor son obligatorios, hasta 150 caracteres; descripción opcional
hasta 5000. Importe no negativo, hasta 8 dígitos enteros y 2 decimales, almacenado
con BigDecimal/DECIMAL(10,2). La propuesta no admite cambios mediante esta API;
el técnico puede crear otra propuesta. Cada presupuesto tiene su propia votación
y resultado independiente; no se elige automáticamente un único proveedor ganador.

Apertura por el administrador (usar fecha futura):

```json
{"fechaFin":"2026-12-15T18:00:00"}
```

El servidor fija fechaInicio y exige fechaFin posterior. Fechas locales del servidor,
con precisión de segundos. Se acepta voto en [fechaInicio, fechaFin): al vencer el
plazo se rechazan votos aunque el estado siga ABIERTA hasta el cierre administrativo.
La respuesta `admiteVotos` incorpora plazo, rol y si el usuario ya votó.

Voto del vecino:

```json
{"opcion":"ABSTENCION"}
```

Opciones: A_FAVOR, EN_CONTRA y ABSTENCION. Solo votan cuentas USUARIO con pertenencia
activa; administrador y técnico no votan con sus roles. No se modifica ni se repite
un voto emitido. Los votos válidos conservan su efecto aunque después se desactive
una pertenencia; se valida la elegibilidad al emitirlos. Los resultados son agregados
y cada usuario solo recibe su voto propio, no la identidad/opción de los demás.

Tras el plazo, el administrador cierra: APROBADO si A_FAVOR > EN_CONTRA; RECHAZADO
en empate, ausencia de votos o mayoría en contra. Sin quórum mínimo. Las abstenciones
cuentan en participación pero no a favor ni en contra. Se guarda la votación CERRADA
y el resultado del presupuesto en la misma transacción.

El historial de la incidencia registra PRESUPUESTO_CREADO con el técnico actor,
VOTACION_ABIERTA y VOTACION_CERRADA con el administrador y el resultado agregado.
No se publica cada voto en ese historial. No cambia el estado de la incidencia
ni se genera un coste/factura automáticamente; el módulo de costes es el orden 12.
La aprobación presupuestaria no añade un bloqueo nuevo al flujo técnico existente.

Sesión y CSRF son obligatorios en escrituras. Datos inválidos: 400; permisos, estados,
voto repetido o fuera de plazo: 403; recurso inexistente: 404. Se bloquea primero la
incidencia y después el presupuesto/votación para serializar operaciones; las lecturas
de votos al emitir/cerrar usan bloqueo y MySQL mantiene la unicidad usuario-votación.

Se utilizan las tablas existentes presupuestos, votaciones y votos, sin migración SQL.
PROGRAMADA y CANCELADA existen en el esquema, pero la apertura de este bloque es
inmediata y no incorpora programación futura, cancelación, edición ni reapertura de
votaciones. El cierre es una acción administrativa, no una tarea automática.

Las pruebas HTTP/MySQL verifican creación por técnico (y rechazo del administrador),
permisos, precisión del importe, plazos, voto único, abstención, resultados, concurrencia
y reversión ante fallos. El paso del tiempo se simula ajustando las fechas de las
votaciones propias de prueba; no se modifican votaciones de usuarios existentes.

Validación del 12 de noviembre de 2026: 69 pruebas superadas en ejecución conjunta,
sin fallos, errores ni omisiones: 29 con repositorios simulados, 3 de integración
de alta y 37 recorridos HTTP/MySQL, incluidos 6 nuevos del orden 10. Las pruebas
comprueban expresamente que el administrador no puede crear el presupuesto y
que solo el técnico asignado puede hacerlo.

### Orden 9: solicitud y validación de pertenencias

Alcance: cuentas ya registradas y activas con rol USUARIO solicitan pertenencia
a comunidades existentes. No incluye registro público, recuperación de contraseña
ni concesión de roles ADMINISTRADOR o TECNICO. Cuenta y pertenencia son estados
distintos: aprobar una pertenencia no modifica la cuenta ni su rol.

| Método y ruta | Uso |
|---|---|
| GET /api/pertenencias/comunidades | Catálogo mínimo para vecinos autenticados: ID, nombre y ciudad, sin contenido privado de las comunidades. |
| POST /api/pertenencias/solicitudes | Solicitar pertenencia propia; 200 con datos de la pertenencia. |
| GET /api/pertenencias/mias | Pertenencias propias y estado actual, incluidas pendientes e inactivas. |
| GET /api/pertenencias/pendientes | Solicitudes de vecinos en comunidades donde el administrador tiene pertenencia activa. |
| POST /api/pertenencias/{id}/resolucion | Aprobar o rechazar; 204. El ID corresponde a la pertenencia, no al usuario. |
| GET /api/pertenencias/{id}/historial | Eventos de solicitud y decisión, para el propio vecino o administrador autorizado. |

Solicitud:

```json
{"comunidadId":10,"motivo":"Soy residente de esta comunidad."}
```

Aprobación y rechazo:

```json
{"aprobar":true}
```

```json
{"aprobar":false,"motivo":"No se ha podido acreditar la residencia."}
```

Los motivos admiten hasta 1000 caracteres. Solicitud y rechazo requieren motivo;
la aprobación admite comentario opcional. Todas las rutas requieren sesión y cuenta
activa; las escrituras además requieren CSRF. El cliente no elige usuario, rol ni estado.

Una nueva pertenencia nace PENDIENTE sin acceso a espacios, incidencias ni evidencias.
Una solicitud repetida mientras siga pendiente devuelve la misma pertenencia sin
duplicar eventos ni modificar el motivo original. Solicitar siendo ya miembro ACTIVO
produce 403. Una pertenencia INACTIVO puede solicitarse de nuevo: vuelve a PENDIENTE,
sin acceso hasta nueva aprobación y conservando sus decisiones anteriores.

Solo un administrador activo de esa comunidad resuelve solicitudes de vecinos:
PENDIENTE -> ACTIVO al aprobar, o -> INACTIVO al rechazar. No puede aprobar cuentas
inactivas, resolverse una solicitud propia ni conceder roles mediante estas rutas.
Una decisión repetida produce 403. Datos inválidos producen 400; recursos ausentes,
404; permisos insuficientes, 403. Se serializan solicitud y decisión bloqueando primero
la cuenta solicitante y después la pertenencia, junto con la restricción única existente.

La migración `04_historial_pertenencias.sql` añade eventos SOLICITUD, APROBACION y
RECHAZO con actor, motivo, fecha y estados anterior/nuevo. Historial y permiso se
guardan en una transacción: si falla el historial, se revierte el cambio de acceso.
Las pertenencias pendientes anteriores a la migración pueden resolverse; no se
reconstruyen eventos históricos inexistentes. `fechaRegistro` es la creación inicial
de la pertenencia; las solicitudes posteriores tienen fecha propia en el historial.

Las pruebas añadidas cubren acceso antes/después de aprobar, rechazo y nueva solicitud,
permisos, CSRF, cuenta inactiva, concurrencia y reversión ante fallo. Para ejecutarlas
debe estar aplicada la migración 04 en la base de desarrollo.

Validación del 12 de noviembre de 2026: migración 04 aplicada en la instalación
local mediante la cuenta administradora. El backend validó el esquema y la ejecución
conjunta superó 63 pruebas, sin fallos, errores ni omisiones: 29 con repositorios
simulados, 3 de integración del alta y 31 recorridos HTTP/MySQL, incluidos los
5 nuevos del orden 9. Se verificaron acceso antes/después de aprobar, rechazo y
nueva solicitud con historial, permisos y CSRF, concurrencia sin duplicados y
reversión de solicitud/aprobación ante fallo del historial. Los datos de prueba
se limpiaron al terminar. El orden 9 queda validado para cuentas ya registradas;
el registro público de cuentas sigue fuera de su alcance.

### Orden 8: fotografías y evidencias

| Método y ruta | Uso |
|---|---|
| POST /api/incidencias/{id}/evidencias | Subir una imagen mediante multipart/form-data; devuelve 201 con metadatos y URL autenticada. |
| GET /api/incidencias/{id}/evidencias | Listar metadatos por fecha e ID ascendentes. |
| GET /api/incidencias/{id}/evidencias/{evidenciaId}/archivo | Obtener los bytes de la imagen tras comprobar acceso. |

La subida requiere cookie de sesión y cabecera CSRF vigente. En el cuerpo
multipart enviar `archivo` (fichero), `tipo` (ANTES, DURANTE o DESPUES) y
`descripcion` opcional (hasta 255 caracteres). El navegador debe generar el
Content-Type multipart con su boundary; no establecerlo manualmente al usar FormData.

Permisos acordados:

- ANTES: creador o administrador de la comunidad, solo en PENDIENTE.
- DURANTE y DESPUES: técnico asignado, solo en EN_PROCESO, antes de finalizar.
- Consulta y descarga: vecinos y administradores de la comunidad; técnicos solo
  de sus trabajos asignados. Cuenta y pertenencia deben estar activas en cada petición.
- Las fotos siguen siendo opcionales para finalizar. No se añaden operaciones de
  eliminación ni sustitución: una subida válida crea otra evidencia y otro evento.

Se admiten JPEG y PNG de hasta 5 MiB (5.242.880 bytes), con un máximo de 20 millones
de píxeles. La petición completa admite hasta 6 MiB. Se valida el contenido mediante
decodificación y no se confía en el nombre ni en el MIME enviado por el cliente.
Un contenido inválido produce 400; exceso del límite multipart, 413; acceso no
permitido, 403; recurso inexistente, 404. Fallos de almacenamiento producen 500
sin exponer rutas del servidor. Sin sesión se mantiene la redirección al login.

Almacenamiento local privado por defecto: `./datos/evidencias`, relativo al directorio
desde el que se inicia el backend. Puede configurarse con `app.evidencias.directorio`
en application-local.properties o con la variable `APP_EVIDENCIAS_DIRECTORIO`.
Usar una ruta persistente y la misma configuración en cada arranque. No se sirve
como carpeta estática: todas las descargas pasan por autorización y llevan no-store
y nosniff. Los archivos reciben claves UUID; el nombre original solo es metadato.
La carpeta predeterminada queda excluida de Git. Respaldar esta carpeta junto con MySQL.

Cada subida crea un evento EVIDENCIA_ADJUNTADA y la evidencia asociada al mismo
aviso e historial. La columna url_archivo contiene la clave privada del archivo;
la API devuelve una URL protegida. Estado y fechas del trabajo no se alteran.
El mapeo de descripción se ajustó a VARCHAR(255), coincidiendo con el SQL existente.
No se necesita migración adicional; debe estar aplicada la migración 02 de integridad.

Si SQL revierte la transacción se elimina el archivo nuevo como compensación.
Esto no es una transacción distribuida: una caída abrupta del proceso entre escritura
y commit puede dejar un archivo huérfano. La limpieza automática ante caídas y el
almacenamiento compartido entre varios servidores quedan fuera de esta implementación local.

Las pruebas usan una carpeta temporal exclusiva y datos MySQL propios; verifican
subida y descarga byte a byte, permisos, fases, contenido real, límites, CSRF y
limpieza del archivo cuando falla el historial. Las pruebas previas también se ejecutan.

Validación del 12 de noviembre de 2026: 58 pruebas superadas en ejecución conjunta,
sin fallos ni errores (29 con repositorios simulados, 3 de integración de alta y
26 recorridos HTTP/MySQL, incluidos los 4 nuevos del orden 8).

### Orden 7: rechazo y duplicadas

Solo un administrador con cuenta y pertenencia activas a la comunidad puede
rechazar o marcar duplicada una incidencia PENDIENTE. Las operaciones requieren
sesión, CSRF y motivo obligatorio (hasta 5000 caracteres, sin espacios exteriores).

| Método y ruta | Cuerpo | Resultado |
|---|---|---|
| POST /api/incidencias/{id}/rechazo | `{"motivo":"El aviso no corresponde."}` | 204; estado RECHAZADA y evento RECHAZO. |
| POST /api/incidencias/{id}/duplicada | `{"incidenciaOriginalId":15,"motivo":"El mismo problema ya está registrado."}` | 204; estado RECHAZADA, vínculo a la original y evento DUPLICADA. |

El ID del ejemplo debe sustituirse por una incidencia real. No se añade un
estado DUPLICADA: el detalle incluye `incidenciaOriginalId`, nulo para rechazo
simple y con el identificador de la original para una duplicada. El frontend
puede mostrar «Duplicada» y enlazar al detalle original usando ese dato.
Se conservan el aviso y su historial; la operación no modifica la original.
La fecha del rechazo se consulta en el evento, sin utilizar fechaCierre, reservada
para el cierre administrativo tras confirmación.

La original debe ser de la misma comunidad, distinta del aviso, no estar
RECHAZADA ni ser ya duplicada. Puede estar en cualquiera de los demás estados,
incluida FINALIZADA. No se permiten cadenas ni ciclos. Una incidencia que ya
es original de otros duplicados no puede rechazarse ni convertirse en duplicada,
para conservar una referencia válida de seguimiento. La reapertura del orden 6
sigue limitada a FINALIZADA; no reabre rechazos ni duplicadas.

Errores: 400 para motivo/identificador inválidos, autorreferencia, comunidad
distinta, original rechazada/duplicada o referencia incompatible; 403 para cuenta,
rol, pertenencia o estado del aviso no permitidos; 404 si un ID no existe.
Repetir una operación completada produce 403 sin añadir otro evento.

El servicio bloquea las dos incidencias en orden de ID al vincularlas, y comprueba
las referencias mediante lectura con bloqueo. La transacción agrupa estado,
vínculo e historial. Se reutiliza id_incidencia_origen y su clave foránea: no hay
migración SQL. Los tests limpian primero los vínculos de sus propios datos para
respetar esa clave foránea al eliminarlos.

Las pruebas HTTP/MySQL cubren rechazo simple y duplicada, detalle e historial,
permisos, CSRF, estados, motivos, autorreferencia, original inexistente, cadenas,
comunidad ajena, reversión ante fallo y duplicadas cruzadas concurrentes.

Validación del 12 de noviembre de 2026: 54 pruebas superadas en ejecución conjunta,
sin fallos ni errores (29 con repositorios simulados, 3 de integración del alta y
22 recorridos HTTP/MySQL, incluidos 5 nuevos del orden 7). Las peticiones cruzadas
obtuvieron 204 y 400, con un único vínculo y sin ciclo.

### Orden 6: confirmación, cierre y reapertura

Todas las acciones requieren cuenta activa, pertenencia activa a la comunidad,
sesión y CSRF. El actor se obtiene de la sesión. La consulta compartida por
comunidad no concede permisos para confirmar ni cerrar.

| Método y ruta | Permiso y transición |
|---|---|
| POST /api/incidencias/{id}/confirmacion | Solo vecino verificador. PENDIENTE_CONFIRMACION -> PENDIENTE_CIERRE si conforme; -> EN_PROCESO si no conforme. |
| POST /api/incidencias/{id}/cierre | Solo administrador de la comunidad. PENDIENTE_CIERRE -> FINALIZADA. Sin cuerpo. |
| POST /api/incidencias/{id}/reapertura | Solo administrador de la comunidad. FINALIZADA -> PENDIENTE, con motivo obligatorio. |

Confirmación positiva:

```json
{"conforme": true}
```

Confirmación negativa (motivo obligatorio):

```json
{"conforme": false, "motivo": "La luz sigue sin encenderse."}
```

Reapertura:

```json
{"motivo": "La avería ha reaparecido después del cierre."}
```

Los motivos admiten hasta 5000 caracteres y se guardan sin espacios exteriores.
La confirmación positiva registra fecha_confirmacion; el cierre, fecha_cierre.
Una reparación no conforme conserva técnico y fecha de inicio, limpia la fecha
de reparación y vuelve a EN_PROCESO. Su motivo y la fecha de reparación anterior
quedan en el evento REPARACION_NO_CONFORME. El técnico puede continuar y finalizar
de nuevo; el administrador no puede cerrar mientras falte una confirmación positiva.

La reapertura administrativa a PENDIENTE es la regla acordada. Conserva reportante,
verificador, espacio y todo el historial. Retira el técnico y limpia las fechas
de asignación, previsión, inicio, reparación, confirmación y cierre del ciclo actual.
El evento REAPERTURA conserva el técnico anterior y las fechas anteriores en su
descripción, además del motivo. Permite asignar técnico de nuevo mediante el orden 4.

Cada operación devuelve 204 al completarse. Datos inválidos producen 400;
rol, pertenencia, verificador o estado incorrectos, 403; incidencia inexistente,
404. Repetir una transición ya realizada produce 403 sin duplicar el historial.
Se utiliza el mismo bloqueo de escritura de los órdenes anteriores y una transacción
que agrupa estado, fechas e historial. Los eventos son CONFIRMACION_REPARACION,
REPARACION_NO_CONFORME, CIERRE y REAPERTURA.

El detalle GET /api/incidencias/{id} incluye ahora fechaAsignacion, fechaInicio,
fechaReparacion, fechaConfirmacion y fechaCierre. Son fechas del ciclo actual;
los ciclos anteriores se consultan en el historial. No se necesita migración SQL.

Las pruebas HTTP/MySQL cubren ciclo completo y nuevo ciclo tras reapertura,
verificador designado en alta administrativa, devolución al técnico, permisos,
CSRF, motivos obligatorios y reversión de cada operación si falla el historial.

Validación del 11 de noviembre de 2026: 48 pruebas superadas en la ejecución
conjunta y una prueba adicional de cierre concurrente superada posteriormente:
49 casos sin fallos. El cierre concurrente devolvió 204 y 403, con un solo evento
CIERRE. Los datos propios de las pruebas se eliminan al terminar.

### Orden 5: trabajo del técnico

El técnico debe tener cuenta activa, ser el responsable asignado y conservar
pertenencia activa a la comunidad. Todas las rutas verifican estas reglas en el
servidor, incluso si la sesión se abrió antes de desactivar la cuenta.
Las consultas vecinales/administrativas existentes conservan sus permisos.

| Método | Ruta | Resultado |
|---|---|---|
| GET | /api/tecnico/trabajos | Trabajos asignados accesibles, por fecha de creación e ID descendentes. Incluye los que ya terminaron la actuación técnica. |
| GET | /api/tecnico/trabajos/{id} | Detalle con espacio, comunidad, prioridad y fechas de asignación, previsión, inicio y reparación. |
| GET | /api/tecnico/trabajos/{id}/historial | Historial del trabajo autorizado. |
| PUT | /api/tecnico/trabajos/{id}/inicio | 204; ASIGNADA -> EN_PROCESO, fecha de inicio y evento INICIO_REPARACION. Sin cuerpo. |
| POST | /api/tecnico/trabajos/{id}/avances | 204; registra AVANCE_REPARACION sin cambiar EN_PROCESO. |
| POST | /api/tecnico/trabajos/{id}/finalizacion | 204; EN_PROCESO -> PENDIENTE_CONFIRMACION, fecha de reparación y evento FIN_REPARACION. |

Avance y finalización reciben una descripción obligatoria de hasta 5000 caracteres:

```json
{"descripcion": "Se sustituyó el interruptor y se comprobó el funcionamiento."}
```

Se eliminan espacios exteriores antes de guardar. Cada POST de avance válido
crea un evento nuevo; no deduplica descripciones iguales. Repetir el inicio
cuando ya está EN_PROCESO devuelve 204 sin cambiar la fecha ni duplicar eventos.
Una finalización repetida se rechaza con 403 porque el trabajo ya no está EN_PROCESO.
Un bloqueo de escritura evita transiciones simultáneas incompatibles; estado,
fechas e historial se guardan dentro de la misma transacción.

Todas las escrituras requieren CSRF. Datos inválidos producen 400; rol,
asignación, pertenencia o estado no permitidos producen 403; ID inexistente, 404.
El técnico no confirma ni cierra: fecha_confirmacion y fecha_cierre permanecen
sin alterar durante su actuación. Esas operaciones están implementadas en el orden 6. Las evidencias continúan
implementadas en el orden 8; no se exige adjuntar fotos para finalizar.

Se reutilizan las columnas de fechas e historial existentes, sin migración SQL.
Las pruebas HTTP/MySQL incluyen recorrido completo, permisos, CSRF, validación,
reversión ante fallos y competencia entre dos finalizaciones simultáneas.

Validación del 11 de noviembre de 2026: 43 pruebas superadas en la ejecución
conjunta (incluidos los tres recorridos nuevos del técnico) y una prueba adicional
de finalización concurrente superada después: 44 casos en total, sin fallos.
La prueba concurrente obtuvo un 204 y un 403, con un único evento FIN_REPARACION.

### Orden 4: asignación inicial de técnico

La asignación requiere un administrador con cuenta y pertenencia activas a la
comunidad de la incidencia. El técnico también debe tener cuenta activa, rol
TECNICO y pertenencia activa a esa misma comunidad. El selector refleja esa
elegibilidad; no calcula disponibilidad horaria ni carga de trabajo.

| Método | Ruta | Resultado |
|---|---|---|
| GET | /api/incidencias/{id}/tecnicos-disponibles | 200 con candidatos `{id,nombre}`, o lista vacía. Solo administradores autorizados. |
| PUT | /api/incidencias/{id}/asignacion | 204 tras asignar. Requiere sesión y CSRF. |

Ejemplo de cuerpo (usar un identificador real y una fecha futura):

```json
{
  "tecnicoId": 15,
  "fechaPrevista": "2026-12-15T10:30:00"
}
```

La fecha se interpreta como fecha/hora local del servidor, sin zona horaria,
y se guarda con precisión de segundos. Es obligatoria y no puede estar en el
pasado al efectuar la asignación inicial.

Solo se permite la transición PENDIENTE -> ASIGNADA. Se guardan técnico,
fecha de asignación, fecha prevista y evento ASIGNACION_TECNICO, con administrador
actor, técnico anterior/nuevo y estados anterior/nuevo. Un bloqueo de escritura
protege la incidencia y una transacción agrupa los cambios con el historial.
Repetir los mismos técnico y fecha mientras siga ASIGNADA devuelve 204 sin
duplicar eventos. Cambiar una asignación existente no está incluido en este bloque.

Errores: 400 para datos o candidato inválidos; 403 para rol, pertenencia o estado
no permitidos; 404 para incidencia inexistente. Los candidatos se vuelven a
validar al guardar. No se modifica el esquema SQL, que ya contiene estos campos.

Los tests del orden 4 están en OrdenesHttpMysqlTests: recorrido de asignación y
repetición, permisos y CSRF, fechas inválidas, candidatos inactivos o sin pertenencia
activa, estados no asignables y reversión ante fallo provocado del historial.
La consulta de trabajos y las acciones del técnico pertenecen al orden 5.

Validación del 11 de noviembre de 2026: 40 pruebas superadas, 0 fallos y
0 errores (29 con repositorios simulados, 3 de integración de alta y 8 recorridos
HTTP/MySQL, incluidos los 3 nuevos del orden 4). El error 500 provocado en las
pruebas de reversión es intencionado y se comprueba como resultado esperado.

### Creación por administrador

Se utiliza POST /api/incidencias con sesión y CSRF, agregando vecinoVerificadorId
al JSON habitual. El frontend deberá permitir seleccionar un vecino verificador.
La consulta de candidatos para ese selector aún está pendiente.

```json
{
  "titulo": "Luz del portal averiada",
  "descripcion": "El pulsador no enciende la luz.",
  "prioridad": "MEDIA",
  "espacioId": 9,
  "vecinoVerificadorId": 9
}
```

Los identificadores son ilustrativos de la instalación local: deben existir y
cumplir los permisos de la comunidad. El POST devuelve 201, estado PENDIENTE,
el administrador como reportante y el vecino indicado como verificador.
No requiere modificar el esquema de incidencias existente.

Un administrador sin pertenencia activa o una cuenta inactiva recibe 403.
Un verificador ausente, inexistente, de otro rol, inactivo o sin pertenencia
activa produce 400 antes de guardar. Los vecinos pueden seguir enviando el
JSON anterior sin vecinoVerificadorId; si lo incluyen debe ser su propio ID.
El técnico no puede crear incidencias.

### Validación de los órdenes uno a tres (11 de noviembre de 2026)

Se ejecutaron las 29 pruebas con repositorios simulados y las 3 pruebas existentes
de integración con MySQL: 32 superadas. Se añadió `OrdenesHttpMysqlTests`, con
5 pruebas adicionales superadas contra HTTP real (Tomcat en puerto aleatorio)
y MySQL 8.0.46. No necesitan que la aplicación esté arrancada en el puerto 8080.

Estas pruebas verifican:

- Login real con cookies y renovación del token CSRF.
- Consulta del mismo caso por autor, otro vecino y administrador; listas vacías
  y acceso directo denegado para usuario ajeno o pertenencia pendiente.
- Redirección al login sin sesión.
- Edición permitida al autor y administrador y denegada a otros usuarios;
  rechazo de datos inválidos y de escritura sin CSRF.
- Persistencia SQL del cambio y su actor; repetición sin duplicar historial.
- Alta administrativa con reportante, verificador, estado y evento correctos;
  rechazo de verificadores inválidos e inactivos, técnico y administrador sin
  pertenencia activa, sin nuevas incidencias para las peticiones rechazadas.
- Dos ediciones concurrentes conservan la cadena de valores anteriores y nuevos.
- Un fallo provocado al guardar el historial revierte la edición en MySQL.
  Solo en ese caso se intercepta el repositorio del historial para provocar el
  fallo; el servicio y la transacción son reales.

Cada prueba crea usuarios, comunidad y espacio exclusivos, y elimina sus datos
al terminar. Ejecutar únicamente sobre una base local de desarrollo preparada.
Los identificadores autoincrementales pueden avanzar aunque se limpien los datos.

```powershell
.\mvnw.cmd "-Dtest=OrdenesHttpMysqlTests" test
```

No se ha probado todavía un reinicio del proceso entre escritura y lectura.
También sigue pendiente la API de candidatos para el selector de verificador.
Las comprobaciones descritas abajo son la matriz de referencia original;
los resultados de esta sección actualizan su estado.

### Matriz de comprobaciones de los órdenes uno a tres

Las pruebas de servicio usan repositorios simulados. Para cerrar la verificación
de integración se necesita una base de desarrollo y sesiones de dos vecinos
y un administrador activos de la misma comunidad, más una cuenta sin acceso.

| Orden | Comprobación HTTP | Comprobación en MySQL |
|---|---|---|
| 1 Consulta | Ambos vecinos y el administrador ven el caso, su detalle e historial. La cuenta ajena recibe listas vacías y 403 en acceso directo. | Las pertenencias activas coinciden con las comunidades consultadas. Una pertenencia pendiente o inactiva no concede acceso. |
| 2 Edición | Autor y administrador obtienen 204. Otro vecino y técnico reciben 403. Datos inválidos producen 400. Repetir los mismos datos no duplica eventos. | Los datos y EDICION_DATOS se guardan juntos. Si falla el historial, se revierte la edición. Dos ediciones concurrentes registran valores anteriores coherentes. |
| 3 Alta administrativa | POST válido produce 201. Verificador inválido produce 400. Administrador ajeno o técnico reciben 403. El alta del vecino sigue funcionando. | Reportante y actor son el administrador; verificador es el vecino elegido. Existe un solo evento CREACION y no hay técnico asignado. Las peticiones rechazadas no insertan filas. |

En las escrituras también se comprobará el rechazo sin token CSRF y la
persistencia de los cambios tras reiniciar el backend. Estas comprobaciones
se registrarán como realizadas solo después de ejecutarlas.

### Edición de los datos de una incidencia

El permiso de lectura por comunidad se comprueba por separado de la escritura.
Para editar, la cuenta y la pertenencia deben estar activas. Puede hacerlo el
vecino creador o un administrador vinculado a esa comunidad. Un vecino que solo
sea verificador no puede editar. El técnico tiene pendientes sus operaciones
específicas de actuación y estado.

Enviar PUT /api/incidencias/{id}/datos con los tres campos:

```json
{
  "titulo": "Luz del portal averiada",
  "descripcion": "El pulsador de la entrada no enciende la luz.",
  "prioridad": "ALTA"
}
```

Una edición correcta devuelve HTTP 204. El título admite hasta 150 caracteres
y la descripción hasta 5000. Ambos son obligatorios junto con la prioridad.
Esta operación modifica datos generales en cualquier estado, sin transiciones
de estado ni cambios de espacio, autor o asignación. La confirmación y el cierre
se realizan mediante las operaciones específicas del orden 6.

Cada cambio crea un evento EDICION_DATOS con el actor y los valores anteriores
y nuevos. La transacción agrupa ambos guardados, y un bloqueo de escritura
serializa las ediciones de un mismo caso. Repetir los mismos datos devuelve 204
sin crear otro evento.

Las pruebas de servicio y validación se ejecutan desde backend:

```powershell
.\mvnw.cmd "-Dtest=ConsultaComunidadTests,EdicionIncidenciaTests" test
```

La edición por HTTP con sesión y CSRF, la concurrencia y la reversión ante fallo
del historial se verificaron el 11 de noviembre con OrdenesHttpMysqlTests.

### Recorrido HTTP del vecino en la instalación local

Con el backend arrancado y la cuenta y el espacio demo preparados, ejecutar desde
`backend` en PowerShell 7:

```powershell
# La contraseña se introduce de forma oculta y se usa solo para esta prueba.
.\scripts\probar-recorrido-vecino.ps1 -ClaveDemo (Read-Host 'Contraseña demo' -AsSecureString)
```

Cada ejecución crea un caso en MySQL. El script imprime el ID recibido al crear
la incidencia y lo utiliza para comprobar el listado, el detalle y el evento
inicial del historial. Si alguna comprobación falla después del alta, conservar
ese ID para investigar antes de repetir la ejecución.

En las pruebas manuales, un vecino activo sin pertenencia obtuvo listas vacías
de espacios e incidencias y acceso denegado al detalle y al historial del caso 6.
Una petición de historial sin sesión desde el navegador se redirigió al login.

### Pruebas de integración del servicio

Con MySQL preparado y las variables de conexión configuradas,
ejecutar desde backend:

```powershell
.\mvnw.cmd -Dtest=IncidenciaServiceTests test
```

La clase contiene tres pruebas:

1. Un vecino activo crea una incidencia y su evento de historial.
2. Un vecino sin pertenencia a la comunidad no puede crearla.
3. Un vecino con pertenencia pendiente no puede crearla.

Las pruebas utilizan datos ficticios y transacciones que se revierten
al terminar. Ejecutarlas sobre una base de datos de desarrollo.
Los contadores AUTO_INCREMENT pueden avanzar aunque se reviertan
las inserciones.

Para ejecutar todas las pruebas del proyecto:

```powershell
.\mvnw.cmd test
```

La ejecución conjunta confirmada de las cinco clases funcionales suma 76 pruebas
(véase la validación de los órdenes 11 y 12). No incluye BackendApplicationTests.

## Validación realizada

### Recorrido del vecino verificado en noviembre de 2026

| Escenario | Resultado observado en la instalación local |
|---|---|
| Crear una incidencia en Portal principal con sesión y CSRF | HTTP 201. El script siguió el ID devuelto (7) y comprobó el listado, los datos del detalle y el evento inicial CREACION en estado PENDIENTE. |
| Consultar el historial de la incidencia 6 como vecino autorizado | La respuesta incluye el evento CREACION, su fecha y el vecino que lo registró. |
| Consultar el historial de una incidencia inexistente | HTTP 404 con el mensaje «Incidencia no encontrada». |
| Abrir el historial sin sesión desde el navegador | Redirección al formulario de acceso. |
| Consultar como vecino activo sin pertenencia a la comunidad | HTTP 403 en detalle e historial. Los listados de incidencias y espacios devolvieron listas vacías. |

La regla acordada el 5 de noviembre permite consultar una incidencia de otro
vecino de la misma comunidad. Solo se requiere cuenta activa y pertenencia
activa para estas consultas de vecinos y administradores. La edición aplica
sus comprobaciones de autoría, rol y pertenencia por separado.

Las pruebas de servicio de esta regla están en ConsultaComunidadTests.
Simulan los repositorios para verificar los permisos sin credenciales MySQL:

```powershell
.\mvnw.cmd "-Dtest=ConsultaComunidadTests,EdicionIncidenciaTests,AltaAdministradorTests" test
```

Cubren lectura de detalle e historial de otro autor, listado por varias
comunidades activas, ausencia de pertenencia activa, cuenta inactiva y rol técnico.
Las pruebas incluyen ahora las consultas administrativas y el alta con
verificador, además de la compatibilidad con el alta vecinal.
Ejecución del 5 de noviembre de 2026: 29 pruebas superadas, 0 fallos y 0 errores
(8 de consulta, 10 de edición y 11 de alta).
La comprobación HTTP con dos vecinos de la misma comunidad y la consulta real
contra MySQL se verificaron el 11 de noviembre con OrdenesHttpMysqlTests.

### Comprobaciones iniciales de octubre de 2026

Durante el 13 de octubre de 2026 se comprobó:

- Arranque local en Windows con Java 21.0.11 y MySQL.
- Detección de los ocho repositorios JPA.
- Ejecución satisfactoria de las tres pruebas del servicio de alta.
- Inicio de sesión desde Postman y consulta del correo y rol.
- Obtención y renovación del token CSRF.
- Creación de una incidencia mediante POST, con respuesta 201.
- Registro de su evento CREACION en MySQL.
- Consulta de la incidencia mediante GET después de reiniciar el backend.

Estas comprobaciones verifican el recorrido inicial de alta y consulta,
no el ciclo completo de gestión de incidencias.

Quedan pendientes pruebas específicas del filtrado del listado,
validaciones HTTP, otros roles y ejecución en otro equipo.

## Errores frecuentes

- Access denied con el texto ${DB_USERNAME}: comprobar que las variables
  de entorno están definidas en la terminal que inicia el backend.
- Access denied con un usuario concreto: comprobar las credenciales
  y los permisos de MySQL.
- 403 al enviar un POST: comprobar la cookie de sesión y el token CSRF.
  También puede indicar que el servicio rechaza la operación por permisos.
- 404 en /: no existe una página de inicio en esta versión.
- 405 al abrir /api/incidencias desde una petición con un método
  no implementado: comprobar el método HTTP utilizado.
