package com.gestioncomunidad.backend.controller;

import java.util.List;
import java.net.URI;
import org.springframework.http.*;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import com.gestioncomunidad.backend.dto.EvidenciaResponse;
import com.gestioncomunidad.backend.entity.TipoEvidencia;
import com.gestioncomunidad.backend.exception.OperacionNoPermitidaException;
import com.gestioncomunidad.backend.repositories.UsuarioRepository;
import com.gestioncomunidad.backend.service.EvidenciaService;

@RestController
@RequestMapping("/api/incidencias/{id}/evidencias")
public class EvidenciaController {
    private final EvidenciaService servicio;
    private final UsuarioRepository usuarios;
    public EvidenciaController(EvidenciaService servicio, UsuarioRepository usuarios) { this.servicio = servicio; this.usuarios = usuarios; }
    private Integer actor(Authentication auth) {
        return usuarios.findByEmail(auth.getName()).orElseThrow(() -> new OperacionNoPermitidaException("La cuenta no está disponible")).getId();
    }
    @GetMapping
    public List<EvidenciaResponse> listar(@PathVariable("id") Integer id, Authentication auth) { return servicio.listar(id, actor(auth)); }
    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<EvidenciaResponse> subir(@PathVariable("id") Integer id,
            @RequestParam("archivo") MultipartFile archivo, @RequestParam("tipo") TipoEvidencia tipo,
            @RequestParam(value = "descripcion", required = false) String descripcion, Authentication auth) {
        var respuesta = servicio.subir(id, actor(auth), archivo, tipo, descripcion);
        return ResponseEntity.created(URI.create(respuesta.urlArchivo())).body(respuesta);
    }
    @GetMapping("/{evidenciaId}/archivo")
    public ResponseEntity<byte[]> descargar(@PathVariable("id") Integer id, @PathVariable("evidenciaId") Integer evidenciaId, Authentication auth) {
        var archivo = servicio.descargar(id, evidenciaId, actor(auth));
        // Evita cachés compartidas y que el navegador interprete la imagen como contenido de otro tipo.
        return ResponseEntity.ok().contentType(MediaType.parseMediaType(archivo.mime()))
                .header(HttpHeaders.CACHE_CONTROL, "no-store").header("X-Content-Type-Options", "nosniff")
                .body(archivo.contenido());
    }
}
