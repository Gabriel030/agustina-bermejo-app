ALTER TABLE subject
    ADD COLUMN weekly_hours INTEGER,
    ADD COLUMN weekly_hours_lab INTEGER,
    ADD COLUMN grade_mode VARCHAR(20);

ALTER TABLE program
    ADD COLUMN total_hours_required INTEGER,
    ADD COLUMN visible_in_catalog BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN teaching_mode VARCHAR(20),
    ADD COLUMN level VARCHAR(20),
    ADD COLUMN graduation_profile TEXT,
    ADD COLUMN admission_requirements TEXT;

ALTER TABLE program_subject
    ADD COLUMN category VARCHAR(30) NOT NULL DEFAULT 'MANDATORY';

ALTER TABLE course
    ADD COLUMN visible_in_catalog BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN teaching_mode VARCHAR(20);
