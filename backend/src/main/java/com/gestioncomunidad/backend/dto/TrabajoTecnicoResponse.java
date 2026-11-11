package com.gestioncomunidad.backend.dto;

import java.time.LocalDateTime;
import com.gestioncomunidad.backend.entity.EstadoIncidencia;
import com.gestioncomunidad.backend.entity.PrioridadIncidencia;

// Expone datos del trabajo y sus fechas sin revelar datos personales de los vecinos.
public record TrabajoTecnicoResponse(Integer id, String titulo, String descripcion,
        PrioridadIncidencia prioridad, EstadoIncidencia estado,
        Integer espacioId, String espacioNombre, Integer comunidadId, String comunidadNombre,
        LocalDateTime fechaAsignacion, LocalDateTime fechaPrevista,
        LocalDateTime fechaInicio, LocalDateTime fechaReparacion) {
}
