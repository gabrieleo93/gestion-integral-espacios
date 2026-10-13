# Gestión Integral de Espacios

Proyecto académico del ciclo formacion profesional de Desarrollo de Aplicaciones Web.

La aplicación tiene como objetivo gestionar incidencias en espacios
comunitarios, coordinando a vecinos, administradores y técnicos para
registrar problemas y realizar su seguimiento hasta la resolución.

## Estado actual

El proyecto se encuentra en desarrollo. Esta versión incluye:

- Estructura inicial del backend con Spring Boot y Maven.
- Entidades JPA Usuario, Rol y Comunidad.
- Enumeración EstadoUsuario.
- Scripts SQL para crear la base de datos y sus tablas.
- Restricciones de integridad entre las evidencias y el historial de incidencias.
- Configuración de conexión a MySQL mediante variables de entorno.

Todavía no están implementados el frontend, las operaciones de la API
ni el flujo completo de gestión de incidencias.
La autenticación y los permisos por rol están pendientes de desarrollo.

## Tecnologías

- Java 21.
- Spring Boot 4.1.1.
- Spring Data JPA.
- Spring Security como dependencia inicial.
- MySQL.
- Maven Wrapper.
- Git y GitHub.

## Organización del proyecto

- backend/: código Java, configuración, dependencias y prueba inicial.
- database/: scripts de creación y modificación de la base de datos.
- README.md: descripción e instrucciones del proyecto.
- .gitignore: exclusión de archivos generados y configuración local.

## Requisitos previos

- JDK 21 y variable JAVA_HOME configurada.
- Servidor MySQL y un cliente como MySQL Workbench.
- Git para descargar el repositorio.
- Conexión a Internet para descargar las dependencias de Maven.

El proyecto incluye Maven Wrapper, por lo que no es necesario instalar
Maven por separado. Los siguientes pasos están preparados para Windows
con PowerShell.

## Descargar el proyecto

```powershell
git clone https://github.com/gabrieleo93/gestion-integral-espacios.git
cd gestion-integral-espacios
```

Si ya se dispone del proyecto local, no es necesario clonarlo de nuevo.

## Preparar la base de datos

Conectarse al servidor desde MySQL Workbench y ejecutar los scripts
de la carpeta database en este orden:

1. 0_crear_base_datos.sql
2. 01_estructura_inicial.sql
3. 02_integridad_evidencias.sql

Ejecutarlos sobre una instalación nueva. Los scripts de estructura
y modificación no están preparados para repetirse sobre una base
de datos ya inicializada.

La base de datos se llama gestion_espacios. Se crean tres roles:
ADMINISTRADOR, USUARIO y TECNICO. Todavía no se incluyen usuarios
de demostración.

## Configurar y ejecutar el backend

La conexión está configurada para MySQL en localhost, puerto 3306.
El usuario de MySQL debe tener acceso a la base de datos gestion_espacios.

En una terminal PowerShell, sustituir los valores de ejemplo por las
credenciales del entorno local:

```powershell
$env:DB_USERNAME = "tu_usuario_mysql"
$env:DB_PASSWORD = "tu_contraseña_mysql"

cd backend
.\mvnw.cmd spring-boot:run
```

Las variables se aplican a esa terminal. No guardar credenciales reales
en los archivos del repositorio.

El backend utiliza el puerto 8080 de forma predeterminada.
Hibernate valida las entidades contra las tablas existentes;
no crea automáticamente la estructura de la base de datos.

## Comprobación y limitaciones

Comprobar que el registro de arranque muestra
Started BackendApplication y que no aparecen errores de conexión
o de validación del esquema.

Esta versión todavía no dispone de pantallas ni de operaciones
de negocio accesibles desde una API. Una pantalla de acceso generada
por Spring Security no representa el inicio de sesión definitivo.

Para ejecutar la prueba inicial de carga del contexto, con MySQL
preparado y las variables de entorno configuradas:

```powershell
.\mvnw.cmd test
```

Esta prueba comprueba la carga del contexto de Spring; no verifica
el ciclo funcional de incidencias. La reproducción de estos pasos
en otro equipo está pendiente de validación.