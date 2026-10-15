ALTER TABLE subject ADD COLUMN rejection_reason varchar(500);

ALTER TABLE term ADD CONSTRAINT chk_term_end_not_before_start CHECK (end_date >= start_date);
