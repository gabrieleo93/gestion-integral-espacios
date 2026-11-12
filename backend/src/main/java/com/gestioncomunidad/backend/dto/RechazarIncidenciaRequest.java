package com.gestioncomunidad.backend.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

// El rechazo debe quedar justificado en el historial visible para la comunidad.
public record RechazarIncidenciaRequest(@NotBlank @Size(max = 5000) String motivo) {
}
