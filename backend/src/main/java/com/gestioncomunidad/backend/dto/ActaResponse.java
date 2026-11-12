package com.gestioncomunidad.backend.dto;

import java.time.LocalDateTime;

public record ActaResponse(Integer id, Integer reunionId, String contenido,
        LocalDateTime fechaPublicacion, Integer publicadorId, String publicadorNombre) { }
