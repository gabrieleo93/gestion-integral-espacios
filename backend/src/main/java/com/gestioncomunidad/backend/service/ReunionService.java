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
public class ReunionService {
    private final UsuarioRepository usuarios;
    private final ComunidadRepository comunidades;
    private final UsuarioComunidadRepository pertenencias;
    private final ReunionRepository reuniones;
    private final ActaRepository actas;
    private final EntityManager em;
    public ReunionService(UsuarioRepository usuarios, ComunidadRepository comunidades,
            UsuarioComunidadRepository pertenencias, ReunionRepository reuniones, ActaRepository actas, EntityManager em) {
        this.usuarios = usuarios; this.comunidades = comunidades; this.pertenencias = pertenencias;
        this.reuniones = reuniones; this.actas = actas; this.em = em;
    }
    private Usuario actor(Integer id, boolean escritura) {
        Usuario u = usuarios.findById(id).orElseThrow(() -> new OperacionNoPermitidaException("La cuenta no está disponible"));
        String rol = u.getRol().getNombre();
        if (u.getEstado() != EstadoUsuario.ACTIVO || (!"ADMINISTRADOR".equals(rol) && (escritura || !"USUARIO".equals(rol))))
            throw new OperacionNoPermitidaException("Tu cuenta o rol no permite acceder a reuniones y actas");
        return u;
    }
    private void acceso(Integer comunidadId, Usuario u) {
        if (!pertenencias.existsByUsuario_IdAndComunidad_IdAndEstado(u.getId(), comunidadId, EstadoUsuario.ACTIVO))
            throw new OperacionNoPermitidaException("No tienes acceso a esta comunidad");
    }
    private Reunion reunion(Integer id, boolean bloquear) {
        return (bloquear ? reuniones.findByIdParaActualizar(id) : reuniones.findById(id))
                .orElseThrow(() -> new RecursoNoEncontradoException("Reunión no encontrada"));
    }
    private ReunionResponse respuesta(Reunion r) {
        return new ReunionResponse(r.getId(), r.getComunidad().getId(), r.getTitulo(), r.getDescripcion(),
                r.getFechaReunion(), r.getLugar(), r.getFechaCreacion(), r.getCreador().getId());
    }
    private ActaResponse respuesta(Acta a) {
        return new ActaResponse(a.getId(), a.getReunion().getId(), a.getContenido(), a.getFechaPublicacion(),
                a.getPublicador().getId(), a.getPublicador().getNombre() + " " + a.getPublicador().getApellidos());
    }
    @Transactional(readOnly = true)
    public List<ReunionResponse> listar(Integer comunidadId, Integer actorId) {
        Usuario u = actor(actorId, false); acceso(comunidadId, u);
        return reuniones.findByComunidad_IdOrderByFechaReunionDescIdDesc(comunidadId).stream().map(this::respuesta).toList();
    }
    @Transactional(readOnly = true)
    public ReunionResponse detalle(Integer id, Integer actorId) {
        Usuario u = actor(actorId, false); Reunion r = reunion(id, false); acceso(r.getComunidad().getId(), u);
        return respuesta(r);
    }
    @Transactional
    public ReunionResponse crear(Integer comunidadId, Integer actorId, CrearReunionRequest request) {
        Usuario u = actor(actorId, true); acceso(comunidadId, u);
        Comunidad c = comunidades.findById(comunidadId).orElseThrow(() -> new RecursoNoEncontradoException("Comunidad no encontrada"));
        LocalDateTime fecha = request.fechaReunion().truncatedTo(ChronoUnit.SECONDS);
        if (!fecha.isAfter(LocalDateTime.now())) throw new DatosNoValidosException("La reunión debe convocarse para una fecha futura");
        String titulo = request.titulo().strip(), lugar = request.lugar().strip();
        if (titulo.isBlank() || lugar.isBlank()) throw new DatosNoValidosException("Título y lugar son obligatorios");
        Reunion r = new Reunion(); r.setComunidad(c); r.setCreador(u); r.setTitulo(titulo); r.setLugar(lugar);
        r.setDescripcion(request.descripcion() == null ? null : request.descripcion().strip()); r.setFechaReunion(fecha);
        reuniones.saveAndFlush(r); em.refresh(r); // La base genera fechaCreacion; se devuelve su valor persistido.
        return respuesta(r);
    }
    @Transactional
    public ActaResponse publicar(Integer reunionId, Integer actorId, PublicarActaRequest request) {
        Usuario u = actor(actorId, true); Reunion r = reunion(reunionId, true); acceso(r.getComunidad().getId(), u);
        if (LocalDateTime.now().isBefore(r.getFechaReunion())) throw new OperacionNoPermitidaException("No se publica un acta antes de la reunión");
        // Lectura con bloqueo y restricción única impiden publicar dos actas bajo concurrencia.
        if (actas.findParaActualizar(reunionId).isPresent()) throw new OperacionNoPermitidaException("El acta ya está publicada y no puede sustituirse");
        String texto = request.contenido().strip();
        if (texto.isBlank()) throw new DatosNoValidosException("El contenido del acta es obligatorio");
        Acta a = new Acta(); a.setReunion(r); a.setPublicador(u); a.setContenido(texto);
        actas.saveAndFlush(a); em.refresh(a);
        // Acta, fecha y autor se publican en una transacción; no hay un borrador parcialmente visible.
        return respuesta(a);
    }
    @Transactional(readOnly = true)
    public ActaResponse acta(Integer reunionId, Integer actorId) {
        Usuario u = actor(actorId, false); Reunion r = reunion(reunionId, false); acceso(r.getComunidad().getId(), u);
        return respuesta(actas.findByReunion_Id(reunionId).orElseThrow(() -> new RecursoNoEncontradoException("Esta reunión todavía no tiene acta publicada")));
    }
}
