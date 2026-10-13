USE gestion_espacios;

ALTER TABLE historial_incidencias
    ADD CONSTRAINT uk_historial_id_incidencia
    UNIQUE (id_historial, id_incidencia);

ALTER TABLE evidencias
    DROP FOREIGN KEY fk_evidencias_historial,
    ADD CONSTRAINT fk_evidencias_historial_incidencia
        FOREIGN KEY (id_historial, id_incidencia)
        REFERENCES historial_incidencias (
            id_historial,
            id_incidencia
        );