create table announcement (
    id                 bigserial primary key,
    version            bigint,
    subject_section_id bigint references subject_section (id),
    course_section_id  bigint references course_section (id),
    created_by_id      bigint not null references person (id),
    title              varchar(100) not null,
    body               varchar(1000) not null,
    category           varchar(32) not null,
    urgent             boolean not null default false,
    expires_at         date,
    audience_label     varchar(200) not null,
    created_at         timestamp not null,
    updated_at         timestamp,
    constraint ck_announcement_one_target check (
        (subject_section_id is not null and course_section_id is null)
     or (subject_section_id is null and course_section_id is not null)
    )
);

create index ix_announcement_subject_section on announcement (subject_section_id, created_at desc);
create index ix_announcement_course_section on announcement (course_section_id, created_at desc);
