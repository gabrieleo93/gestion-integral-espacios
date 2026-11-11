package com.gestioncomunidad.backend.service;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.gestioncomunidad.backend.dto.*;
import com.gestioncomunidad.backend.entity.*;
import com.gestioncomunidad.backend.exception.*;
import com.gestioncomunidad.backend.repositories.*;

@Service
public class AsignacionTecnicoService {
    private final UsuarioRepository usuarios;
    private final UsuarioComunidadRepository pertenencias;
    private final IncidenciaRepository incidencias;
    private final HistorialIncidenciaRepository historial;

    public AsignacionTecnicoService(UsuarioRepository usuarios, UsuarioComunidadRepository pertenencias,
            IncidenciaRepository incidencias, HistorialIncidenciaRepository historial) {
        this.usuarios = usuarios;
        this.pertenencias = pertenencias;
        this.incidencias = incidencias;
        this.historial = historial;
    }

    // Este permiso es administrativo; poder consultar una incidencia no permite asignarla.
    private Usuario administrador(Integer actorId) {
        Usuario actor = usuarios.findById(actorId).orElseThrow(() ->
                new OperacionNoPermitidaException("La cuenta de la sesión ya no está disponible"));
        if (actor.getEstado() != EstadoUsuario.ACTIVO || !"ADMINISTRADOR".equals(actor.getRol().getNombre()))
            throw new OperacionNoPermitidaException("La asignación requiere un administrador activo");
        return actor;
    }

    private void comprobarComunidad(Usuario actor, Integer comunidadId) {
        if (!pertenencias.existsByUsuario_IdAndComunidad_IdAndEstado(actor.getId(), comunidadId, EstadoUsuario.ACTIVO))
            throw new OperacionNoPermitidaException("No tienes permiso para gestionar esta comunidad");
    }

    @Transactional(readOnly = true)
    public List<TecnicoResponse> candidatos(Integer incidenciaId, Integer actorId) {
        Usuario actor = administrador(actorId);
        Incidencia incidencia = incidencias.findById(incidenciaId).orElseThrow(() ->
                new RecursoNoEncontradoException("Incidencia no encontrada"));
        Integer comunidadId = incidencia.getEspacio().getComunidad().getId();
        comprobarComunidad(actor, comunidadId);
        return pertenencias.findByComunidad_IdAndEstadoAndUsuario_EstadoAndUsuario_Rol_NombreOrderByUsuario_NombreAscUsuario_IdAsc(
                comunidadId, EstadoUsuario.ACTIVO, EstadoUsuario.ACTIVO, "TECNICO").stream()
                .map(p -> new TecnicoResponse(p.getUsuario().getId(),
                        p.getUsuario().getNombre() + " " + p.getUsuario().getApellidos())).toList();
    }

    @Transactional
    public void asignar(Integer incidenciaId, Integer actorId, AsignarTecnicoRequest request) {
        Usuario actor = administrador(actorId);
        // El mismo bloqueo que la edición evita que dos administradores asignen a la vez.
        Incidencia incidencia = incidencias.findByIdParaActualizar(incidenciaId).orElseThrow(() ->
                new RecursoNoEncontradoException("Incidencia no encontrada"));
        Integer comunidadId = incidencia.getEspacio().getComunidad().getId();
        comprobarComunidad(actor, comunidadId);
        Usuario tecnico = usuarios.findById(request.tecnicoId()).orElseThrow(() ->
                new DatosNoValidosException("El técnico no existe"));
        // Se vuelve a validar al guardar: el selector puede haber quedado desactualizado.
        if (tecnico.getEstado() != EstadoUsuario.ACTIVO || !"TECNICO".equals(tecnico.getRol().getNombre())
                || !pertenencias.existsByUsuario_IdAndComunidad_IdAndEstado(tecnico.getId(), comunidadId, EstadoUsuario.ACTIVO))
            throw new DatosNoValidosException("El técnico debe estar activo y pertenecer activamente a esta comunidad");
        // MySQL guarda estas fechas con precisión de segundos; normalizamos también la comparación.
        LocalDateTime prevista = request.fechaPrevista().truncatedTo(ChronoUnit.SECONDS);
        if (incidencia.getEstado() == EstadoIncidencia.ASIGNADA && incidencia.getTecnicoAsignado() != null
                && tecnico.getId().equals(incidencia.getTecnicoAsignado().getId())
                && prevista.equals(incidencia.getFechaPrevista())) return;
        if (incidencia.getEstado() != EstadoIncidencia.PENDIENTE)
            throw new OperacionNoPermitidaException("Solo se puede asignar inicialmente una incidencia pendiente");
        LocalDateTime ahora = LocalDateTime.now().truncatedTo(ChronoUnit.SECONDS);
        if (prevista.isBefore(ahora)) throw new DatosNoValidosException("La fecha prevista no puede estar en el pasado");

        HistorialIncidencia evento = new HistorialIncidencia();
        evento.setIncidencia(incidencia);
        evento.setUsuarioActor(actor);
        evento.setAccion("ASIGNACION_TECNICO");
        evento.setEstadoAnterior(incidencia.getEstado().name());
        evento.setEstadoNuevo(EstadoIncidencia.ASIGNADA.name());
        evento.setTecnicoAnterior(incidencia.getTecnicoAsignado());
        evento.setTecnicoNuevo(tecnico);
        evento.setDescripcion("Técnico asignado: " + tecnico.getNombre() + " " + tecnico.getApellidos()
                + ". Fecha prevista: " + prevista);
        incidencia.setTecnicoAsignado(tecnico);
        incidencia.setFechaAsignacion(ahora);
        incidencia.setFechaPrevista(prevista);
        incidencia.setEstado(EstadoIncidencia.ASIGNADA);
        // Incidencia e historial se confirman juntos; un fallo revierte ambos cambios.
        incidencias.save(incidencia);
        historial.save(evento);
    }
}
