package com.gestioncomunidad.backend.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.gestioncomunidad.backend.dto.*;
import com.gestioncomunidad.backend.entity.*;
import com.gestioncomunidad.backend.exception.*;
import com.gestioncomunidad.backend.repositories.*;

@Service
public class RechazoIncidenciaService {
    private final UsuarioRepository usuarios;
    private final UsuarioComunidadRepository pertenencias;
    private final IncidenciaRepository incidencias;
    private final HistorialIncidenciaRepository historial;

    public RechazoIncidenciaService(UsuarioRepository usuarios, UsuarioComunidadRepository pertenencias,
            IncidenciaRepository incidencias, HistorialIncidenciaRepository historial) {
        this.usuarios = usuarios;
        this.pertenencias = pertenencias;
        this.incidencias = incidencias;
        this.historial = historial;
    }
    private Usuario administrador(Integer id) {
        Usuario actor = usuarios.findById(id).orElseThrow(() ->
                new OperacionNoPermitidaException("La cuenta de la sesión ya no está disponible"));
        if (actor.getEstado() != EstadoUsuario.ACTIVO || !"ADMINISTRADOR".equals(actor.getRol().getNombre()))
            throw new OperacionNoPermitidaException("Esta operación requiere un administrador activo");
        return actor;
    }
    private Incidencia bloquear(Integer id) {
        return incidencias.findByIdParaActualizar(id).orElseThrow(() ->
                new RecursoNoEncontradoException("Incidencia no encontrada"));
    }
    private void autorizar(Incidencia i, Usuario actor) {
        if (!pertenencias.existsByUsuario_IdAndComunidad_IdAndEstado(actor.getId(),
                i.getEspacio().getComunidad().getId(), EstadoUsuario.ACTIVO))
            throw new OperacionNoPermitidaException("No tienes permiso para gestionar esta incidencia");
        if (i.getEstado() != EstadoIncidencia.PENDIENTE)
            throw new OperacionNoPermitidaException("Solo se pueden rechazar incidencias pendientes");
        // No dejamos sin referencia válida a avisos que ya dependen de esta incidencia original.
        if (!incidencias.findByIncidenciaOrigen_Id(i.getId()).isEmpty())
            throw new DatosNoValidosException("Esta incidencia ya es la original de otros avisos duplicados");
    }
    private String motivo(String texto) {
        String limpio = texto.strip();
        if (limpio.isBlank()) throw new DatosNoValidosException("El motivo es obligatorio");
        return limpio;
    }

    @Transactional
    public void rechazar(Integer id, Integer actorId, RechazarIncidenciaRequest request) {
        Usuario actor = administrador(actorId);
        Incidencia i = bloquear(id);
        autorizar(i, actor);
        registrar(i, actor, null, motivo(request.motivo()));
    }

    @Transactional
    public void duplicada(Integer id, Integer actorId, MarcarDuplicadaRequest request) {
        Usuario actor = administrador(actorId);
        Integer originalId = request.incidenciaOriginalId();
        if (id.equals(originalId)) throw new DatosNoValidosException("Una incidencia no puede ser duplicada de sí misma");
        // Orden fijo de bloqueo para que dos peticiones cruzadas no bloqueen los registros en orden inverso.
        Incidencia menor = bloquear(Math.min(id, originalId));
        Incidencia mayor = bloquear(Math.max(id, originalId));
        Incidencia i = id.equals(menor.getId()) ? menor : mayor;
        Incidencia original = originalId.equals(menor.getId()) ? menor : mayor;
        autorizar(i, actor);
        if (!i.getEspacio().getComunidad().getId().equals(original.getEspacio().getComunidad().getId()))
            throw new DatosNoValidosException("La incidencia original debe pertenecer a la misma comunidad");
        // Una original rechazada o ya duplicada no ofrece un seguimiento válido; no permitimos cadenas.
        if (original.getEstado() == EstadoIncidencia.RECHAZADA || original.getIncidenciaOrigen() != null)
            throw new DatosNoValidosException("La incidencia original no puede estar rechazada ni ser duplicada");
        registrar(i, actor, original, motivo(request.motivo()));
    }

    private void registrar(Incidencia i, Usuario actor, Incidencia original, String motivo) {
        HistorialIncidencia e = new HistorialIncidencia();
        e.setIncidencia(i);
        e.setUsuarioActor(actor);
        e.setAccion(original == null ? "RECHAZO" : "DUPLICADA");
        e.setEstadoAnterior(i.getEstado().name());
        e.setEstadoNuevo(EstadoIncidencia.RECHAZADA.name());
        e.setDescripcion(original == null ? motivo : motivo + "\nIncidencia original: " + original.getId());
        i.setIncidenciaOrigen(original);
        i.setEstado(EstadoIncidencia.RECHAZADA);
        // Conservamos el aviso y su historial. No se registra cierre administrativo ni se modifica la original.
        // La transacción confirma vínculo, estado y evento juntos o los revierte si falla cualquier guardado.
        incidencias.save(i);
        historial.save(e);
    }
}
