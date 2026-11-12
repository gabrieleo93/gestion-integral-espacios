package com.gestioncomunidad.backend.dto;

import java.time.LocalDateTime;
import com.gestioncomunidad.backend.entity.*;

// Resultados agregados y voto propio: no se publica la elección individual de los demás vecinos.
public record VotacionResponse(Integer id, Integer presupuestoId, String titulo,
        LocalDateTime fechaInicio, LocalDateTime fechaFin, EstadoVotacion estado,
        boolean admiteVotos, long aFavor, long enContra, long abstenciones, long totalVotos,
        OpcionVoto miVoto, EstadoPresupuesto estadoPresupuesto) { }
