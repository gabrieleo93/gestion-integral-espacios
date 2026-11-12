package com.gestioncomunidad.backend.controller;

import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import com.gestioncomunidad.backend.dto.*;
import com.gestioncomunidad.backend.exception.OperacionNoPermitidaException;
import com.gestioncomunidad.backend.repositories.UsuarioRepository;
import com.gestioncomunidad.backend.service.RechazoIncidenciaService;

@RestController
@RequestMapping("/api/incidencias")
public class RechazoIncidenciaController {
    private final RechazoIncidenciaService servicio;
    private final UsuarioRepository usuarios;
    public RechazoIncidenciaController(RechazoIncidenciaService servicio, UsuarioRepository usuarios) {
        this.servicio = servicio;
        this.usuarios = usuarios;
    }
    // El administrador procede de la sesión; ambas escrituras exigen CSRF con la seguridad existente.
    private Integer actor(Authentication auth) {
        return usuarios.findByEmail(auth.getName()).orElseThrow(() ->
                new OperacionNoPermitidaException("La cuenta de la sesión ya no está disponible")).getId();
    }
    @PostMapping("/{id}/rechazo")
    public ResponseEntity<Void> rechazar(@PathVariable("id") Integer id,
            @Valid @RequestBody RechazarIncidenciaRequest request, Authentication auth) {
        servicio.rechazar(id, actor(auth), request); return ResponseEntity.noContent().build();
    }
    @PostMapping("/{id}/duplicada")
    public ResponseEntity<Void> duplicada(@PathVariable("id") Integer id,
            @Valid @RequestBody MarcarDuplicadaRequest request, Authentication auth) {
        servicio.duplicada(id, actor(auth), request); return ResponseEntity.noContent().build();
    }
}
