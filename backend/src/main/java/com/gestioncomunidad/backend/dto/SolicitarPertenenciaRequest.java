package com.gestioncomunidad.backend.dto;

import jakarta.validation.constraints.*;

// El cliente elige comunidad y explica su solicitud; nunca puede indicar usuario, rol o estado.
public record SolicitarPertenenciaRequest(@NotNull @Positive Integer comunidadId,
        @NotBlank @Size(max = 1000) String motivo) { }
