package com.gestioncomunidad.backend.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.gestioncomunidad.backend.dto.CrearIncidenciaRequest;
import com.gestioncomunidad.backend.dto.IncidenciaDetalleResponse;
import com.gestioncomunidad.backend.dto.IncidenciaResponse;
import com.gestioncomunidad.backend.entity.Espacio;
import com.gestioncomunidad.backend.entity.EstadoIncidencia;
import com.gestioncomunidad.backend.entity.EstadoUsuario;
import com.gestioncomunidad.backend.entity.HistorialIncidencia;
import com.gestioncomunidad.backend.entity.Incidencia;
import com.gestioncomunidad.backend.entity.Usuario;
import com.gestioncomunidad.backend.exception.OperacionNoPermitidaException;
import com.gestioncomunidad.backend.exception.RecursoNoEncontradoException;
import com.gestioncomunidad.backend.repositories.EspacioRepository;
import com.gestioncomunidad.backend.repositories.HistorialIncidenciaRepository;
import com.gestioncomunidad.backend.repositories.IncidenciaRepository;
import com.gestioncomunidad.backend.repositories.UsuarioComunidadRepository;
import com.gestioncomunidad.backend.repositories.UsuarioRepository;
import com.gestioncomunidad.backend.dto.HistorialIncidenciaResponse;

import jakarta.persistence.EntityManager;

import java.util.List;

@Service
public class IncidenciaService {

        private final UsuarioRepository usuarioRepository;
        private final EspacioRepository espacioRepository;
        private final UsuarioComunidadRepository usuarioComunidadRepository;
        private final IncidenciaRepository incidenciaRepository;
        private final HistorialIncidenciaRepository historialRepository;
        private final EntityManager entityManager;

        public IncidenciaService(
                        UsuarioRepository usuarioRepository,
                        EspacioRepository espacioRepository,
                        UsuarioComunidadRepository usuarioComunidadRepository,
                        IncidenciaRepository incidenciaRepository,
                        HistorialIncidenciaRepository historialRepository,
                        EntityManager entityManager) {
                this.usuarioRepository = usuarioRepository;
                this.espacioRepository = espacioRepository;
                this.usuarioComunidadRepository = usuarioComunidadRepository;
                this.incidenciaRepository = incidenciaRepository;
                this.historialRepository = historialRepository;
                this.entityManager = entityManager;
        }

        @Transactional
        public IncidenciaResponse crear(
                        CrearIncidenciaRequest request,
                        Integer usuarioAutenticadoId) {

                Usuario usuario = usuarioRepository.findById(usuarioAutenticadoId)
                                .orElseThrow(() -> new RecursoNoEncontradoException("Usuario no encontrado"));

                if (usuario.getEstado() != EstadoUsuario.ACTIVO) {
                        throw new OperacionNoPermitidaException(
                                        "La cuenta de usuario debe estar activa");
                }

                if (!"USUARIO".equals(usuario.getRol().getNombre())) {
                        throw new OperacionNoPermitidaException(
                                        "Esta operación de alta está habilitada para vecinos");
                }

                Espacio espacio = espacioRepository.findById(request.espacioId())
                                .orElseThrow(() -> new RecursoNoEncontradoException("Espacio no encontrado"));

                Integer comunidadId = espacio.getComunidad().getId();

                boolean pertenece = usuarioComunidadRepository
                                .existsByUsuario_IdAndComunidad_IdAndEstado(
                                                usuario.getId(),
                                                comunidadId,
                                                EstadoUsuario.ACTIVO);

                if (!pertenece) {
                        throw new OperacionNoPermitidaException(
                                        "No tienes una pertenencia activa a esta comunidad");
                }

                Incidencia incidencia = new Incidencia();
                incidencia.setTitulo(request.titulo().strip());
                incidencia.setDescripcion(request.descripcion().strip());
                incidencia.setPrioridad(request.prioridad());
                incidencia.setEstado(EstadoIncidencia.PENDIENTE);
                incidencia.setEspacio(espacio);
                incidencia.setUsuarioReporta(usuario);
                incidencia.setVecinoVerificador(usuario);

                incidenciaRepository.saveAndFlush(incidencia);

                // Recupera la fecha de creación generada por MySQL.
                entityManager.refresh(incidencia);

                HistorialIncidencia evento = new HistorialIncidencia();
                evento.setIncidencia(incidencia);
                evento.setUsuarioActor(usuario);
                evento.setAccion("CREACION");
                evento.setEstadoAnterior(null);
                evento.setEstadoNuevo(EstadoIncidencia.PENDIENTE.name());
                evento.setDescripcion("El vecino registra la incidencia.");

                historialRepository.save(evento);

                return new IncidenciaResponse(
                                incidencia.getId(),
                                incidencia.getTitulo(),
                                incidencia.getDescripcion(),
                                incidencia.getPrioridad(),
                                incidencia.getEstado(),
                                incidencia.getFechaCreacion(),
                                espacio.getId(),
                                comunidadId,
                                usuario.getId(),
                                usuario.getId(),
                                null);
        }

