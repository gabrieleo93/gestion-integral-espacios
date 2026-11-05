package com.gestioncomunidad.backend.dto;

import com.gestioncomunidad.backend.entity.PrioridadIncidencia;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

public record CrearIncidenciaRequest(

    @NotBlank(message = "El título es obligatorio")
    @Size(max = 150, message = "El título no puede superar 150 caracteres")
    String titulo,

    @NotBlank(message = "La descripción es obligatoria")
    @Size(
        max = 5000,
        message = "La descripción no puede superar 5000 caracteres"
    )
    String descripcion,

    @NotNull(message = "La prioridad es obligatoria")
    PrioridadIncidencia prioridad,

    @NotNull(message = "El espacio es obligatorio")
    @Positive(message = "El identificador del espacio debe ser positivo")
    Integer espacioId,

    // El administrador debe elegir un vecino verificador. Para el vecino,
    // el servicio conserva al propio autor como verificador inicial.
    @Positive(message = "El identificador del verificador debe ser positivo")
    Integer vecinoVerificadorId
){
    // Conserva las llamadas Java existentes y el alta del vecino sin este nuevo dato.
    public CrearIncidenciaRequest(String titulo, String descripcion,
            PrioridadIncidencia prioridad, Integer espacioId) {
        this(titulo, descripcion, prioridad, espacioId, null);
    }
}
