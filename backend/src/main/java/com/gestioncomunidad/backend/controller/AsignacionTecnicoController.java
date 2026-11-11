package com.gestioncomunidad.backend.controller;

import java.util.List;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import com.gestioncomunidad.backend.dto.*;
import com.gestioncomunidad.backend.exception.OperacionNoPermitidaException;
import com.gestioncomunidad.backend.repositories.UsuarioRepository;
import com.gestioncomunidad.backend.service.AsignacionTecnicoService;

@RestController
@RequestMapping("/api/incidencias")
public class AsignacionTecnicoController {
    private final AsignacionTecnicoService servicio;
    private final UsuarioRepository usuarios;
    public AsignacionTecnicoController(AsignacionTecnicoService servicio, UsuarioRepository usuarios) {
        this.servicio = servicio;
        this.usuarios = usuarios;
    }
    // La identidad siempre procede de la sesión, nunca del cuerpo enviado por el cliente.
    private Integer actor(Authentication authentication) {
        return usuarios.findByEmail(authentication.getName()).orElseThrow(() ->
                new OperacionNoPermitidaException("La cuenta de la sesión ya no está disponible")).getId();
    }
    @GetMapping("/{id}/tecnicos-disponibles")
    public List<TecnicoResponse> candidatos(@PathVariable("id") Integer id, Authentication authentication) {
        return servicio.candidatos(id, actor(authentication));
    }
    // La configuración de seguridad existente exige sesión y CSRF para este PUT.
    @PutMapping("/{id}/asignacion")
    public ResponseEntity<Void> asignar(@PathVariable("id") Integer id,
            @Valid @RequestBody AsignarTecnicoRequest request, Authentication authentication) {
        servicio.asignar(id, actor(authentication), request);
        return ResponseEntity.noContent().build();
    }
}
