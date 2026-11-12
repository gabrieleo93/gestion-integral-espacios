USE gestion_espacios;

-- Migración aditiva: conserva las pertenencias existentes y registra los eventos nuevos.
-- Las pertenencias antiguas pendientes también pueden resolverse; no se inventa su historial previo.
CREATE TABLE historial_pertenencias (
    id_historial BIGINT AUTO_INCREMENT PRIMARY KEY,
    id_usuario_comunidad INT NOT NULL,
    id_usuario_actor INT NOT NULL,
    accion VARCHAR(30) NOT NULL,
    estado_anterior VARCHAR(20),
    estado_nuevo VARCHAR(20) NOT NULL,
    motivo VARCHAR(1000) NOT NULL,
    fecha_evento DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    CONSTRAINT fk_hp_pertenencia FOREIGN KEY (id_usuario_comunidad)
        REFERENCES usuarios_comunidades(id_usuario_comunidad),
    CONSTRAINT fk_hp_actor FOREIGN KEY (id_usuario_actor) REFERENCES usuarios(id_usuario),
    INDEX idx_hp_pertenencia_fecha (id_usuario_comunidad, fecha_evento, id_historial)
);
