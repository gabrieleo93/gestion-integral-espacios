package com.gestioncomunidad.backend.exception;

// Distingue un fallo del disco de una imagen inválida, sin revelar rutas al cliente.
public class AlmacenamientoException extends RuntimeException {
    public AlmacenamientoException(String mensaje, Throwable causa) { super(mensaje, causa); }
}
