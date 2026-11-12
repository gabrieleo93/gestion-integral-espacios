package com.gestioncomunidad.backend.dto;

import java.math.BigDecimal;
import jakarta.validation.constraints.*;

// BigDecimal conserva los céntimos y las validaciones respetan DECIMAL(10,2) de MySQL.
public record CrearPresupuestoRequest(@NotBlank @Size(max = 150) String titulo,
        @Size(max = 5000) String descripcion, @NotBlank @Size(max = 150) String proveedor,
        @NotNull @DecimalMin("0.00") @Digits(integer = 8, fraction = 2) BigDecimal importe) { }
