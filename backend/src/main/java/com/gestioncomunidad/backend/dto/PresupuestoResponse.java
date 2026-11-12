package com.gestioncomunidad.backend.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import com.gestioncomunidad.backend.entity.EstadoPresupuesto;

public record PresupuestoResponse(Integer id, Integer incidenciaId, String titulo, String descripcion,
        String proveedor, BigDecimal importe, EstadoPresupuesto estado, LocalDateTime fechaCreacion) { }
