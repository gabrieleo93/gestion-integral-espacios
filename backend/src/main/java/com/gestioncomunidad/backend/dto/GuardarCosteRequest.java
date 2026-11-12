package com.gestioncomunidad.backend.dto;

import java.math.BigDecimal;
import jakarta.validation.constraints.*;

// Sirve para alta y edición; estado de facturación y actor nunca se aceptan desde el cliente.
public record GuardarCosteRequest(@NotBlank @Size(max = 150) String concepto,
        @NotNull @DecimalMin("0.00") @Digits(integer = 8, fraction = 2) BigDecimal importe) { }
