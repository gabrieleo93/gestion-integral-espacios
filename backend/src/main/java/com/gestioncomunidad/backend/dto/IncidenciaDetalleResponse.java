package com.gestioncomunidad.backend.dto;

import java.time.LocalDateTime;

import com.gestioncomunidad.backend.entity.EstadoIncidencia;
import com.gestioncomunidad.backend.entity.PrioridadIncidencia;

public record IncidenciaDetalleResponse(
    Integer id,
    String titulo,
    String descripcion,
    PrioridadIncidencia prioridad,
    EstadoIncidencia estado,
    LocalDateTime fechaCreacion,
    LocalDateTime fechaPrevista,
    Integer espacioId,
    String espacioNombre,
    Integer comunidadId,
    String comunidadNombre,
    Integer usuarioReportaId,
    String usuarioReportaNombre,
    Integer vecinoVerificadorId,
    String vecinoVerificadorNombre,
    Integer tecnicoAsignadoId,
    String tecnicoAsignadoNombre
) {
}