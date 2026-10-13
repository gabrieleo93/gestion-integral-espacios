package com.gestioncomunidad.backend;

import com.gestioncomunidad.backend.dto.CrearIncidenciaRequest;
import com.gestioncomunidad.backend.dto.IncidenciaResponse;
import com.gestioncomunidad.backend.entity.*;
import com.gestioncomunidad.backend.repositories.*;
import com.gestioncomunidad.backend.service.IncidenciaService;

import jakarta.persistence.EntityManager;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@Transactional
class IncidenciaServiceTests {

    @Autowired
    private IncidenciaService incidenciaService;

    @Autowired
    private ComunidadRepository comunidadRepository;

    @Autowired
    private EspacioRepository espacioRepository;

    @Autowired
    private UsuarioRepository usuarioRepository;

    @Autowired
    private RolRepository rolRepository;

    @Autowired
    private UsuarioComunidadRepository usuarioComunidadRepository;

    @Autowired
    private IncidenciaRepository incidenciaRepository;

    @Autowired
    private HistorialIncidenciaRepository historialRepository;

    @Autowired
    private EntityManager entityManager;

    @Test
    void vecinoActivoCreaIncidenciaConHistorial() {
        // Preparar una comunidad de prueba.
        Comunidad comunidad = new Comunidad();
        comunidad.setNombre("Comunidad de prueba");
        comunidad.setDireccion("Calle de prueba 1");
        comunidad.setCiudad("Madrid");
        comunidadRepository.save(comunidad);

        // Preparar un espacio con el tipo obligatorio.
        Espacio espacio = new Espacio();
        espacio.setNombre("Portal de prueba");
        espacio.setTipo("PORTAL");
        espacio.setComunidad(comunidad);
        espacioRepository.save(espacio);

        // Utilizar el rol creado por el script SQL.
        Rol rol = rolRepository.findByNombre("USUARIO")
            .orElseThrow(() ->
                new IllegalStateException("Falta el rol USUARIO en MySQL")
            );

        // Preparar un vecino activo con correo único.
        Usuario vecino = new Usuario();
        vecino.setNombre("Vecino");
        vecino.setApellidos("De prueba");
        vecino.setEmail(UUID.randomUUID() + "@example.test");
        vecino.setPasswordHash("DATO_FICTICIO_SIN_ACCESO");
        vecino.setEstado(EstadoUsuario.ACTIVO);
        vecino.setRol(rol);
        usuarioRepository.save(vecino);

        // Vincular el vecino a la comunidad.
        UsuarioComunidad pertenencia = new UsuarioComunidad();
        pertenencia.setUsuario(vecino);
        pertenencia.setComunidad(comunidad);
        pertenencia.setEstado(EstadoUsuario.ACTIVO);
        usuarioComunidadRepository.save(pertenencia);

        CrearIncidenciaRequest request = new CrearIncidenciaRequest(
            "Luz del portal averiada",
            "La luz no se enciende al pulsar el interruptor.",
            PrioridadIncidencia.MEDIA,
            espacio.getId()
        );

        // Ejecutar la operación real del servicio.
        IncidenciaResponse respuesta =
            incidenciaService.crear(request, vecino.getId());

        assertNotNull(respuesta.id());
        assertNotNull(respuesta.fechaCreacion());
        assertEquals(EstadoIncidencia.PENDIENTE, respuesta.estado());

        // Enviar los cambios a MySQL y vaciar la caché de entidades.
        entityManager.flush();
        entityManager.clear();

        // Recuperar la incidencia desde la base de datos.
        Incidencia guardada = incidenciaRepository
            .findById(respuesta.id())
            .orElseThrow();

        assertEquals("Luz del portal averiada", guardada.getTitulo());
        assertEquals(
            "La luz no se enciende al pulsar el interruptor.",
            guardada.getDescripcion()
        );
        assertEquals(PrioridadIncidencia.MEDIA, guardada.getPrioridad());
        assertEquals(EstadoIncidencia.PENDIENTE, guardada.getEstado());
        assertEquals(vecino.getId(), guardada.getUsuarioReporta().getId());
        assertEquals(vecino.getId(), guardada.getVecinoVerificador().getId());
        assertEquals(espacio.getId(), guardada.getEspacio().getId());
        assertNull(guardada.getTecnicoAsignado());
        assertNotNull(guardada.getFechaCreacion());

        // Comprobar el primer evento del historial.
        var historial = historialRepository
            .findByIncidencia_IdOrderByFechaEventoAscIdAsc(guardada.getId());

        assertEquals(1, historial.size());

        HistorialIncidencia evento = historial.get(0);
        assertEquals("CREACION", evento.getAccion());
        assertNull(evento.getEstadoAnterior());
        assertEquals("PENDIENTE", evento.getEstadoNuevo());
        assertEquals(vecino.getId(), evento.getUsuarioActor().getId());
        assertEquals(guardada.getId(), evento.getIncidencia().getId());
        assertNotNull(evento.getFechaEvento());
    }
}