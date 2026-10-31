CREATE FUNCTION pg_temp.term_type_of_text(raw text) RETURNS text AS $$
    SELECT CASE
        WHEN upper(trim(raw)) IN ('ANUAL', 'ANNUAL', 'A') THEN 'ANNUAL'
        WHEN upper(trim(raw)) IN ('1C', '1ER', '1ER CUATRIMESTRE', 'PRIMER CUATRIMESTRE', 'PRIMERO', '1', 'FIRST', 'FIRST_SEMESTER', '1S') THEN 'FIRST_SEMESTER'
        WHEN upper(trim(raw)) IN ('2C', '2DO', '2DO CUATRIMESTRE', 'SEGUNDO CUATRIMESTRE', 'SEGUNDO', '2', 'SECOND', 'SECOND_SEMESTER', '2S') THEN 'SECOND_SEMESTER'
        WHEN upper(trim(raw)) = 'INTENSIVE' OR lower(raw) LIKE '%intensiv%' OR lower(raw) LIKE '%verano%' THEN 'INTENSIVE'
        ELSE NULL
    END
$$ LANGUAGE sql IMMUTABLE;

DO $$
DECLARE
    version_row record;
    entry text;
    mapped text;
    mapped_types jsonb;
BEGIN
    FOR version_row IN SELECT id, config_values FROM configuration_version WHERE scope = 'IRREGULAR_TERMS' LOOP
        mapped_types := '[]'::jsonb;
        FOR entry IN SELECT jsonb_array_elements_text(version_row.config_values -> 'types') LOOP
            mapped := pg_temp.term_type_of_text(entry);
            IF mapped IS NULL THEN
                RAISE EXCEPTION 'No se puede migrar la lista de períodos irregulares (versión %): el tipo "%" no corresponde a ningún tipo del catálogo (Anual, Primer cuatrimestre, Segundo cuatrimestre, Intensivo de verano). Corregí la lista antes de reintentar.', version_row.id, entry;
            END IF;
            IF NOT mapped_types ? mapped THEN
                mapped_types := mapped_types || to_jsonb(mapped);
            END IF;
        END LOOP;
        UPDATE configuration_version
           SET config_values = jsonb_set(config_values, '{types}', (SELECT coalesce(jsonb_agg(t ORDER BY t), '[]'::jsonb) FROM jsonb_array_elements_text(mapped_types) t))
         WHERE id = version_row.id;
    END LOOP;
END $$;

UPDATE term SET type = CASE
    WHEN pg_temp.term_type_of_text(type) IS NOT NULL THEN pg_temp.term_type_of_text(type)
    WHEN start_date IS NULL OR end_date IS NULL THEN 'ANNUAL'
    WHEN end_date >= start_date + interval '9 months' THEN 'ANNUAL'
    WHEN extract(month FROM start_date) <= 6 THEN 'FIRST_SEMESTER'
    ELSE 'SECOND_SEMESTER'
END;

DO $$
DECLARE
    duplicated text;
BEGIN
    SELECT string_agg(year_number || '/' || type || ' (ids ' || ids || ')', '; ')
      INTO duplicated
      FROM (SELECT year_number, type, string_agg(id::text, ',' ORDER BY id) AS ids
              FROM term
             WHERE status = 'ACTIVE' AND year_number IS NOT NULL
             GROUP BY year_number, type
            HAVING count(*) > 1) d;
    IF duplicated IS NOT NULL THEN
        RAISE EXCEPTION 'No se puede imponer un único período activo por año y tipo: hay duplicados (%). Desactivá o corregí los períodos repetidos y reintentá.', duplicated;
    END IF;
END $$;

ALTER TABLE term ALTER COLUMN type SET DEFAULT 'ANNUAL';
ALTER TABLE term ADD CONSTRAINT chk_term_type_catalog CHECK (type IN ('ANNUAL', 'FIRST_SEMESTER', 'SECOND_SEMESTER', 'INTENSIVE'));
CREATE UNIQUE INDEX uk_term_active_year_type ON term (year_number, type) WHERE status = 'ACTIVE';
