package com.gestioncomunidad.backend.dto;

// Catálogo mínimo para solicitar acceso: no incluye vecinos, dirección ni incidencias de la comunidad.
public record ComunidadSolicitudResponse(Integer id, String nombre, String ciudad) { }
