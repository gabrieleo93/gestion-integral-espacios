package com.gestioncomunidad.backend.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

// La referencia identifica la incidencia cuyo seguimiento sustituye al aviso duplicado.
public record MarcarDuplicadaRequest(@NotNull @Positive Integer incidenciaOriginalId,
        @NotBlank @Size(max = 5000) String motivo) {
}
