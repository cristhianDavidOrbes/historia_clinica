CREATE TABLE IF NOT EXISTS pacientes (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    nombre VARCHAR(100) NOT NULL,
    documento VARCHAR(30) NOT NULL UNIQUE
);

CREATE TABLE IF NOT EXISTS historias_clinicas (
    paciente_id BIGINT PRIMARY KEY REFERENCES pacientes(id),
    antecedentes VARCHAR(4000) NOT NULL,
    alergias VARCHAR(4000) NOT NULL,
    medicamentos VARCHAR(4000) NOT NULL,
    version BIGINT NOT NULL DEFAULT 1 CHECK (version > 0),
    actualizada_en TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS accesos_emergencia (
    id UUID PRIMARY KEY,
    paciente_id BIGINT NOT NULL REFERENCES historias_clinicas(paciente_id),
    profesional VARCHAR(80) NOT NULL,
    abierta_en TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    justificacion VARCHAR(2000),
    justificada_en TIMESTAMPTZ,
    revisada_por VARCHAR(80),
    revisada_en TIMESTAMPTZ,
    revision VARCHAR(2000)
);

CREATE TABLE IF NOT EXISTS auditoria_eventos (
    event_id UUID PRIMARY KEY,
    usuario VARCHAR(80) NOT NULL,
    rol VARCHAR(50) NOT NULL,
    paciente_id BIGINT,
    accion VARCHAR(100) NOT NULL,
    resultado VARCHAR(30) NOT NULL,
    punto_acceso VARCHAR(100) NOT NULL,
    modo VARCHAR(30) NOT NULL,
    registrado_en TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_auditoria_registrado_en ON auditoria_eventos (registrado_en);
CREATE INDEX IF NOT EXISTS idx_auditoria_paciente ON auditoria_eventos (paciente_id);
