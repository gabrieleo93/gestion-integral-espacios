package com.gestioncomunidad.backend.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

// La decisión es obligatoria; el servicio exige motivo cuando el vecino no está conforme.
public record ConfirmarReparacionRequest(@NotNull Boolean conforme,
        @Size(max = 5000) String motivo) {
}
