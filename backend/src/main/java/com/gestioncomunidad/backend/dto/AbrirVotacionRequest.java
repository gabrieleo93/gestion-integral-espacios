package com.gestioncomunidad.backend.dto;

import java.time.LocalDateTime;
import jakarta.validation.constraints.NotNull;

// La apertura fija el inicio en el servidor; el administrador indica cuándo termina el plazo.
public record AbrirVotacionRequest(@NotNull LocalDateTime fechaFin) { }
