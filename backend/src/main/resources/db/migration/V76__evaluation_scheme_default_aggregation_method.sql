UPDATE evaluation_scheme SET aggregation_method = 'SIMPLE_AVERAGE' WHERE aggregation_method IS NULL;

ALTER TABLE evaluation_scheme ALTER COLUMN aggregation_method SET DEFAULT 'SIMPLE_AVERAGE';
ALTER TABLE evaluation_scheme ALTER COLUMN aggregation_method SET NOT NULL;
