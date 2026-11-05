package com.gestioncomunidad.backend.exception;

// Validación de datos que depende del rol autenticado y no solo del DTO.
public class DatosNoValidosException extends RuntimeException {
    public DatosNoValidosException(String mensaje) {
        super(mensaje);
    }
}
