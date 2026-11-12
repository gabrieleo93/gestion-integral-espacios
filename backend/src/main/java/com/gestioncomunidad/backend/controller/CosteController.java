package com.gestioncomunidad.backend.controller;

import java.util.List;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import com.gestioncomunidad.backend.dto.*;
import com.gestioncomunidad.backend.exception.OperacionNoPermitidaException;
import com.gestioncomunidad.backend.repositories.UsuarioRepository;
import com.gestioncomunidad.backend.service.CosteService;

@RestController
@RequestMapping("/api")
public class CosteController {
    private final CosteService servicio;
    private final UsuarioRepository usuarios;
    public CosteController(CosteService servicio, UsuarioRepository usuarios) { this.servicio = servicio; this.usuarios = usuarios; }
    // El administrador procede de la sesión, no del JSON enviado por el cliente.
    private Integer actor(Authentication auth) {
        return usuarios.findByEmail(auth.getName()).orElseThrow(() -> new OperacionNoPermitidaException("La cuenta no está disponible")).getId();
    }
    @GetMapping("/incidencias/{id}/costes")
    public List<CosteResponse> listar(@PathVariable("id") Integer id, Authentication auth) { return servicio.listar(id, actor(auth)); }
    @GetMapping("/incidencias/{id}/costes/resumen")
    public ResumenCostesResponse resumen(@PathVariable("id") Integer id, Authentication auth) { return servicio.resumen(id, actor(auth)); }
    @PostMapping("/incidencias/{id}/costes")
    public ResponseEntity<CosteResponse> crear(@PathVariable("id") Integer id, @Valid @RequestBody GuardarCosteRequest request, Authentication auth) {
        return ResponseEntity.status(201).body(servicio.crear(id, actor(auth), request));
    }
    @PutMapping("/costes/{id}")
    public ResponseEntity<Void> editar(@PathVariable("id") Integer id, @Valid @RequestBody GuardarCosteRequest request, Authentication auth) {
        servicio.editar(id, actor(auth), request); return ResponseEntity.noContent().build();
    }
    @PostMapping("/costes/{id}/facturacion")
    public ResponseEntity<Void> facturar(@PathVariable("id") Integer id, Authentication auth) {
        servicio.facturar(id, actor(auth)); return ResponseEntity.noContent().build();
    }
}
