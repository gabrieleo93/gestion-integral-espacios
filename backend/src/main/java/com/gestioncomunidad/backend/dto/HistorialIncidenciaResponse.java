package com.gestioncomunidad.backend.dto;

import java.time.LocalDateTime;

public record HistorialIncidenciaResponse(
    Long id,
    String accion,
    String estadoAnterior,
    String estadoNuevo,
    String descripcion,
    LocalDateTime fechaEvento,
    Integer usuarioActorId,
    String usuarioActorNombre
) {
}