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
- Consulta de incidencias del vecino autenticado.
- Perfil demo para crear una cuenta de prueba.
- Pruebas de integración del servicio de alta con MySQL.

El alta está habilitada para vecinos con cuenta activa y pertenencia
activa a la comunidad del espacio. El vecino que registra la incidencia
queda también como verificador inicial.

La consulta devuelve los casos donde el vecino es reportante o
verificador y mantiene una pertenencia activa a la comunidad.

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

Solo se devuelven casos donde el vecino sea reportante o verificador
y tenga pertenencia activa a la comunidad. Si no hay casos accesibles,
la respuesta es una lista vacía.

## Pruebas automatizadas

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