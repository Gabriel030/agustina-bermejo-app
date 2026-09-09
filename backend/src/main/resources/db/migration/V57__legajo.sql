CREATE SEQUENCE legajo_seq START WITH 10000 INCREMENT BY 1;

WITH asignados AS (
    SELECT id, nextval('legajo_seq')::varchar AS legajo
    FROM person
    WHERE file IS NULL
    ORDER BY id
)
UPDATE person SET file = asignados.legajo FROM asignados WHERE person.id = asignados.id;

ALTER TABLE person ALTER COLUMN file SET DEFAULT nextval('legajo_seq')::varchar;
