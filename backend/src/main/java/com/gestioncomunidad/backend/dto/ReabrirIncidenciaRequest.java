package com.gestioncomunidad.backend.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

// Reabrir inicia otro ciclo de trabajo: el motivo permite justificarlo en el historial.
public record ReabrirIncidenciaRequest(@NotBlank @Size(max = 5000) String motivo) {
}
