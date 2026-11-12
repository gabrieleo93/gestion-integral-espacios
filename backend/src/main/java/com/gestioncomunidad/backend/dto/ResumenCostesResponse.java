package com.gestioncomunidad.backend.dto;

import java.math.BigDecimal;

// Los importes usan decimal exacto para que total = pendiente + facturado sin redondeos binarios.
public record ResumenCostesResponse(Integer incidenciaId, BigDecimal total,
        BigDecimal pendienteFacturar, BigDecimal facturado) { }
