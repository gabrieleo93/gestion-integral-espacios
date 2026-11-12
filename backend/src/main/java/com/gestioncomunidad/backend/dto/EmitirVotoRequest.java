package com.gestioncomunidad.backend.dto;

import jakarta.validation.constraints.NotNull;
import com.gestioncomunidad.backend.entity.OpcionVoto;

// El votante se obtiene de la sesión, nunca del cuerpo de la petición.
public record EmitirVotoRequest(@NotNull OpcionVoto opcion) { }
