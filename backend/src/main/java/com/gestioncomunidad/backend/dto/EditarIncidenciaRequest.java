package com.gestioncomunidad.backend.dto;

import com.gestioncomunidad.backend.entity.PrioridadIncidencia;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

// La edición de datos generales solo acepta estos campos. Estado, comunidad,
// autor y técnico se gestionarán mediante sus operaciones y permisos específicos.
public record EditarIncidenciaRequest(
        @NotBlank(message = "El título es obligatorio")
        @Size(max = 150, message = "El título no puede superar 150 caracteres")
        String titulo,
        @NotBlank(message = "La descripción es obligatoria")
        @Size(max = 5000, message = "La descripción no puede superar 5000 caracteres")
        String descripcion,
        @NotNull(message = "La prioridad es obligatoria")
        PrioridadIncidencia prioridad) {
}
