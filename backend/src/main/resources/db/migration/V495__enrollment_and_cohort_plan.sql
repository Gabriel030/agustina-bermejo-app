alter table program_cohort add column plan_id bigint;
alter table program_enrollment add column plan_id bigint;

update program_cohort pc
set plan_id = (select cp.id from curriculum_plan cp
               where cp.program_id = pc.program_id and cp.status <> 'DRAFT'
               order by cp.version_number desc limit 1);

update program_enrollment pe
set plan_id = coalesce(
    (select pc.plan_id from program_cohort pc where pc.id = pe.program_cohort_id),
    (select cp.id from curriculum_plan cp
     where cp.program_id = pe.program_id and cp.status <> 'DRAFT'
     order by cp.version_number desc limit 1));

alter table program_cohort add constraint fk_program_cohort_plan
    foreign key (plan_id) references curriculum_plan (id);
alter table program_enrollment add constraint fk_program_enrollment_plan
    foreign key (plan_id) references curriculum_plan (id);

create index idx_program_cohort_plan on program_cohort (plan_id);
create index idx_program_enrollment_plan on program_enrollment (plan_id);

alter table program_cohort add constraint program_cohort_plan_required check (plan_id is not null) not valid;
alter table program_enrollment add constraint program_enrollment_plan_required check (plan_id is not null) not valid;
