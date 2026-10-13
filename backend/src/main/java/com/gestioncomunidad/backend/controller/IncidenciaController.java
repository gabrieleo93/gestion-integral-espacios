package com.gestioncomunidad.backend.controller;

import com.gestioncomunidad.backend.dto.CrearIncidenciaRequest;
import com.gestioncomunidad.backend.dto.IncidenciaResponse;
import com.gestioncomunidad.backend.exception.OperacionNoPermitidaException;
import com.gestioncomunidad.backend.repositories.UsuarioRepository;
import com.gestioncomunidad.backend.service.IncidenciaService;

import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/incidencias")
public class IncidenciaController {

    private final IncidenciaService incidenciaService;
    private final UsuarioRepository usuarioRepository;

    public IncidenciaController(
            IncidenciaService incidenciaService,
            UsuarioRepository usuarioRepository) {

        this.incidenciaService = incidenciaService;
        this.usuarioRepository = usuarioRepository;
    }

    @PostMapping
    public ResponseEntity<IncidenciaResponse> crear(
            @Valid @RequestBody CrearIncidenciaRequest request,
            Authentication authentication) {

        Integer usuarioId = usuarioRepository
                .findByEmail(authentication.getName())
                .orElseThrow(() -> new OperacionNoPermitidaException(
                        "La cuenta de la sesión ya no está disponible"))
                .getId();

        IncidenciaResponse respuesta = incidenciaService.crear(request, usuarioId);

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(respuesta);
    }

    @GetMapping
    public List<IncidenciaResponse> listarMisIncidencias(
            Authentication authentication) {

        Integer usuarioId = usuarioRepository
                .findByEmail(authentication.getName())
                .orElseThrow(() -> new OperacionNoPermitidaException(
                        "La cuenta de la sesión ya no está disponible"))
                .getId();

        return incidenciaService.listarMisIncidencias(usuarioId);
    }
}