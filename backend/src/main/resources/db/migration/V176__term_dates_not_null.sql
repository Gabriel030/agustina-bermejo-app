UPDATE term
SET start_date = COALESCE(start_date, enrollment_start, now())
WHERE start_date IS NULL;

UPDATE term
SET end_date = GREATEST(COALESCE(end_date, enrollment_end, start_date + interval '1 year'), start_date)
WHERE end_date IS NULL OR end_date < start_date;

ALTER TABLE term ALTER COLUMN start_date SET NOT NULL;
ALTER TABLE term ALTER COLUMN end_date SET NOT NULL;
