package com.gestioncomunidad.backend.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

// Avances y finalización necesitan una explicación que pueda consultar el vecino.
public record ActuacionTecnicaRequest(
        @NotBlank @Size(max = 5000) String descripcion) {
}
