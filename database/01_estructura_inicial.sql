USE gestion_espacios;

CREATE TABLE roles (
    id_rol INT AUTO_INCREMENT PRIMARY KEY,
    nombre VARCHAR(50) NOT NULL,
    descripcion VARCHAR(255),
    CONSTRAINT uk_roles_nombre UNIQUE (nombre)
);

CREATE TABLE comunidades (
    id_comunidad INT AUTO_INCREMENT PRIMARY KEY,
    nombre VARCHAR(150) NOT NULL,
    direccion VARCHAR(255) NOT NULL,
    ciudad VARCHAR(100) NOT NULL,
    codigo_postal VARCHAR(10),
    descripcion TEXT,
    fecha_alta DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP
);

INSERT INTO roles (nombre, descripcion)
VALUES
    ('ADMINISTRADOR', 'Gestiona las comunidades bajo su responsabilidad'),
    ('USUARIO', 'Vecino que reporta y verifica incidencias'),
    ('TECNICO', 'Realiza los trabajos que tiene asignados');

CREATE TABLE usuarios (
    id_usuario INT AUTO_INCREMENT PRIMARY KEY,
    nombre VARCHAR(100) NOT NULL,
    apellidos VARCHAR(150) NOT NULL,
    email VARCHAR(150) NOT NULL,
    password VARCHAR(255) NOT NULL,
    telefono VARCHAR(20),
    estado ENUM('PENDIENTE', 'ACTIVO', 'INACTIVO')
        NOT NULL DEFAULT 'PENDIENTE',
    fecha_registro DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    id_rol INT NOT NULL,

    CONSTRAINT uk_usuarios_email UNIQUE (email),
    CONSTRAINT fk_usuarios_rol
        FOREIGN KEY (id_rol) REFERENCES roles (id_rol)
);

