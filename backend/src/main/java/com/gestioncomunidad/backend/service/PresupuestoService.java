package com.gestioncomunidad.backend.service;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import jakarta.persistence.EntityManager;
import com.gestioncomunidad.backend.dto.*;
import com.gestioncomunidad.backend.entity.*;
import com.gestioncomunidad.backend.exception.*;
import com.gestioncomunidad.backend.repositories.*;

@Service
public class PresupuestoService {
    private final UsuarioRepository usuarios;
    private final UsuarioComunidadRepository pertenencias;
    private final IncidenciaRepository incidencias;
    private final PresupuestoRepository presupuestos;
    private final VotacionRepository votaciones;
    private final VotoRepository votos;
    private final HistorialIncidenciaRepository historial;
    private final EntityManager em;
    public PresupuestoService(UsuarioRepository usuarios, UsuarioComunidadRepository pertenencias,
            IncidenciaRepository incidencias, PresupuestoRepository presupuestos, VotacionRepository votaciones,
            VotoRepository votos, HistorialIncidenciaRepository historial, EntityManager em) {
        this.usuarios = usuarios; this.pertenencias = pertenencias; this.incidencias = incidencias;
        this.presupuestos = presupuestos; this.votaciones = votaciones; this.votos = votos;
        this.historial = historial; this.em = em;
    }
    private Usuario actor(Integer id) {
        Usuario u = usuarios.findById(id).orElseThrow(() -> new OperacionNoPermitidaException("La cuenta no está disponible"));
        if (u.getEstado() != EstadoUsuario.ACTIVO) throw new OperacionNoPermitidaException("La cuenta debe estar activa");
        return u;
    }
    private void rol(Usuario u, String rol) {
        if (!rol.equals(u.getRol().getNombre())) throw new OperacionNoPermitidaException("Tu rol no permite esta operación");
    }
    private void acceso(Incidencia i, Usuario u) {
        String rol = u.getRol().getNombre();
        boolean tecnico = "TECNICO".equals(rol) && i.getTecnicoAsignado() != null
                && u.getId().equals(i.getTecnicoAsignado().getId());
        if ((!"USUARIO".equals(rol) && !"ADMINISTRADOR".equals(rol) && !tecnico)
                || !pertenencias.existsByUsuario_IdAndComunidad_IdAndEstado(u.getId(), i.getEspacio().getComunidad().getId(), EstadoUsuario.ACTIVO))
            throw new OperacionNoPermitidaException("No tienes acceso a los presupuestos de esta incidencia");
    }
    private Incidencia incidencia(Integer id, boolean escritura) {
        return (escritura ? incidencias.findByIdParaActualizar(id) : incidencias.findById(id))
                .orElseThrow(() -> new RecursoNoEncontradoException("Incidencia no encontrada"));
    }
    private void admitePropuestas(Incidencia i) {
        if (i.getEstado() != EstadoIncidencia.ASIGNADA && i.getEstado() != EstadoIncidencia.EN_PROCESO)
            throw new OperacionNoPermitidaException("El presupuesto requiere una incidencia asignada o en proceso");
    }
    private PresupuestoResponse respuesta(Presupuesto p) {
        return new PresupuestoResponse(p.getId(), p.getIncidencia().getId(), p.getTitulo(), p.getDescripcion(),
                p.getProveedor(), p.getImporte(), p.getEstado(), p.getFechaCreacion());
    }
    private LocalDateTime ahora() { return LocalDateTime.now().truncatedTo(ChronoUnit.SECONDS); }

