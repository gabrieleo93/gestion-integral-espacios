package com.gestioncomunidad.backend.service;

import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;
import jakarta.persistence.EntityManager;
import com.gestioncomunidad.backend.dto.EvidenciaResponse;
import com.gestioncomunidad.backend.entity.*;
import com.gestioncomunidad.backend.exception.*;
import com.gestioncomunidad.backend.repositories.*;

@Service
public class EvidenciaService {
    private final UsuarioRepository usuarios;
    private final UsuarioComunidadRepository pertenencias;
    private final IncidenciaRepository incidencias;
    private final HistorialIncidenciaRepository historial;
    private final EvidenciaRepository evidencias;
    private final AlmacenamientoEvidencias archivos;
    private final EntityManager em;
    public EvidenciaService(UsuarioRepository usuarios, UsuarioComunidadRepository pertenencias,
            IncidenciaRepository incidencias, HistorialIncidenciaRepository historial,
            EvidenciaRepository evidencias, AlmacenamientoEvidencias archivos, EntityManager em) {
        this.usuarios = usuarios; this.pertenencias = pertenencias; this.incidencias = incidencias;
        this.historial = historial; this.evidencias = evidencias; this.archivos = archivos; this.em = em;
    }
    private Usuario actor(Integer id) {
        Usuario u = usuarios.findById(id).orElseThrow(() -> new OperacionNoPermitidaException("La cuenta no está disponible"));
        if (u.getEstado() != EstadoUsuario.ACTIVO) throw new OperacionNoPermitidaException("La cuenta debe estar activa");
        return u;
    }
    private void lectura(Incidencia i, Usuario u) {
        String rol = u.getRol().getNombre();
        boolean tecnicoAsignado = "TECNICO".equals(rol) && i.getTecnicoAsignado() != null
                && u.getId().equals(i.getTecnicoAsignado().getId());
        if ((!"USUARIO".equals(rol) && !"ADMINISTRADOR".equals(rol) && !tecnicoAsignado)
                || !pertenencias.existsByUsuario_IdAndComunidad_IdAndEstado(u.getId(), i.getEspacio().getComunidad().getId(), EstadoUsuario.ACTIVO))
            throw new OperacionNoPermitidaException("No tienes permiso para consultar estas evidencias");
    }
    private Incidencia incidencia(Integer id, boolean bloquear) {
        return (bloquear ? incidencias.findByIdParaActualizar(id) : incidencias.findById(id))
                .orElseThrow(() -> new RecursoNoEncontradoException("Incidencia no encontrada"));
    }
    private EvidenciaResponse respuesta(Evidencia e) {
        return new EvidenciaResponse(e.getId(), e.getIncidencia().getId(), e.getNombreOriginal(), e.getTipoMime(),
                e.getDescripcion(), e.getTipo(), e.getFechaSubida(), e.getUsuarioSubida().getId(), e.getHistorial().getId(),
                "/api/incidencias/" + e.getIncidencia().getId() + "/evidencias/" + e.getId() + "/archivo");
    }
    @Transactional(readOnly = true)
    public List<EvidenciaResponse> listar(Integer id, Integer actorId) {
        Usuario u = actor(actorId); Incidencia i = incidencia(id, false); lectura(i, u);
        return evidencias.findByIncidencia_IdOrderByFechaSubidaAscIdAsc(id).stream().map(this::respuesta).toList();
    }
    public record Archivo(byte[] contenido, String mime) { }
    @Transactional(readOnly = true)
    public Archivo descargar(Integer id, Integer evidenciaId, Integer actorId) {
        Usuario u = actor(actorId); Incidencia i = incidencia(id, false); lectura(i, u);
        Evidencia e = evidencias.findById(evidenciaId).orElseThrow(() -> new RecursoNoEncontradoException("Evidencia no encontrada"));
        if (!id.equals(e.getIncidencia().getId())) throw new RecursoNoEncontradoException("Evidencia no encontrada");
        return new Archivo(archivos.leer(e.getUrlArchivo()), e.getTipoMime());
    }
    @Transactional
    public EvidenciaResponse subir(Integer id, Integer actorId, MultipartFile archivo, TipoEvidencia tipo, String descripcion) {
        Usuario u = actor(actorId); Incidencia i = incidencia(id, true); lectura(i, u);
        String rol = u.getRol().getNombre();
        // La lectura compartida nunca concede escritura: cada fase tiene sus autores autorizados.
        boolean antes = tipo == TipoEvidencia.ANTES && i.getEstado() == EstadoIncidencia.PENDIENTE
                && ("ADMINISTRADOR".equals(rol) || ("USUARIO".equals(rol) && u.getId().equals(i.getUsuarioReporta().getId())));
        boolean tecnico = "TECNICO".equals(rol) && i.getEstado() == EstadoIncidencia.EN_PROCESO
                && (tipo == TipoEvidencia.DURANTE || tipo == TipoEvidencia.DESPUES);
        if (!antes && !tecnico) throw new OperacionNoPermitidaException("Tu rol o el estado no permite subir este tipo de evidencia");
        String texto = descripcion == null ? null : descripcion.strip();
        // La tabla SQL existente admite 255 caracteres, aunque el mapeo previo declaraba TEXT.
        if (texto != null && texto.length() > 255) throw new DatosNoValidosException("La descripción admite hasta 255 caracteres");
        String nombre = archivo.getOriginalFilename();
        nombre = nombre == null ? "imagen" : nombre.replace('\\', '/');
        nombre = nombre.substring(nombre.lastIndexOf('/') + 1).replaceAll("[\\p{Cntrl}]", "");
        if (nombre.isBlank()) nombre = "imagen";
        if (nombre.length() > 255) throw new DatosNoValidosException("El nombre del archivo admite hasta 255 caracteres");
        var imagen = archivos.validar(archivo);
        String clave = archivos.guardar(imagen);
        // Disco y MySQL no comparten transacción: compensamos también fallos ocurridos al confirmar SQL.
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override public void afterCompletion(int status) {
                if (status == STATUS_ROLLED_BACK) {
                    try { archivos.eliminar(clave); }
                    catch (RuntimeException error) {
                        org.slf4j.LoggerFactory.getLogger(EvidenciaService.class).error("No se pudo retirar una evidencia tras rollback", error);
                    }
                }
            }
        });
        HistorialIncidencia evento = new HistorialIncidencia();
        evento.setIncidencia(i); evento.setUsuarioActor(u); evento.setAccion("EVIDENCIA_ADJUNTADA");
        evento.setEstadoAnterior(i.getEstado().name()); evento.setEstadoNuevo(i.getEstado().name());
        evento.setDescripcion("Fotografía " + tipo.name() + ": " + nombre + (texto == null || texto.isBlank() ? "" : "\n" + texto));
        historial.save(evento);
        Evidencia e = new Evidencia();
        e.setIncidencia(i); e.setUsuarioSubida(u); e.setHistorial(evento); e.setTipo(tipo);
        e.setDescripcion(texto); e.setNombreOriginal(nombre); e.setTipoMime(imagen.mime()); e.setUrlArchivo(clave);
        evidencias.saveAndFlush(e);
        em.refresh(e); // Recupera la fecha generada por MySQL antes de responder al cliente.
        return respuesta(e);
    }
}
