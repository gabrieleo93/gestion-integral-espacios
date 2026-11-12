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

    // Mantiene respuestas JSON también cuando el contenedor rechaza un multipart demasiado grande.
    @ExceptionHandler(org.springframework.web.multipart.MaxUploadSizeExceededException.class)
    public ResponseEntity<ErrorResponse> archivoGrande(Exception exception) {
        return ResponseEntity.status(413).body(new ErrorResponse(413, "La imagen supera el límite de 5 MiB o la petición supera 6 MiB", Map.of()));
    }

    @ExceptionHandler({org.springframework.web.multipart.support.MissingServletRequestPartException.class,
            org.springframework.web.bind.MissingServletRequestParameterException.class,
            org.springframework.web.method.annotation.MethodArgumentTypeMismatchException.class})
    public ResponseEntity<ErrorResponse> parametroInvalido(Exception exception) {
        return ResponseEntity.badRequest().body(new ErrorResponse(400, "Faltan parámetros o tienen un formato inválido", Map.of()));
    }

    @ExceptionHandler(AlmacenamientoException.class)
    public ResponseEntity<ErrorResponse> almacenamiento(AlmacenamientoException exception) {
        org.slf4j.LoggerFactory.getLogger(GlobalExceptionHandler.class).error("Error de almacenamiento de evidencias", exception);
        return ResponseEntity.internalServerError().body(new ErrorResponse(500, "No se pudo completar la operación con el archivo", Map.of()));
    }

    // Mantiene el formato de error de la API para validaciones de negocio.
    @ExceptionHandler(DatosNoValidosException.class)
    public ResponseEntity<ErrorResponse> datosDeNegocioNoValidos(DatosNoValidosException exception) {
        return ResponseEntity.badRequest().body(new ErrorResponse(
                HttpStatus.BAD_REQUEST.value(), exception.getMessage(), Map.of()));
    }

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
