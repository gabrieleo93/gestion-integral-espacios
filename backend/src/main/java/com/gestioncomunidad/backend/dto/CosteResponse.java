package com.gestioncomunidad.backend.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import com.gestioncomunidad.backend.entity.EstadoFacturacion;

public record CosteResponse(Integer id, Integer incidenciaId, String concepto, BigDecimal importe,
        EstadoFacturacion estadoFacturacion, LocalDateTime fechaRegistro) { }
