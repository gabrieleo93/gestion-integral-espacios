package com.gestioncomunidad.backend.service;

import java.math.BigDecimal;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import jakarta.persistence.EntityManager;
import com.gestioncomunidad.backend.dto.*;
import com.gestioncomunidad.backend.entity.*;
import com.gestioncomunidad.backend.exception.*;
import com.gestioncomunidad.backend.repositories.*;

@Service
public class CosteService {
    private final UsuarioRepository usuarios;
    private final UsuarioComunidadRepository pertenencias;
    private final IncidenciaRepository incidencias;
    private final CosteRepository costes;
    private final HistorialIncidenciaRepository historial;
    private final EntityManager em;
    public CosteService(UsuarioRepository usuarios, UsuarioComunidadRepository pertenencias,
            IncidenciaRepository incidencias, CosteRepository costes, HistorialIncidenciaRepository historial, EntityManager em) {
        this.usuarios = usuarios; this.pertenencias = pertenencias; this.incidencias = incidencias;
        this.costes = costes; this.historial = historial; this.em = em;
    }
    private Usuario actor(Integer id, boolean escritura) {
        Usuario u = usuarios.findById(id).orElseThrow(() -> new OperacionNoPermitidaException("La cuenta no está disponible"));
        String rol = u.getRol().getNombre();
        if (u.getEstado() != EstadoUsuario.ACTIVO || (!"ADMINISTRADOR".equals(rol) && (escritura || !"USUARIO".equals(rol))))
            throw new OperacionNoPermitidaException("Tu cuenta o rol no permite esta operación de costes");
        return u;
    }
    private Incidencia incidencia(Integer id, Usuario u, boolean escritura) {
        Incidencia i = (escritura ? incidencias.findByIdParaActualizar(id) : incidencias.findById(id))
                .orElseThrow(() -> new RecursoNoEncontradoException("Incidencia no encontrada"));
        if (!pertenencias.existsByUsuario_IdAndComunidad_IdAndEstado(u.getId(), i.getEspacio().getComunidad().getId(), EstadoUsuario.ACTIVO))
            throw new OperacionNoPermitidaException("No tienes acceso a los costes de esta comunidad");
        if (escritura && i.getEstado() == EstadoIncidencia.RECHAZADA)
            throw new OperacionNoPermitidaException("No se registran ni modifican costes de incidencias rechazadas");
        return i;
    }
    private Coste bloquear(Integer id, Usuario u) {
        Integer incidenciaId = costes.findIncidenciaId(id).orElseThrow(() -> new RecursoNoEncontradoException("Coste no encontrado"));
        // Mismo orden global: incidencia primero y coste después para serializar edición y facturación.
        incidencia(incidenciaId, u, true);
        return costes.findByIdParaActualizar(id).orElseThrow();
    }
    private CosteResponse respuesta(Coste c) {
        return new CosteResponse(c.getId(), c.getIncidencia().getId(), c.getConcepto(), c.getImporte(), c.getEstadoFacturacion(), c.getFechaRegistro());
    }
    private String concepto(GuardarCosteRequest r) {
        String valor = r.concepto().strip();
        if (valor.isBlank()) throw new DatosNoValidosException("El concepto es obligatorio");
        return valor;
    }
    @Transactional(readOnly = true)
    public List<CosteResponse> listar(Integer incidenciaId, Integer actorId) {
        Usuario u = actor(actorId, false); incidencia(incidenciaId, u, false);
        return costes.findByIncidencia_IdOrderByFechaRegistroAscIdAsc(incidenciaId).stream().map(this::respuesta).toList();
    }
    @Transactional(readOnly = true)
    public ResumenCostesResponse resumen(Integer incidenciaId, Integer actorId) {
        Usuario u = actor(actorId, false); incidencia(incidenciaId, u, false);
        BigDecimal pendiente = new BigDecimal("0.00"), facturado = new BigDecimal("0.00");
        for (Coste c : costes.findByIncidencia_IdOrderByFechaRegistroAscIdAsc(incidenciaId)) {
            if (c.getEstadoFacturacion() == EstadoFacturacion.FACTURADO) facturado = facturado.add(c.getImporte());
            else pendiente = pendiente.add(c.getImporte());
        }
        return new ResumenCostesResponse(incidenciaId, pendiente.add(facturado), pendiente, facturado);
    }
    @Transactional
    public CosteResponse crear(Integer incidenciaId, Integer actorId, GuardarCosteRequest request) {
        Usuario u = actor(actorId, true); Incidencia i = incidencia(incidenciaId, u, true);
        Coste c = new Coste(); c.setIncidencia(i); c.setConcepto(concepto(request)); c.setImporte(request.importe());
        // Un coste real no se crea automáticamente a partir de un presupuesto aprobado.
        c.setEstadoFacturacion(EstadoFacturacion.PENDIENTE_FACTURAR); costes.saveAndFlush(c); em.refresh(c);
        evento(c, u, "COSTE_REGISTRADO", "Concepto: " + c.getConcepto() + ". Importe: " + c.getImporte());
        return respuesta(c);
    }
    @Transactional
    public void editar(Integer id, Integer actorId, GuardarCosteRequest request) {
        Usuario u = actor(actorId, true); Coste c = bloquear(id, u);
        if (c.getEstadoFacturacion() != EstadoFacturacion.PENDIENTE_FACTURAR)
            throw new OperacionNoPermitidaException("Un coste facturado no se puede editar");
        String concepto = concepto(request);
        if (concepto.equals(c.getConcepto()) && request.importe().compareTo(c.getImporte()) == 0) return;
        String detalle = "Concepto anterior: " + c.getConcepto() + ". Importe anterior: " + c.getImporte()
                + ". Concepto nuevo: " + concepto + ". Importe nuevo: " + request.importe();
        c.setConcepto(concepto); c.setImporte(request.importe()); costes.save(c);
        evento(c, u, "COSTE_EDITADO", detalle);
    }
    @Transactional
    public void facturar(Integer id, Integer actorId) {
        Usuario u = actor(actorId, true); Coste c = bloquear(id, u);
        // Repetir la marca de facturado es idempotente: no duplica el evento ni modifica importes.
        if (c.getEstadoFacturacion() == EstadoFacturacion.FACTURADO) return;
        c.setEstadoFacturacion(EstadoFacturacion.FACTURADO); costes.save(c);
        evento(c, u, "COSTE_FACTURADO", "PENDIENTE_FACTURAR -> FACTURADO. Importe: " + c.getImporte());
    }
    private void evento(Coste c, Usuario u, String accion, String descripcion) {
        HistorialIncidencia e = new HistorialIncidencia(); e.setIncidencia(c.getIncidencia()); e.setUsuarioActor(u);
        e.setAccion(accion); e.setEstadoAnterior(c.getIncidencia().getEstado().name()); e.setEstadoNuevo(c.getIncidencia().getEstado().name());
        e.setDescripcion("Coste " + c.getId() + ". " + descripcion);
        // El historial audita actor y fecha; fallo de auditoría revierte el cambio económico.
        historial.save(e);
    }
}
