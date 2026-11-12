package com.gestioncomunidad.backend.dto;

import java.time.LocalDateTime;
import com.gestioncomunidad.backend.entity.EstadoUsuario;

// fechaRegistro es la creación inicial de la pertenencia; cada nueva solicitud tiene fecha en el historial.
public record PertenenciaResponse(Integer id, Integer usuarioId, String usuarioNombre,
        Integer comunidadId, String comunidadNombre, EstadoUsuario estado, LocalDateTime fechaRegistro) { }
