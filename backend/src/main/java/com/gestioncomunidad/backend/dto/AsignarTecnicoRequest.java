package com.gestioncomunidad.backend.dto;

import java.time.LocalDateTime;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

// Solo se reciben los datos de asignación; el administrador procede de la sesión.
public record AsignarTecnicoRequest(
        @NotNull @Positive Integer tecnicoId,
        @NotNull LocalDateTime fechaPrevista) {
}
