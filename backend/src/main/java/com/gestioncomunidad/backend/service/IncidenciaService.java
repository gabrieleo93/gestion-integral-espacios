package com.gestioncomunidad.backend.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.gestioncomunidad.backend.dto.CrearIncidenciaRequest;
import com.gestioncomunidad.backend.dto.EditarIncidenciaRequest;
import com.gestioncomunidad.backend.dto.IncidenciaDetalleResponse;
import com.gestioncomunidad.backend.dto.IncidenciaResponse;
import com.gestioncomunidad.backend.entity.Espacio;
import com.gestioncomunidad.backend.entity.EstadoIncidencia;
import com.gestioncomunidad.backend.entity.EstadoUsuario;
import com.gestioncomunidad.backend.entity.HistorialIncidencia;
import com.gestioncomunidad.backend.entity.Incidencia;
import com.gestioncomunidad.backend.entity.Usuario;
import com.gestioncomunidad.backend.exception.OperacionNoPermitidaException;
import com.gestioncomunidad.backend.exception.DatosNoValidosException;
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

                // El alta admite vecinos y administradores; ambos necesitan pertenencia activa.
                boolean esAdministrador = "ADMINISTRADOR".equals(usuario.getRol().getNombre());
                if (!"USUARIO".equals(usuario.getRol().getNombre()) && !esAdministrador) {
                        throw new OperacionNoPermitidaException(
                                        "Esta operación de alta está habilitada para vecinos y administradores");
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

                // La confirmación posterior corresponde a un vecino activo de esta comunidad.
                // Comprobamos todos los datos antes de guardar la incidencia o su historial.
                Usuario verificador = usuario;
                if (esAdministrador) {
                        if (request.vecinoVerificadorId() == null) {
                                throw new DatosNoValidosException("El administrador debe indicar un vecino verificador");
                        }
                        verificador = usuarioRepository.findById(request.vecinoVerificadorId())
                                        .orElseThrow(() -> new DatosNoValidosException("El vecino verificador no existe"));
                        if (verificador.getEstado() != EstadoUsuario.ACTIVO
                                        || !"USUARIO".equals(verificador.getRol().getNombre())
                                        || !usuarioComunidadRepository.existsByUsuario_IdAndComunidad_IdAndEstado(
                                                        verificador.getId(), comunidadId, EstadoUsuario.ACTIVO)) {
                                throw new DatosNoValidosException(
                                                "El verificador debe ser un vecino activo con pertenencia activa a esta comunidad");
                        }
                } else if (request.vecinoVerificadorId() != null
                                && !usuario.getId().equals(request.vecinoVerificadorId())) {
                        // Un vecino no puede utilizar el nuevo campo para delegar su confirmación.
                        throw new DatosNoValidosException("El vecino creador debe ser el verificador inicial");
                }

                Incidencia incidencia = new Incidencia();
                incidencia.setTitulo(request.titulo().strip());
                incidencia.setDescripcion(request.descripcion().strip());
                incidencia.setPrioridad(request.prioridad());
                incidencia.setEstado(EstadoIncidencia.PENDIENTE);
                incidencia.setEspacio(espacio);
                incidencia.setUsuarioReporta(usuario);
                incidencia.setVecinoVerificador(verificador);

                incidenciaRepository.saveAndFlush(incidencia);

                // Recupera la fecha de creación generada por MySQL.
                entityManager.refresh(incidencia);

                HistorialIncidencia evento = new HistorialIncidencia();
                evento.setIncidencia(incidencia);
                evento.setUsuarioActor(usuario);
                evento.setAccion("CREACION");
                evento.setEstadoAnterior(null);
                evento.setEstadoNuevo(EstadoIncidencia.PENDIENTE.name());
                // El actor siempre es quien inicia sesión, incluso si el verificador es otra persona.
                evento.setDescripcion(esAdministrador
                                ? "El administrador registra la incidencia."
                                : "El vecino registra la incidencia.");
                evento.setVerificadorNuevo(verificador);

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
                                verificador.getId(),
                                null);
        }

        // Esta operación solo consulta. La transacción permite leer las relaciones JPA del DTO.
        @Transactional(readOnly = true)
        public List<IncidenciaResponse> listarMisIncidencias(
                        Integer usuarioAutenticadoId) {

                Usuario usuario = usuarioRepository.findById(usuarioAutenticadoId)
                                .orElseThrow(() -> new RecursoNoEncontradoException("Usuario no encontrado"));

                if (usuario.getEstado() != EstadoUsuario.ACTIVO) {
                        throw new OperacionNoPermitidaException(
                                        "La cuenta de usuario debe estar activa");
                }

                // El administrador consulta los casos de sus comunidades con la misma pertenencia activa.
                if (!"USUARIO".equals(usuario.getRol().getNombre())
                                && !"ADMINISTRADOR".equals(usuario.getRol().getNombre())) {
                        throw new OperacionNoPermitidaException(
                                        "Esta consulta está habilitada para vecinos y administradores");
                }

                var comunidadesActivas = usuarioComunidadRepository
                                .findByUsuario_IdAndEstado(
                                                usuario.getId(),
                                                EstadoUsuario.ACTIVO)
                                .stream()
                                .map(pertenencia -> pertenencia.getComunidad().getId())
                                .collect(java.util.stream.Collectors.toSet());

                // Sin pertenencias activas no hay incidencias visibles; evitamos una consulta IN vacía.
                if (comunidadesActivas.isEmpty()) {
                        return List.of();
                }

                // La visibilidad depende de la comunidad, aunque el aviso lo haya creado otro vecino.
                // Se conserva el nombre del método para mantener los controladores existentes.
                var incidencias = incidenciaRepository
                                .findByEspacio_Comunidad_IdInOrderByFechaCreacionDescIdDesc(comunidadesActivas);

                return incidencias.stream()
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

        // La escritura tiene su propia autorización: consultar por comunidad
        // nunca basta para modificar un aviso creado por otro vecino.
        @Transactional
        public void editarDatos(Integer incidenciaId, Integer usuarioId, EditarIncidenciaRequest request) {
                Usuario actor = usuarioRepository.findById(usuarioId)
                                .orElseThrow(() -> new OperacionNoPermitidaException(
                                                "La cuenta de la sesión ya no está disponible"));
                if (actor.getEstado() != EstadoUsuario.ACTIVO) {
                        throw new OperacionNoPermitidaException("La cuenta de usuario debe estar activa");
                }

                String rol = actor.getRol().getNombre();
                if (!"USUARIO".equals(rol) && !"ADMINISTRADOR".equals(rol)) {
                        throw new OperacionNoPermitidaException("Tu rol no permite editar los datos de la incidencia");
                }

                Incidencia incidencia = incidenciaRepository.findByIdParaActualizar(incidenciaId)
                                .orElseThrow(() -> new RecursoNoEncontradoException("Incidencia no encontrada"));
                boolean pertenece = usuarioComunidadRepository.existsByUsuario_IdAndComunidad_IdAndEstado(
                                actor.getId(), incidencia.getEspacio().getComunidad().getId(), EstadoUsuario.ACTIVO);
                boolean esAutor = actor.getId().equals(incidencia.getUsuarioReporta().getId());
                // También el administrador necesita pertenencia activa a esta comunidad.
                if (!pertenece || (!esAutor && !"ADMINISTRADOR".equals(rol))) {
                        throw new OperacionNoPermitidaException("No tienes permiso para modificar esta incidencia");
                }

                String titulo = request.titulo().strip();
                String descripcion = request.descripcion().strip();
                // Repetir la misma petición no genera eventos de edición vacíos.
                if (titulo.equals(incidencia.getTitulo()) && descripcion.equals(incidencia.getDescripcion())
                                && request.prioridad() == incidencia.getPrioridad()) {
                        return;
                }

                HistorialIncidencia evento = new HistorialIncidencia();
                evento.setIncidencia(incidencia);
                evento.setUsuarioActor(actor);
                evento.setAccion("EDICION_DATOS");
                evento.setEstadoAnterior(incidencia.getEstado().name());
                evento.setEstadoNuevo(incidencia.getEstado().name());
                // Guardamos antes y después para poder explicar la modificación en el historial.
                evento.setDescripcion("Título anterior: " + incidencia.getTitulo()
                                + "\nTítulo nuevo: " + titulo
                                + "\nDescripción anterior: " + incidencia.getDescripcion()
                                + "\nDescripción nueva: " + descripcion
                                + "\nPrioridad anterior: " + incidencia.getPrioridad()
                                + "\nPrioridad nueva: " + request.prioridad());
                incidencia.setTitulo(titulo);
                incidencia.setDescripcion(descripcion);
                incidencia.setPrioridad(request.prioridad());
                // La transacción guarda los datos y el historial juntos o revierte ambos si falla.
                incidenciaRepository.save(incidencia);
                historialRepository.save(evento);
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

                // El administrador consulta los casos de sus comunidades con la misma pertenencia activa.
                if (!"USUARIO".equals(usuario.getRol().getNombre())
                                && !"ADMINISTRADOR".equals(usuario.getRol().getNombre())) {
                        throw new OperacionNoPermitidaException(
                                        "Esta consulta está habilitada para vecinos y administradores");
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

                // Detalle e historial comparten visibilidad para todos los vecinos activos
                // de la comunidad. Este permiso es de lectura: futuras escrituras deben
                // comprobar por separado la autoría, el rol y la acción solicitada.
                if (!pertenece) {
                        throw new OperacionNoPermitidaException(
                                        "No tienes permiso para consultar esta incidencia");
                }

                return incidencia;
        }

}
