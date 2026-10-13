package com.gestioncomunidad.backend.dto;

import com.gestioncomunidad.backend.entity.EstadoIncidencia;
import com.gestioncomunidad.backend.entity.PrioridadIncidencia;

import java.time.LocalDateTime;

public record IncidenciaResponse(
    Integer id,
    String titulo,
    String descripcion,
    PrioridadIncidencia prioridad,
    EstadoIncidencia estado,
    LocalDateTime fechaCreacion,
    Integer espacioId,
    Integer comunidadId,
    Integer usuarioReportaId,
    Integer vecinoVerificadorId,
    Integer tecnicoAsignadoId
) {
}