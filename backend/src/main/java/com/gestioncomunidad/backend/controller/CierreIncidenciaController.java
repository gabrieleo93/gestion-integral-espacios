package com.gestioncomunidad.backend.controller;

import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import com.gestioncomunidad.backend.dto.*;
import com.gestioncomunidad.backend.exception.OperacionNoPermitidaException;
import com.gestioncomunidad.backend.repositories.UsuarioRepository;
import com.gestioncomunidad.backend.service.CierreIncidenciaService;

@RestController
@RequestMapping("/api/incidencias")
public class CierreIncidenciaController {
    private final CierreIncidenciaService servicio;
    private final UsuarioRepository usuarios;
    public CierreIncidenciaController(CierreIncidenciaService servicio, UsuarioRepository usuarios) {
        this.servicio = servicio;
        this.usuarios = usuarios;
    }
    // Las tres acciones usan sesión y CSRF; el cliente no elige la identidad del actor.
    private Integer actor(Authentication auth) {
        return usuarios.findByEmail(auth.getName()).orElseThrow(() ->
                new OperacionNoPermitidaException("La cuenta de la sesión ya no está disponible")).getId();
    }
    @PostMapping("/{id}/confirmacion")
    public ResponseEntity<Void> confirmar(@PathVariable("id") Integer id,
            @Valid @RequestBody ConfirmarReparacionRequest request, Authentication auth) {
        servicio.confirmar(id, actor(auth), request); return ResponseEntity.noContent().build();
    }
    @PostMapping("/{id}/cierre")
    public ResponseEntity<Void> cerrar(@PathVariable("id") Integer id, Authentication auth) {
        servicio.cerrar(id, actor(auth)); return ResponseEntity.noContent().build();
    }
    @PostMapping("/{id}/reapertura")
    public ResponseEntity<Void> reabrir(@PathVariable("id") Integer id,
            @Valid @RequestBody ReabrirIncidenciaRequest request, Authentication auth) {
        servicio.reabrir(id, actor(auth), request); return ResponseEntity.noContent().build();
    }
}
