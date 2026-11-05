alter table person add column birth_date date;

alter table student
    add column registered_at timestamp,
    add column registered_by_person_id bigint references person(id),
    add column withdrawn_at timestamp;

create table student_status_change (
    id bigserial primary key,
    student_id bigint not null references student(id),
    change_type varchar(20) not null,
    reason varchar(500),
    actor_person_id bigint not null references person(id),
    occurred_at timestamp not null
);
create index idx_student_status_change_student on student_status_change(student_id, occurred_at desc);
