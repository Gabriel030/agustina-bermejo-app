CREATE TABLE shift_range (
    shift      VARCHAR(16) PRIMARY KEY,
    start_time TIME NOT NULL,
    end_time   TIME NOT NULL,
    CONSTRAINT ck_shift_range_order CHECK (end_time > start_time),
    CONSTRAINT ck_shift_range_shift CHECK (shift IN ('MORNING', 'AFTERNOON', 'EVENING'))
);

INSERT INTO shift_range (shift, start_time, end_time) VALUES
    ('MORNING', '08:00', '12:30'),
    ('AFTERNOON', '13:30', '18:00'),
    ('EVENING', '18:30', '22:30');

CREATE TABLE section_code_counter (
    prefix     VARCHAR(40) NOT NULL,
    period     VARCHAR(20) NOT NULL,
    last_value INTEGER NOT NULL,
    PRIMARY KEY (prefix, period)
);

ALTER TABLE subject_section ADD COLUMN shift VARCHAR(16), ADD COLUMN code VARCHAR(60), ADD COLUMN description VARCHAR(500);
ALTER TABLE course_section ADD COLUMN shift VARCHAR(16), ADD COLUMN code VARCHAR(60), ADD COLUMN description VARCHAR(500);

ALTER TABLE subject_section ADD CONSTRAINT ck_subject_section_shift CHECK (shift IS NULL OR shift IN ('MORNING', 'AFTERNOON', 'EVENING'));
ALTER TABLE course_section ADD CONSTRAINT ck_course_section_shift CHECK (shift IS NULL OR shift IN ('MORNING', 'AFTERNOON', 'EVENING'));

CREATE FUNCTION pg_temp.shift_of_start(first_start time) RETURNS text AS $$
    SELECT CASE
        WHEN first_start IS NULL THEN 'MORNING'
        WHEN first_start < TIME '13:00' THEN 'MORNING'
        WHEN first_start < TIME '18:00' THEN 'AFTERNOON'
        ELSE 'EVENING'
    END
$$ LANGUAGE sql IMMUTABLE;

CREATE FUNCTION pg_temp.period_code(term_year integer, term_type text, term_start timestamp) RETURNS text AS $$
    SELECT coalesce(term_year, extract(year FROM term_start)::integer)::text || CASE term_type
        WHEN 'FIRST_SEMESTER' THEN 'C1'
        WHEN 'SECOND_SEMESTER' THEN 'C2'
        WHEN 'INTENSIVE' THEN 'V'
        ELSE 'A'
    END
$$ LANGUAGE sql IMMUTABLE;

UPDATE subject_section s
   SET shift = pg_temp.shift_of_start((SELECT min(sl.start_time) FROM section_schedule_slot sl WHERE sl.subject_section_id = s.id));

UPDATE course_section s
   SET shift = pg_temp.shift_of_start((SELECT min(sl.start_time) FROM section_schedule_slot sl WHERE sl.course_section_id = s.id));

CREATE TEMP TABLE section_code_backfill ON COMMIT DROP AS
SELECT 'S' AS kind, s.id, sub.code AS prefix,
       pg_temp.period_code(t.year_number, t.type, t.start_date) AS period
  FROM subject_section s
  JOIN subject sub ON sub.id = s.subject_id
  JOIN term t ON t.id = s.term_id
UNION ALL
SELECT 'C', s.id, 'CM' || s.course_subject_id,
       pg_temp.period_code(t.year_number, t.type, t.start_date)
  FROM course_section s
  JOIN term t ON t.id = s.term_id;

CREATE TEMP TABLE section_code_numbered ON COMMIT DROP AS
SELECT kind, id, prefix, period,
       row_number() OVER (PARTITION BY prefix, period ORDER BY kind, id) AS seq
  FROM section_code_backfill;

UPDATE subject_section s
   SET code = n.prefix || '-' || n.period || '-' || lpad(n.seq::text, 2, '0')
  FROM section_code_numbered n
 WHERE n.kind = 'S' AND n.id = s.id;

UPDATE course_section s
   SET code = n.prefix || '-' || n.period || '-' || lpad(n.seq::text, 2, '0')
  FROM section_code_numbered n
 WHERE n.kind = 'C' AND n.id = s.id;

INSERT INTO section_code_counter (prefix, period, last_value)
SELECT prefix, period, max(seq) FROM section_code_numbered GROUP BY prefix, period;

CREATE UNIQUE INDEX uq_subject_section_code ON subject_section (code);
CREATE UNIQUE INDEX uq_course_section_code ON course_section (code);
