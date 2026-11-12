package com.gestioncomunidad.backend.controller;

import java.util.List;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import com.gestioncomunidad.backend.dto.*;
import com.gestioncomunidad.backend.exception.OperacionNoPermitidaException;
import com.gestioncomunidad.backend.repositories.UsuarioRepository;
import com.gestioncomunidad.backend.service.PertenenciaService;

@RestController
@RequestMapping("/api/pertenencias")
public class PertenenciaController {
    private final PertenenciaService servicio;
    private final UsuarioRepository usuarios;
    public PertenenciaController(PertenenciaService servicio, UsuarioRepository usuarios) { this.servicio = servicio; this.usuarios = usuarios; }
    // Solicitante y administrador se identifican con la sesión; las escrituras mantienen CSRF.
    private Integer actor(Authentication auth) {
        return usuarios.findByEmail(auth.getName()).orElseThrow(() -> new OperacionNoPermitidaException("La cuenta no está disponible")).getId();
    }
    @GetMapping("/comunidades")
    public List<ComunidadSolicitudResponse> comunidades(Authentication auth) { return servicio.comunidades(actor(auth)); }
    @GetMapping("/mias")
    public List<PertenenciaResponse> propias(Authentication auth) { return servicio.propias(actor(auth)); }
    @GetMapping("/pendientes")
    public List<PertenenciaResponse> pendientes(Authentication auth) { return servicio.pendientes(actor(auth)); }
    @GetMapping("/{id}/historial")
    public List<HistorialPertenenciaResponse> historial(@PathVariable("id") Integer id, Authentication auth) { return servicio.historial(id, actor(auth)); }
    @PostMapping("/solicitudes")
    public PertenenciaResponse solicitar(@Valid @RequestBody SolicitarPertenenciaRequest request, Authentication auth) {
        // 200 sirve tanto para una solicitud nueva como para devolver una pendiente ya existente.
        return servicio.solicitar(actor(auth), request);
    }
    @PostMapping("/{id}/resolucion")
    public ResponseEntity<Void> resolver(@PathVariable("id") Integer id, @Valid @RequestBody ResolverPertenenciaRequest request, Authentication auth) {
        servicio.resolver(id, actor(auth), request); return ResponseEntity.noContent().build();
    }
}
