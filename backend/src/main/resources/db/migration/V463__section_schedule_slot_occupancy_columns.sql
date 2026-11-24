create extension if not exists btree_gist;

alter table section_schedule_slot
    add column term_period tsrange,
    add column occupies boolean not null default false,
    add column time_span int4range;

create function section_schedule_slot_time_span(p_day_of_week varchar, p_start_time time, p_end_time time)
returns int4range
language sql
immutable
as $$
    select int4range(
        (case p_day_of_week
            when 'MONDAY' then 0 when 'TUESDAY' then 1 when 'WEDNESDAY' then 2 when 'THURSDAY' then 3
            when 'FRIDAY' then 4 when 'SATURDAY' then 5 when 'SUNDAY' then 6 end) * 86400
            + floor(extract(epoch from p_start_time))::int,
        (case p_day_of_week
            when 'MONDAY' then 0 when 'TUESDAY' then 1 when 'WEDNESDAY' then 2 when 'THURSDAY' then 3
            when 'FRIDAY' then 4 when 'SATURDAY' then 5 when 'SUNDAY' then 6 end) * 86400
            + floor(extract(epoch from p_end_time))::int,
        '[)')
$$;

create function section_schedule_slot_term_period(p_subject_section_id bigint, p_course_section_id bigint)
returns tsrange
language sql
stable
as $$
    select case when t.start_date is not null and t.end_date is not null and t.end_date >= t.start_date
                then tsrange(t.start_date, t.end_date, '[]') end
    from term t
    where t.id = coalesce(
        (select ss.term_id from subject_section ss where ss.id = p_subject_section_id),
        (select cs.term_id from course_section cs where cs.id = p_course_section_id))
$$;

create function section_schedule_slot_occupies(p_subject_section_id bigint, p_course_section_id bigint)
returns boolean
language sql
stable
as $$
    select coalesce(
        (select ss.status = 'ACTIVE' or c.status in ('DRAFT', 'PENDING_APPROVAL')
         from subject_section ss
         left join academic_offering_cycle c on c.id = ss.academic_offering_cycle_id
         where ss.id = p_subject_section_id),
        (select cs.status = 'ACTIVE' or c.status in ('DRAFT', 'PENDING_APPROVAL')
         from course_section cs
         left join academic_offering_cycle c on c.id = cs.academic_offering_cycle_id
         where cs.id = p_course_section_id),
        false)
$$;

create function maintain_section_schedule_slot_occupancy()
returns trigger
language plpgsql
as $$
begin
    new.term_period := section_schedule_slot_term_period(new.subject_section_id, new.course_section_id);
    new.occupies := section_schedule_slot_occupies(new.subject_section_id, new.course_section_id);
    new.time_span := section_schedule_slot_time_span(new.day_of_week, new.start_time, new.end_time);
    return new;
end
$$;

create trigger trg_section_schedule_slot_occupancy
    before insert or update on section_schedule_slot
    for each row execute function maintain_section_schedule_slot_occupancy();

create function refresh_subject_section_slot_occupancy()
returns trigger
language plpgsql
as $$
begin
    update section_schedule_slot set day_of_week = day_of_week where subject_section_id = new.id;
    return null;
end
$$;

create function refresh_course_section_slot_occupancy()
returns trigger
language plpgsql
as $$
begin
    update section_schedule_slot set day_of_week = day_of_week where course_section_id = new.id;
    return null;
end
$$;

create function refresh_term_slot_occupancy()
returns trigger
language plpgsql
as $$
begin
    update section_schedule_slot set day_of_week = day_of_week
    where subject_section_id in (select id from subject_section where term_id = new.id)
       or course_section_id in (select id from course_section where term_id = new.id);
    return null;
end
$$;

create function refresh_cycle_slot_occupancy()
returns trigger
language plpgsql
as $$
begin
    update section_schedule_slot set day_of_week = day_of_week
    where subject_section_id in (select id from subject_section where academic_offering_cycle_id = new.id)
       or course_section_id in (select id from course_section where academic_offering_cycle_id = new.id);
    return null;
end
$$;

create trigger trg_subject_section_slot_occupancy
    after update of status, term_id, academic_offering_cycle_id on subject_section
    for each row
    when (old.status is distinct from new.status
          or old.term_id is distinct from new.term_id
          or old.academic_offering_cycle_id is distinct from new.academic_offering_cycle_id)
    execute function refresh_subject_section_slot_occupancy();

create trigger trg_course_section_slot_occupancy
    after update of status, term_id, academic_offering_cycle_id on course_section
    for each row
    when (old.status is distinct from new.status
          or old.term_id is distinct from new.term_id
          or old.academic_offering_cycle_id is distinct from new.academic_offering_cycle_id)
    execute function refresh_course_section_slot_occupancy();

create trigger trg_term_slot_occupancy
    after update of start_date, end_date on term
    for each row
    when (old.start_date is distinct from new.start_date or old.end_date is distinct from new.end_date)
    execute function refresh_term_slot_occupancy();

create trigger trg_cycle_slot_occupancy
    after update of status on academic_offering_cycle
    for each row
    when (old.status is distinct from new.status)
    execute function refresh_cycle_slot_occupancy();

update section_schedule_slot set day_of_week = day_of_week;
