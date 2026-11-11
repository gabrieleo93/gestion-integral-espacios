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
public class TrabajoTecnicoService {
    private final UsuarioRepository usuarios;
    private final UsuarioComunidadRepository pertenencias;
    private final IncidenciaRepository incidencias;
    private final HistorialIncidenciaRepository historial;

    public TrabajoTecnicoService(UsuarioRepository usuarios, UsuarioComunidadRepository pertenencias,
            IncidenciaRepository incidencias, HistorialIncidenciaRepository historial) {
        this.usuarios = usuarios;
        this.pertenencias = pertenencias;
        this.incidencias = incidencias;
        this.historial = historial;
    }

    private Usuario tecnico(Integer actorId) {
        Usuario actor = usuarios.findById(actorId).orElseThrow(() ->
                new OperacionNoPermitidaException("La cuenta de la sesión ya no está disponible"));
        if (actor.getEstado() != EstadoUsuario.ACTIVO || !"TECNICO".equals(actor.getRol().getNombre()))
            throw new OperacionNoPermitidaException("Esta operación requiere un técnico activo");
        return actor;
    }

    private Incidencia trabajo(Integer id, Usuario actor, boolean escritura) {
        // Todas las escrituras comparten bloqueo con edición y asignación para serializar estados.
        Incidencia i = (escritura ? incidencias.findByIdParaActualizar(id) : incidencias.findById(id))
                .orElseThrow(() -> new RecursoNoEncontradoException("Incidencia no encontrada"));
        if (i.getTecnicoAsignado() == null || !actor.getId().equals(i.getTecnicoAsignado().getId())
                || !pertenencias.existsByUsuario_IdAndComunidad_IdAndEstado(actor.getId(),
                        i.getEspacio().getComunidad().getId(), EstadoUsuario.ACTIVO))
            throw new OperacionNoPermitidaException("No tienes permiso para acceder a este trabajo");
        return i;
    }

    private TrabajoTecnicoResponse respuesta(Incidencia i) {
        return new TrabajoTecnicoResponse(i.getId(), i.getTitulo(), i.getDescripcion(), i.getPrioridad(),
                i.getEstado(), i.getEspacio().getId(), i.getEspacio().getNombre(),
                i.getEspacio().getComunidad().getId(), i.getEspacio().getComunidad().getNombre(),
                i.getFechaAsignacion(), i.getFechaPrevista(), i.getFechaInicio(), i.getFechaReparacion());
    }

    @Transactional(readOnly = true)
    public List<TrabajoTecnicoResponse> listar(Integer actorId) {
        Usuario actor = tecnico(actorId);
        var comunidades = pertenencias.findByUsuario_IdAndEstado(actor.getId(), EstadoUsuario.ACTIVO)
                .stream().map(p -> p.getComunidad().getId()).distinct().toList();
        if (comunidades.isEmpty()) return List.of();
        return incidencias.findByTecnicoAsignado_IdAndEspacio_Comunidad_IdInOrderByFechaCreacionDescIdDesc(
                actor.getId(), comunidades).stream().map(this::respuesta).toList();
    }

    @Transactional(readOnly = true)
    public TrabajoTecnicoResponse detalle(Integer id, Integer actorId) {
        return respuesta(trabajo(id, tecnico(actorId), false));
    }

    @Transactional(readOnly = true)
    public List<HistorialIncidenciaResponse> historial(Integer id, Integer actorId) {
        Incidencia i = trabajo(id, tecnico(actorId), false);
        return historial.findByIncidencia_IdOrderByFechaEventoAscIdAsc(i.getId()).stream()
                .map(e -> new HistorialIncidenciaResponse(e.getId(), e.getAccion(), e.getEstadoAnterior(),
                        e.getEstadoNuevo(), e.getDescripcion(), e.getFechaEvento(), e.getUsuarioActor().getId(),
                        e.getUsuarioActor().getNombre() + " " + e.getUsuarioActor().getApellidos())).toList();
    }

    @Transactional
    public void iniciar(Integer id, Integer actorId) {
        Usuario actor = tecnico(actorId);
        Incidencia i = trabajo(id, actor, true);
        // Un reintento no altera la fecha de inicio ni duplica el evento.
        if (i.getEstado() == EstadoIncidencia.EN_PROCESO) return;
        exigirEstado(i, EstadoIncidencia.ASIGNADA);
        i.setFechaInicio(LocalDateTime.now().truncatedTo(ChronoUnit.SECONDS));
        evento(i, actor, "INICIO_REPARACION", "El técnico inicia la reparación.", EstadoIncidencia.EN_PROCESO);
    }

    @Transactional
    public void avanzar(Integer id, Integer actorId, ActuacionTecnicaRequest request) {
        Usuario actor = tecnico(actorId);
        Incidencia i = trabajo(id, actor, true);
        exigirEstado(i, EstadoIncidencia.EN_PROCESO);
        // Un avance es un evento nuevo; no cambia el estado ni los datos generales del aviso.
        evento(i, actor, "AVANCE_REPARACION", descripcion(request), EstadoIncidencia.EN_PROCESO);
    }

    @Transactional
    public void finalizar(Integer id, Integer actorId, ActuacionTecnicaRequest request) {
        Usuario actor = tecnico(actorId);
        Incidencia i = trabajo(id, actor, true);
        exigirEstado(i, EstadoIncidencia.EN_PROCESO);
        i.setFechaReparacion(LocalDateTime.now().truncatedTo(ChronoUnit.SECONDS));
        // Terminar el trabajo técnico no cierra la incidencia: queda para confirmación del vecino.
        evento(i, actor, "FIN_REPARACION", descripcion(request), EstadoIncidencia.PENDIENTE_CONFIRMACION);
    }

    private String descripcion(ActuacionTecnicaRequest request) {
        String texto = request.descripcion().strip();
        if (texto.isBlank()) throw new DatosNoValidosException("La descripción de la actuación es obligatoria");
        return texto;
    }

    private void exigirEstado(Incidencia i, EstadoIncidencia requerido) {
        if (i.getEstado() != requerido)
            throw new OperacionNoPermitidaException("Esta operación requiere el estado " + requerido.name());
    }

    private void evento(Incidencia i, Usuario actor, String accion, String descripcion, EstadoIncidencia nuevo) {
        HistorialIncidencia e = new HistorialIncidencia();
        e.setIncidencia(i);
        e.setUsuarioActor(actor);
        e.setAccion(accion);
        e.setEstadoAnterior(i.getEstado().name());
        e.setEstadoNuevo(nuevo.name());
        e.setDescripcion(descripcion);
        i.setEstado(nuevo);
        // El método público transaccional confirma estado, fechas e historial como una unidad.
        incidencias.save(i);
        historial.save(e);
    }
}
