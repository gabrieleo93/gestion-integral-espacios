package com.gestioncomunidad.backend.dto;

import java.time.LocalDateTime;
import com.gestioncomunidad.backend.entity.TipoEvidencia;

// La URL apunta a una ruta autenticada, nunca a una ubicación física del servidor.
public record EvidenciaResponse(Integer id, Integer incidenciaId, String nombreOriginal,
        String tipoMime, String descripcion, TipoEvidencia tipo, LocalDateTime fechaSubida,
        Integer usuarioSubidaId, Long historialId, String urlArchivo) {
}
