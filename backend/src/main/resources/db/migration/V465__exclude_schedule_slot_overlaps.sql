create extension if not exists btree_gist;

alter table section_schedule_slot add column active boolean not null default false;

create function section_schedule_slot_active(p_subject_section_id bigint, p_course_section_id bigint)
returns boolean
language sql
stable
as $$
    select coalesce(
        (select ss.status = 'ACTIVE' from subject_section ss where ss.id = p_subject_section_id),
        (select cs.status = 'ACTIVE' from course_section cs where cs.id = p_course_section_id),
        false)
$$;

create or replace function maintain_section_schedule_slot_occupancy()
returns trigger
language plpgsql
as $$
begin
    new.term_period := section_schedule_slot_term_period(new.subject_section_id, new.course_section_id);
    new.occupies := section_schedule_slot_occupies(new.subject_section_id, new.course_section_id);
    new.active := section_schedule_slot_active(new.subject_section_id, new.course_section_id);
    new.time_span := section_schedule_slot_time_span(new.day_of_week, new.start_time, new.end_time);
    return new;
end
$$;

update section_schedule_slot set day_of_week = day_of_week;

alter table section_schedule_slot
    add constraint ex_schedule_slot_classroom_overlap
    exclude using gist (classroom_id with =, time_span with &&, term_period with &&)
    where (active);

create table section_schedule_slot_instructor (
    slot_id       bigint    not null references section_schedule_slot (id) on delete cascade,
    instructor_id bigint    not null references instructor (id) on delete cascade,
    time_span     int4range not null,
    term_period   tsrange,
    primary key (slot_id, instructor_id),
    constraint ex_schedule_slot_instructor_overlap
        exclude using gist (instructor_id with =, time_span with &&, term_period with &&)
);

create function rebuild_section_schedule_slot_instructors(p_slot_id bigint)
returns void
language plpgsql
as $$
begin
    delete from section_schedule_slot_instructor where slot_id = p_slot_id;
    insert into section_schedule_slot_instructor (slot_id, instructor_id, time_span, term_period)
    select distinct s.id, l.instructor_id, s.time_span, s.term_period
    from section_schedule_slot s
    join (select program_section_id as section_id, null::bigint as course_id, instructor_id from subject_section_instructor
          union all
          select null::bigint, course_section_id, instructor_id from course_section_instructor) l
        on (l.section_id is not null and l.section_id = s.subject_section_id)
        or (l.course_id is not null and l.course_id = s.course_section_id)
    where s.id = p_slot_id and s.active;
end
$$;

create function refresh_slot_instructors_on_slot_change()
returns trigger
language plpgsql
as $$
begin
    perform rebuild_section_schedule_slot_instructors(new.id);
    return null;
end
$$;

create function refresh_slot_instructors_on_subject_link_change()
returns trigger
language plpgsql
as $$
declare
    v_slot_id bigint;
begin
    for v_slot_id in
        select id from section_schedule_slot
        where subject_section_id in (coalesce(new.program_section_id, old.program_section_id), coalesce(old.program_section_id, new.program_section_id))
    loop
        perform rebuild_section_schedule_slot_instructors(v_slot_id);
    end loop;
    return null;
end
$$;

create function refresh_slot_instructors_on_course_link_change()
returns trigger
language plpgsql
as $$
declare
    v_slot_id bigint;
begin
    for v_slot_id in
        select id from section_schedule_slot
        where course_section_id in (coalesce(new.course_section_id, old.course_section_id), coalesce(old.course_section_id, new.course_section_id))
    loop
        perform rebuild_section_schedule_slot_instructors(v_slot_id);
    end loop;
    return null;
end
$$;

create trigger trg_slot_instructors_on_slot
    after insert or update on section_schedule_slot
    for each row execute function refresh_slot_instructors_on_slot_change();

create trigger trg_slot_instructors_on_subject_link
    after insert or update or delete on subject_section_instructor
    for each row execute function refresh_slot_instructors_on_subject_link_change();

create trigger trg_slot_instructors_on_course_link
    after insert or update or delete on course_section_instructor
    for each row execute function refresh_slot_instructors_on_course_link_change();

select rebuild_section_schedule_slot_instructors(id) from section_schedule_slot;