    @Transactional(readOnly = true)
    public List<PresupuestoResponse> listar(Integer incidenciaId, Integer actorId) {
        Usuario u = actor(actorId); Incidencia i = incidencia(incidenciaId, false); acceso(i, u);
        return presupuestos.findByIncidencia_IdOrderByFechaCreacionDescIdDesc(i.getId()).stream().map(this::respuesta).toList();
    }
    @Transactional
    public PresupuestoResponse crear(Integer incidenciaId, Integer actorId, CrearPresupuestoRequest request) {
        Usuario u = actor(actorId); rol(u, "TECNICO");
        Incidencia i = incidencia(incidenciaId, true); acceso(i, u); admitePropuestas(i);
        // Solo el técnico asignado estima el coste. El administrador decidirá cuándo someterlo a votación.
        Presupuesto p = new Presupuesto(); p.setIncidencia(i);
        p.setTitulo(request.titulo().strip()); p.setProveedor(request.proveedor().strip());
        if (p.getTitulo().isBlank() || p.getProveedor().isBlank()) throw new DatosNoValidosException("Título y proveedor son obligatorios");
        p.setDescripcion(request.descripcion() == null ? null : request.descripcion().strip());
        p.setImporte(request.importe()); p.setEstado(EstadoPresupuesto.PENDIENTE);
        presupuestos.saveAndFlush(p); em.refresh(p);
        evento(i, u, "PRESUPUESTO_CREADO", "Presupuesto " + p.getId() + ": " + p.getTitulo()
                + ". Proveedor: " + p.getProveedor() + ". Importe: " + p.getImporte());
        return respuesta(p);
    }
    @Transactional
    public VotacionResponse abrir(Integer presupuestoId, Integer actorId, AbrirVotacionRequest request) {
        Usuario u = actor(actorId); rol(u, "ADMINISTRADOR");
        Integer incidenciaId = presupuestos.findIncidenciaId(presupuestoId)
                .orElseThrow(() -> new RecursoNoEncontradoException("Presupuesto no encontrado"));
        // Todas las escrituras bloquean primero incidencia y después presupuesto/votación: mismo orden.
        Incidencia i = incidencia(incidenciaId, true); acceso(i, u); admitePropuestas(i);
        Presupuesto p = presupuestos.findByIdParaActualizar(presupuestoId).orElseThrow();
        if (p.getEstado() != EstadoPresupuesto.PENDIENTE || votaciones.findByPresupuesto_Id(presupuestoId).isPresent())
            throw new OperacionNoPermitidaException("Este presupuesto ya tiene una votación o está resuelto");
        LocalDateTime inicio = ahora(), fin = request.fechaFin().truncatedTo(ChronoUnit.SECONDS);
        if (!fin.isAfter(inicio)) throw new DatosNoValidosException("La fecha final debe ser posterior a la apertura");
        Votacion v = new Votacion(); v.setPresupuesto(p); v.setTitulo(p.getTitulo()); v.setDescripcion(p.getDescripcion());
        v.setFechaInicio(inicio); v.setFechaFin(fin); v.setEstado(EstadoVotacion.ABIERTA);
        p.setEstado(EstadoPresupuesto.EN_VOTACION); presupuestos.save(p); votaciones.saveAndFlush(v);
        evento(i, u, "VOTACION_ABIERTA", "Votación " + v.getId() + ", presupuesto " + p.getId() + ". Plazo: " + inicio + " a " + fin);
        return respuesta(v, u, List.of());
    }
    @Transactional(readOnly = true)
    public VotacionResponse consultar(Integer presupuestoId, Integer actorId) {
        Usuario u = actor(actorId);
        Presupuesto p = presupuestos.findById(presupuestoId).orElseThrow(() -> new RecursoNoEncontradoException("Presupuesto no encontrado"));
        acceso(p.getIncidencia(), u);
        Votacion v = votaciones.findByPresupuesto_Id(presupuestoId).orElseThrow(() -> new RecursoNoEncontradoException("El presupuesto todavía no tiene votación"));
        return respuesta(v, u, votos.findByVotacion_Id(v.getId()));
    }
    private Votacion bloquearVotacion(Integer id, Usuario u) {
        Integer incidenciaId = votaciones.findIncidenciaId(id).orElseThrow(() -> new RecursoNoEncontradoException("Votación no encontrada"));
        Incidencia i = incidencia(incidenciaId, true); acceso(i, u);
        return votaciones.findByIdParaActualizar(id).orElseThrow();
    }
    @Transactional
    public void votar(Integer id, Integer actorId, EmitirVotoRequest request) {
        Usuario u = actor(actorId); rol(u, "USUARIO");
        Votacion v = bloquearVotacion(id, u);
        LocalDateTime instante = ahora();
        // El límite es exclusivo: no se acepta voto a partir de fechaFin aunque aún no se haya cerrado.
        if (v.getEstado() != EstadoVotacion.ABIERTA || instante.isBefore(v.getFechaInicio()) || !instante.isBefore(v.getFechaFin()))
            throw new OperacionNoPermitidaException("La votación no admite votos en este momento");
        var emitidos = votos.findParaActualizar(id);
        if (emitidos.stream().anyMatch(voto -> voto.getUsuario().getId().equals(u.getId())))
            throw new OperacionNoPermitidaException("Ya has votado; no se permite modificar ni repetir el voto");
        Voto voto = new Voto(); voto.setUsuario(u); voto.setVotacion(v); voto.setOpcion(request.opcion());
        votos.saveAndFlush(voto);
        // No publicamos la opción individual en el historial compartido. La tabla de votos conserva la trazabilidad.
    }
    @Transactional
    public VotacionResponse cerrar(Integer id, Integer actorId) {
        Usuario u = actor(actorId); rol(u, "ADMINISTRADOR"); Votacion v = bloquearVotacion(id, u);
        if (v.getEstado() != EstadoVotacion.ABIERTA) throw new OperacionNoPermitidaException("La votación no está abierta");
        if (ahora().isBefore(v.getFechaFin())) throw new OperacionNoPermitidaException("No se puede cerrar antes de terminar el plazo");
        var emitidos = votos.findParaActualizar(id);
        long favor = contar(emitidos, OpcionVoto.A_FAVOR), contra = contar(emitidos, OpcionVoto.EN_CONTRA);
        Presupuesto p = v.getPresupuesto();
        // Mayoría simple sin quórum: abstenciones no inclinan la decisión; empate y cero votos rechazan.
        p.setEstado(favor > contra ? EstadoPresupuesto.APROBADO : EstadoPresupuesto.RECHAZADO);
        v.setEstado(EstadoVotacion.CERRADA); presupuestos.save(p); votaciones.save(v);
        evento(p.getIncidencia(), u, "VOTACION_CERRADA", "Votación " + id + ", presupuesto " + p.getId()
                + ". A favor: " + favor + ", en contra: " + contra + ", abstenciones: " + contar(emitidos, OpcionVoto.ABSTENCION)
                + ". Resultado: " + p.getEstado());
        return respuesta(v, u, emitidos);
    }
    private long contar(List<Voto> votos, OpcionVoto opcion) { return votos.stream().filter(v -> v.getOpcion() == opcion).count(); }
    private VotacionResponse respuesta(Votacion v, Usuario u, List<Voto> emitidos) {
        LocalDateTime instante = ahora();
        OpcionVoto propio = emitidos.stream().filter(voto -> voto.getUsuario().getId().equals(u.getId()))
                .map(Voto::getOpcion).findFirst().orElse(null);
        boolean admite = "USUARIO".equals(u.getRol().getNombre()) && propio == null && v.getEstado() == EstadoVotacion.ABIERTA
                && !instante.isBefore(v.getFechaInicio()) && instante.isBefore(v.getFechaFin());
        return new VotacionResponse(v.getId(), v.getPresupuesto().getId(), v.getTitulo(), v.getFechaInicio(), v.getFechaFin(),
                v.getEstado(), admite, contar(emitidos, OpcionVoto.A_FAVOR), contar(emitidos, OpcionVoto.EN_CONTRA),
                contar(emitidos, OpcionVoto.ABSTENCION), emitidos.size(), propio, v.getPresupuesto().getEstado());
    }
    private void evento(Incidencia i, Usuario u, String accion, String descripcion) {
        HistorialIncidencia e = new HistorialIncidencia(); e.setIncidencia(i); e.setUsuarioActor(u); e.setAccion(accion);
        e.setEstadoAnterior(i.getEstado().name()); e.setEstadoNuevo(i.getEstado().name()); e.setDescripcion(descripcion);
        // Presupuesto, votación y evento comparten transacción; no se altera el estado de la incidencia.
        historial.save(e);
    }
}
