package com.gestioncomunidad.backend.dto;

import java.time.LocalDateTime;
import jakarta.validation.constraints.*;

// La comunidad procede de la ruta; no se permite elegir un creador distinto de la sesión.
public record CrearReunionRequest(@NotBlank @Size(max = 150) String titulo,
        @Size(max = 5000) String descripcion, @NotNull LocalDateTime fechaReunion,
        @NotBlank @Size(max = 255) String lugar) { }
