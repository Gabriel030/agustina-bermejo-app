alter table program_cohort add column program_id bigint;
alter table program_cohort add column term_id bigint;

update program_cohort c
set program_id = t.program_id, term_id = t.term_id
from program_year_billing_template t
where t.id = c.template_id;

alter table program_cohort alter column program_id set not null;
alter table program_cohort alter column term_id set not null;
alter table program_cohort alter column template_id drop not null;

alter table program_cohort drop constraint uk_program_cohort_intake;
alter table program_cohort
    add constraint uk_program_cohort_program_term_intake unique (program_id, term_id, intake);

alter table program_cohort
    add constraint fk_program_cohort_program foreign key (program_id) references program not valid;
alter table program_cohort
    add constraint fk_program_cohort_term foreign key (term_id) references term not valid;

create index ix_program_cohort_term on program_cohort (term_id);
create index ix_program_cohort_template on program_cohort (template_id);

insert into permission (code, module, description, fixed_to_director, sensitive) values
    ('academicstructure.manage_program_cohorts', 'academicstructure', 'Crear cohortes de una carrera en un año lectivo sin plantilla de facturación', false, false);

insert into role_permission (role, permission_id)
select grants.role, permission.id
from (values
    ('ADMINISTRATIVO', 'academicstructure.manage_program_cohorts')
) as grants (role, code)
join permission on permission.code = grants.code;
