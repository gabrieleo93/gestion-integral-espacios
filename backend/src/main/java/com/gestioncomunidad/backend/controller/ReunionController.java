package com.gestioncomunidad.backend.controller;

import java.net.URI;
import java.util.List;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import com.gestioncomunidad.backend.dto.*;
import com.gestioncomunidad.backend.exception.OperacionNoPermitidaException;
import com.gestioncomunidad.backend.repositories.UsuarioRepository;
import com.gestioncomunidad.backend.service.ReunionService;

@RestController
@RequestMapping("/api")
public class ReunionController {
    private final ReunionService servicio;
    private final UsuarioRepository usuarios;
    public ReunionController(ReunionService servicio, UsuarioRepository usuarios) { this.servicio = servicio; this.usuarios = usuarios; }
    // La identidad procede de la sesión y las publicaciones requieren el CSRF existente.
    private Integer actor(Authentication auth) {
        return usuarios.findByEmail(auth.getName()).orElseThrow(() -> new OperacionNoPermitidaException("La cuenta no está disponible")).getId();
    }
    @GetMapping("/comunidades/{id}/reuniones")
    public List<ReunionResponse> listar(@PathVariable("id") Integer id, Authentication auth) { return servicio.listar(id, actor(auth)); }
    @PostMapping("/comunidades/{id}/reuniones")
    public ResponseEntity<ReunionResponse> crear(@PathVariable("id") Integer id, @Valid @RequestBody CrearReunionRequest request, Authentication auth) {
        var r = servicio.crear(id, actor(auth), request);
        return ResponseEntity.created(URI.create("/api/reuniones/" + r.id())).body(r);
    }
    @GetMapping("/reuniones/{id}")
    public ReunionResponse detalle(@PathVariable("id") Integer id, Authentication auth) { return servicio.detalle(id, actor(auth)); }
    @GetMapping("/reuniones/{id}/acta")
    public ActaResponse acta(@PathVariable("id") Integer id, Authentication auth) { return servicio.acta(id, actor(auth)); }
    @PostMapping("/reuniones/{id}/acta")
    public ResponseEntity<ActaResponse> publicar(@PathVariable("id") Integer id, @Valid @RequestBody PublicarActaRequest request, Authentication auth) {
        return ResponseEntity.created(URI.create("/api/reuniones/" + id + "/acta")).body(servicio.publicar(id, actor(auth), request));
    }
}
