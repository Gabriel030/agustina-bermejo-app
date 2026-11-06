ALTER TABLE event_publication ADD COLUMN IF NOT EXISTS status TEXT;
ALTER TABLE event_publication ADD COLUMN IF NOT EXISTS completion_attempts INT;
ALTER TABLE event_publication ADD COLUMN IF NOT EXISTS last_resubmission_date TIMESTAMP WITH TIME ZONE;

UPDATE event_publication
SET status = CASE WHEN completion_date IS NULL THEN 'PUBLISHED' ELSE 'COMPLETED' END,
    completion_attempts = 0
WHERE status IS NULL;
