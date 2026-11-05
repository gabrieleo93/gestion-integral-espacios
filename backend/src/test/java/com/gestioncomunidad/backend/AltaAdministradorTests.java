package com.gestioncomunidad.backend;

import com.gestioncomunidad.backend.dto.CrearIncidenciaRequest;
import com.gestioncomunidad.backend.entity.*;
import com.gestioncomunidad.backend.exception.DatosNoValidosException;
import com.gestioncomunidad.backend.exception.OperacionNoPermitidaException;
import com.gestioncomunidad.backend.repositories.*;
import com.gestioncomunidad.backend.service.IncidenciaService;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;
import java.util.Optional;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

// Verifica el alta administrativa y la compatibilidad del alta vecinal sin tocar MySQL.
class AltaAdministradorTests {
    private final UsuarioRepository usuarios = mock(UsuarioRepository.class);
    private final EspacioRepository espacios = mock(EspacioRepository.class);
    private final UsuarioComunidadRepository pertenencias = mock(UsuarioComunidadRepository.class);
    private final IncidenciaRepository incidencias = mock(IncidenciaRepository.class);
    private final HistorialIncidenciaRepository historial = mock(HistorialIncidenciaRepository.class);
    private final IncidenciaService servicio = new IncidenciaService(usuarios, espacios, pertenencias,
            incidencias, historial, mock(EntityManager.class));
    private Usuario administrador;
    private Usuario vecino;
    private final CrearIncidenciaRequest solicitud = new CrearIncidenciaRequest(
            "Luz averiada", "Luz del portal", PrioridadIncidencia.MEDIA, 20, 2);

    private Usuario usuario(int id, String nombreRol) {
        Usuario usuario = new Usuario();
        ReflectionTestUtils.setField(usuario, "id", id);
        usuario.setEstado(EstadoUsuario.ACTIVO);
        Rol rol = new Rol();
        rol.setNombre(nombreRol);
        usuario.setRol(rol);
        return usuario;
    }

    @BeforeEach
    void preparar() {
        administrador = usuario(1, "ADMINISTRADOR");
        vecino = usuario(2, "USUARIO");
        Comunidad comunidad = new Comunidad();
        ReflectionTestUtils.setField(comunidad, "id", 10);
        Espacio espacio = new Espacio();
        ReflectionTestUtils.setField(espacio, "id", 20);
        espacio.setComunidad(comunidad);
        when(usuarios.findById(1)).thenReturn(Optional.of(administrador));
        when(usuarios.findById(2)).thenReturn(Optional.of(vecino));
        when(espacios.findById(20)).thenReturn(Optional.of(espacio));
        when(pertenencias.existsByUsuario_IdAndComunidad_IdAndEstado(1, 10, EstadoUsuario.ACTIVO)).thenReturn(true);
        when(pertenencias.existsByUsuario_IdAndComunidad_IdAndEstado(2, 10, EstadoUsuario.ACTIVO)).thenReturn(true);
    }

    @Test
    void administradorCreaConVecinoVerificadorYFiguraComoActor() {
        var respuesta = servicio.crear(solicitud, 1);
        assertEquals(1, respuesta.usuarioReportaId());
        assertEquals(2, respuesta.vecinoVerificadorId());
        assertEquals(EstadoIncidencia.PENDIENTE, respuesta.estado());
        assertNull(respuesta.tecnicoAsignadoId());
        ArgumentCaptor<Incidencia> caso = ArgumentCaptor.forClass(Incidencia.class);
        verify(incidencias).saveAndFlush(caso.capture());
        assertSame(administrador, caso.getValue().getUsuarioReporta());
        assertSame(vecino, caso.getValue().getVecinoVerificador());
        ArgumentCaptor<HistorialIncidencia> evento = ArgumentCaptor.forClass(HistorialIncidencia.class);
        verify(historial).save(evento.capture());
        assertSame(administrador, evento.getValue().getUsuarioActor());
        assertSame(vecino, evento.getValue().getVerificadorNuevo());
        assertEquals("CREACION", evento.getValue().getAccion());
    }

    @Test
    void vecinoConservaElAltaAnteriorSinNuevoCampo() {
        var respuesta = servicio.crear(new CrearIncidenciaRequest(
                "Aviso", "Descripción", PrioridadIncidencia.MEDIA, 20), 2);
        assertEquals(2, respuesta.usuarioReportaId());
        assertEquals(2, respuesta.vecinoVerificadorId());
    }

    @Test
    void vecinoNoPuedeDesignarOtroVerificador() {
        assertThrows(DatosNoValidosException.class, () -> servicio.crear(new CrearIncidenciaRequest(
                "Aviso", "Descripción", PrioridadIncidencia.MEDIA, 20, 1), 2));
        verifyNoInteractions(incidencias, historial);
    }

    @Test
    void administradorDebeIndicarVerificador() {
        assertThrows(DatosNoValidosException.class, () -> servicio.crear(new CrearIncidenciaRequest(
                "Aviso", "Descripción", PrioridadIncidencia.MEDIA, 20), 1));
        verifyNoInteractions(incidencias, historial);
    }

    @Test
    void administradorSinPertenenciaNoCrea() {
        when(pertenencias.existsByUsuario_IdAndComunidad_IdAndEstado(1, 10, EstadoUsuario.ACTIVO)).thenReturn(false);
        assertThrows(OperacionNoPermitidaException.class, () -> servicio.crear(solicitud, 1));
        verifyNoInteractions(incidencias, historial);
    }

    @Test
    void administradorInactivoNoCrea() {
        administrador.setEstado(EstadoUsuario.INACTIVO);
        assertThrows(OperacionNoPermitidaException.class, () -> servicio.crear(solicitud, 1));
        verifyNoInteractions(incidencias, historial);
    }

    @Test
    void tecnicoNoCrea() {
        administrador.getRol().setNombre("TECNICO");
        assertThrows(OperacionNoPermitidaException.class, () -> servicio.crear(solicitud, 1));
        verifyNoInteractions(incidencias, historial);
    }

    @Test
    void verificadorInexistenteNoPermiteGuardar() {
        when(usuarios.findById(2)).thenReturn(Optional.empty());
        assertThrows(DatosNoValidosException.class, () -> servicio.crear(solicitud, 1));
        verifyNoInteractions(incidencias, historial);
    }

    @Test
    void verificadorInactivoNoPermiteGuardar() {
        vecino.setEstado(EstadoUsuario.INACTIVO);
        assertThrows(DatosNoValidosException.class, () -> servicio.crear(solicitud, 1));
        verifyNoInteractions(incidencias, historial);
    }

    @Test
    void verificadorDebeSerVecino() {
        vecino.getRol().setNombre("TECNICO");
        assertThrows(DatosNoValidosException.class, () -> servicio.crear(solicitud, 1));
        verifyNoInteractions(incidencias, historial);
    }

    @Test
    void verificadorSinPertenenciaActivaNoPermiteGuardar() {
        when(pertenencias.existsByUsuario_IdAndComunidad_IdAndEstado(2, 10, EstadoUsuario.ACTIVO)).thenReturn(false);
        assertThrows(DatosNoValidosException.class, () -> servicio.crear(solicitud, 1));
        verifyNoInteractions(incidencias, historial);
    }
}
