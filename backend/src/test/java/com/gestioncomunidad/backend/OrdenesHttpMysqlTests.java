package com.gestioncomunidad.backend;

import java.net.*;
import java.net.http.*;
import java.time.Duration;
import java.util.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import static org.junit.jupiter.api.Assertions.*;

// Usa HTTP real y MySQL: cada caso crea una comunidad propia y elimina solo sus datos.
// No lleva @Transactional: las peticiones del servidor necesitan ver datos confirmados.
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class OrdenesHttpMysqlTests {
    @LocalServerPort int port;
    @Autowired JdbcTemplate db;
    @Autowired PasswordEncoder encoder;
    @org.springframework.test.context.bean.override.mockito.MockitoSpyBean
    com.gestioncomunidad.backend.repositories.HistorialIncidenciaRepository historial;
    final ObjectMapper json = new ObjectMapper();
    final Map<String, Integer> users = new HashMap<>();
    final Map<String, String> emails = new HashMap<>();
    String password;
    int community, space;

    @BeforeEach void preparar() {
        password = UUID.randomUUID().toString();
        String tag = "validacion-" + UUID.randomUUID();
        db.update("INSERT INTO comunidades(nombre,direccion,ciudad) VALUES (?, 'Prueba', 'Madrid')", tag);
        community = db.queryForObject("SELECT id_comunidad FROM comunidades WHERE nombre=?", Integer.class, tag);
        db.update("INSERT INTO espacios(nombre,tipo,id_comunidad) VALUES ('Portal prueba','PORTAL',?)", community);
        space = db.queryForObject("SELECT id_espacio FROM espacios WHERE id_comunidad=?", Integer.class, community);
        for (String name : List.of("autor", "vecino", "admin", "ajeno", "tecnico", "pendiente")) {
            String role = name.equals("admin") ? "ADMINISTRADOR" : name.equals("tecnico") ? "TECNICO" : "USUARIO";
            String email = name + "-" + tag + "@example.test";
            db.update("INSERT INTO usuarios(nombre,apellidos,email,password,estado,id_rol) SELECT ?, 'Prueba', ?, ?, 'ACTIVO', id_rol FROM roles WHERE nombre=?", name, email, encoder.encode(password), role);
            int id = db.queryForObject("SELECT id_usuario FROM usuarios WHERE email=?", Integer.class, email);
            users.put(name, id); emails.put(name, email);
            if (!name.equals("ajeno")) db.update("INSERT INTO usuarios_comunidades(id_usuario,id_comunidad,estado) VALUES (?,?,?)", id, community, name.equals("pendiente") ? "PENDIENTE" : "ACTIVO");
        }
    }

    @AfterEach void limpiar() {
        org.mockito.Mockito.reset(historial);
        // Borrado acotado a los identificadores creados por este caso, respetando claves foráneas.
        if (space != 0) {
            db.update("DELETE h FROM historial_incidencias h JOIN incidencias i ON i.id_incidencia=h.id_incidencia WHERE i.id_espacio=?", space);
            db.update("DELETE FROM incidencias WHERE id_espacio=?", space);
            db.update("DELETE FROM espacios WHERE id_espacio=?", space);
        }
        db.update("DELETE FROM usuarios_comunidades WHERE id_comunidad=?", community);
        for (int id : users.values()) db.update("DELETE FROM usuarios WHERE id_usuario=?", id);
        db.update("DELETE FROM comunidades WHERE id_comunidad=?", community);
    }

    class Sesion implements AutoCloseable {
        final HttpClient client = HttpClient.newBuilder().cookieHandler(new CookieManager(null, CookiePolicy.ACCEPT_ALL)).connectTimeout(Duration.ofSeconds(10)).build();
        JsonNode csrf;
        HttpResponse<String> call(String method, String path, String body, boolean token) throws Exception {
            var b = HttpRequest.newBuilder(URI.create("http://localhost:" + port + path)).timeout(Duration.ofSeconds(20));
            if (token) b.header(csrf.get("headerName").asText(), csrf.get("token").asText());
            if (body != null) b.header("Content-Type", "application/json");
            return client.send(b.method(method, body == null ? HttpRequest.BodyPublishers.noBody() : HttpRequest.BodyPublishers.ofString(body)).build(), HttpResponse.BodyHandlers.ofString());
        }
        void login(String name) throws Exception {
            csrf = json.readTree(call("GET", "/api/csrf", null, false).body());
            String form = "username=" + URLEncoder.encode(emails.get(name), java.nio.charset.StandardCharsets.UTF_8) + "&password=" + password + "&" + csrf.get("parameterName").asText() + "=" + URLEncoder.encode(csrf.get("token").asText(), java.nio.charset.StandardCharsets.UTF_8);
            var request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/login")).header("Content-Type", "application/x-www-form-urlencoded").POST(HttpRequest.BodyPublishers.ofString(form)).build();
            assertEquals(302, client.send(request, HttpResponse.BodyHandlers.ofString()).statusCode());
            assertEquals(emails.get(name), json.readTree(call("GET", "/api/sesion", null, false).body()).get("email").asText());
            csrf = json.readTree(call("GET", "/api/csrf", null, false).body());
        }
        public void close() { client.close(); }
    }
    Sesion session(String name) throws Exception { Sesion s = new Sesion(); s.login(name); return s; }
    String alta(Integer verifier) { return "{\"titulo\":\"Inicial\",\"descripcion\":\"Prueba HTTP\",\"prioridad\":\"MEDIA\",\"espacioId\":" + space + (verifier == null ? "" : ",\"vecinoVerificadorId\":" + verifier) + "}"; }
    String edit(String title) { return "{\"titulo\":\"" + title + "\",\"descripcion\":\"Editada\",\"prioridad\":\"ALTA\"}"; }
    int crear(Sesion s, Integer verifier) throws Exception {
        var r = s.call("POST", "/api/incidencias", alta(verifier), true);
        assertEquals(201, r.statusCode(), r.body());
        return json.readTree(r.body()).get("id").asInt();
    }
    int eventos(int id) { return db.queryForObject("SELECT COUNT(*) FROM historial_incidencias WHERE id_incidencia=?", Integer.class, id); }

    int reparada(Sesion autor, Sesion admin, Sesion tecnico) throws Exception {
        int id = trabajoAsignado(autor, admin);
        terminarReparacion(id, tecnico);
        return id;
    }
    void terminarReparacion(int id, Sesion tecnico) throws Exception {
        String path = "/api/tecnico/trabajos/" + id;
        assertEquals(204, tecnico.call("PUT", path + "/inicio", null, true).statusCode());
        assertEquals(204, tecnico.call("POST", path + "/finalizacion", "{\"descripcion\":\"Reparado\"}", true).statusCode());
    }
    String estadoSql(int id) { return db.queryForObject("SELECT estado FROM incidencias WHERE id_incidencia=?", String.class, id); }

    @Test void cierresConcurrentesNoDuplicanEvento() throws Exception {
        try (var autor = session("autor"); var admin = session("admin"); var tecnico = session("tecnico");
             var otraSesion = session("admin"); var pool = java.util.concurrent.Executors.newFixedThreadPool(2)) {
            int id = reparada(autor, admin, tecnico); String path = "/api/incidencias/" + id;
            assertEquals(204, autor.call("POST", path + "/confirmacion", "{\"conforme\":true}", true).statusCode());
            var inicio = new java.util.concurrent.CountDownLatch(1);
            var a = pool.submit(() -> { inicio.await(); return admin.call("POST", path + "/cierre", null, true).statusCode(); });
            var b = pool.submit(() -> { inicio.await(); return otraSesion.call("POST", path + "/cierre", null, true).statusCode(); });
            inicio.countDown();
            var codigos = new ArrayList<>(List.of(a.get(30, java.util.concurrent.TimeUnit.SECONDS), b.get(30, java.util.concurrent.TimeUnit.SECONDS)));
            Collections.sort(codigos);
            assertEquals(List.of(204, 403), codigos);
            assertEquals("FINALIZADA", estadoSql(id));
            assertEquals(1, db.queryForObject("SELECT COUNT(*) FROM historial_incidencias WHERE id_incidencia=? AND accion='CIERRE'", Integer.class, id));
        }
    }

    @Test void confirmacionCierreYReaperturaConNuevoCiclo() throws Exception {
        try (var autor = session("autor"); var admin = session("admin"); var tecnico = session("tecnico")) {
            int id = reparada(autor, admin, tecnico);
            String path = "/api/incidencias/" + id;
            assertEquals(403, admin.call("POST", path + "/cierre", null, true).statusCode());
            assertEquals(204, autor.call("POST", path + "/confirmacion", "{\"conforme\":true}", true).statusCode());
            assertEquals("PENDIENTE_CIERRE", estadoSql(id));
            assertNotNull(db.queryForMap("SELECT fecha_confirmacion FROM incidencias WHERE id_incidencia=?", id).get("fecha_confirmacion"));
            assertEquals(403, autor.call("POST", path + "/confirmacion", "{\"conforme\":true}", true).statusCode());
            assertEquals(204, admin.call("POST", path + "/cierre", null, true).statusCode());
            assertEquals("FINALIZADA", estadoSql(id));
            var detalle = json.readTree(autor.call("GET", path, null, false).body());
            assertFalse(detalle.get("fechaCierre").isNull()); assertFalse(detalle.get("fechaConfirmacion").isNull());
            assertEquals(403, admin.call("POST", path + "/cierre", null, true).statusCode());
            assertEquals(400, admin.call("POST", path + "/reapertura", "{\"motivo\":\" \"}", true).statusCode());
            assertEquals(204, admin.call("POST", path + "/reapertura", "{\"motivo\":\"  El fallo ha vuelto  \"}", true).statusCode());
            var row = db.queryForMap("SELECT * FROM incidencias WHERE id_incidencia=?", id);
            assertEquals("PENDIENTE", row.get("estado"));
            for (String campo : List.of("id_tecnico_asignado", "fecha_asignacion", "fecha_prevista", "fecha_inicio", "fecha_reparacion", "fecha_confirmacion", "fecha_cierre")) assertNull(row.get(campo), campo);
            assertEquals(users.get("autor"), row.get("id_usuario_reporta"));
            assertEquals(users.get("autor"), row.get("id_vecino_verificador"));
            var evento = db.queryForMap("SELECT * FROM historial_incidencias WHERE id_incidencia=? AND accion='REAPERTURA'", id);
            assertEquals(users.get("admin"), evento.get("id_usuario_actor"));
            assertEquals(users.get("tecnico"), evento.get("id_tecnico_anterior"));
            assertTrue(evento.get("descripcion").toString().contains("Cierre anterior: "));
            assertEquals(7, eventos(id));
            assertEquals(403, admin.call("POST", path + "/reapertura", "{\"motivo\":\"Repetida\"}", true).statusCode());
            assertEquals(403, tecnico.call("GET", "/api/tecnico/trabajos/" + id, null, false).statusCode());
            assertEquals(204, admin.call("PUT", path + "/asignacion", asignacion(users.get("tecnico"), java.time.LocalDateTime.now().plusDays(3).toString()), true).statusCode());
            terminarReparacion(id, tecnico);
            assertEquals(204, autor.call("POST", path + "/confirmacion", "{\"conforme\":true}", true).statusCode());
            assertEquals(204, admin.call("POST", path + "/cierre", null, true).statusCode());
            assertEquals("FINALIZADA", estadoSql(id));
        }
    }

    @Test void verificadorDesignadoPuedeDevolverReparacion() throws Exception {
        try (var admin = session("admin"); var vecino = session("vecino"); var autor = session("autor"); var tecnico = session("tecnico")) {
            // Alta administrativa: confirma el vecino elegido, no el reportante ni otro vecino.
            int id = crear(admin, users.get("vecino")); String path = "/api/incidencias/" + id;
            assertEquals(204, admin.call("PUT", path + "/asignacion", asignacion(users.get("tecnico"), java.time.LocalDateTime.now().plusDays(2).toString()), true).statusCode());
            terminarReparacion(id, tecnico);
            assertEquals(403, autor.call("POST", path + "/confirmacion", "{\"conforme\":true}", true).statusCode());
            assertEquals(403, admin.call("POST", path + "/confirmacion", "{\"conforme\":true}", true).statusCode());
            assertEquals(400, vecino.call("POST", path + "/confirmacion", "{}", true).statusCode());
            assertEquals(400, vecino.call("POST", path + "/confirmacion", "{\"conforme\":false}", true).statusCode());
            assertEquals(204, vecino.call("POST", path + "/confirmacion", "{\"conforme\":false,\"motivo\":\"Sigue fallando\"}", true).statusCode());
            assertEquals("EN_PROCESO", estadoSql(id));
            var row = db.queryForMap("SELECT * FROM incidencias WHERE id_incidencia=?", id);
            assertNull(row.get("fecha_reparacion")); assertNull(row.get("fecha_confirmacion"));
            assertEquals(users.get("tecnico"), row.get("id_tecnico_asignado"));
            assertEquals(204, tecnico.call("POST", "/api/tecnico/trabajos/" + id + "/finalizacion", "{\"descripcion\":\"Segunda reparacion\"}", true).statusCode());
            assertEquals(204, vecino.call("POST", path + "/confirmacion", "{\"conforme\":true}", true).statusCode());
            assertEquals(204, admin.call("POST", path + "/cierre", null, true).statusCode());
        }
    }

    @Test void cierrePermisosCsrfYCuentasActivas() throws Exception {
        try (var autor = session("autor"); var admin = session("admin"); var tecnico = session("tecnico")) {
            int id = reparada(autor, admin, tecnico); String path = "/api/incidencias/" + id;
            for (String name : List.of("vecino", "ajeno", "tecnico")) try (var s = session(name)) {
                assertEquals(403, s.call("POST", path + "/confirmacion", "{\"conforme\":true}", true).statusCode());
                assertEquals(403, s.call("POST", path + "/cierre", null, true).statusCode());
                assertEquals(403, s.call("POST", path + "/reapertura", "{\"motivo\":\"Intento\"}", true).statusCode());
            }
            assertEquals(403, autor.call("POST", path + "/confirmacion", "{\"conforme\":true}", false).statusCode());
            db.update("UPDATE usuarios_comunidades SET estado='INACTIVO' WHERE id_usuario=? AND id_comunidad=?", users.get("autor"), community);
            assertEquals(403, autor.call("POST", path + "/confirmacion", "{\"conforme\":true}", true).statusCode());
            db.update("UPDATE usuarios_comunidades SET estado='ACTIVO' WHERE id_usuario=? AND id_comunidad=?", users.get("autor"), community);
            assertEquals(204, autor.call("POST", path + "/confirmacion", "{\"conforme\":true}", true).statusCode());
            assertEquals(403, admin.call("POST", path + "/cierre", null, false).statusCode());
            db.update("UPDATE usuarios SET estado='INACTIVO' WHERE id_usuario=?", users.get("admin"));
            assertEquals(403, admin.call("POST", path + "/cierre", null, true).statusCode());
            db.update("UPDATE usuarios SET estado='ACTIVO' WHERE id_usuario=?", users.get("admin"));
            db.update("UPDATE usuarios_comunidades SET estado='INACTIVO' WHERE id_usuario=? AND id_comunidad=?", users.get("admin"), community);
            assertEquals(403, admin.call("POST", path + "/cierre", null, true).statusCode());
            db.update("UPDATE usuarios_comunidades SET estado='ACTIVO' WHERE id_usuario=? AND id_comunidad=?", users.get("admin"), community);
            assertEquals(204, admin.call("POST", path + "/cierre", null, true).statusCode());
            assertEquals(403, admin.call("POST", path + "/reapertura", "{\"motivo\":\"Fallo\"}", false).statusCode());
            assertEquals("FINALIZADA", estadoSql(id));
        }
    }

    @Test void fallosHistorialReviertenConfirmacionCierreYReapertura() throws Exception {
        try (var autor = session("autor"); var admin = session("admin"); var tecnico = session("tecnico")) {
            int id = reparada(autor, admin, tecnico); String path = "/api/incidencias/" + id;
            for (String accion : List.of("confirmacion", "cierre", "reapertura")) {
                String anterior = estadoSql(id); int eventosAntes = eventos(id);
                var antes = db.queryForMap("SELECT * FROM incidencias WHERE id_incidencia=?", id);
                String body = accion.equals("confirmacion") ? "{\"conforme\":true}" : accion.equals("reapertura") ? "{\"motivo\":\"Fallo de nuevo\"}" : null;
                Sesion s = accion.equals("confirmacion") ? autor : admin;
                // Se provoca solo el fallo del historial para verificar la reversión de los datos reales.
                org.mockito.Mockito.doThrow(new IllegalStateException("Fallo provocado de historial"))
                    .when(historial).save(org.mockito.ArgumentMatchers.any(com.gestioncomunidad.backend.entity.HistorialIncidencia.class));
                assertEquals(500, s.call("POST", path + "/" + accion, body, true).statusCode());
                assertEquals(anterior, estadoSql(id)); assertEquals(eventosAntes, eventos(id));
                assertEquals(antes, db.queryForMap("SELECT * FROM incidencias WHERE id_incidencia=?", id));
                org.mockito.Mockito.reset(historial);
                assertEquals(204, s.call("POST", path + "/" + accion, body, true).statusCode());
            }
        }
    }

    int trabajoAsignado(Sesion autor, Sesion admin) throws Exception {
        int id = crear(autor, null);
        assertEquals(204, admin.call("PUT", "/api/incidencias/" + id + "/asignacion",
                asignacion(users.get("tecnico"), java.time.LocalDateTime.now().plusDays(2).toString()), true).statusCode());
        return id;
    }

    @Test void finalizacionesConcurrentesSoloRegistranUnaTransicion() throws Exception {
        try (var autor = session("autor"); var admin = session("admin");
             var tecnico = session("tecnico"); var segundaSesion = session("tecnico");
             var pool = java.util.concurrent.Executors.newFixedThreadPool(2)) {
            int id = trabajoAsignado(autor, admin);
            String path = "/api/tecnico/trabajos/" + id;
            assertEquals(204, tecnico.call("PUT", path + "/inicio", null, true).statusCode());
            var inicio = new java.util.concurrent.CountDownLatch(1);
            String body = "{\"descripcion\":\"Reparacion terminada\"}";
            // Dos sesiones del mismo técnico compiten; el bloqueo debe permitir una sola transición.
            var a = pool.submit(() -> { inicio.await(); return tecnico.call("POST", path + "/finalizacion", body, true).statusCode(); });
            var b = pool.submit(() -> { inicio.await(); return segundaSesion.call("POST", path + "/finalizacion", body, true).statusCode(); });
            inicio.countDown();
            var codigos = new ArrayList<>(List.of(a.get(30, java.util.concurrent.TimeUnit.SECONDS), b.get(30, java.util.concurrent.TimeUnit.SECONDS)));
            Collections.sort(codigos);
            assertEquals(List.of(204, 403), codigos);
            assertEquals(1, db.queryForObject("SELECT COUNT(*) FROM historial_incidencias WHERE id_incidencia=? AND accion='FIN_REPARACION'", Integer.class, id));
            assertEquals("PENDIENTE_CONFIRMACION", db.queryForObject("SELECT estado FROM incidencias WHERE id_incidencia=?", String.class, id));
        }
    }

    @Test void recorridoTecnicoHastaConfirmacion() throws Exception {
        try (var autor = session("autor"); var admin = session("admin"); var tecnico = session("tecnico")) {
            int id = trabajoAsignado(autor, admin);
            int sinAsignar = crear(autor, null);
            String path = "/api/tecnico/trabajos/" + id;
            var lista = json.readTree(tecnico.call("GET", "/api/tecnico/trabajos", null, false).body());
            assertEquals(1, lista.size()); assertEquals(id, lista.get(0).get("id").asInt());
            assertEquals(403, tecnico.call("GET", "/api/tecnico/trabajos/" + sinAsignar, null, false).statusCode());
            assertEquals(200, tecnico.call("GET", path, null, false).statusCode());
            assertEquals(200, tecnico.call("GET", path + "/historial", null, false).statusCode());
            String body = "{\"descripcion\":\"  Sustituido el interruptor  \"}";
            assertEquals(403, tecnico.call("POST", path + "/avances", body, true).statusCode());
            assertEquals(403, tecnico.call("POST", path + "/finalizacion", body, true).statusCode());
            assertEquals(403, tecnico.call("PUT", path + "/inicio", null, false).statusCode());
            assertEquals(204, tecnico.call("PUT", path + "/inicio", null, true).statusCode());
            var inicio = db.queryForObject("SELECT fecha_inicio FROM incidencias WHERE id_incidencia=?", java.time.LocalDateTime.class, id);
            assertNotNull(inicio);
            assertEquals(204, tecnico.call("PUT", path + "/inicio", null, true).statusCode());
            assertEquals(3, eventos(id));
            assertEquals(400, tecnico.call("POST", path + "/avances", "{\"descripcion\":\" \"}", true).statusCode());
            assertEquals(400, tecnico.call("POST", path + "/finalizacion", "{}", true).statusCode());
            assertEquals(403, tecnico.call("POST", path + "/avances", body, false).statusCode());
            assertEquals(204, tecnico.call("POST", path + "/avances", body, true).statusCode());
            assertEquals("EN_PROCESO", db.queryForObject("SELECT estado FROM incidencias WHERE id_incidencia=?", String.class, id));
            assertEquals(403, tecnico.call("POST", path + "/finalizacion", body, false).statusCode());
            assertEquals(204, tecnico.call("POST", path + "/finalizacion", body, true).statusCode());
            var row = db.queryForMap("SELECT * FROM incidencias WHERE id_incidencia=?", id);
            assertEquals("PENDIENTE_CONFIRMACION", row.get("estado"));
            assertNotNull(row.get("fecha_reparacion")); assertNull(row.get("fecha_confirmacion")); assertNull(row.get("fecha_cierre"));
            assertEquals(5, eventos(id));
            var fin = db.queryForMap("SELECT * FROM historial_incidencias WHERE id_incidencia=? AND accion='FIN_REPARACION'", id);
            assertEquals(users.get("tecnico"), fin.get("id_usuario_actor"));
            assertEquals("EN_PROCESO", fin.get("estado_anterior"));
            assertEquals("PENDIENTE_CONFIRMACION", fin.get("estado_nuevo"));
            assertEquals("Sustituido el interruptor", fin.get("descripcion"));
            assertEquals(403, tecnico.call("POST", path + "/finalizacion", body, true).statusCode());
            assertEquals(403, tecnico.call("POST", path + "/avances", body, true).statusCode());
            assertEquals(403, tecnico.call("PUT", path + "/inicio", null, true).statusCode());
            assertEquals(5, json.readTree(autor.call("GET", "/api/incidencias/" + id + "/historial", null, false).body()).size());
        }
    }

    @Test void trabajoTecnicoExigeRolAsignacionYPertenencia() throws Exception {
        try (var autor = session("autor"); var admin = session("admin"); var tecnico = session("tecnico")) {
            int id = trabajoAsignado(autor, admin);
            String path = "/api/tecnico/trabajos/" + id;
            String body = "{\"descripcion\":\"Intento\"}";
            // Ni el autor ni el administrador pueden actuar como técnico con estas rutas.
            for (String name : List.of("autor", "vecino", "admin", "ajeno")) try (var s = session(name)) {
                assertEquals(403, s.call("GET", "/api/tecnico/trabajos", null, false).statusCode());
                assertEquals(403, s.call("GET", path, null, false).statusCode());
                assertEquals(403, s.call("GET", path + "/historial", null, false).statusCode());
                assertEquals(403, s.call("PUT", path + "/inicio", null, true).statusCode());
                assertEquals(403, s.call("POST", path + "/avances", body, true).statusCode());
                assertEquals(403, s.call("POST", path + "/finalizacion", body, true).statusCode());
            }
            // Convertimos una cuenta propia de prueba en segundo técnico de la misma comunidad.
            db.update("UPDATE usuarios SET id_rol=(SELECT id_rol FROM roles WHERE nombre='TECNICO') WHERE id_usuario=?", users.get("vecino"));
            try (var otro = session("vecino")) {
                assertEquals("[]", otro.call("GET", "/api/tecnico/trabajos", null, false).body());
                assertEquals(403, otro.call("GET", path, null, false).statusCode());
                assertEquals(403, otro.call("PUT", path + "/inicio", null, true).statusCode());
            }
            db.update("UPDATE usuarios_comunidades SET estado='INACTIVO' WHERE id_usuario=? AND id_comunidad=?", users.get("tecnico"), community);
            assertEquals("[]", tecnico.call("GET", "/api/tecnico/trabajos", null, false).body());
            assertEquals(403, tecnico.call("GET", path + "/historial", null, false).statusCode());
            assertEquals(403, tecnico.call("PUT", path + "/inicio", null, true).statusCode());
            db.update("UPDATE usuarios SET estado='INACTIVO' WHERE id_usuario=?", users.get("tecnico"));
            assertEquals(403, tecnico.call("GET", "/api/tecnico/trabajos", null, false).statusCode());
            assertEquals(2, eventos(id));
        }
    }

    @Test void falloHistorialRevierteInicioYFinalizacionTecnica() throws Exception {
        try (var autor = session("autor"); var admin = session("admin"); var tecnico = session("tecnico")) {
            int id = trabajoAsignado(autor, admin);
            String path = "/api/tecnico/trabajos/" + id;
            // Solo se simula la avería del historial; fechas, estados y transacción son reales.
            org.mockito.Mockito.doThrow(new IllegalStateException("Fallo provocado de historial"))
                .when(historial).save(org.mockito.ArgumentMatchers.any(com.gestioncomunidad.backend.entity.HistorialIncidencia.class));
            assertEquals(500, tecnico.call("PUT", path + "/inicio", null, true).statusCode());
            assertEquals("ASIGNADA", db.queryForObject("SELECT estado FROM incidencias WHERE id_incidencia=?", String.class, id));
            assertNull(db.queryForMap("SELECT fecha_inicio FROM incidencias WHERE id_incidencia=?", id).get("fecha_inicio"));
            org.mockito.Mockito.reset(historial);
            assertEquals(204, tecnico.call("PUT", path + "/inicio", null, true).statusCode());
            org.mockito.Mockito.doThrow(new IllegalStateException("Fallo provocado de historial"))
                .when(historial).save(org.mockito.ArgumentMatchers.any(com.gestioncomunidad.backend.entity.HistorialIncidencia.class));
            assertEquals(500, tecnico.call("POST", path + "/finalizacion", "{\"descripcion\":\"Reparado\"}", true).statusCode());
            assertEquals("EN_PROCESO", db.queryForObject("SELECT estado FROM incidencias WHERE id_incidencia=?", String.class, id));
            assertNull(db.queryForMap("SELECT fecha_reparacion FROM incidencias WHERE id_incidencia=?", id).get("fecha_reparacion"));
            assertEquals(3, eventos(id));
        }
    }

    String asignacion(int tecnico, String fecha) {
        return "{\"tecnicoId\":" + tecnico + ",\"fechaPrevista\":\"" + fecha + "\"}";
    }

    @Test void asignacionTecnicoConPermisosFechaEHistorial() throws Exception {
        // Comprueba el orden 4 con sesiones y SQL reales, reutilizando datos aislados.
        try (var autor = session("autor"); var admin = session("admin")) {
            int id = crear(autor, null);
            String path = "/api/incidencias/" + id + "/asignacion";
            String candidatos = "/api/incidencias/" + id + "/tecnicos-disponibles";
            String fecha = java.time.LocalDateTime.now().plusDays(2).withNano(0).toString();
            String body = asignacion(users.get("tecnico"), fecha);
            var lista = json.readTree(admin.call("GET", candidatos, null, false).body());
            assertEquals(1, lista.size()); assertEquals(users.get("tecnico").intValue(), lista.get(0).get("id").asInt());
            for (String name : List.of("autor", "vecino", "tecnico", "ajeno")) try (var s = session(name)) {
                assertEquals(403, s.call("GET", candidatos, null, false).statusCode());
                assertEquals(403, s.call("PUT", path, body, true).statusCode());
            }
            assertEquals(403, admin.call("PUT", path, body, false).statusCode());
            assertEquals(400, admin.call("PUT", path, "{}", true).statusCode());
            assertEquals(400, admin.call("PUT", path, asignacion(users.get("vecino"), fecha), true).statusCode());
            assertEquals(400, admin.call("PUT", path, asignacion(users.get("tecnico"), "2000-01-01T10:00:00"), true).statusCode());
            assertEquals(1, eventos(id));
            assertEquals(204, admin.call("PUT", path, body, true).statusCode());
            assertEquals(204, admin.call("PUT", path, body, true).statusCode());
            assertEquals(2, eventos(id));
            var row = db.queryForMap("SELECT * FROM incidencias WHERE id_incidencia=?", id);
            assertEquals("ASIGNADA", row.get("estado"));
            assertEquals(users.get("tecnico"), row.get("id_tecnico_asignado"));
            assertNotNull(row.get("fecha_asignacion"));
            assertEquals(java.time.LocalDateTime.parse(fecha), db.queryForObject("SELECT fecha_prevista FROM incidencias WHERE id_incidencia=?", java.time.LocalDateTime.class, id));
            var evento = db.queryForMap("SELECT * FROM historial_incidencias WHERE id_incidencia=? AND accion='ASIGNACION_TECNICO'", id);
            assertEquals(users.get("admin"), evento.get("id_usuario_actor"));
            assertEquals(users.get("tecnico"), evento.get("id_tecnico_nuevo"));
            assertEquals("PENDIENTE", evento.get("estado_anterior"));
            assertEquals("ASIGNADA", evento.get("estado_nuevo"));
            assertEquals(403, admin.call("PUT", path, asignacion(users.get("tecnico"), java.time.LocalDateTime.now().plusDays(3).withNano(0).toString()), true).statusCode());
        }
    }

    @Test void asignacionRevalidaCuentaPertenenciaYEstado() throws Exception {
        try (var autor = session("autor"); var admin = session("admin")) {
            int id = crear(autor, null);
            String path = "/api/incidencias/" + id + "/asignacion";
            String candidatos = "/api/incidencias/" + id + "/tecnicos-disponibles";
            String body = asignacion(users.get("tecnico"), java.time.LocalDateTime.now().plusDays(2).toString());
            db.update("UPDATE usuarios SET estado='INACTIVO' WHERE id_usuario=?", users.get("tecnico"));
            assertEquals("[]", admin.call("GET", candidatos, null, false).body());
            assertEquals(400, admin.call("PUT", path, body, true).statusCode());
            db.update("UPDATE usuarios SET estado='ACTIVO' WHERE id_usuario=?", users.get("tecnico"));
            db.update("UPDATE usuarios_comunidades SET estado='PENDIENTE' WHERE id_usuario=? AND id_comunidad=?", users.get("tecnico"), community);
            assertEquals("[]", admin.call("GET", candidatos, null, false).body());
            assertEquals(400, admin.call("PUT", path, body, true).statusCode());
            db.update("UPDATE usuarios_comunidades SET estado='ACTIVO' WHERE id_usuario=? AND id_comunidad=?", users.get("tecnico"), community);
            for (String estado : List.of("EN_PROCESO", "PENDIENTE_CONFIRMACION", "PENDIENTE_CIERRE", "FINALIZADA", "RECHAZADA")) {
                db.update("UPDATE incidencias SET estado=? WHERE id_incidencia=?", estado, id);
                assertEquals(403, admin.call("PUT", path, body, true).statusCode());
            }
            db.update("UPDATE incidencias SET estado='PENDIENTE' WHERE id_incidencia=?", id);
            db.update("UPDATE usuarios_comunidades SET estado='INACTIVO' WHERE id_usuario=? AND id_comunidad=?", users.get("admin"), community);
            assertEquals(403, admin.call("PUT", path, body, true).statusCode());
            assertEquals(403, admin.call("GET", candidatos, null, false).statusCode());
            assertEquals(1, eventos(id));
            assertNull(db.queryForMap("SELECT id_tecnico_asignado FROM incidencias WHERE id_incidencia=?", id).get("id_tecnico_asignado"));
        }
    }

    @Test void falloHistorialRevierteAsignacion() throws Exception {
        try (var autor = session("autor"); var admin = session("admin")) {
            int id = crear(autor, null);
            // El fallo simulado permite comprobar la atomicidad real de la transacción MySQL.
            org.mockito.Mockito.doThrow(new IllegalStateException("Fallo provocado de historial"))
                .when(historial).save(org.mockito.ArgumentMatchers.any(com.gestioncomunidad.backend.entity.HistorialIncidencia.class));
            assertEquals(500, admin.call("PUT", "/api/incidencias/" + id + "/asignacion",
                asignacion(users.get("tecnico"), java.time.LocalDateTime.now().plusDays(2).toString()), true).statusCode());
            var row = db.queryForMap("SELECT * FROM incidencias WHERE id_incidencia=?", id);
            assertEquals("PENDIENTE", row.get("estado"));
            assertNull(row.get("id_tecnico_asignado")); assertNull(row.get("fecha_prevista")); assertNull(row.get("fecha_asignacion"));
            assertEquals(1, eventos(id));
        }
    }

    @Test void falloHistorialRevierteEdicion() throws Exception {
        try (var autor = session("autor")) {
            int id = crear(autor, null);
            // Solo se provoca el fallo del repositorio; el servicio y su transacción son reales.
            org.mockito.Mockito.doThrow(new IllegalStateException("Fallo provocado de historial"))
                .when(historial).save(org.mockito.ArgumentMatchers.any(com.gestioncomunidad.backend.entity.HistorialIncidencia.class));
            assertEquals(500, autor.call("PUT", "/api/incidencias/" + id + "/datos", edit("No debe persistir"), true).statusCode());
            assertEquals("Inicial", db.queryForObject("SELECT titulo FROM incidencias WHERE id_incidencia=?", String.class, id));
            assertEquals(1, eventos(id));
        }
    }

    @Test void edicionesConcurrentesMantienenCadenaDeHistorial() throws Exception {
        try (var autor = session("autor"); var admin = session("admin");
             var pool = java.util.concurrent.Executors.newFixedThreadPool(2)) {
            int id = crear(autor, null);
            var inicio = new java.util.concurrent.CountDownLatch(1);
            var a = pool.submit(() -> { inicio.await(); return autor.call("PUT", "/api/incidencias/" + id + "/datos", edit("Cambio A"), true); });
            var b = pool.submit(() -> { inicio.await(); return admin.call("PUT", "/api/incidencias/" + id + "/datos", edit("Cambio B"), true); });
            inicio.countDown();
            assertEquals(204, a.get(30, java.util.concurrent.TimeUnit.SECONDS).statusCode());
            assertEquals(204, b.get(30, java.util.concurrent.TimeUnit.SECONDS).statusCode());
            var eventos = db.queryForList("SELECT descripcion FROM historial_incidencias WHERE id_incidencia=? AND accion='EDICION_DATOS' ORDER BY id_historial", String.class, id);
            assertEquals(2, eventos.size());
            String primero = eventos.get(0).contains("Título nuevo: Cambio A") ? "Cambio A" : "Cambio B";
            String ultimo = primero.equals("Cambio A") ? "Cambio B" : "Cambio A";
            assertTrue(eventos.get(0).contains("Título anterior: Inicial"));
            assertTrue(eventos.get(1).contains("Título anterior: " + primero));
            assertTrue(eventos.get(1).contains("Título nuevo: " + ultimo));
            assertEquals(ultimo, db.queryForObject("SELECT titulo FROM incidencias WHERE id_incidencia=?", String.class, id));
        }
    }

    @Test void consultaComunidadYSesion() throws Exception {
        try (var autor = session("autor")) {
            int id = crear(autor, null);
            for (String name : List.of("autor", "vecino", "admin")) try (var s = session(name)) {
                var list = json.readTree(s.call("GET", "/api/incidencias", null, false).body());
                assertEquals(1, list.size()); assertEquals(id, list.get(0).get("id").asInt());
                assertEquals(200, s.call("GET", "/api/incidencias/" + id, null, false).statusCode());
                assertEquals(200, s.call("GET", "/api/incidencias/" + id + "/historial", null, false).statusCode());
            }
            for (String name : List.of("ajeno", "pendiente")) try (var s = session(name)) {
                assertEquals("[]", s.call("GET", "/api/incidencias", null, false).body());
                assertEquals("[]", s.call("GET", "/api/espacios", null, false).body());
                assertEquals(403, s.call("GET", "/api/incidencias/" + id, null, false).statusCode());
                assertEquals(403, s.call("GET", "/api/incidencias/" + id + "/historial", null, false).statusCode());
            }
            try (var anon = new Sesion()) {
                for (String path : List.of("/api/incidencias", "/api/espacios", "/api/incidencias/" + id + "/historial")) {
                    var r = anon.call("GET", path, null, false);
                    assertEquals(302, r.statusCode()); assertTrue(r.headers().firstValue("location").orElse("").endsWith("/login"));
                }
            }
        }
    }

    @Test void edicionPermisosCsrfEHistorial() throws Exception {
        try (var autor = session("autor"); var admin = session("admin")) {
            int id = crear(autor, null); String path = "/api/incidencias/" + id + "/datos";
            for (String name : List.of("vecino", "ajeno", "tecnico", "pendiente")) try (var s = session(name)) {
                assertEquals(403, s.call("PUT", path, edit("Denegada"), true).statusCode());
            }
            assertEquals(403, autor.call("PUT", path, edit("Sin token"), false).statusCode());
            assertEquals(400, autor.call("PUT", path, edit(""), true).statusCode());
            assertEquals(1, eventos(id));
            assertEquals(204, autor.call("PUT", path, edit("Autor"), true).statusCode());
            assertEquals(204, autor.call("PUT", path, edit("Autor"), true).statusCode());
            assertEquals(2, eventos(id));
            assertEquals(204, admin.call("PUT", path, edit("Administrador"), true).statusCode());
            assertEquals(3, eventos(id));
            assertEquals("Administrador", db.queryForObject("SELECT titulo FROM incidencias WHERE id_incidencia=?", String.class, id));
            assertEquals(users.get("admin"), db.queryForObject("SELECT id_usuario_actor FROM historial_incidencias WHERE id_incidencia=? ORDER BY id_historial DESC LIMIT 1", Integer.class, id));
            assertTrue(db.queryForObject("SELECT descripcion FROM historial_incidencias WHERE id_incidencia=? ORDER BY id_historial DESC LIMIT 1", String.class, id).contains("Título anterior: Autor"));
        }
    }

    @Test void altaAdministrativaYRechazosSinInserciones() throws Exception {
        try (var admin = session("admin")) {
            assertEquals(403, admin.call("POST", "/api/incidencias", alta(users.get("vecino")), false).statusCode());
            for (Integer verifier : Arrays.asList(null, users.get("admin"), users.get("ajeno"), users.get("pendiente"), -1))
                assertEquals(400, admin.call("POST", "/api/incidencias", alta(verifier), true).statusCode());
            try (var tecnico = session("tecnico")) { assertEquals(403, tecnico.call("POST", "/api/incidencias", alta(null), true).statusCode()); }
            assertEquals(0, db.queryForObject("SELECT COUNT(*) FROM incidencias WHERE id_espacio=?", Integer.class, space));
            int id = crear(admin, users.get("vecino"));
            var row = db.queryForMap("SELECT * FROM incidencias WHERE id_incidencia=?", id);
            assertEquals(users.get("admin"), row.get("id_usuario_reporta"));
            assertEquals(users.get("vecino"), row.get("id_vecino_verificador"));
            assertEquals("PENDIENTE", row.get("estado")); assertNull(row.get("id_tecnico_asignado"));
            assertEquals(1, eventos(id));
            assertEquals(users.get("admin"), db.queryForObject("SELECT id_usuario_actor FROM historial_incidencias WHERE id_incidencia=?", Integer.class, id));
            db.update("UPDATE usuarios SET estado='INACTIVO' WHERE id_usuario=?", users.get("vecino"));
            assertEquals(400, admin.call("POST", "/api/incidencias", alta(users.get("vecino")), true).statusCode());
            db.update("UPDATE usuarios_comunidades SET estado='INACTIVO' WHERE id_usuario=? AND id_comunidad=?", users.get("admin"), community);
            assertEquals(403, admin.call("POST", "/api/incidencias", alta(users.get("autor")), true).statusCode());
            assertEquals(1, db.queryForObject("SELECT COUNT(*) FROM incidencias WHERE id_espacio=?", Integer.class, space));
        }
    }
}
