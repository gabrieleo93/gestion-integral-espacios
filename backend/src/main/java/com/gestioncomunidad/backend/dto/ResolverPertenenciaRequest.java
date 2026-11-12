package com.gestioncomunidad.backend.dto;

import jakarta.validation.constraints.*;

// El servicio exige motivo si se rechaza; aprobar admite un comentario opcional.
public record ResolverPertenenciaRequest(@NotNull Boolean aprobar,
        @Size(max = 1000) String motivo) { }
