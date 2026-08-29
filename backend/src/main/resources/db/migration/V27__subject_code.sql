-- Código de materia (4 dígitos) — pedido explícito del usuario: cada materia
-- tiene que tener un número que la identifique, mostrado antes del nombre en
-- Plan de estudio y usado para nombrar las correlativas ("Nombre + código").
ALTER TABLE subject ADD COLUMN code VARCHAR(4);

-- Backfill secuencial para las materias que ya existen (1001, 1002, ...) — el id
-- real no se usa como código (es un detalle interno, no algo que la institución
-- deba mostrar), pero sirve como base determinística para no colisionar.
UPDATE subject SET code = LPAD((1000 + id)::text, 4, '0') WHERE code IS NULL;

ALTER TABLE subject ALTER COLUMN code SET NOT NULL;
ALTER TABLE subject ADD CONSTRAINT uk_subject_code UNIQUE (code);
