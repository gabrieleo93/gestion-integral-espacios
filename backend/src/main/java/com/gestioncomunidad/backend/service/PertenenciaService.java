package com.gestioncomunidad.backend.service;

import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.data.domain.Sort;
import jakarta.persistence.EntityManager;
import com.gestioncomunidad.backend.dto.*;
import com.gestioncomunidad.backend.entity.*;
import com.gestioncomunidad.backend.exception.*;
import com.gestioncomunidad.backend.repositories.*;

@Service
public class PertenenciaService {
    private final UsuarioRepository usuarios;
    private final ComunidadRepository comunidades;
    private final UsuarioComunidadRepository pertenencias;
    private final HistorialPertenenciaRepository historial;
    private final EntityManager em;
    public PertenenciaService(UsuarioRepository usuarios, ComunidadRepository comunidades,
            UsuarioComunidadRepository pertenencias, HistorialPertenenciaRepository historial, EntityManager em) {
        this.usuarios = usuarios; this.comunidades = comunidades; this.pertenencias = pertenencias;
        this.historial = historial; this.em = em;
    }
    private void exigir(Usuario u, String rol) {
        if (u.getEstado() != EstadoUsuario.ACTIVO || !rol.equals(u.getRol().getNombre()))
            throw new OperacionNoPermitidaException("Tu cuenta o rol no permite esta operación");
    }
    private Usuario actor(Integer id, String rol) {
        Usuario u = usuarios.findById(id).orElseThrow(() -> new OperacionNoPermitidaException("La cuenta no está disponible"));
        exigir(u, rol); return u;
    }
    private void administrar(Usuario admin, Integer comunidad) {
        if (!pertenencias.existsByUsuario_IdAndComunidad_IdAndEstado(admin.getId(), comunidad, EstadoUsuario.ACTIVO))
            throw new OperacionNoPermitidaException("No puedes gestionar solicitudes de esta comunidad");
    }
    private PertenenciaResponse respuesta(UsuarioComunidad p) {
        return new PertenenciaResponse(p.getId(), p.getUsuario().getId(),
                p.getUsuario().getNombre() + " " + p.getUsuario().getApellidos(), p.getComunidad().getId(),
                p.getComunidad().getNombre(), p.getEstado(), p.getFechaAsignacion());
    }
    @Transactional(readOnly = true)
    public List<ComunidadSolicitudResponse> comunidades(Integer actorId) {
        actor(actorId, "USUARIO");
        // Es solo un catálogo para solicitar acceso; no habilita consultas del contenido de las comunidades.
        return comunidades.findAll(Sort.by("nombre", "id")).stream()
                .map(c -> new ComunidadSolicitudResponse(c.getId(), c.getNombre(), c.getCiudad())).toList();
    }
    @Transactional(readOnly = true)
    public List<PertenenciaResponse> propias(Integer actorId) {
        actor(actorId, "USUARIO");
        return pertenencias.findByUsuario_IdOrderByIdDesc(actorId).stream().map(this::respuesta).toList();
    }
    @Transactional(readOnly = true)
    public List<PertenenciaResponse> pendientes(Integer actorId) {
        actor(actorId, "ADMINISTRADOR");
        var ids = pertenencias.findByUsuario_IdAndEstado(actorId, EstadoUsuario.ACTIVO).stream()
                .map(p -> p.getComunidad().getId()).distinct().toList();
        if (ids.isEmpty()) return List.of();
        return pertenencias.findByComunidad_IdInAndEstadoAndUsuario_Rol_NombreOrderByIdAsc(ids, EstadoUsuario.PENDIENTE, "USUARIO")
                .stream().map(this::respuesta).toList();
    }
    @Transactional(readOnly = true)
    public List<HistorialPertenenciaResponse> historial(Integer id, Integer actorId) {
        Usuario u = usuarios.findById(actorId).orElseThrow(() -> new OperacionNoPermitidaException("La cuenta no está disponible"));
        if (u.getEstado() != EstadoUsuario.ACTIVO) throw new OperacionNoPermitidaException("La cuenta debe estar activa");
        UsuarioComunidad p = pertenencias.findById(id).orElseThrow(() -> new RecursoNoEncontradoException("Pertenencia no encontrada"));
        // El solicitante puede ver su decisión aunque su pertenencia esté pendiente o rechazada.
        if ("ADMINISTRADOR".equals(u.getRol().getNombre())) administrar(u, p.getComunidad().getId());
        else if (!"USUARIO".equals(u.getRol().getNombre()) || !u.getId().equals(p.getUsuario().getId()))
            throw new OperacionNoPermitidaException("No puedes consultar esta solicitud");
        return historial.findByPertenencia_IdOrderByFechaEventoAscIdAsc(id).stream()
                .map(e -> new HistorialPertenenciaResponse(e.getId(), e.getAccion(), e.getEstadoAnterior(),
                        e.getEstadoNuevo(), e.getMotivo(), e.getFechaEvento(), e.getActor().getId(),
                        e.getActor().getNombre() + " " + e.getActor().getApellidos())).toList();
    }
    @Transactional
    public PertenenciaResponse solicitar(Integer actorId, SolicitarPertenenciaRequest request) {
        // Bloqueamos primero la cuenta: dos solicitudes simultáneas no crean dos pertenencias.
        Usuario u = usuarios.findByIdParaActualizar(actorId).orElseThrow(() -> new OperacionNoPermitidaException("La cuenta no está disponible"));
        exigir(u, "USUARIO");
        Comunidad c = comunidades.findById(request.comunidadId()).orElseThrow(() -> new RecursoNoEncontradoException("Comunidad no encontrada"));
        String motivo = request.motivo().strip();
        if (motivo.isBlank()) throw new DatosNoValidosException("El motivo de solicitud es obligatorio");
        UsuarioComunidad p = pertenencias.findParaActualizar(u.getId(), c.getId()).orElse(null);
        if (p != null && p.getEstado() == EstadoUsuario.ACTIVO)
            throw new OperacionNoPermitidaException("Ya tienes pertenencia activa a esta comunidad");
        // Un reintento mientras sigue pendiente devuelve la solicitud existente sin duplicar eventos.
        if (p != null && p.getEstado() == EstadoUsuario.PENDIENTE) return respuesta(p);
        EstadoUsuario anterior = p == null ? null : p.getEstado();
        if (p == null) { p = new UsuarioComunidad(); p.setUsuario(u); p.setComunidad(c); }
        p.setEstado(EstadoUsuario.PENDIENTE);
        pertenencias.saveAndFlush(p);
        evento(p, u, "SOLICITUD", anterior, motivo);
        em.refresh(p);
        return respuesta(p);
    }
    @Transactional
    public void resolver(Integer id, Integer actorId, ResolverPertenenciaRequest request) {
        Usuario admin = actor(actorId, "ADMINISTRADOR");
        Integer solicitanteId = pertenencias.findUsuarioId(id).orElseThrow(() -> new RecursoNoEncontradoException("Pertenencia no encontrada"));
        // Mismo orden de bloqueo que solicitar: usuario y después pertenencia.
        Usuario solicitante = usuarios.findByIdParaActualizar(solicitanteId).orElseThrow(() -> new RecursoNoEncontradoException("Usuario no encontrado"));
        UsuarioComunidad p = pertenencias.findByIdParaActualizar(id).orElseThrow(() -> new RecursoNoEncontradoException("Pertenencia no encontrada"));
        administrar(admin, p.getComunidad().getId());
        // Esta API no permite concederse acceso ni gestionar altas de administradores o técnicos.
        if (admin.getId().equals(solicitanteId) || !"USUARIO".equals(solicitante.getRol().getNombre()))
            throw new OperacionNoPermitidaException("Solo se resuelven solicitudes de vecinos");
        if (p.getEstado() != EstadoUsuario.PENDIENTE)
            throw new OperacionNoPermitidaException("La solicitud ya no está pendiente");
        boolean aprobar = Boolean.TRUE.equals(request.aprobar());
        if (aprobar && solicitante.getEstado() != EstadoUsuario.ACTIVO)
            throw new OperacionNoPermitidaException("No se puede aprobar una cuenta inactiva");
        String motivo = request.motivo() == null ? "" : request.motivo().strip();
        if (!aprobar && motivo.isBlank()) throw new DatosNoValidosException("El motivo de rechazo es obligatorio");
        if (motivo.isBlank()) motivo = "El administrador aprueba la pertenencia a la comunidad.";
        p.setEstado(aprobar ? EstadoUsuario.ACTIVO : EstadoUsuario.INACTIVO);
        pertenencias.save(p);
        evento(p, admin, aprobar ? "APROBACION" : "RECHAZO", EstadoUsuario.PENDIENTE, motivo);
    }
    private void evento(UsuarioComunidad p, Usuario actor, String accion, EstadoUsuario anterior, String motivo) {
        HistorialPertenencia e = new HistorialPertenencia();
        e.setPertenencia(p); e.setActor(actor); e.setAccion(accion);
        e.setEstadoAnterior(anterior == null ? null : anterior.name()); e.setEstadoNuevo(p.getEstado().name());
        e.setMotivo(motivo);
        // Guardado dentro de la misma transacción: si falla el historial, se revierte también el permiso.
        historial.save(e);
    }
}
