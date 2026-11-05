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
- Ocho repositorios JPA para acceder a los datos.
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
- Operaciones específicas de administradores y técnicos.
- Asignación, reparación, confirmación, cierre y reapertura de incidencias.
- Subida y consulta de archivos de evidencia.
- Funcionalidades de presupuestos, votaciones y costes.
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

En una instalación ya preparada con los tres primeros scripts, ejecutar
únicamente el cuarto. El modelo inicial se conserva como punto de partida
y el cuarto script aplica la decisión de usar facturación en costes.

### Estado de facturación de los costes

La columna final es `estado_facturacion`:

| Valor en la base de datos | Etiqueta de la pantalla |
|---|---|
| PENDIENTE_FACTURAR | Pendiente de facturar |
| FACTURADO | Facturado |

El valor inicial será PENDIENTE_FACTURAR. El módulo registrará si el coste
ha sido facturado. Sus entidades Java y operaciones de API siguen pendientes.

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

### Comprobaciones pendientes para cerrar los órdenes uno a tres

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
se implementarán en sus operaciones específicas.

Cada cambio crea un evento EDICION_DATOS con el actor y los valores anteriores
y nuevos. La transacción agrupa ambos guardados, y un bloqueo de escritura
serializa las ediciones de un mismo caso. Repetir los mismos datos devuelve 204
sin crear otro evento.

Las pruebas de servicio y validación se ejecutan desde backend:

```powershell
.\mvnw.cmd "-Dtest=ConsultaComunidadTests,EdicionIncidenciaTests" test
```

Queda pendiente probar esta edición por HTTP con sesión y CSRF y verificar en
MySQL el bloqueo concurrente y la reversión si falla el guardado del historial.

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

El resultado confirmado corresponde a las tres pruebas de
IncidenciaServiceTests. La ejecución completa de la suite debe
comprobarse por separado.

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
La comprobación HTTP con dos vecinos de la misma comunidad y la validación de
la consulta del repositorio contra MySQL quedan pendientes.

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
