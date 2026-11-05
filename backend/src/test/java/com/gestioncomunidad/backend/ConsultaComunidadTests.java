package com.gestioncomunidad.backend;

import com.gestioncomunidad.backend.entity.*;
import com.gestioncomunidad.backend.exception.OperacionNoPermitidaException;
import com.gestioncomunidad.backend.repositories.*;
import com.gestioncomunidad.backend.service.IncidenciaService;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

// Pruebas de permisos del servicio sin depender de credenciales ni modificar la BD local.
class ConsultaComunidadTests {
    private final UsuarioRepository usuarios = mock(UsuarioRepository.class);
    private final UsuarioComunidadRepository pertenencias = mock(UsuarioComunidadRepository.class);
    private final IncidenciaRepository incidencias = mock(IncidenciaRepository.class);
    private final HistorialIncidenciaRepository historial = mock(HistorialIncidenciaRepository.class);
    private final IncidenciaService servicio = new IncidenciaService(
            usuarios, mock(EspacioRepository.class), pertenencias, incidencias,
            historial, mock(EntityManager.class));
    private Usuario lector;
    private Comunidad comunidad;
    private Incidencia aviso;

    @BeforeEach
    void preparar() {
        Rol rol = new Rol();
        rol.setNombre("USUARIO");
        lector = new Usuario();
        ReflectionTestUtils.setField(lector, "id", 1);
        lector.setEstado(EstadoUsuario.ACTIVO);
        lector.setRol(rol);

        // El autor tiene otro ID: esta es la diferencia frente al antiguo permiso por autoría.
        Usuario autor = new Usuario();
        ReflectionTestUtils.setField(autor, "id", 2);
        autor.setNombre("Otro");
        autor.setApellidos("Vecino");
        comunidad = new Comunidad();
        ReflectionTestUtils.setField(comunidad, "id", 10);
        comunidad.setNombre("Comunidad compartida");
        Espacio espacio = new Espacio();
        ReflectionTestUtils.setField(espacio, "id", 20);
        espacio.setNombre("Portal");
        espacio.setComunidad(comunidad);
        aviso = new Incidencia();
        ReflectionTestUtils.setField(aviso, "id", 30);
        aviso.setTitulo("Aviso de otro vecino");
        aviso.setEspacio(espacio);
        aviso.setUsuarioReporta(autor);
        aviso.setVecinoVerificador(autor);
        when(usuarios.findById(1)).thenReturn(Optional.of(lector));
        when(incidencias.findById(30)).thenReturn(Optional.of(aviso));
    }

    @Test
    void vecinoActivoConsultaDetalleEHistorialDeOtroAutor() {
        when(pertenencias.existsByUsuario_IdAndComunidad_IdAndEstado(1, 10, EstadoUsuario.ACTIVO))
                .thenReturn(true);
        HistorialIncidencia evento = new HistorialIncidencia();
        evento.setUsuarioActor(aviso.getUsuarioReporta());
        evento.setAccion("CREACION");
        when(historial.findByIncidencia_IdOrderByFechaEventoAscIdAsc(30)).thenReturn(List.of(evento));
        assertEquals(2, servicio.consultarDetalle(30, 1).usuarioReportaId());
        assertEquals("CREACION", servicio.consultarHistorial(30, 1).getFirst().accion());
    }

    @Test
    void listadoIncluyeAvisosAjenosDeTodasLasComunidadesActivas() {
        UsuarioComunidad primera = new UsuarioComunidad();
        primera.setComunidad(comunidad);
        Comunidad otra = new Comunidad();
        ReflectionTestUtils.setField(otra, "id", 11);
        UsuarioComunidad segunda = new UsuarioComunidad();
        segunda.setComunidad(otra);
        when(pertenencias.findByUsuario_IdAndEstado(1, EstadoUsuario.ACTIVO))
                .thenReturn(List.of(primera, segunda));
        when(incidencias.findByEspacio_Comunidad_IdInOrderByFechaCreacionDescIdDesc(Set.of(10, 11)))
                .thenReturn(List.of(aviso));
        assertEquals(30, servicio.listarMisIncidencias(1).getFirst().id());
        verify(incidencias).findByEspacio_Comunidad_IdInOrderByFechaCreacionDescIdDesc(Set.of(10, 11));
    }

    @Test
    void sinPertenenciaActivaNoHayListadoNiAccesoDirecto() {
        // Ausencia de pertenencia ACTIVA cubre también las pertenencias pendientes o inactivas.
        when(pertenencias.findByUsuario_IdAndEstado(1, EstadoUsuario.ACTIVO)).thenReturn(List.of());
        assertTrue(servicio.listarMisIncidencias(1).isEmpty());
        verify(incidencias, never()).findByEspacio_Comunidad_IdInOrderByFechaCreacionDescIdDesc(any());
        assertThrows(OperacionNoPermitidaException.class, () -> servicio.consultarDetalle(30, 1));
        assertThrows(OperacionNoPermitidaException.class, () -> servicio.consultarHistorial(30, 1));
        verifyNoInteractions(historial);
    }

    @Test
    void cuentaInactivaNoPuedeConsultar() {
        lector.setEstado(EstadoUsuario.INACTIVO);
        assertThrows(OperacionNoPermitidaException.class, () -> servicio.listarMisIncidencias(1));
        assertThrows(OperacionNoPermitidaException.class, () -> servicio.consultarDetalle(30, 1));
        assertThrows(OperacionNoPermitidaException.class, () -> servicio.consultarHistorial(30, 1));
        verifyNoInteractions(incidencias, historial);
    }

    @Test
    void administradorConsultaDetalleEHistorialDeSuComunidad() {
        // La administración necesita leer los casos que crea o gestiona.
        lector.getRol().setNombre("ADMINISTRADOR");
        vecinoActivoConsultaDetalleEHistorialDeOtroAutor();
    }

    @Test
    void administradorListaSusComunidadesActivas() {
        lector.getRol().setNombre("ADMINISTRADOR");
        listadoIncluyeAvisosAjenosDeTodasLasComunidadesActivas();
    }

    @Test
    void administradorSinPertenenciaNoTieneAccesoGlobal() {
        lector.getRol().setNombre("ADMINISTRADOR");
        sinPertenenciaActivaNoHayListadoNiAccesoDirecto();
    }

    @Test
    void tecnicoNoObtieneAccesoGeneralALaComunidad() {
        lector.getRol().setNombre("TECNICO");
        assertThrows(OperacionNoPermitidaException.class, () -> servicio.listarMisIncidencias(1));
        assertThrows(OperacionNoPermitidaException.class, () -> servicio.consultarDetalle(30, 1));
        assertThrows(OperacionNoPermitidaException.class, () -> servicio.consultarHistorial(30, 1));
        verifyNoInteractions(incidencias, historial);
    }
}
