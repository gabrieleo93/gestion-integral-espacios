package com.gestioncomunidad.backend.controller;

import java.util.List;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import com.gestioncomunidad.backend.dto.*;
import com.gestioncomunidad.backend.exception.OperacionNoPermitidaException;
import com.gestioncomunidad.backend.repositories.UsuarioRepository;
import com.gestioncomunidad.backend.service.TrabajoTecnicoService;

@RestController
@RequestMapping("/api/tecnico/trabajos")
public class TrabajoTecnicoController {
    private final TrabajoTecnicoService servicio;
    private final UsuarioRepository usuarios;
    public TrabajoTecnicoController(TrabajoTecnicoService servicio, UsuarioRepository usuarios) {
        this.servicio = servicio;
        this.usuarios = usuarios;
    }
    // Ninguna ruta acepta un actor enviado por el cliente: se obtiene de la sesión autenticada.
    private Integer actor(Authentication authentication) {
        return usuarios.findByEmail(authentication.getName()).orElseThrow(() ->
                new OperacionNoPermitidaException("La cuenta de la sesión ya no está disponible")).getId();
    }
    @GetMapping
    public List<TrabajoTecnicoResponse> listar(Authentication auth) { return servicio.listar(actor(auth)); }
    @GetMapping("/{id}")
    public TrabajoTecnicoResponse detalle(@PathVariable("id") Integer id, Authentication auth) {
        return servicio.detalle(id, actor(auth));
    }
    @GetMapping("/{id}/historial")
    public List<HistorialIncidenciaResponse> historial(@PathVariable("id") Integer id, Authentication auth) {
        return servicio.historial(id, actor(auth));
    }
    // Las escrituras quedan protegidas por la sesión y CSRF ya configurados.
    @PutMapping("/{id}/inicio")
    public ResponseEntity<Void> iniciar(@PathVariable("id") Integer id, Authentication auth) {
        servicio.iniciar(id, actor(auth)); return ResponseEntity.noContent().build();
    }
    @PostMapping("/{id}/avances")
    public ResponseEntity<Void> avanzar(@PathVariable("id") Integer id,
            @Valid @RequestBody ActuacionTecnicaRequest request, Authentication auth) {
        servicio.avanzar(id, actor(auth), request); return ResponseEntity.noContent().build();
    }
    @PostMapping("/{id}/finalizacion")
    public ResponseEntity<Void> finalizar(@PathVariable("id") Integer id,
            @Valid @RequestBody ActuacionTecnicaRequest request, Authentication auth) {
        servicio.finalizar(id, actor(auth), request); return ResponseEntity.noContent().build();
    }
}