        @Transactional
        public List<IncidenciaResponse> listarMisIncidencias(
                        Integer usuarioAutenticadoId) {

                Usuario usuario = usuarioRepository.findById(usuarioAutenticadoId)
                                .orElseThrow(() -> new RecursoNoEncontradoException("Usuario no encontrado"));

                if (usuario.getEstado() != EstadoUsuario.ACTIVO) {
                        throw new OperacionNoPermitidaException(
                                        "La cuenta de usuario debe estar activa");
                }

                if (!"USUARIO".equals(usuario.getRol().getNombre())) {
                        throw new OperacionNoPermitidaException(
                                        "Esta consulta está habilitada para vecinos");
                }

                var comunidadesActivas = usuarioComunidadRepository
                                .findByUsuario_IdAndEstado(
                                                usuario.getId(),
                                                EstadoUsuario.ACTIVO)
                                .stream()
                                .map(pertenencia -> pertenencia.getComunidad().getId())
                                .collect(java.util.stream.Collectors.toSet());

                var incidencias = incidenciaRepository
                                .findByUsuarioReporta_IdOrVecinoVerificador_IdOrderByFechaCreacionDescIdDesc(
                                                usuario.getId(),
                                                usuario.getId());

                return incidencias.stream()
                                .filter(incidencia -> comunidadesActivas.contains(
                                                incidencia.getEspacio().getComunidad().getId()))
                                .map(incidencia -> new IncidenciaResponse(
                                                incidencia.getId(),
                                                incidencia.getTitulo(),
                                                incidencia.getDescripcion(),
                                                incidencia.getPrioridad(),
                                                incidencia.getEstado(),
                                                incidencia.getFechaCreacion(),
                                                incidencia.getEspacio().getId(),
                                                incidencia.getEspacio().getComunidad().getId(),
                                                incidencia.getUsuarioReporta().getId(),
                                                incidencia.getVecinoVerificador().getId(),
                                                incidencia.getTecnicoAsignado() != null
                                                                ? incidencia.getTecnicoAsignado().getId()
                                                                : null))
                                .toList();
        }

        @Transactional(readOnly = true)
        public IncidenciaDetalleResponse consultarDetalle(
                        Integer incidenciaId,
                        Integer usuarioAutenticadoId) {
                Incidencia incidencia = obtenerIncidenciaAccesible(
                                incidenciaId,
                                usuarioAutenticadoId);

                Espacio espacio = incidencia.getEspacio();
                var comunidad = espacio.getComunidad();

                Usuario reportante = incidencia.getUsuarioReporta();
                Usuario verificador = incidencia.getVecinoVerificador();
                Usuario tecnico = incidencia.getTecnicoAsignado();

                return new IncidenciaDetalleResponse(
                                incidencia.getId(),
                                incidencia.getTitulo(),
                                incidencia.getDescripcion(),
                                incidencia.getPrioridad(),
                                incidencia.getEstado(),
                                incidencia.getFechaCreacion(),
                                incidencia.getFechaPrevista(),
                                espacio.getId(),
                                espacio.getNombre(),
                                comunidad.getId(),
                                comunidad.getNombre(),
                                reportante.getId(),
                                reportante.getNombre() + " " + reportante.getApellidos(),
                                verificador.getId(),
                                verificador.getNombre() + " " + verificador.getApellidos(),
                                tecnico != null ? tecnico.getId() : null,
                                tecnico != null
                                                ? tecnico.getNombre() + " " + tecnico.getApellidos()
                                                : null);
        }

        @Transactional(readOnly = true)
        public List<HistorialIncidenciaResponse> consultarHistorial(
                        Integer incidenciaId,
                        Integer usuarioAutenticadoId) {

                Incidencia incidencia = obtenerIncidenciaAccesible(
                                incidenciaId,
                                usuarioAutenticadoId);

                return historialRepository
                                .findByIncidencia_IdOrderByFechaEventoAscIdAsc(
                                                incidencia.getId())
                                .stream()
                                .map(evento -> new HistorialIncidenciaResponse(
                                                evento.getId(),
                                                evento.getAccion(),
                                                evento.getEstadoAnterior(),
                                                evento.getEstadoNuevo(),
                                                evento.getDescripcion(),
                                                evento.getFechaEvento(),
                                                evento.getUsuarioActor().getId(),
                                                evento.getUsuarioActor().getNombre()
                                                                + " "
                                                                + evento.getUsuarioActor().getApellidos()))
                                .toList();
        }

        private Incidencia obtenerIncidenciaAccesible(
                        Integer incidenciaId,
                        Integer usuarioAutenticadoId) {

                Usuario usuario = usuarioRepository.findById(usuarioAutenticadoId)
                                .orElseThrow(() -> new OperacionNoPermitidaException(
                                                "La cuenta de la sesión ya no está disponible"));

                if (usuario.getEstado() != EstadoUsuario.ACTIVO) {
                        throw new OperacionNoPermitidaException(
                                        "La cuenta de usuario debe estar activa");
                }

                if (!"USUARIO".equals(usuario.getRol().getNombre())) {
                        throw new OperacionNoPermitidaException(
                                        "Esta consulta está habilitada para vecinos");
                }

                Incidencia incidencia = incidenciaRepository.findById(incidenciaId)
                                .orElseThrow(() -> new RecursoNoEncontradoException(
                                                "Incidencia no encontrada"));

                Integer comunidadId = incidencia.getEspacio().getComunidad().getId();

                boolean pertenece = usuarioComunidadRepository
                                .existsByUsuario_IdAndComunidad_IdAndEstado(
                                                usuario.getId(),
                                                comunidadId,
                                                EstadoUsuario.ACTIVO);

                boolean esReportante = usuario.getId()
                                .equals(incidencia.getUsuarioReporta().getId());

                boolean esVerificador = usuario.getId()
                                .equals(incidencia.getVecinoVerificador().getId());

                if (!pertenece || (!esReportante && !esVerificador)) {
                        throw new OperacionNoPermitidaException(
                                        "No tienes permiso para consultar esta incidencia");
                }

                return incidencia;
        }

}
