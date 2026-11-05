insert into enrollment_payment_standing (program_enrollment_id, student_id, status, suspended_since, last_closed_month)
select e.id, e.student_id, e.payment_status,
       case when e.payment_status = 'SUSPENDED' then date_trunc('month', coalesce(e.payment_status_updated_at, now()))::date end,
       (date_trunc('month', current_date) - interval '1 month')::date
from program_enrollment e
where not exists (select 1 from enrollment_payment_standing s where s.program_enrollment_id = e.id);

insert into enrollment_payment_standing (course_enrollment_id, student_id, status, suspended_since, last_closed_month)
select e.id, e.student_id, e.payment_status,
       case when e.payment_status = 'SUSPENDED' then date_trunc('month', coalesce(e.payment_status_updated_at, now()))::date end,
       (date_trunc('month', current_date) - interval '1 month')::date
from course_enrollment e
where not exists (select 1 from enrollment_payment_standing s where s.course_enrollment_id = e.id);

insert into enrollment_payment_status_change (standing_id, event, from_status, to_status, reason, actor_process, business_date, occurred_at)
select s.id, 'MIGRATED', null, s.status, 'Estado de cobranza copiado de la inscripción', 'MIGRATION',
       coalesce(e.payment_status_updated_at::date, current_date), now()
from enrollment_payment_standing s
join program_enrollment e on e.id = s.program_enrollment_id
where not exists (select 1 from enrollment_payment_status_change c where c.standing_id = s.id);

insert into enrollment_payment_status_change (standing_id, event, from_status, to_status, reason, actor_process, business_date, occurred_at)
select s.id, 'MIGRATED', null, s.status, 'Estado de cobranza copiado de la inscripción', 'MIGRATION',
       coalesce(e.payment_status_updated_at::date, current_date), now()
from enrollment_payment_standing s
join course_enrollment e on e.id = s.course_enrollment_id
where not exists (select 1 from enrollment_payment_status_change c where c.standing_id = s.id);

alter table program_enrollment drop column payment_status;
alter table program_enrollment drop column payment_status_updated_at;
alter table course_enrollment drop column payment_status;
alter table course_enrollment drop column payment_status_updated_at;
