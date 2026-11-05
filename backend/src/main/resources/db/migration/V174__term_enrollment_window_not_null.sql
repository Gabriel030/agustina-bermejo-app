UPDATE term
SET enrollment_start = COALESCE(enrollment_start, start_date, now())
WHERE enrollment_start IS NULL;

UPDATE term
SET enrollment_end = GREATEST(COALESCE(enrollment_end, end_date, start_date + interval '1 year', enrollment_start), enrollment_start)
WHERE enrollment_end IS NULL OR enrollment_end < enrollment_start;

ALTER TABLE term ALTER COLUMN enrollment_start SET NOT NULL;
ALTER TABLE term ALTER COLUMN enrollment_end SET NOT NULL;
ALTER TABLE term ADD CONSTRAINT chk_term_enrollment_end_not_before_start CHECK (enrollment_end >= enrollment_start);
