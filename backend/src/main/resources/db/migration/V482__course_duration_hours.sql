alter table course add column duration_hours integer;

alter table course
    add constraint course_duration_hours_positive check (duration_hours is null or duration_hours > 0) not valid;
