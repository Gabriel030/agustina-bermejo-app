alter table evaluation_scheme add column recovery_quota integer not null default 1;
alter table evaluation_scheme add constraint ck_evaluation_scheme_recovery_quota check (recovery_quota >= 0 and recovery_quota <= 10);

alter table grade_item add column original_score numeric(4,2) check (original_score is null or original_score between 1 and 10);
alter table grade_item add column recovery_loaded_by bigint references person (id);
alter table grade_item add column recovery_loaded_at timestamp(6);
alter table grade_item add constraint ck_grade_item_recovery_consistency check (
    (recovery_loaded_at is null and original_score is null and recovery_loaded_by is null)
    or (recovery_loaded_at is not null and original_score is not null and recovery_loaded_by is not null));
