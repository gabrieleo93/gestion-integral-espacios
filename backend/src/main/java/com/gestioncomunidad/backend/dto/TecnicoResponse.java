package com.gestioncomunidad.backend.dto;

// El selector necesita identificador y nombre, sin exponer correo ni datos de acceso.
public record TecnicoResponse(Integer id, String nombre) {
}
