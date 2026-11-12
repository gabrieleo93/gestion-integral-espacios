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
    // Carpeta exclusiva de esta ejecución: las pruebas nunca escriben en el almacén real.
    static final java.nio.file.Path fotosPrueba = carpetaFotos();
    static java.nio.file.Path carpetaFotos() {
        try { return java.nio.file.Files.createTempDirectory("evidencias-test-"); }
        catch (java.io.IOException e) { throw new java.io.UncheckedIOException(e); }
    }
    @org.springframework.test.context.DynamicPropertySource
    static void propiedades(org.springframework.test.context.DynamicPropertyRegistry registry) {
        registry.add("app.evidencias.directorio", () -> fotosPrueba.toString());
    }
    @AfterAll static void limpiarCarpetaFotos() throws Exception {
        // Solo se retira la carpeta temporal propia, vacía tras limpiar las evidencias de cada caso.
        java.nio.file.Files.delete(fotosPrueba);
    }
    @LocalServerPort int port;
    @Autowired JdbcTemplate db;
    @Autowired PasswordEncoder encoder;
    @org.springframework.test.context.bean.override.mockito.MockitoSpyBean
    com.gestioncomunidad.backend.repositories.HistorialIncidenciaRepository historial;
    @org.springframework.test.context.bean.override.mockito.MockitoSpyBean
    com.gestioncomunidad.backend.repositories.HistorialPertenenciaRepository historialAltas;
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
        org.mockito.Mockito.reset(historialAltas);
        // Borrado acotado a los identificadores creados por este caso, respetando claves foráneas.
        if (space != 0) {
            db.update("DELETE c FROM costes c JOIN incidencias i ON i.id_incidencia=c.id_incidencia WHERE i.id_espacio=?", space);
            // Respeta votos -> votaciones -> presupuestos -> incidencia, solo en los datos del caso.
            db.update("DELETE v FROM votos v JOIN votaciones t ON t.id_votacion=v.id_votacion JOIN presupuestos p ON p.id_presupuesto=t.id_presupuesto JOIN incidencias i ON i.id_incidencia=p.id_incidencia WHERE i.id_espacio=?", space);
            db.update("DELETE t FROM votaciones t JOIN presupuestos p ON p.id_presupuesto=t.id_presupuesto JOIN incidencias i ON i.id_incidencia=p.id_incidencia WHERE i.id_espacio=?", space);
            db.update("DELETE p FROM presupuestos p JOIN incidencias i ON i.id_incidencia=p.id_incidencia WHERE i.id_espacio=?", space);
            for (String clave : db.queryForList("SELECT e.url_archivo FROM evidencias e JOIN incidencias i ON i.id_incidencia=e.id_incidencia WHERE i.id_espacio=?", String.class, space)) {
                try { java.nio.file.Files.deleteIfExists(fotosPrueba.resolve(clave)); }
                catch (java.io.IOException e) { throw new java.io.UncheckedIOException(e); }
            }
            db.update("DELETE e FROM evidencias e JOIN incidencias i ON i.id_incidencia=e.id_incidencia WHERE i.id_espacio=?", space);
            // Desvincula únicamente los avisos de prueba antes de borrar la relación autorreferenciada.
            db.update("UPDATE incidencias SET id_incidencia_origen=NULL WHERE id_espacio=?", space);
            db.update("DELETE h FROM historial_incidencias h JOIN incidencias i ON i.id_incidencia=h.id_incidencia WHERE i.id_espacio=?", space);
            db.update("DELETE FROM incidencias WHERE id_espacio=?", space);
            db.update("DELETE FROM espacios WHERE id_espacio=?", space);
        }
        // El historial de altas depende de la pertenencia: se limpia primero solo para esta comunidad de prueba.
        db.update("DELETE h FROM historial_pertenencias h JOIN usuarios_comunidades p ON p.id_usuario_comunidad=h.id_usuario_comunidad WHERE p.id_comunidad=?", community);
        db.update("DELETE FROM usuarios_comunidades WHERE id_comunidad=?", community);
        // Actas y reuniones se eliminan antes de sus autores y comunidad, solo dentro del caso de prueba.
        db.update("DELETE a FROM actas a JOIN reuniones r ON r.id_reunion=a.id_reunion WHERE r.id_comunidad=?", community);
        db.update("DELETE FROM reuniones WHERE id_comunidad=?", community);
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

    String reunionBody() { return "{\"titulo\":\"Reunion de comunidad\",\"descripcion\":\"Orden del dia\",\"lugar\":\"Sala comun\",\"fechaReunion\":\"" + java.time.LocalDateTime.now().plusDays(2).withNano(0) + "\"}"; }
    int crearReunion(Sesion admin) throws Exception {
        var r = admin.call("POST", "/api/comunidades/" + community + "/reuniones", reunionBody(), true);
        assertEquals(201, r.statusCode(), r.body()); return json.readTree(r.body()).get("id").asInt();
    }
    void reunionCelebrada(int id) { db.update("UPDATE reuniones SET fecha_reunion=? WHERE id_reunion=?", java.time.LocalDateTime.now().minusDays(1), id); }

    @Test void reunionYActaPublicadaConAutorYConsulta() throws Exception {
        try (var admin = session("admin"); var autor = session("autor"); var vecino = session("vecino")) {
            int id = crearReunion(admin); String path = "/api/reuniones/" + id;
            assertEquals(1, json.readTree(autor.call("GET", "/api/comunidades/" + community + "/reuniones", null, false).body()).size());
            assertEquals(200, vecino.call("GET", path, null, false).statusCode());
            assertEquals(404, vecino.call("GET", path + "/acta", null, false).statusCode());
            assertEquals(403, admin.call("POST", path + "/acta", "{\"contenido\":\"Acuerdos\"}", true).statusCode());
            reunionCelebrada(id);
            var r = admin.call("POST", path + "/acta", "{\"contenido\":\"  Se aprueba revisar la iluminación.  \"}", true);
            assertEquals(201, r.statusCode(), r.body());
            var acta = json.readTree(r.body()); assertEquals(users.get("admin").intValue(), acta.get("publicadorId").asInt());
            assertFalse(acta.get("fechaPublicacion").isNull());
            assertEquals("Se aprueba revisar la iluminación.", acta.get("contenido").asText());
            assertEquals(200, autor.call("GET", path + "/acta", null, false).statusCode());
            assertEquals(403, admin.call("POST", path + "/acta", "{\"contenido\":\"Sustitucion\"}", true).statusCode());
            assertEquals(1, db.queryForObject("SELECT COUNT(*) FROM actas WHERE id_reunion=?", Integer.class, id));
            assertEquals(users.get("admin"), db.queryForObject("SELECT id_usuario_creador FROM reuniones WHERE id_reunion=?", Integer.class, id));
        }
    }

    @Test void reunionesActasPermisosCsrfYValidaciones() throws Exception {
        try (var admin = session("admin"); var autor = session("autor"); var ajeno = session("ajeno"); var tecnico = session("tecnico")) {
            String base = "/api/comunidades/" + community + "/reuniones";
            assertEquals(403, autor.call("POST", base, reunionBody(), true).statusCode());
            assertEquals(403, admin.call("POST", base, reunionBody(), false).statusCode());
            assertEquals(400, admin.call("POST", base, "{}", true).statusCode());
            assertEquals(400, admin.call("POST", base, "{\"titulo\":\"Pasada\",\"lugar\":\"Sala\",\"fechaReunion\":\"2000-01-01T10:00:00\"}", true).statusCode());
            int id = crearReunion(admin); String path = "/api/reuniones/" + id; reunionCelebrada(id);
            assertEquals(400, admin.call("POST", path + "/acta", "{\"contenido\":\" \"}", true).statusCode());
            assertEquals(403, admin.call("POST", path + "/acta", "{\"contenido\":\"Acta\"}", false).statusCode());
            assertEquals(403, autor.call("POST", path + "/acta", "{\"contenido\":\"Acta\"}", true).statusCode());
            for (Sesion s : List.of(ajeno, tecnico)) {
                assertEquals(403, s.call("GET", base, null, false).statusCode());
                assertEquals(403, s.call("GET", path, null, false).statusCode());
                assertEquals(403, s.call("GET", path + "/acta", null, false).statusCode());
            }
            db.update("UPDATE usuarios SET estado='INACTIVO' WHERE id_usuario=?", users.get("admin"));
            assertEquals(403, admin.call("POST", path + "/acta", "{\"contenido\":\"Acta\"}", true).statusCode());
            db.update("UPDATE usuarios SET estado='ACTIVO' WHERE id_usuario=?", users.get("admin"));
            db.update("UPDATE usuarios_comunidades SET estado='INACTIVO' WHERE id_usuario=? AND id_comunidad=?", users.get("admin"), community);
            assertEquals(403, admin.call("POST", path + "/acta", "{\"contenido\":\"Acta\"}", true).statusCode());
            assertEquals(0, db.queryForObject("SELECT COUNT(*) FROM actas WHERE id_reunion=?", Integer.class, id));
            try (var anon = new Sesion()) { assertEquals(302, anon.call("GET", base, null, false).statusCode()); }
        }
    }

    @Test void publicacionConcurrenteDeActaEsUnica() throws Exception {
        try (var admin = session("admin"); var otra = session("admin"); var pool = java.util.concurrent.Executors.newFixedThreadPool(2)) {
            int id = crearReunion(admin); reunionCelebrada(id);
            String path = "/api/reuniones/" + id + "/acta"; var inicio = new java.util.concurrent.CountDownLatch(1);
            var a = pool.submit(() -> { inicio.await(); return admin.call("POST", path, "{\"contenido\":\"Acta A\"}", true).statusCode(); });
            var b = pool.submit(() -> { inicio.await(); return otra.call("POST", path, "{\"contenido\":\"Acta B\"}", true).statusCode(); });
            inicio.countDown(); var codigos = new ArrayList<>(List.of(a.get(30, java.util.concurrent.TimeUnit.SECONDS), b.get(30, java.util.concurrent.TimeUnit.SECONDS)));
            Collections.sort(codigos); assertEquals(List.of(201, 403), codigos);
            assertEquals(1, db.queryForObject("SELECT COUNT(*) FROM actas WHERE id_reunion=?", Integer.class, id));
        }
    }

    String costeBody(String importe) { return "{\"concepto\":\"Material de reparacion\",\"importe\":" + importe + "}"; }
    int crearCoste(int incidenciaId, Sesion admin, String importe) throws Exception {
        var r = admin.call("POST", "/api/incidencias/" + incidenciaId + "/costes", costeBody(importe), true);
        assertEquals(201, r.statusCode(), r.body()); return json.readTree(r.body()).get("id").asInt();
    }

    @Test void costesEdicionFacturacionYTotalesExactos() throws Exception {
        try (var autor = session("autor"); var admin = session("admin"); var vecino = session("vecino")) {
            int i = crear(autor, null), c = crearCoste(i, admin, "0.10"); crearCoste(i, admin, "0.20");
            String path = "/api/incidencias/" + i + "/costes";
            var resumen = json.readTree(vecino.call("GET", path + "/resumen", null, false).body());
            assertEquals(0, new java.math.BigDecimal("0.30").compareTo(resumen.get("total").decimalValue()));
            assertEquals(204, admin.call("PUT", "/api/costes/" + c, costeBody("12.34"), true).statusCode());
            assertEquals(204, admin.call("PUT", "/api/costes/" + c, costeBody("12.34"), true).statusCode());
            assertEquals(1, db.queryForObject("SELECT COUNT(*) FROM historial_incidencias WHERE id_incidencia=? AND accion='COSTE_EDITADO'", Integer.class, i));
            assertEquals(204, admin.call("POST", "/api/costes/" + c + "/facturacion", null, true).statusCode());
            assertEquals(204, admin.call("POST", "/api/costes/" + c + "/facturacion", null, true).statusCode());
            assertEquals(403, admin.call("PUT", "/api/costes/" + c, costeBody("1.00"), true).statusCode());
            resumen = json.readTree(autor.call("GET", path + "/resumen", null, false).body());
            assertEquals(0, new java.math.BigDecimal("12.54").compareTo(resumen.get("total").decimalValue()));
            assertEquals(0, new java.math.BigDecimal("12.34").compareTo(resumen.get("facturado").decimalValue()));
            assertEquals(0, new java.math.BigDecimal("0.20").compareTo(resumen.get("pendienteFacturar").decimalValue()));
            assertEquals("FACTURADO", db.queryForObject("SELECT estado_facturacion FROM costes WHERE id_coste=?", String.class, c));
            assertEquals(1, db.queryForObject("SELECT COUNT(*) FROM historial_incidencias WHERE id_incidencia=? AND accion='COSTE_FACTURADO'", Integer.class, i));
            assertEquals("PENDIENTE", estadoSql(i));
        }
    }

    @Test void costesPermisosEstadoValidacionYCsrf() throws Exception {
        try (var autor = session("autor"); var admin = session("admin"); var ajeno = session("ajeno"); var tecnico = session("tecnico")) {
            int i = crear(autor, null); String path = "/api/incidencias/" + i + "/costes";
            assertEquals(403, autor.call("POST", path, costeBody("1.00"), true).statusCode());
            assertEquals(403, tecnico.call("POST", path, costeBody("1.00"), true).statusCode());
            assertEquals(403, admin.call("POST", path, costeBody("1.00"), false).statusCode());
            assertEquals(400, admin.call("POST", path, costeBody("-1.00"), true).statusCode());
            assertEquals(400, admin.call("POST", path, costeBody("1.234"), true).statusCode());
            int c = crearCoste(i, admin, "1.00");
            assertEquals(403, ajeno.call("GET", path, null, false).statusCode());
            assertEquals(403, ajeno.call("GET", path + "/resumen", null, false).statusCode());
            assertEquals(403, autor.call("POST", "/api/costes/" + c + "/facturacion", null, true).statusCode());
            assertEquals(403, admin.call("POST", "/api/costes/" + c + "/facturacion", null, false).statusCode());
            assertEquals(403, admin.call("PUT", "/api/costes/" + c, costeBody("2.00"), false).statusCode());
            db.update("UPDATE usuarios_comunidades SET estado='INACTIVO' WHERE id_usuario=? AND id_comunidad=?", users.get("admin"), community);
            assertEquals(403, admin.call("PUT", "/api/costes/" + c, costeBody("2.00"), true).statusCode());
            assertEquals(403, admin.call("POST", "/api/costes/" + c + "/facturacion", null, true).statusCode());
            db.update("UPDATE usuarios_comunidades SET estado='ACTIVO' WHERE id_usuario=? AND id_comunidad=?", users.get("admin"), community);
            db.update("UPDATE incidencias SET estado='RECHAZADA' WHERE id_incidencia=?", i);
            assertEquals(403, admin.call("POST", path, costeBody("2.00"), true).statusCode());
            assertEquals(403, admin.call("POST", "/api/costes/" + c + "/facturacion", null, true).statusCode());
            assertEquals("PENDIENTE_FACTURAR", db.queryForObject("SELECT estado_facturacion FROM costes WHERE id_coste=?", String.class, c));
        }
    }

    @Test void falloHistorialRevierteCosteEdicionYFacturacion() throws Exception {
        try (var autor = session("autor"); var admin = session("admin")) {
            int i = crear(autor, null);
            org.mockito.Mockito.doThrow(new IllegalStateException("Fallo provocado de historial"))
                .when(historial).save(org.mockito.ArgumentMatchers.any(com.gestioncomunidad.backend.entity.HistorialIncidencia.class));
            assertEquals(500, admin.call("POST", "/api/incidencias/" + i + "/costes", costeBody("1.00"), true).statusCode());
            assertEquals(0, db.queryForObject("SELECT COUNT(*) FROM costes WHERE id_incidencia=?", Integer.class, i));
            org.mockito.Mockito.reset(historial); int c = crearCoste(i, admin, "1.00");
            org.mockito.Mockito.doThrow(new IllegalStateException("Fallo provocado de historial"))
                .when(historial).save(org.mockito.ArgumentMatchers.any(com.gestioncomunidad.backend.entity.HistorialIncidencia.class));
            assertEquals(500, admin.call("PUT", "/api/costes/" + c, costeBody("2.00"), true).statusCode());
            assertEquals(new java.math.BigDecimal("1.00"), db.queryForObject("SELECT importe FROM costes WHERE id_coste=?", java.math.BigDecimal.class, c));
            assertEquals(500, admin.call("POST", "/api/costes/" + c + "/facturacion", null, true).statusCode());
            assertEquals("PENDIENTE_FACTURAR", db.queryForObject("SELECT estado_facturacion FROM costes WHERE id_coste=?", String.class, c));
            assertEquals(2, eventos(i));
        }
    }

    @Test void facturacionConcurrenteNoDuplicaHistorial() throws Exception {
        try (var autor = session("autor"); var admin = session("admin"); var otra = session("admin"); var pool = java.util.concurrent.Executors.newFixedThreadPool(2)) {
            int i = crear(autor, null), c = crearCoste(i, admin, "1.00");
            var inicio = new java.util.concurrent.CountDownLatch(1); String path = "/api/costes/" + c + "/facturacion";
            var a = pool.submit(() -> { inicio.await(); return admin.call("POST", path, null, true).statusCode(); });
            var b = pool.submit(() -> { inicio.await(); return otra.call("POST", path, null, true).statusCode(); });
            inicio.countDown(); assertEquals(204, a.get(30, java.util.concurrent.TimeUnit.SECONDS)); assertEquals(204, b.get(30, java.util.concurrent.TimeUnit.SECONDS));
            assertEquals(1, db.queryForObject("SELECT COUNT(*) FROM historial_incidencias WHERE id_incidencia=? AND accion='COSTE_FACTURADO'", Integer.class, i));
        }
    }

    String presupuestoBody() { return "{\"titulo\":\"Sustituir interruptor\",\"descripcion\":\"Material y mano de obra\",\"proveedor\":\"Servicio técnico\",\"importe\":123.45}"; }
    int presupuesto(int incidenciaId, Sesion tecnico) throws Exception {
        var r = tecnico.call("POST", "/api/incidencias/" + incidenciaId + "/presupuestos", presupuestoBody(), true);
        assertEquals(201, r.statusCode(), r.body()); return json.readTree(r.body()).get("id").asInt();
    }
    String abrirBody() { return "{\"fechaFin\":\"" + java.time.LocalDateTime.now().plusDays(1).withNano(0) + "\"}"; }
    int abrirPresupuesto(int presupuestoId, Sesion admin) throws Exception {
        var r = admin.call("POST", "/api/presupuestos/" + presupuestoId + "/votacion", abrirBody(), true);
        assertEquals(201, r.statusCode(), r.body()); return json.readTree(r.body()).get("id").asInt();
    }
    void vencerVotacion(int id) {
        // Simula el transcurso del plazo sobre la votación exclusiva del test, sin esperas de reloj.
        db.update("UPDATE votaciones SET fecha_inicio=?, fecha_fin=? WHERE id_votacion=?",
                java.time.LocalDateTime.now().minusDays(2), java.time.LocalDateTime.now().minusDays(1), id);
    }
    int votar(Sesion s, int votacionId, String opcion) throws Exception {
        return s.call("POST", "/api/votaciones/" + votacionId + "/votos", "{\"opcion\":\"" + opcion + "\"}", true).statusCode();
    }

    @Test void presupuestoTecnicoVotacionYResultados() throws Exception {
        try (var autor = session("autor"); var vecino = session("vecino"); var admin = session("admin"); var tecnico = session("tecnico")) {
            int incidenciaId = trabajoAsignado(autor, admin), p = presupuesto(incidenciaId, tecnico);
            assertEquals(new java.math.BigDecimal("123.45"), db.queryForObject("SELECT importe FROM presupuestos WHERE id_presupuesto=?", java.math.BigDecimal.class, p));
            assertEquals(users.get("tecnico"), db.queryForObject("SELECT id_usuario_actor FROM historial_incidencias WHERE id_incidencia=? AND accion='PRESUPUESTO_CREADO'", Integer.class, incidenciaId));
            assertEquals(1, json.readTree(admin.call("GET", "/api/incidencias/" + incidenciaId + "/presupuestos", null, false).body()).size());
            int v = abrirPresupuesto(p, admin);
            assertEquals(204, votar(autor, v, "A_FAVOR")); assertEquals(204, votar(vecino, v, "ABSTENCION"));
            assertEquals(403, votar(autor, v, "EN_CONTRA"));
            var parcial = json.readTree(autor.call("GET", "/api/presupuestos/" + p + "/votacion", null, false).body());
            assertEquals("A_FAVOR", parcial.get("miVoto").asText()); assertEquals(2, parcial.get("totalVotos").asInt());
            assertFalse(parcial.get("admiteVotos").asBoolean()); assertFalse(parcial.has("votantes"));
            assertEquals(403, admin.call("POST", "/api/votaciones/" + v + "/cierre", null, true).statusCode());
            vencerVotacion(v);
            var cierre = admin.call("POST", "/api/votaciones/" + v + "/cierre", null, true);
            assertEquals(200, cierre.statusCode(), cierre.body());
            var resultado = json.readTree(cierre.body());
            assertEquals("CERRADA", resultado.get("estado").asText()); assertEquals("APROBADO", resultado.get("estadoPresupuesto").asText());
            assertEquals(1, resultado.get("aFavor").asInt()); assertEquals(1, resultado.get("abstenciones").asInt());
            assertEquals("ASIGNADA", estadoSql(incidenciaId));
            assertEquals(403, admin.call("POST", "/api/votaciones/" + v + "/cierre", null, true).statusCode());
            assertEquals(403, votar(vecino, v, "A_FAVOR"));
        }
    }

    @Test void votacionEmpateAusenciaYAbstencionesRechazan() throws Exception {
        try (var autor = session("autor"); var vecino = session("vecino"); var admin = session("admin"); var tecnico = session("tecnico")) {
            int incidenciaId = trabajoAsignado(autor, admin);
            for (String caso : List.of("empate", "sin votos", "solo abstenciones")) {
                int p = presupuesto(incidenciaId, tecnico), v = abrirPresupuesto(p, admin);
                if (caso.equals("empate")) { assertEquals(204, votar(autor, v, "A_FAVOR")); assertEquals(204, votar(vecino, v, "EN_CONTRA")); }
                if (caso.equals("solo abstenciones")) assertEquals(204, votar(autor, v, "ABSTENCION"));
                vencerVotacion(v);
                var r = admin.call("POST", "/api/votaciones/" + v + "/cierre", null, true);
                assertEquals(200, r.statusCode(), r.body()); assertEquals("RECHAZADO", json.readTree(r.body()).get("estadoPresupuesto").asText());
            }
        }
    }

    @Test void presupuestosPermisosPlazosValidacionYCsrf() throws Exception {
        try (var autor = session("autor"); var admin = session("admin"); var tecnico = session("tecnico"); var ajeno = session("ajeno"); var pendiente = session("pendiente")) {
            int incidenciaId = trabajoAsignado(autor, admin); String base = "/api/incidencias/" + incidenciaId + "/presupuestos";
            assertEquals(403, admin.call("POST", base, presupuestoBody(), true).statusCode());
            assertEquals(403, autor.call("POST", base, presupuestoBody(), true).statusCode());
            assertEquals(403, tecnico.call("POST", base, presupuestoBody(), false).statusCode());
            assertEquals(400, tecnico.call("POST", base, presupuestoBody().replace("123.45", "-1"), true).statusCode());
            assertEquals(400, tecnico.call("POST", base, presupuestoBody().replace("123.45", "1.234"), true).statusCode());
            int p = presupuesto(incidenciaId, tecnico); String apertura = "/api/presupuestos/" + p + "/votacion";
            assertEquals(403, tecnico.call("POST", apertura, abrirBody(), true).statusCode());
            assertEquals(403, admin.call("POST", apertura, abrirBody(), false).statusCode());
            assertEquals(400, admin.call("POST", apertura, "{\"fechaFin\":\"2000-01-01T00:00:00\"}", true).statusCode());
            int v = abrirPresupuesto(p, admin); String votarPath = "/api/votaciones/" + v + "/votos";
            assertEquals(403, admin.call("POST", apertura, abrirBody(), true).statusCode());
            assertEquals(403, votar(tecnico, v, "A_FAVOR")); assertEquals(403, votar(admin, v, "A_FAVOR"));
            assertEquals(403, votar(ajeno, v, "A_FAVOR")); assertEquals(403, votar(pendiente, v, "A_FAVOR"));
            assertEquals(403, autor.call("POST", votarPath, "{\"opcion\":\"A_FAVOR\"}", false).statusCode());
            assertEquals(400, autor.call("POST", votarPath, "{}", true).statusCode());
            assertEquals(403, ajeno.call("GET", apertura, null, false).statusCode());
            db.update("UPDATE usuarios SET estado='INACTIVO' WHERE id_usuario=?", users.get("autor"));
            assertEquals(403, votar(autor, v, "A_FAVOR"));
            db.update("UPDATE usuarios SET estado='ACTIVO' WHERE id_usuario=?", users.get("autor"));
            db.update("UPDATE votaciones SET fecha_inicio=?,fecha_fin=? WHERE id_votacion=?", java.time.LocalDateTime.now().plusDays(1), java.time.LocalDateTime.now().plusDays(2), v);
            assertEquals(403, votar(autor, v, "A_FAVOR"));
            vencerVotacion(v); assertEquals(403, votar(autor, v, "A_FAVOR"));
            assertEquals(403, autor.call("POST", "/api/votaciones/" + v + "/cierre", null, true).statusCode());
            assertEquals(403, admin.call("POST", "/api/votaciones/" + v + "/cierre", null, false).statusCode());
            db.update("UPDATE usuarios_comunidades SET estado='INACTIVO' WHERE id_usuario=? AND id_comunidad=?", users.get("admin"), community);
            assertEquals(403, admin.call("POST", "/api/votaciones/" + v + "/cierre", null, true).statusCode());
        }
    }

    @Test void presupuestoNoAsignadoNiTerminadoYAccesoTecnicoAjeno() throws Exception {
        try (var autor = session("autor"); var admin = session("admin"); var tecnico = session("tecnico")) {
            int id = crear(autor, null);
            assertEquals(403, tecnico.call("POST", "/api/incidencias/" + id + "/presupuestos", presupuestoBody(), true).statusCode());
            id = trabajoAsignado(autor, admin);
            db.update("UPDATE usuarios SET id_rol=(SELECT id_rol FROM roles WHERE nombre='TECNICO') WHERE id_usuario=?", users.get("vecino"));
            try (var otro = session("vecino")) {
                assertEquals(403, otro.call("POST", "/api/incidencias/" + id + "/presupuestos", presupuestoBody(), true).statusCode());
                assertEquals(403, otro.call("GET", "/api/incidencias/" + id + "/presupuestos", null, false).statusCode());
            }
            int p = presupuesto(id, tecnico);
            terminarReparacion(id, tecnico);
            assertEquals(403, tecnico.call("POST", "/api/incidencias/" + id + "/presupuestos", presupuestoBody(), true).statusCode());
            assertEquals(403, admin.call("POST", "/api/presupuestos/" + p + "/votacion", abrirBody(), true).statusCode());
        }
    }

    @Test void votoYCierreConcurrentesNoDuplican() throws Exception {
        try (var autor = session("autor"); var autor2 = session("autor"); var admin = session("admin"); var admin2 = session("admin"); var tecnico = session("tecnico");
             var pool = java.util.concurrent.Executors.newFixedThreadPool(2)) {
            int i = trabajoAsignado(autor, admin), p = presupuesto(i, tecnico), v = abrirPresupuesto(p, admin);
            var inicio = new java.util.concurrent.CountDownLatch(1);
            var a = pool.submit(() -> { inicio.await(); return votar(autor, v, "A_FAVOR"); });
            var b = pool.submit(() -> { inicio.await(); return votar(autor2, v, "EN_CONTRA"); });
            inicio.countDown(); var codigos = new ArrayList<>(List.of(a.get(30, java.util.concurrent.TimeUnit.SECONDS), b.get(30, java.util.concurrent.TimeUnit.SECONDS)));
            Collections.sort(codigos); assertEquals(List.of(204, 403), codigos);
            assertEquals(1, db.queryForObject("SELECT COUNT(*) FROM votos WHERE id_votacion=?", Integer.class, v));
            vencerVotacion(v);
            var cierre = new java.util.concurrent.CountDownLatch(1); String path = "/api/votaciones/" + v + "/cierre";
            var c = pool.submit(() -> { cierre.await(); return admin.call("POST", path, null, true).statusCode(); });
            var d = pool.submit(() -> { cierre.await(); return admin2.call("POST", path, null, true).statusCode(); });
            cierre.countDown(); codigos = new ArrayList<>(List.of(c.get(30, java.util.concurrent.TimeUnit.SECONDS), d.get(30, java.util.concurrent.TimeUnit.SECONDS)));
            Collections.sort(codigos); assertEquals(List.of(200, 403), codigos);
            assertEquals(1, db.queryForObject("SELECT COUNT(*) FROM historial_incidencias WHERE id_incidencia=? AND accion='VOTACION_CERRADA'", Integer.class, i));
        }
    }

    @Test void falloHistorialReviertePresupuestoAperturaYCierre() throws Exception {
        try (var autor = session("autor"); var admin = session("admin"); var tecnico = session("tecnico")) {
            int i = trabajoAsignado(autor, admin);
            org.mockito.Mockito.doThrow(new IllegalStateException("Fallo provocado de historial"))
                .when(historial).save(org.mockito.ArgumentMatchers.any(com.gestioncomunidad.backend.entity.HistorialIncidencia.class));
            assertEquals(500, tecnico.call("POST", "/api/incidencias/" + i + "/presupuestos", presupuestoBody(), true).statusCode());
            assertEquals(0, db.queryForObject("SELECT COUNT(*) FROM presupuestos WHERE id_incidencia=?", Integer.class, i));
            org.mockito.Mockito.reset(historial); int p = presupuesto(i, tecnico);
            org.mockito.Mockito.doThrow(new IllegalStateException("Fallo provocado de historial"))
                .when(historial).save(org.mockito.ArgumentMatchers.any(com.gestioncomunidad.backend.entity.HistorialIncidencia.class));
            assertEquals(500, admin.call("POST", "/api/presupuestos/" + p + "/votacion", abrirBody(), true).statusCode());
            assertEquals("PENDIENTE", db.queryForObject("SELECT estado FROM presupuestos WHERE id_presupuesto=?", String.class, p));
            assertEquals(0, db.queryForObject("SELECT COUNT(*) FROM votaciones WHERE id_presupuesto=?", Integer.class, p));
            org.mockito.Mockito.reset(historial); int v = abrirPresupuesto(p, admin);
            assertEquals(204, votar(autor, v, "A_FAVOR")); vencerVotacion(v);
            org.mockito.Mockito.doThrow(new IllegalStateException("Fallo provocado de historial"))
                .when(historial).save(org.mockito.ArgumentMatchers.any(com.gestioncomunidad.backend.entity.HistorialIncidencia.class));
            assertEquals(500, admin.call("POST", "/api/votaciones/" + v + "/cierre", null, true).statusCode());
            assertEquals("ABIERTA", db.queryForObject("SELECT estado FROM votaciones WHERE id_votacion=?", String.class, v));
            assertEquals("EN_VOTACION", db.queryForObject("SELECT estado FROM presupuestos WHERE id_presupuesto=?", String.class, p));
            assertEquals(1, db.queryForObject("SELECT COUNT(*) FROM votos WHERE id_votacion=?", Integer.class, v));
        }
    }

    String solicitudBody() { return "{\"comunidadId\":" + community + ",\"motivo\":\"Soy residente de esta comunidad\"}"; }
    int solicitar(Sesion s) throws Exception {
        var r = s.call("POST", "/api/pertenencias/solicitudes", solicitudBody(), true);
        assertEquals(200, r.statusCode(), r.body()); return json.readTree(r.body()).get("id").asInt();
    }
    String estadoPertenencia(int id) { return db.queryForObject("SELECT estado FROM usuarios_comunidades WHERE id_usuario_comunidad=?", String.class, id); }
    int eventosPertenencia(int id) { return db.queryForObject("SELECT COUNT(*) FROM historial_pertenencias WHERE id_usuario_comunidad=?", Integer.class, id); }

    @Test void solicitudAprobadaConcedeAccesoSinCambiarCuentaNiRol() throws Exception {
        try (var solicitante = session("ajeno"); var autor = session("autor"); var admin = session("admin")) {
            int incidenciaId = crear(autor, null);
            assertEquals(200, solicitante.call("GET", "/api/pertenencias/comunidades", null, false).statusCode());
            assertEquals("[]", solicitante.call("GET", "/api/pertenencias/mias", null, false).body());
            int id = solicitar(solicitante);
            assertEquals("PENDIENTE", estadoPertenencia(id)); assertEquals(1, eventosPertenencia(id));
            assertEquals(id, solicitar(solicitante)); assertEquals(1, eventosPertenencia(id));
            assertEquals(403, solicitante.call("GET", "/api/incidencias/" + incidenciaId, null, false).statusCode());
            assertEquals("[]", solicitante.call("GET", "/api/espacios", null, false).body());
            assertEquals(1, json.readTree(solicitante.call("GET", "/api/pertenencias/mias", null, false).body()).size());
            var pendientes = json.readTree(admin.call("GET", "/api/pertenencias/pendientes", null, false).body());
            boolean encontrado = false; for (var p : pendientes) if (p.get("id").asInt() == id) encontrado = true;
            assertTrue(encontrado);
            assertEquals(204, admin.call("POST", "/api/pertenencias/" + id + "/resolucion", "{\"aprobar\":true}", true).statusCode());
            assertEquals("ACTIVO", estadoPertenencia(id)); assertEquals(2, eventosPertenencia(id));
            assertEquals(200, solicitante.call("GET", "/api/incidencias/" + incidenciaId, null, false).statusCode());
            var eventos = json.readTree(solicitante.call("GET", "/api/pertenencias/" + id + "/historial", null, false).body());
            assertEquals("APROBACION", eventos.get(1).get("accion").asText());
            assertEquals(users.get("admin").intValue(), eventos.get(1).get("actorId").asInt());
            assertEquals("USUARIO", db.queryForObject("SELECT r.nombre FROM roles r JOIN usuarios u ON u.id_rol=r.id_rol WHERE u.id_usuario=?", String.class, users.get("ajeno")));
            assertEquals(403, solicitante.call("POST", "/api/pertenencias/solicitudes", solicitudBody(), true).statusCode());
            assertEquals(403, admin.call("POST", "/api/pertenencias/" + id + "/resolucion", "{\"aprobar\":false,\"motivo\":\"Repetida\"}", true).statusCode());
        }
    }

    @Test void rechazoAltaYRenovacionConservanDecisiones() throws Exception {
        try (var s = session("ajeno"); var admin = session("admin")) {
            int id = solicitar(s); String path = "/api/pertenencias/" + id;
            assertEquals(400, admin.call("POST", path + "/resolucion", "{\"aprobar\":false}", true).statusCode());
            assertEquals(204, admin.call("POST", path + "/resolucion", "{\"aprobar\":false,\"motivo\":\"  Falta acreditar residencia  \"}", true).statusCode());
            assertEquals("INACTIVO", estadoPertenencia(id));
            var h = json.readTree(s.call("GET", path + "/historial", null, false).body());
            assertEquals("Falta acreditar residencia", h.get(1).get("motivo").asText());
            assertEquals("[]", s.call("GET", "/api/espacios", null, false).body());
            assertEquals(id, solicitar(s)); assertEquals("PENDIENTE", estadoPertenencia(id));
            assertEquals(3, eventosPertenencia(id));
            assertEquals(1, db.queryForObject("SELECT COUNT(*) FROM usuarios_comunidades WHERE id_usuario=? AND id_comunidad=?", Integer.class, users.get("ajeno"), community));
            assertEquals(204, admin.call("POST", path + "/resolucion", "{\"aprobar\":true}", true).statusCode());
            assertEquals(4, eventosPertenencia(id));
        }
    }

    @Test void permisosAltasValidacionYCuentasInactivas() throws Exception {
        try (var s = session("ajeno"); var autor = session("autor"); var admin = session("admin"); var tecnico = session("tecnico")) {
            assertEquals(403, s.call("POST", "/api/pertenencias/solicitudes", solicitudBody(), false).statusCode());
            assertEquals(400, s.call("POST", "/api/pertenencias/solicitudes", "{}", true).statusCode());
            assertEquals(404, s.call("POST", "/api/pertenencias/solicitudes", "{\"comunidadId\":2147483647,\"motivo\":\"Prueba\"}", true).statusCode());
            assertEquals(403, admin.call("POST", "/api/pertenencias/solicitudes", solicitudBody(), true).statusCode());
            assertEquals(403, tecnico.call("POST", "/api/pertenencias/solicitudes", solicitudBody(), true).statusCode());
            assertEquals(403, s.call("GET", "/api/pertenencias/pendientes", null, false).statusCode());
            int id = solicitar(s); String path = "/api/pertenencias/" + id;
            assertEquals(403, autor.call("GET", path + "/historial", null, false).statusCode());
            assertEquals(403, s.call("POST", path + "/resolucion", "{\"aprobar\":true}", true).statusCode());
            assertEquals(403, tecnico.call("POST", path + "/resolucion", "{\"aprobar\":true}", true).statusCode());
            assertEquals(403, admin.call("POST", path + "/resolucion", "{\"aprobar\":true}", false).statusCode());
            db.update("UPDATE usuarios SET estado='INACTIVO' WHERE id_usuario=?", users.get("ajeno"));
            assertEquals(403, s.call("GET", "/api/pertenencias/mias", null, false).statusCode());
            assertEquals(403, admin.call("POST", path + "/resolucion", "{\"aprobar\":true}", true).statusCode());
            db.update("UPDATE usuarios SET estado='ACTIVO' WHERE id_usuario=?", users.get("ajeno"));
            db.update("UPDATE usuarios_comunidades SET estado='INACTIVO' WHERE id_usuario=? AND id_comunidad=?", users.get("admin"), community);
            assertEquals("[]", admin.call("GET", "/api/pertenencias/pendientes", null, false).body());
            assertEquals(403, admin.call("GET", path + "/historial", null, false).statusCode());
            assertEquals(403, admin.call("POST", path + "/resolucion", "{\"aprobar\":true}", true).statusCode());
            assertEquals("PENDIENTE", estadoPertenencia(id)); assertEquals(1, eventosPertenencia(id));
            try (var anon = new Sesion()) { assertEquals(302, anon.call("GET", "/api/pertenencias/mias", null, false).statusCode()); }
        }
    }

    @Test void altasConcurrentesYDecisionesNoSeDuplican() throws Exception {
        try (var s = session("ajeno"); var otra = session("ajeno"); var admin = session("admin"); var admin2 = session("admin");
             var pool = java.util.concurrent.Executors.newFixedThreadPool(2)) {
            var inicio = new java.util.concurrent.CountDownLatch(1);
            var a = pool.submit(() -> { inicio.await(); return solicitar(s); });
            var b = pool.submit(() -> { inicio.await(); return solicitar(otra); });
            inicio.countDown(); int id = a.get(30, java.util.concurrent.TimeUnit.SECONDS);
            assertEquals(id, b.get(30, java.util.concurrent.TimeUnit.SECONDS)); assertEquals(1, eventosPertenencia(id));
            var decision = new java.util.concurrent.CountDownLatch(1);
            String path = "/api/pertenencias/" + id + "/resolucion";
            var c = pool.submit(() -> { decision.await(); return admin.call("POST", path, "{\"aprobar\":true}", true).statusCode(); });
            var d = pool.submit(() -> { decision.await(); return admin2.call("POST", path, "{\"aprobar\":false,\"motivo\":\"No acreditado\"}", true).statusCode(); });
            decision.countDown();
            var codigos = new ArrayList<>(List.of(c.get(30, java.util.concurrent.TimeUnit.SECONDS), d.get(30, java.util.concurrent.TimeUnit.SECONDS)));
            Collections.sort(codigos); assertEquals(List.of(204, 403), codigos); assertEquals(2, eventosPertenencia(id));
        }
    }

    @Test void falloHistorialAltaRevierteSolicitudYPermiso() throws Exception {
        try (var s = session("ajeno"); var admin = session("admin")) {
            org.mockito.Mockito.doThrow(new IllegalStateException("Fallo provocado del historial de altas"))
                .when(historialAltas).save(org.mockito.ArgumentMatchers.any(com.gestioncomunidad.backend.entity.HistorialPertenencia.class));
            assertEquals(500, s.call("POST", "/api/pertenencias/solicitudes", solicitudBody(), true).statusCode());
            assertEquals(0, db.queryForObject("SELECT COUNT(*) FROM usuarios_comunidades WHERE id_usuario=? AND id_comunidad=?", Integer.class, users.get("ajeno"), community));
            org.mockito.Mockito.reset(historialAltas);
            int id = solicitar(s);
            org.mockito.Mockito.doThrow(new IllegalStateException("Fallo provocado del historial de altas"))
                .when(historialAltas).save(org.mockito.ArgumentMatchers.any(com.gestioncomunidad.backend.entity.HistorialPertenencia.class));
            assertEquals(500, admin.call("POST", "/api/pertenencias/" + id + "/resolucion", "{\"aprobar\":true}", true).statusCode());
            assertEquals("PENDIENTE", estadoPertenencia(id)); assertEquals(1, eventosPertenencia(id));
        }
    }

    byte[] foto(String formato) throws Exception {
        var imagen = new java.awt.image.BufferedImage(3, 3, java.awt.image.BufferedImage.TYPE_INT_RGB);
        var salida = new java.io.ByteArrayOutputStream();
        assertTrue(javax.imageio.ImageIO.write(imagen, formato, salida)); return salida.toByteArray();
    }
    HttpResponse<String> subirFoto(Sesion s, int id, byte[] bytes, String tipo, String descripcion, boolean csrf) throws Exception {
        String boundary = "foto-" + UUID.randomUUID();
        var out = new java.io.ByteArrayOutputStream();
        String campos = "--" + boundary + "\r\nContent-Disposition: form-data; name=\"tipo\"\r\n\r\n" + tipo
            + "\r\n--" + boundary + "\r\nContent-Disposition: form-data; name=\"descripcion\"\r\n\r\n" + descripcion
            + "\r\n--" + boundary + "\r\nContent-Disposition: form-data; name=\"archivo\"; filename=\"foto.png\"\r\nContent-Type: image/png\r\n\r\n";
        out.write(campos.getBytes(java.nio.charset.StandardCharsets.UTF_8)); out.write(bytes);
        out.write(("\r\n--" + boundary + "--\r\n").getBytes(java.nio.charset.StandardCharsets.UTF_8));
        var b = HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/api/incidencias/" + id + "/evidencias"))
                .timeout(Duration.ofSeconds(20)).header("Content-Type", "multipart/form-data; boundary=" + boundary);
        if (csrf) b.header(s.csrf.get("headerName").asText(), s.csrf.get("token").asText());
        return s.client.send(b.POST(HttpRequest.BodyPublishers.ofByteArray(out.toByteArray())).build(), HttpResponse.BodyHandlers.ofString());
    }
    long archivosPrueba() throws Exception {
        try (var stream = java.nio.file.Files.list(fotosPrueba)) { return stream.count(); }
    }

    @Test void fotografiasSeGuardanYConsultanConPermisos() throws Exception {
        try (var autor = session("autor"); var vecino = session("vecino"); var admin = session("admin"); var ajeno = session("ajeno"); var tecnico = session("tecnico")) {
            int id = crear(autor, null); byte[] bytes = foto("png");
            var subida = subirFoto(autor, id, bytes, "ANTES", "Foto inicial", true);
            assertEquals(201, subida.statusCode(), subida.body());
            var e = json.readTree(subida.body()); String url = e.get("urlArchivo").asText();
            assertEquals("image/png", e.get("tipoMime").asText()); assertFalse(e.get("fechaSubida").isNull());
            assertEquals(2, eventos(id)); assertEquals(1, archivosPrueba());
            var row = db.queryForMap("SELECT * FROM evidencias WHERE id_evidencia=?", e.get("id").asInt());
            assertEquals(users.get("autor"), row.get("id_usuario_subida"));
            assertEquals(e.get("historialId").asLong(), ((Number) row.get("id_historial")).longValue());
            for (Sesion s : List.of(autor, vecino, admin)) {
                assertEquals(1, json.readTree(s.call("GET", "/api/incidencias/" + id + "/evidencias", null, false).body()).size());
                var r = s.client.send(HttpRequest.newBuilder(URI.create("http://localhost:" + port + url)).GET().build(), HttpResponse.BodyHandlers.ofByteArray());
                assertEquals(200, r.statusCode()); assertArrayEquals(bytes, r.body());
                assertEquals("no-store", r.headers().firstValue("cache-control").orElse(""));
            }
            assertEquals(403, ajeno.call("GET", url, null, false).statusCode());
            assertEquals(403, tecnico.call("GET", url, null, false).statusCode());
            int otra = crear(autor, null);
            assertEquals(404, autor.call("GET", "/api/incidencias/" + otra + "/evidencias/" + e.get("id").asInt() + "/archivo", null, false).statusCode());
            try (var anon = new Sesion()) { assertEquals(302, anon.call("GET", url, null, false).statusCode()); }
            assertEquals(403, subirFoto(vecino, id, bytes, "ANTES", "Ajena", true).statusCode());
            // La lectura vuelve a comprobar la pertenencia aunque el usuario conserve su cookie.
            db.update("UPDATE usuarios_comunidades SET estado='INACTIVO' WHERE id_usuario=? AND id_comunidad=?", users.get("vecino"), community);
            assertEquals(403, vecino.call("GET", url, null, false).statusCode());
        }
    }

    @Test void evidenciasTecnicasRespetanFaseYContenidoReal() throws Exception {
        try (var autor = session("autor"); var admin = session("admin"); var tecnico = session("tecnico")) {
            int id = trabajoAsignado(autor, admin); byte[] jpeg = foto("jpeg");
            assertEquals(403, subirFoto(tecnico, id, jpeg, "DURANTE", "Aun no iniciado", true).statusCode());
            assertEquals(204, tecnico.call("PUT", "/api/tecnico/trabajos/" + id + "/inicio", null, true).statusCode());
            assertEquals(403, subirFoto(autor, id, jpeg, "ANTES", "Tarde", true).statusCode());
            assertEquals(403, subirFoto(tecnico, id, jpeg, "ANTES", "Tipo incorrecto", true).statusCode());
            for (String tipo : List.of("DURANTE", "DESPUES")) {
                var r = subirFoto(tecnico, id, jpeg, tipo, "Trabajo", true);
                assertEquals(201, r.statusCode(), r.body());
                // Aunque el multipart anuncia PNG, se reconoce el JPEG real y se sirve con su MIME correcto.
                assertEquals("image/jpeg", json.readTree(r.body()).get("tipoMime").asText());
            }
            assertEquals(2, json.readTree(tecnico.call("GET", "/api/incidencias/" + id + "/evidencias", null, false).body()).size());
            assertEquals(204, tecnico.call("POST", "/api/tecnico/trabajos/" + id + "/finalizacion", "{\"descripcion\":\"Terminado\"}", true).statusCode());
            assertEquals(403, subirFoto(tecnico, id, jpeg, "DESPUES", "Fuera de fase", true).statusCode());
        }
    }

    @Test void imagenInvalidaLimitesYCsrfNoCreanDatos() throws Exception {
        try (var autor = session("autor")) {
            int id = crear(autor, null);
            assertEquals(403, subirFoto(autor, id, foto("png"), "ANTES", "Foto", false).statusCode());
            assertEquals(400, subirFoto(autor, id, new byte[0], "ANTES", "Vacia", true).statusCode());
            assertEquals(400, subirFoto(autor, id, "<script>alert(1)</script>".getBytes(), "ANTES", "Falsa", true).statusCode());
            assertEquals(400, subirFoto(autor, id, foto("gif"), "ANTES", "Gif", true).statusCode());
            assertEquals(400, subirFoto(autor, id, foto("png"), "INVALIDO", "Tipo", true).statusCode());
            assertEquals(400, subirFoto(autor, id, foto("png"), "ANTES", "x".repeat(256), true).statusCode());
            assertEquals(413, subirFoto(autor, id, new byte[5 * 1024 * 1024 + 1], "ANTES", "Grande", true).statusCode());
            assertEquals(1, eventos(id)); assertEquals(0, archivosPrueba());
            assertEquals(0, db.queryForObject("SELECT COUNT(*) FROM evidencias WHERE id_incidencia=?", Integer.class, id));
        }
    }

    @Test void falloSqlRetiraArchivoDeEvidencia() throws Exception {
        try (var autor = session("autor")) {
            int id = crear(autor, null);
            org.mockito.Mockito.doThrow(new IllegalStateException("Fallo provocado de historial"))
                .when(historial).save(org.mockito.ArgumentMatchers.any(com.gestioncomunidad.backend.entity.HistorialIncidencia.class));
            assertEquals(500, subirFoto(autor, id, foto("png"), "ANTES", "Rollback", true).statusCode());
            assertEquals(0, archivosPrueba()); assertEquals(1, eventos(id));
            assertEquals(0, db.queryForObject("SELECT COUNT(*) FROM evidencias WHERE id_incidencia=?", Integer.class, id));
        }
    }

    String duplicadaBody(int original) { return "{\"incidenciaOriginalId\":" + original + ",\"motivo\":\"Mismo problema\"}"; }

    @Test void rechazoYDuplicadaConservanHistorialYReferencia() throws Exception {
        try (var autor = session("autor"); var admin = session("admin")) {
            int original = crear(autor, null), copia = crear(autor, null), rechazo = crear(autor, null);
            assertEquals(204, admin.call("POST", "/api/incidencias/" + rechazo + "/rechazo", "{\"motivo\":\"  No corresponde  \"}", true).statusCode());
            assertEquals("RECHAZADA", estadoSql(rechazo)); assertEquals(2, eventos(rechazo));
            var rechazado = db.queryForMap("SELECT * FROM incidencias WHERE id_incidencia=?", rechazo);
            assertNull(rechazado.get("id_incidencia_origen")); assertNull(rechazado.get("fecha_cierre"));
            assertEquals("No corresponde", db.queryForObject("SELECT descripcion FROM historial_incidencias WHERE id_incidencia=? AND accion='RECHAZO'", String.class, rechazo));
            assertEquals(204, admin.call("POST", "/api/incidencias/" + copia + "/duplicada", duplicadaBody(original), true).statusCode());
            assertEquals("RECHAZADA", estadoSql(copia)); assertEquals("PENDIENTE", estadoSql(original));
            assertEquals(2, eventos(copia)); assertEquals(1, eventos(original));
            assertEquals(original, json.readTree(autor.call("GET", "/api/incidencias/" + copia, null, false).body()).get("incidenciaOriginalId").asInt());
            var e = db.queryForMap("SELECT * FROM historial_incidencias WHERE id_incidencia=? AND accion='DUPLICADA'", copia);
            assertEquals(users.get("admin"), e.get("id_usuario_actor"));
            assertEquals("PENDIENTE", e.get("estado_anterior")); assertEquals("RECHAZADA", e.get("estado_nuevo"));
            assertTrue(e.get("descripcion").toString().contains("Incidencia original: " + original));
            assertEquals(403, admin.call("POST", "/api/incidencias/" + copia + "/duplicada", duplicadaBody(original), true).statusCode());
            assertEquals(400, admin.call("POST", "/api/incidencias/" + original + "/rechazo", "{\"motivo\":\"Intento\"}", true).statusCode());
        }
    }

    @Test void rechazoExigePermisosEstadoYCsrf() throws Exception {
        try (var autor = session("autor"); var admin = session("admin")) {
            int id = crear(autor, null), original = crear(autor, null); String path = "/api/incidencias/" + id;
            String motivo = "{\"motivo\":\"Prueba\"}";
            for (String name : List.of("autor", "vecino", "tecnico", "ajeno")) try (var s = session(name)) {
                assertEquals(403, s.call("POST", path + "/rechazo", motivo, true).statusCode());
                assertEquals(403, s.call("POST", path + "/duplicada", duplicadaBody(original), true).statusCode());
            }
            assertEquals(403, admin.call("POST", path + "/rechazo", motivo, false).statusCode());
            assertEquals(403, admin.call("POST", path + "/duplicada", duplicadaBody(original), false).statusCode());
            assertEquals(400, admin.call("POST", path + "/rechazo", "{\"motivo\":\" \"}", true).statusCode());
            assertEquals(400, admin.call("POST", path + "/duplicada", "{}", true).statusCode());
            for (String estado : List.of("ASIGNADA", "EN_PROCESO", "PENDIENTE_CONFIRMACION", "PENDIENTE_CIERRE", "FINALIZADA", "RECHAZADA")) {
                db.update("UPDATE incidencias SET estado=? WHERE id_incidencia=?", estado, id);
                assertEquals(403, admin.call("POST", path + "/rechazo", motivo, true).statusCode());
                assertEquals(403, admin.call("POST", path + "/duplicada", duplicadaBody(original), true).statusCode());
            }
            db.update("UPDATE incidencias SET estado='PENDIENTE' WHERE id_incidencia=?", id);
            db.update("UPDATE usuarios SET estado='INACTIVO' WHERE id_usuario=?", users.get("admin"));
            assertEquals(403, admin.call("POST", path + "/rechazo", motivo, true).statusCode());
            db.update("UPDATE usuarios SET estado='ACTIVO' WHERE id_usuario=?", users.get("admin"));
            db.update("UPDATE usuarios_comunidades SET estado='INACTIVO' WHERE id_usuario=? AND id_comunidad=?", users.get("admin"), community);
            assertEquals(403, admin.call("POST", path + "/rechazo", motivo, true).statusCode());
            assertEquals(403, admin.call("POST", path + "/duplicada", duplicadaBody(original), true).statusCode());
            assertEquals(1, eventos(id)); assertEquals("PENDIENTE", estadoSql(id));
        }
    }

    @Test void duplicadasRechazanCadenasCiclosYOriginalInvalida() throws Exception {
        try (var autor = session("autor"); var admin = session("admin")) {
            int a = crear(autor, null), b = crear(autor, null), c = crear(autor, null);
            assertEquals(400, admin.call("POST", "/api/incidencias/" + a + "/duplicada", duplicadaBody(a), true).statusCode());
            assertEquals(404, admin.call("POST", "/api/incidencias/" + a + "/duplicada", duplicadaBody(Integer.MAX_VALUE), true).statusCode());
            assertEquals(204, admin.call("POST", "/api/incidencias/" + a + "/duplicada", duplicadaBody(b), true).statusCode());
            assertEquals(400, admin.call("POST", "/api/incidencias/" + c + "/duplicada", duplicadaBody(a), true).statusCode());
            assertEquals(400, admin.call("POST", "/api/incidencias/" + b + "/duplicada", duplicadaBody(c), true).statusCode());
            // Una comunidad ajena se prepara y elimina solo para esta validación cruzada.
            String nombre = "ajena-" + UUID.randomUUID();
            db.update("INSERT INTO comunidades(nombre,direccion,ciudad) VALUES (?, 'Prueba','Madrid')", nombre);
            int ajena = db.queryForObject("SELECT id_comunidad FROM comunidades WHERE nombre=?", Integer.class, nombre);
            db.update("INSERT INTO espacios(nombre,tipo,id_comunidad) VALUES ('Ajeno','PORTAL',?)", ajena);
            int espacioAjeno = db.queryForObject("SELECT id_espacio FROM espacios WHERE id_comunidad=?", Integer.class, ajena);
            try {
                db.update("UPDATE incidencias SET id_espacio=? WHERE id_incidencia=?", espacioAjeno, b);
                assertEquals(400, admin.call("POST", "/api/incidencias/" + c + "/duplicada", duplicadaBody(b), true).statusCode());
            } finally {
                db.update("UPDATE incidencias SET id_espacio=? WHERE id_incidencia=?", space, b);
                db.update("DELETE FROM espacios WHERE id_espacio=?", espacioAjeno);
                db.update("DELETE FROM comunidades WHERE id_comunidad=?", ajena);
            }
            assertEquals(1, eventos(c));
        }
    }

    @Test void falloHistorialRevierteRechazoYDuplicada() throws Exception {
        try (var autor = session("autor"); var admin = session("admin")) {
            int id = crear(autor, null), original = crear(autor, null);
            org.mockito.Mockito.doThrow(new IllegalStateException("Fallo provocado de historial"))
                .when(historial).save(org.mockito.ArgumentMatchers.any(com.gestioncomunidad.backend.entity.HistorialIncidencia.class));
            for (String accion : List.of("rechazo", "duplicada")) {
                String body = accion.equals("rechazo") ? "{\"motivo\":\"Prueba\"}" : duplicadaBody(original);
                assertEquals(500, admin.call("POST", "/api/incidencias/" + id + "/" + accion, body, true).statusCode());
                assertEquals("PENDIENTE", estadoSql(id)); assertEquals(1, eventos(id));
                assertNull(db.queryForMap("SELECT id_incidencia_origen FROM incidencias WHERE id_incidencia=?", id).get("id_incidencia_origen"));
            }
        }
    }

    @Test void duplicadasCruzadasConcurrentesNoCreanCiclo() throws Exception {
        try (var autor = session("autor"); var admin = session("admin"); var otra = session("admin");
             var pool = java.util.concurrent.Executors.newFixedThreadPool(2)) {
            int a = crear(autor, null), b = crear(autor, null);
            var inicio = new java.util.concurrent.CountDownLatch(1);
            var uno = pool.submit(() -> { inicio.await(); return admin.call("POST", "/api/incidencias/" + a + "/duplicada", duplicadaBody(b), true).statusCode(); });
            var dos = pool.submit(() -> { inicio.await(); return otra.call("POST", "/api/incidencias/" + b + "/duplicada", duplicadaBody(a), true).statusCode(); });
            inicio.countDown();
            var codigos = new ArrayList<>(List.of(uno.get(30, java.util.concurrent.TimeUnit.SECONDS), dos.get(30, java.util.concurrent.TimeUnit.SECONDS)));
            Collections.sort(codigos); assertEquals(List.of(204, 400), codigos);
            assertEquals(1, db.queryForObject("SELECT COUNT(*) FROM incidencias WHERE id_espacio=? AND id_incidencia_origen IS NOT NULL", Integer.class, space));
            assertEquals(3, eventos(a) + eventos(b));
        }
    }

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
