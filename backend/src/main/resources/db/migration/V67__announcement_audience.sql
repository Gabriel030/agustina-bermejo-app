alter table announcement add column audience_key varchar(32) not null default 'ALL';
alter table announcement add column audience_instances varchar(64);
alter table announcement add column audience_quantifier varchar(8);
alter table announcement add column audience_threshold integer;
alter table announcement alter column audience_key drop default;

create table announcement_recipient (
    id              bigserial primary key,
    announcement_id bigint not null references announcement (id) on delete cascade,
    student_id      bigint not null references student (id),
    constraint uk_announcement_recipient unique (announcement_id, student_id)
);

create index ix_announcement_recipient_student on announcement_recipient (student_id);

insert into announcement_recipient (announcement_id, student_id)
select distinct a.id, e.student_id
from announcement a
         join subject_section_enrollment e on e.program_section_id = a.subject_section_id
where a.subject_section_id is not null
  and e.status in ('ENROLLED', 'IN_PROGRESS', 'REGULAR', 'APPROVED', 'FAILED');

insert into announcement_recipient (announcement_id, student_id)
select distinct a.id, e.student_id
from announcement a
         join course_section_enrollment e on e.course_section_id = a.course_section_id
where a.course_section_id is not null
  and e.status in ('ENROLLED', 'IN_PROGRESS', 'REGULAR', 'APPROVED', 'FAILED');
