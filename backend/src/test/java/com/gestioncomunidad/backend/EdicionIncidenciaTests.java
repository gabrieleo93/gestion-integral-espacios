package com.gestioncomunidad.backend;

import com.gestioncomunidad.backend.dto.EditarIncidenciaRequest;
import com.gestioncomunidad.backend.entity.*;
import com.gestioncomunidad.backend.exception.OperacionNoPermitidaException;
import com.gestioncomunidad.backend.exception.RecursoNoEncontradoException;
import com.gestioncomunidad.backend.repositories.*;
import com.gestioncomunidad.backend.service.IncidenciaService;
import jakarta.persistence.EntityManager;
import jakarta.validation.Validation;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;
import java.util.Optional;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

// Los repositorios simulados permiten probar cada permiso sin alterar MySQL.
class EdicionIncidenciaTests {
    private final UsuarioRepository usuarios = mock(UsuarioRepository.class);
    private final UsuarioComunidadRepository pertenencias = mock(UsuarioComunidadRepository.class);
    private final IncidenciaRepository incidencias = mock(IncidenciaRepository.class);
    private final HistorialIncidenciaRepository historial = mock(HistorialIncidenciaRepository.class);
    private final IncidenciaService servicio = new IncidenciaService(usuarios, mock(EspacioRepository.class),
            pertenencias, incidencias, historial, mock(EntityManager.class));
    private final EditarIncidenciaRequest cambios = new EditarIncidenciaRequest(" Nuevo título ",
            " Nueva descripción ", PrioridadIncidencia.ALTA);
    private Usuario actor;
    private Incidencia aviso;

    @BeforeEach
    void preparar() {
        actor = new Usuario();
        ReflectionTestUtils.setField(actor, "id", 1);
        actor.setEstado(EstadoUsuario.ACTIVO);
        Rol rol = new Rol();
        rol.setNombre("USUARIO");
        actor.setRol(rol);
        Comunidad comunidad = new Comunidad();
        ReflectionTestUtils.setField(comunidad, "id", 10);
        Espacio espacio = new Espacio();
        espacio.setComunidad(comunidad);
        aviso = new Incidencia();
        aviso.setEspacio(espacio);
        aviso.setUsuarioReporta(actor);
        aviso.setEstado(EstadoIncidencia.PENDIENTE);
        aviso.setTitulo("Título anterior");
        aviso.setDescripcion("Descripción anterior");
        aviso.setPrioridad(PrioridadIncidencia.MEDIA);
        when(usuarios.findById(1)).thenReturn(Optional.of(actor));
        when(incidencias.findByIdParaActualizar(30)).thenReturn(Optional.of(aviso));
        when(pertenencias.existsByUsuario_IdAndComunidad_IdAndEstado(1, 10, EstadoUsuario.ACTIVO))
                .thenReturn(true);
    }

    @Test
    void autorEditaYRegistraAntesYDespuesSinCambiarEstado() {
        servicio.editarDatos(30, 1, cambios);
        assertEquals("Nuevo título", aviso.getTitulo());
        assertEquals("Nueva descripción", aviso.getDescripcion());
        assertEquals(PrioridadIncidencia.ALTA, aviso.getPrioridad());
        assertEquals(EstadoIncidencia.PENDIENTE, aviso.getEstado());
        ArgumentCaptor<HistorialIncidencia> evento = ArgumentCaptor.forClass(HistorialIncidencia.class);
        verify(historial).save(evento.capture());
        assertEquals("EDICION_DATOS", evento.getValue().getAccion());
        assertSame(actor, evento.getValue().getUsuarioActor());
        assertTrue(evento.getValue().getDescripcion().contains("Título anterior"));
        assertTrue(evento.getValue().getDescripcion().contains("Nuevo título"));
        assertEquals(evento.getValue().getEstadoAnterior(), evento.getValue().getEstadoNuevo());
    }

    private void otroAutor() {
        Usuario autor = new Usuario();
        ReflectionTestUtils.setField(autor, "id", 2);
        aviso.setUsuarioReporta(autor);
    }

    @Test
    void administradorDeLaComunidadEditaAvisoAjeno() {
        otroAutor();
        actor.getRol().setNombre("ADMINISTRADOR");
        servicio.editarDatos(30, 1, cambios);
        verify(incidencias).save(aviso);
        verify(historial).save(any());
    }

    @Test
    void vecinoVerificadorNoPuedeEditarSiNoEsAutor() {
        otroAutor();
        aviso.setVecinoVerificador(actor);
        assertThrows(OperacionNoPermitidaException.class, () -> servicio.editarDatos(30, 1, cambios));
        assertEquals("Título anterior", aviso.getTitulo());
        verify(incidencias, never()).save(any());
        verifyNoInteractions(historial);
    }

    @Test
    void administradorSinPertenenciaActivaNoPuedeEditar() {
        actor.getRol().setNombre("ADMINISTRADOR");
        when(pertenencias.existsByUsuario_IdAndComunidad_IdAndEstado(1, 10, EstadoUsuario.ACTIVO))
                .thenReturn(false);
        assertThrows(OperacionNoPermitidaException.class, () -> servicio.editarDatos(30, 1, cambios));
        verify(incidencias, never()).save(any());
        verifyNoInteractions(historial);
    }

    @Test
    void autorSinPertenenciaActivaNoPuedeEditar() {
        when(pertenencias.existsByUsuario_IdAndComunidad_IdAndEstado(1, 10, EstadoUsuario.ACTIVO))
                .thenReturn(false);
        assertThrows(OperacionNoPermitidaException.class, () -> servicio.editarDatos(30, 1, cambios));
        verifyNoInteractions(historial);
    }

    @Test
    void tecnicoAsignadoNoEditaDatosGenerales() {
        actor.getRol().setNombre("TECNICO");
        aviso.setTecnicoAsignado(actor);
        assertThrows(OperacionNoPermitidaException.class, () -> servicio.editarDatos(30, 1, cambios));
        verifyNoInteractions(incidencias, historial);
    }

    @Test
    void cuentaInactivaNoEdita() {
        actor.setEstado(EstadoUsuario.INACTIVO);
        assertThrows(OperacionNoPermitidaException.class, () -> servicio.editarDatos(30, 1, cambios));
        verifyNoInteractions(incidencias, historial);
    }

    @Test
    void incidenciaInexistenteProduceErrorDeRecurso() {
        when(incidencias.findByIdParaActualizar(30)).thenReturn(Optional.empty());
        assertThrows(RecursoNoEncontradoException.class, () -> servicio.editarDatos(30, 1, cambios));
        verifyNoInteractions(historial);
    }

    @Test
    void repetirEdicionNoDuplicaEventos() {
        servicio.editarDatos(30, 1, cambios);
        servicio.editarDatos(30, 1, cambios);
        verify(historial, times(1)).save(any());
        verify(incidencias, times(1)).save(any());
    }

    @Test
    void dtoRechazaCamposVaciosAusentesYDemasiadoLargos() {
        // El controlador usa esta validación antes de ejecutar una edición.
        try (var factory = Validation.buildDefaultValidatorFactory()) {
            var validator = factory.getValidator();
            assertEquals(3, validator.validate(new EditarIncidenciaRequest(" ", "", null)).size());
            assertEquals(2, validator.validate(new EditarIncidenciaRequest(
                    "x".repeat(151), "x".repeat(5001), PrioridadIncidencia.MEDIA)).size());
            assertTrue(validator.validate(cambios).isEmpty());
        }
    }
}
