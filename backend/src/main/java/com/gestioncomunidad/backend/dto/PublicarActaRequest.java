package com.gestioncomunidad.backend.dto;

import jakarta.validation.constraints.*;

// Texto plano: el frontend debe mostrarlo como texto, no interpretarlo como HTML.
public record PublicarActaRequest(@NotBlank @Size(max = 15000) String contenido) { }
