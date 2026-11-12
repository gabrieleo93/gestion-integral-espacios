package com.gestioncomunidad.backend.dto;

import java.time.LocalDateTime;

public record ReunionResponse(Integer id, Integer comunidadId, String titulo, String descripcion,
        LocalDateTime fechaReunion, String lugar, LocalDateTime fechaCreacion, Integer creadorId) { }
