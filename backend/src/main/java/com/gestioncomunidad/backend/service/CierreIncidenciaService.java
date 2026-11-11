package com.gestioncomunidad.backend.service;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.gestioncomunidad.backend.dto.*;
import com.gestioncomunidad.backend.entity.*;
import com.gestioncomunidad.backend.exception.*;
import com.gestioncomunidad.backend.repositories.*;

@Service
public class CierreIncidenciaService {
    private final UsuarioRepository usuarios;
    private final UsuarioComunidadRepository pertenencias;
    private final IncidenciaRepository incidencias;
    private final HistorialIncidenciaRepository historial;

    public CierreIncidenciaService(UsuarioRepository usuarios, UsuarioComunidadRepository pertenencias,
            IncidenciaRepository incidencias, HistorialIncidenciaRepository historial) {
        this.usuarios = usuarios;
        this.pertenencias = pertenencias;
        this.incidencias = incidencias;
        this.historial = historial;
    }

    private Usuario actor(Integer id, String rol) {
        Usuario actor = usuarios.findById(id).orElseThrow(() ->
                new OperacionNoPermitidaException("La cuenta de la sesión ya no está disponible"));
        if (actor.getEstado() != EstadoUsuario.ACTIVO || !rol.equals(actor.getRol().getNombre()))
            throw new OperacionNoPermitidaException("Tu cuenta o rol no permite esta operación");
        return actor;
    }

    private Incidencia incidencia(Integer id, Usuario actor) {
        // Comparte bloqueo con asignación y trabajo técnico: cada transición parte del estado real.
        Incidencia i = incidencias.findByIdParaActualizar(id).orElseThrow(() ->
                new RecursoNoEncontradoException("Incidencia no encontrada"));
        if (!pertenencias.existsByUsuario_IdAndComunidad_IdAndEstado(actor.getId(),
                i.getEspacio().getComunidad().getId(), EstadoUsuario.ACTIVO))
            throw new OperacionNoPermitidaException("No tienes permiso para gestionar esta incidencia");
        return i;
    }

    private void estado(Incidencia i, EstadoIncidencia requerido) {
        if (i.getEstado() != requerido)
            throw new OperacionNoPermitidaException("Esta operación requiere el estado " + requerido.name());
    }

    @Transactional
    public void confirmar(Integer id, Integer actorId, ConfirmarReparacionRequest request) {
        Usuario vecino = actor(actorId, "USUARIO");
        Incidencia i = incidencia(id, vecino);
        // Confirma el verificador designado, no cualquier vecino que pueda consultar el aviso.
        if (!vecino.getId().equals(i.getVecinoVerificador().getId()))
            throw new OperacionNoPermitidaException("Solo el vecino verificador puede confirmar la reparación");
        estado(i, EstadoIncidencia.PENDIENTE_CONFIRMACION);
        String motivo = request.motivo() == null ? "" : request.motivo().strip();
        if (Boolean.TRUE.equals(request.conforme())) {
            i.setFechaConfirmacion(LocalDateTime.now().truncatedTo(ChronoUnit.SECONDS));
            guardar(i, vecino, "CONFIRMACION_REPARACION", EstadoIncidencia.PENDIENTE_CIERRE,
                    motivo.isBlank() ? "El vecino confirma que la reparación es correcta." : motivo, false);
        } else {
            if (motivo.isBlank()) throw new DatosNoValidosException("Debes indicar por qué la reparación no es conforme");
            // Se conserva la asignación para continuar el trabajo. La reparación anterior queda en historial.
            String descripcion = motivo + "\nFecha de reparación anterior: " + i.getFechaReparacion();
            i.setFechaReparacion(null);
            i.setFechaConfirmacion(null);
            guardar(i, vecino, "REPARACION_NO_CONFORME", EstadoIncidencia.EN_PROCESO, descripcion, false);
        }
    }

    @Transactional
    public void cerrar(Integer id, Integer actorId) {
        Usuario admin = actor(actorId, "ADMINISTRADOR");
        Incidencia i = incidencia(id, admin);
        estado(i, EstadoIncidencia.PENDIENTE_CIERRE);
        i.setFechaCierre(LocalDateTime.now().truncatedTo(ChronoUnit.SECONDS));
        guardar(i, admin, "CIERRE", EstadoIncidencia.FINALIZADA,
                "El administrador cierra la incidencia tras la confirmación del vecino.", false);
    }

    @Transactional
    public void reabrir(Integer id, Integer actorId, ReabrirIncidenciaRequest request) {
        Usuario admin = actor(actorId, "ADMINISTRADOR");
        Incidencia i = incidencia(id, admin);
        estado(i, EstadoIncidencia.FINALIZADA);
        String motivo = request.motivo().strip();
        if (motivo.isBlank()) throw new DatosNoValidosException("El motivo de reapertura es obligatorio");
        // Conservamos las fechas del ciclo anterior en el evento antes de reiniciar los campos actuales.
        String descripcion = motivo + "\nAsignación anterior: " + i.getFechaAsignacion()
                + "\nPrevisión anterior: " + i.getFechaPrevista() + "\nInicio anterior: " + i.getFechaInicio()
                + "\nReparación anterior: " + i.getFechaReparacion()
                + "\nConfirmación anterior: " + i.getFechaConfirmacion() + "\nCierre anterior: " + i.getFechaCierre();
        i.setFechaAsignacion(null);
        i.setFechaPrevista(null);
        i.setFechaInicio(null);
        i.setFechaReparacion(null);
        i.setFechaConfirmacion(null);
        i.setFechaCierre(null);
        // La regla acordada devuelve a PENDIENTE para asignar técnico de nuevo; conserva autor y verificador.
        guardar(i, admin, "REAPERTURA", EstadoIncidencia.PENDIENTE, descripcion, true);
    }

    private void guardar(Incidencia i, Usuario actor, String accion, EstadoIncidencia nuevo,
            String descripcion, boolean quitarTecnico) {
        HistorialIncidencia e = new HistorialIncidencia();
        e.setIncidencia(i);
        e.setUsuarioActor(actor);
        e.setAccion(accion);
        e.setEstadoAnterior(i.getEstado().name());
        e.setEstadoNuevo(nuevo.name());
        e.setDescripcion(descripcion);
        if (quitarTecnico) {
            e.setTecnicoAnterior(i.getTecnicoAsignado());
            e.setTecnicoNuevo(null);
            i.setTecnicoAsignado(null);
        }
        i.setEstado(nuevo);
        // Cada método público es transaccional: un fallo del historial revierte también fechas y estado.
        incidencias.save(i);
        historial.save(e);
    }
}
