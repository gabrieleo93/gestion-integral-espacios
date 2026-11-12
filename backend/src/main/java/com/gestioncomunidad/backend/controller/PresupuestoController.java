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
import com.gestioncomunidad.backend.service.PresupuestoService;

@RestController
@RequestMapping("/api")
public class PresupuestoController {
    private final PresupuestoService servicio;
    private final UsuarioRepository usuarios;
    public PresupuestoController(PresupuestoService servicio, UsuarioRepository usuarios) { this.servicio = servicio; this.usuarios = usuarios; }
    // Todos los actores proceden de la sesión; las escrituras conservan CSRF.
    private Integer actor(Authentication auth) {
        return usuarios.findByEmail(auth.getName()).orElseThrow(() -> new OperacionNoPermitidaException("La cuenta no está disponible")).getId();
    }
    @GetMapping("/incidencias/{id}/presupuestos")
    public List<PresupuestoResponse> listar(@PathVariable("id") Integer id, Authentication auth) { return servicio.listar(id, actor(auth)); }
    @PostMapping("/incidencias/{id}/presupuestos")
    public ResponseEntity<PresupuestoResponse> crear(@PathVariable("id") Integer id,
            @Valid @RequestBody CrearPresupuestoRequest request, Authentication auth) {
        return ResponseEntity.status(201).body(servicio.crear(id, actor(auth), request));
    }
    @PostMapping("/presupuestos/{id}/votacion")
    public ResponseEntity<VotacionResponse> abrir(@PathVariable("id") Integer id,
            @Valid @RequestBody AbrirVotacionRequest request, Authentication auth) {
        return ResponseEntity.created(URI.create("/api/presupuestos/" + id + "/votacion")).body(servicio.abrir(id, actor(auth), request));
    }
    @GetMapping("/presupuestos/{id}/votacion")
    public VotacionResponse consultar(@PathVariable("id") Integer id, Authentication auth) { return servicio.consultar(id, actor(auth)); }
    @PostMapping("/votaciones/{id}/votos")
    public ResponseEntity<Void> votar(@PathVariable("id") Integer id, @Valid @RequestBody EmitirVotoRequest request, Authentication auth) {
        servicio.votar(id, actor(auth), request); return ResponseEntity.noContent().build();
    }
    @PostMapping("/votaciones/{id}/cierre")
    public VotacionResponse cerrar(@PathVariable("id") Integer id, Authentication auth) { return servicio.cerrar(id, actor(auth)); }
}
