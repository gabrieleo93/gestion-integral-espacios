package com.gestioncomunidad.backend.dto;

public record EspacioResponse(Integer id,
    String nombre,
    String tipo,
    Integer comunidadId,
    String comunidadNombre) {

}
