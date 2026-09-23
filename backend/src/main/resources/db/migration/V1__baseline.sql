-- Baseline migration. Domain tables arrive with their own phases.
CREATE TABLE app_metadata (
    key        VARCHAR(100) PRIMARY KEY,
    value      VARCHAR(500) NOT NULL,
    created_at TIMESTAMPTZ  NOT NULL DEFAULT now()
);

INSERT INTO app_metadata (key, value) VALUES ('schema_baseline', 'phase-0');