CREATE TABLE usuarios_comunidades (
    id_usuario_comunidad INT AUTO_INCREMENT PRIMARY KEY,
    id_usuario INT NOT NULL,
    id_comunidad INT NOT NULL,
    estado ENUM('PENDIENTE', 'ACTIVO', 'INACTIVO')
        NOT NULL DEFAULT 'PENDIENTE',
    fecha_asignacion DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT uk_usuario_comunidad
        UNIQUE (id_usuario, id_comunidad),

    CONSTRAINT fk_uc_usuario
        FOREIGN KEY (id_usuario) REFERENCES usuarios (id_usuario),

    CONSTRAINT fk_uc_comunidad
        FOREIGN KEY (id_comunidad) REFERENCES comunidades (id_comunidad)
);    
CREATE TABLE espacios (
    id_espacio INT AUTO_INCREMENT PRIMARY KEY,
    nombre VARCHAR(100) NOT NULL,
    tipo VARCHAR(100) NOT NULL,
    descripcion TEXT,
    codigo_qr VARCHAR(255),
    id_comunidad INT NOT NULL,

    CONSTRAINT uk_espacios_codigo_qr
        UNIQUE (codigo_qr),

    CONSTRAINT fk_espacios_comunidad
        FOREIGN KEY (id_comunidad)
        REFERENCES comunidades (id_comunidad)
);
CREATE TABLE incidencias (
    id_incidencia INT AUTO_INCREMENT PRIMARY KEY,
    titulo VARCHAR(150) NOT NULL,
    descripcion TEXT NOT NULL,

    prioridad ENUM('BAJA', 'MEDIA', 'ALTA')
        NOT NULL DEFAULT 'MEDIA',

    estado ENUM(
        'PENDIENTE',
        'ASIGNADA',
        'EN_PROCESO',
        'PENDIENTE_CONFIRMACION',
        'PENDIENTE_CIERRE',
        'FINALIZADA',
        'RECHAZADA'
    ) NOT NULL DEFAULT 'PENDIENTE',

    fecha_creacion DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    fecha_asignacion DATETIME,
    fecha_prevista DATETIME,
    fecha_inicio DATETIME,
    fecha_reparacion DATETIME,
    fecha_confirmacion DATETIME,
    fecha_cierre DATETIME,

    id_usuario_reporta INT NOT NULL,
    id_vecino_verificador INT NOT NULL,
    id_tecnico_asignado INT,
    id_espacio INT NOT NULL,
    id_incidencia_origen INT,

    CONSTRAINT fk_incidencias_reportante
        FOREIGN KEY (id_usuario_reporta)
        REFERENCES usuarios (id_usuario),

    CONSTRAINT fk_incidencias_verificador
        FOREIGN KEY (id_vecino_verificador)
        REFERENCES usuarios (id_usuario),

    CONSTRAINT fk_incidencias_tecnico
        FOREIGN KEY (id_tecnico_asignado)
        REFERENCES usuarios (id_usuario),

    CONSTRAINT fk_incidencias_espacio
        FOREIGN KEY (id_espacio)
        REFERENCES espacios (id_espacio),

    CONSTRAINT fk_incidencias_origen
        FOREIGN KEY (id_incidencia_origen)
        REFERENCES incidencias (id_incidencia)
);
CREATE TABLE historial_incidencias (
    id_historial BIGINT AUTO_INCREMENT PRIMARY KEY,
    id_incidencia INT NOT NULL,
    id_usuario_actor INT NOT NULL,

    accion VARCHAR(50) NOT NULL,
    estado_anterior VARCHAR(30),
    estado_nuevo VARCHAR(30),
    descripcion TEXT NOT NULL,

    id_tecnico_anterior INT,
    id_tecnico_nuevo INT,
    id_verificador_anterior INT,
    id_verificador_nuevo INT,

    fecha_evento DATETIME(6) NOT NULL
        DEFAULT CURRENT_TIMESTAMP(6),

    CONSTRAINT fk_historial_incidencia
        FOREIGN KEY (id_incidencia)
        REFERENCES incidencias (id_incidencia),

    CONSTRAINT fk_historial_actor
        FOREIGN KEY (id_usuario_actor)
        REFERENCES usuarios (id_usuario),

    CONSTRAINT fk_historial_tecnico_anterior
        FOREIGN KEY (id_tecnico_anterior)
        REFERENCES usuarios (id_usuario),

    CONSTRAINT fk_historial_tecnico_nuevo
        FOREIGN KEY (id_tecnico_nuevo)
        REFERENCES usuarios (id_usuario),

    CONSTRAINT fk_historial_verificador_anterior
        FOREIGN KEY (id_verificador_anterior)
        REFERENCES usuarios (id_usuario),

    CONSTRAINT fk_historial_verificador_nuevo
        FOREIGN KEY (id_verificador_nuevo)
        REFERENCES usuarios (id_usuario),

    INDEX idx_historial_incidencia_fecha (
        id_incidencia,
        fecha_evento,
        id_historial
    )
);
CREATE TABLE evidencias (
    id_evidencia INT AUTO_INCREMENT PRIMARY KEY,
    url_archivo VARCHAR(255) NOT NULL,
    nombre_original VARCHAR(255) NOT NULL,
    tipo_mime VARCHAR(100) NOT NULL,
    descripcion VARCHAR(255),

    tipo ENUM('ANTES', 'DURANTE', 'DESPUES')
        NOT NULL,

    fecha_subida DATETIME(6) NOT NULL
        DEFAULT CURRENT_TIMESTAMP(6),

    id_incidencia INT NOT NULL,
    id_usuario_subida INT NOT NULL,
    id_historial BIGINT NOT NULL,

    CONSTRAINT fk_evidencias_incidencia
        FOREIGN KEY (id_incidencia)
        REFERENCES incidencias (id_incidencia),

    CONSTRAINT fk_evidencias_usuario
        FOREIGN KEY (id_usuario_subida)
        REFERENCES usuarios (id_usuario),

    CONSTRAINT fk_evidencias_historial
        FOREIGN KEY (id_historial)
        REFERENCES historial_incidencias (id_historial)
);
CREATE TABLE presupuestos (
    id_presupuesto INT AUTO_INCREMENT PRIMARY KEY,
    titulo VARCHAR(150) NOT NULL,
    descripcion TEXT,
    proveedor VARCHAR(150) NOT NULL,
    importe DECIMAL(10,2) NOT NULL,

    estado ENUM(
        'PENDIENTE',
        'EN_VOTACION',
        'APROBADO',
        'RECHAZADO'
    ) NOT NULL DEFAULT 'PENDIENTE',

    fecha_creacion DATETIME NOT NULL
        DEFAULT CURRENT_TIMESTAMP,

    id_incidencia INT NOT NULL,

    CONSTRAINT chk_presupuestos_importe
        CHECK (importe >= 0),

    CONSTRAINT fk_presupuestos_incidencia
        FOREIGN KEY (id_incidencia)
        REFERENCES incidencias (id_incidencia)
);
CREATE TABLE votaciones (
    id_votacion INT AUTO_INCREMENT PRIMARY KEY,
    titulo VARCHAR(150) NOT NULL,
    descripcion TEXT,
    fecha_inicio DATETIME NOT NULL,
    fecha_fin DATETIME NOT NULL,

    estado ENUM('PROGRAMADA', 'ABIERTA', 'CERRADA', 'CANCELADA')
        NOT NULL DEFAULT 'PROGRAMADA',

    id_presupuesto INT NOT NULL,

    CONSTRAINT uk_votaciones_presupuesto
        UNIQUE (id_presupuesto),

    CONSTRAINT chk_votaciones_fechas
        CHECK (fecha_fin > fecha_inicio),

    CONSTRAINT fk_votaciones_presupuesto
        FOREIGN KEY (id_presupuesto)
        REFERENCES presupuestos (id_presupuesto)
);

CREATE TABLE votos (
    id_voto INT AUTO_INCREMENT PRIMARY KEY,

    opcion ENUM('A_FAVOR', 'EN_CONTRA', 'ABSTENCION')
        NOT NULL,

    fecha_voto DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    id_usuario INT NOT NULL,
    id_votacion INT NOT NULL,

    CONSTRAINT uk_votos_usuario_votacion
        UNIQUE (id_usuario, id_votacion),

    CONSTRAINT fk_votos_usuario
        FOREIGN KEY (id_usuario)
        REFERENCES usuarios (id_usuario),

    CONSTRAINT fk_votos_votacion
        FOREIGN KEY (id_votacion)
        REFERENCES votaciones (id_votacion)
);
CREATE TABLE costes (
    id_coste INT AUTO_INCREMENT PRIMARY KEY,
    concepto VARCHAR(150) NOT NULL,
    importe DECIMAL(10,2) NOT NULL,

    estado_pago ENUM('PENDIENTE', 'PAGADO')
        NOT NULL DEFAULT 'PENDIENTE',

    fecha_registro DATETIME NOT NULL
        DEFAULT CURRENT_TIMESTAMP,

    id_incidencia INT NOT NULL,

    CONSTRAINT chk_costes_importe
        CHECK (importe >= 0),

    CONSTRAINT fk_costes_incidencia
        FOREIGN KEY (id_incidencia)
        REFERENCES incidencias (id_incidencia)
);