package com.gestioncomunidad.backend.exception;

import com.gestioncomunidad.backend.dto.ErrorResponse;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.LinkedHashMap;
import java.util.Map;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(RecursoNoEncontradoException.class)
    public ResponseEntity<ErrorResponse> recursoNoEncontrado(
            RecursoNoEncontradoException exception) {

        ErrorResponse respuesta = new ErrorResponse(
            HttpStatus.NOT_FOUND.value(),
            exception.getMessage(),
            Map.of()
        );

        return ResponseEntity
            .status(HttpStatus.NOT_FOUND)
            .body(respuesta);
    }

    @ExceptionHandler(OperacionNoPermitidaException.class)
    public ResponseEntity<ErrorResponse> operacionNoPermitida(
            OperacionNoPermitidaException exception) {

        ErrorResponse respuesta = new ErrorResponse(
            HttpStatus.FORBIDDEN.value(),
            exception.getMessage(),
            Map.of()
        );

        return ResponseEntity
            .status(HttpStatus.FORBIDDEN)
            .body(respuesta);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> datosNoValidos(
            MethodArgumentNotValidException exception) {

        Map<String, String> errores = new LinkedHashMap<>();

        exception.getBindingResult().getFieldErrors().forEach(error -> {
            String mensaje = error.getDefaultMessage();

            errores.putIfAbsent(
                error.getField(),
                mensaje != null ? mensaje : "Valor no válido"
            );
        });

        ErrorResponse respuesta = new ErrorResponse(
            HttpStatus.BAD_REQUEST.value(),
            "Revisa los datos enviados",
            errores
        );

        return ResponseEntity
            .status(HttpStatus.BAD_REQUEST)
            .body(respuesta);
    }
}