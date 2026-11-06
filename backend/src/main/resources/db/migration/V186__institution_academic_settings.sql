alter table institution add column justification_deadline_business_days integer not null default 5;
alter table institution add column evaluation_config_due_days integer not null default 15;
alter table institution add constraint ck_institution_justification_deadline
    check (justification_deadline_business_days between 0 and 60);
alter table institution add constraint ck_institution_evaluation_config_due
    check (evaluation_config_due_days between 1 and 120);
