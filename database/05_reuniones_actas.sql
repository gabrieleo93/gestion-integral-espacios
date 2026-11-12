USE gestion_espacios;

-- Migración aditiva, ejecutar una sola vez con una cuenta que pueda crear tablas.
CREATE TABLE reuniones (
    id_reunion INT AUTO_INCREMENT PRIMARY KEY,
    titulo VARCHAR(150) NOT NULL,
    descripcion TEXT,
    fecha_reunion DATETIME NOT NULL,
    lugar VARCHAR(255) NOT NULL,
    fecha_creacion DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    id_comunidad INT NOT NULL,
    id_usuario_creador INT NOT NULL,
    CONSTRAINT fk_reunion_comunidad FOREIGN KEY (id_comunidad) REFERENCES comunidades(id_comunidad),
    CONSTRAINT fk_reunion_creador FOREIGN KEY (id_usuario_creador) REFERENCES usuarios(id_usuario),
    INDEX idx_reunion_comunidad_fecha (id_comunidad, fecha_reunion, id_reunion)
);

-- Un acta publicada por reunión: no se sustituye ni se borra mediante la API.
CREATE TABLE actas (
    id_acta INT AUTO_INCREMENT PRIMARY KEY,
    contenido TEXT NOT NULL,
    fecha_publicacion DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    id_reunion INT NOT NULL,
    id_usuario_publicador INT NOT NULL,
    CONSTRAINT uk_acta_reunion UNIQUE (id_reunion),
    CONSTRAINT fk_acta_reunion FOREIGN KEY (id_reunion) REFERENCES reuniones(id_reunion),
    CONSTRAINT fk_acta_publicador FOREIGN KEY (id_usuario_publicador) REFERENCES usuarios(id_usuario)
);
