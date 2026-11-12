package com.gestioncomunidad.backend.dto;

import java.time.LocalDateTime;

public record HistorialPertenenciaResponse(Long id, String accion, String estadoAnterior,
        String estadoNuevo, String motivo, LocalDateTime fechaEvento, Integer actorId, String actorNombre) { }
