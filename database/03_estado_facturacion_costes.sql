USE gestion_espacios;

-- Ejecutar una sola vez, después de 02_integridad_evidencias.sql.
-- El estado anterior describía pagos. No permite deducir si existe una factura.
-- Por eso solo migramos automáticamente una tabla vacía. Si contiene registros,
-- se conserva intacta y se requiere revisar su facturación antes de migrarlos.
DELIMITER $$
CREATE PROCEDURE migrar_estado_facturacion_costes()
BEGIN
    IF EXISTS (SELECT 1 FROM costes LIMIT 1) THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'Costes contiene registros: revisar su facturacion antes de migrar. No se han convertido los estados.';
    ELSE
        ALTER TABLE costes
            CHANGE COLUMN estado_pago estado_facturacion
            ENUM('PENDIENTE_FACTURAR', 'FACTURADO')
            NOT NULL DEFAULT 'PENDIENTE_FACTURAR';
    END IF;
END$$
DELIMITER ;

CALL migrar_estado_facturacion_costes();
DROP PROCEDURE migrar_estado_facturacion_costes;
