\pset pager off
\timing off
vacuum analyze;

\echo ##### C1 person(last_name, name, id) para el orden del listado de alumnos (Q2)
\echo --- antes
\o /dev/null
explain (analyze, buffers) select s.*, p2.* from student s left join person p1 on p1.id=s.person_id join person p2 on p2.id=s.person_id where s.onboarding_status='CONFIRMADO' and exists(select 1 from person p3 join person_role r on p3.id=r.person_id join role ro on ro.id=r.role_id where p3.id=s.person_id and ro.name='ALUMNO') order by p1.last_name,p1.name,s.id offset 0 rows fetch first 25 rows only;
\o
explain (analyze, buffers) select s.*, p2.* from student s left join person p1 on p1.id=s.person_id join person p2 on p2.id=s.person_id where s.onboarding_status='CONFIRMADO' and exists(select 1 from person p3 join person_role r on p3.id=r.person_id join role ro on ro.id=r.role_id where p3.id=s.person_id and ro.name='ALUMNO') order by p1.last_name,p1.name,s.id offset 0 rows fetch first 25 rows only;
\echo --- despues
create index tmp_c1 on person (last_name, name, id);
analyze;
\o /dev/null
explain (analyze, buffers) select s.*, p2.* from student s left join person p1 on p1.id=s.person_id join person p2 on p2.id=s.person_id where s.onboarding_status='CONFIRMADO' and exists(select 1 from person p3 join person_role r on p3.id=r.person_id join role ro on ro.id=r.role_id where p3.id=s.person_id and ro.name='ALUMNO') order by p1.last_name,p1.name,s.id offset 0 rows fetch first 25 rows only;
\o
explain (analyze, buffers) select s.*, p2.* from student s left join person p1 on p1.id=s.person_id join person p2 on p2.id=s.person_id where s.onboarding_status='CONFIRMADO' and exists(select 1 from person p3 join person_role r on p3.id=r.person_id join role ro on ro.id=r.role_id where p3.id=s.person_id and ro.name='ALUMNO') order by p1.last_name,p1.name,s.id offset 0 rows fetch first 25 rows only;
drop index tmp_c1;

\echo ##### C2 student(onboarding_status, person_id) para filtro y conteo de alumnos (Q7)
\echo --- antes
\o /dev/null
explain (analyze, buffers) select count(s.id) from student s where s.onboarding_status='CONFIRMADO' and exists(select 1 from person p join person_role r on p.id=r.person_id join role ro on ro.id=r.role_id where p.id=s.person_id and ro.name='ALUMNO');
\o
explain (analyze, buffers) select count(s.id) from student s where s.onboarding_status='CONFIRMADO' and exists(select 1 from person p join person_role r on p.id=r.person_id join role ro on ro.id=r.role_id where p.id=s.person_id and ro.name='ALUMNO');
\echo --- despues
create index tmp_c2 on student (onboarding_status, person_id);
analyze;
\o /dev/null
explain (analyze, buffers) select count(s.id) from student s where s.onboarding_status='CONFIRMADO' and exists(select 1 from person p join person_role r on p.id=r.person_id join role ro on ro.id=r.role_id where p.id=s.person_id and ro.name='ALUMNO');
\o
explain (analyze, buffers) select count(s.id) from student s where s.onboarding_status='CONFIRMADO' and exists(select 1 from person p join person_role r on p.id=r.person_id join role ro on ro.id=r.role_id where p.id=s.person_id and ro.name='ALUMNO');
drop index tmp_c2;

\echo ##### C3 subject_section_enrollment(program_section_id, status) para listas de espera (Q4)
\echo --- antes
\o /dev/null
explain (analyze, buffers) select ss.id,(s.name||' '||ss.name),t.description,t.year_number,ss.capacity,count(case when sse.status in ('ENROLLED','APPROVED','IN_PROGRESS') then 1 end),count(case when sse.status='WAITLISTED' then 1 end) from subject_section ss join subject s on s.id=ss.subject_id join term t on t.id=ss.term_id left join subject_section_enrollment sse on sse.program_section_id=ss.id group by ss.id,s.name,ss.name,t.description,t.year_number,ss.capacity having count(case when sse.status='WAITLISTED' then 1 end)>0;
\o
explain (analyze, buffers) select ss.id,(s.name||' '||ss.name),t.description,t.year_number,ss.capacity,count(case when sse.status in ('ENROLLED','APPROVED','IN_PROGRESS') then 1 end),count(case when sse.status='WAITLISTED' then 1 end) from subject_section ss join subject s on s.id=ss.subject_id join term t on t.id=ss.term_id left join subject_section_enrollment sse on sse.program_section_id=ss.id group by ss.id,s.name,ss.name,t.description,t.year_number,ss.capacity having count(case when sse.status='WAITLISTED' then 1 end)>0;
\echo --- despues
create index tmp_c3 on subject_section_enrollment (program_section_id, status);
analyze;
\o /dev/null
explain (analyze, buffers) select ss.id,(s.name||' '||ss.name),t.description,t.year_number,ss.capacity,count(case when sse.status in ('ENROLLED','APPROVED','IN_PROGRESS') then 1 end),count(case when sse.status='WAITLISTED' then 1 end) from subject_section ss join subject s on s.id=ss.subject_id join term t on t.id=ss.term_id left join subject_section_enrollment sse on sse.program_section_id=ss.id group by ss.id,s.name,ss.name,t.description,t.year_number,ss.capacity having count(case when sse.status='WAITLISTED' then 1 end)>0;
\o
explain (analyze, buffers) select ss.id,(s.name||' '||ss.name),t.description,t.year_number,ss.capacity,count(case when sse.status in ('ENROLLED','APPROVED','IN_PROGRESS') then 1 end),count(case when sse.status='WAITLISTED' then 1 end) from subject_section ss join subject s on s.id=ss.subject_id join term t on t.id=ss.term_id left join subject_section_enrollment sse on sse.program_section_id=ss.id group by ss.id,s.name,ss.name,t.description,t.year_number,ss.capacity having count(case when sse.status='WAITLISTED' then 1 end)>0;
drop index tmp_c3;

\echo ##### C4 program_enrollment(status, student_id) para el distinct de alumnos con inscripcion vigente (Q11)
\echo --- antes
\o /dev/null
explain (analyze, buffers) select distinct pe.student_id from program_enrollment pe where pe.status in ('ACTIVE','ENROLLED');
\o
explain (analyze, buffers) select distinct pe.student_id from program_enrollment pe where pe.status in ('ACTIVE','ENROLLED');
\echo --- despues
create index tmp_c4 on program_enrollment (status, student_id);
analyze;
\o /dev/null
explain (analyze, buffers) select distinct pe.student_id from program_enrollment pe where pe.status in ('ACTIVE','ENROLLED');
\o
explain (analyze, buffers) select distinct pe.student_id from program_enrollment pe where pe.status in ('ACTIVE','ENROLLED');
drop index tmp_c4;

\echo ##### C5 invoice(student_current_account_id) parcial OVERDUE para el join del dashboard (Q10)
\echo --- antes
\o /dev/null
explain (analyze, buffers) select distinct sca.student_id from invoice i, student_current_account sca where sca.id=i.student_current_account_id and i.payment_status='OVERDUE';
\o
explain (analyze, buffers) select distinct sca.student_id from invoice i, student_current_account sca where sca.id=i.student_current_account_id and i.payment_status='OVERDUE';
\echo --- despues
create index tmp_c5 on invoice (student_current_account_id) where payment_status = 'OVERDUE';
analyze;
\o /dev/null
explain (analyze, buffers) select distinct sca.student_id from invoice i, student_current_account sca where sca.id=i.student_current_account_id and i.payment_status='OVERDUE';
\o
explain (analyze, buffers) select distinct sca.student_id from invoice i, student_current_account sca where sca.id=i.student_current_account_id and i.payment_status='OVERDUE';
drop index tmp_c5;

\echo ##### C6 invoice_line(invoice_id, line_number) parcial por enrollment para las subconsultas de morosos paginado (Q3)
\echo --- antes
\o /dev/null
explain (analyze, buffers) with overdue as (select i.id as invoice_id, i.currency, i.due_date, greatest(i.outstanding_amount,0) as outstanding, (select l.course_enrollment_id from invoice_line l where l.invoice_id=i.id and l.course_enrollment_id is not null order by l.line_number limit 1) as course_enrollment_id, (select l.program_enrollment_id from invoice_line l where l.invoice_id=i.id and l.program_enrollment_id is not null order by l.line_number limit 1) as program_enrollment_id from invoice i where i.payment_status='OVERDUE'), resolved as (select o.invoice_id,o.currency,o.due_date,o.outstanding,coalesce(ce.student_id,pe.student_id) as student_id, case when ce.id is not null then c.name else p.name end as enrollment_name from overdue o left join course_enrollment ce on ce.id=o.course_enrollment_id left join course c on c.id=ce.course_id left join program_enrollment pe on pe.id=o.program_enrollment_id and ce.id is null left join program p on p.id=pe.program_id where ce.id is not null or pe.id is not null), grouped as (select r.student_id,r.currency,sum(r.outstanding) as total_debt,min(r.due_date) as oldest_due_date,(array_agg(r.enrollment_name order by r.due_date,r.invoice_id))[1] as enrollment_name from resolved r group by r.student_id,r.currency) select g.student_id,g.currency,g.total_debt,g.enrollment_name,(date '2026-11-17'-g.oldest_due_date) as debt_age_days,per.name,per.last_name,per.dni,count(*) over () from grouped g join student s on s.id=g.student_id join person per on per.id=s.person_id order by (date '2026-11-17'-g.oldest_due_date) desc nulls last,g.student_id,g.currency limit 25 offset 0;
\o
explain (analyze, buffers) with overdue as (select i.id as invoice_id, i.currency, i.due_date, greatest(i.outstanding_amount,0) as outstanding, (select l.course_enrollment_id from invoice_line l where l.invoice_id=i.id and l.course_enrollment_id is not null order by l.line_number limit 1) as course_enrollment_id, (select l.program_enrollment_id from invoice_line l where l.invoice_id=i.id and l.program_enrollment_id is not null order by l.line_number limit 1) as program_enrollment_id from invoice i where i.payment_status='OVERDUE'), resolved as (select o.invoice_id,o.currency,o.due_date,o.outstanding,coalesce(ce.student_id,pe.student_id) as student_id, case when ce.id is not null then c.name else p.name end as enrollment_name from overdue o left join course_enrollment ce on ce.id=o.course_enrollment_id left join course c on c.id=ce.course_id left join program_enrollment pe on pe.id=o.program_enrollment_id and ce.id is null left join program p on p.id=pe.program_id where ce.id is not null or pe.id is not null), grouped as (select r.student_id,r.currency,sum(r.outstanding) as total_debt,min(r.due_date) as oldest_due_date,(array_agg(r.enrollment_name order by r.due_date,r.invoice_id))[1] as enrollment_name from resolved r group by r.student_id,r.currency) select g.student_id,g.currency,g.total_debt,g.enrollment_name,(date '2026-11-17'-g.oldest_due_date) as debt_age_days,per.name,per.last_name,per.dni,count(*) over () from grouped g join student s on s.id=g.student_id join person per on per.id=s.person_id order by (date '2026-11-17'-g.oldest_due_date) desc nulls last,g.student_id,g.currency limit 25 offset 0;
\echo --- despues
create index tmp_c6 on invoice_line (invoice_id, line_number) where course_enrollment_id is not null or program_enrollment_id is not null;
analyze;
\o /dev/null
explain (analyze, buffers) with overdue as (select i.id as invoice_id, i.currency, i.due_date, greatest(i.outstanding_amount,0) as outstanding, (select l.course_enrollment_id from invoice_line l where l.invoice_id=i.id and l.course_enrollment_id is not null order by l.line_number limit 1) as course_enrollment_id, (select l.program_enrollment_id from invoice_line l where l.invoice_id=i.id and l.program_enrollment_id is not null order by l.line_number limit 1) as program_enrollment_id from invoice i where i.payment_status='OVERDUE'), resolved as (select o.invoice_id,o.currency,o.due_date,o.outstanding,coalesce(ce.student_id,pe.student_id) as student_id, case when ce.id is not null then c.name else p.name end as enrollment_name from overdue o left join course_enrollment ce on ce.id=o.course_enrollment_id left join course c on c.id=ce.course_id left join program_enrollment pe on pe.id=o.program_enrollment_id and ce.id is null left join program p on p.id=pe.program_id where ce.id is not null or pe.id is not null), grouped as (select r.student_id,r.currency,sum(r.outstanding) as total_debt,min(r.due_date) as oldest_due_date,(array_agg(r.enrollment_name order by r.due_date,r.invoice_id))[1] as enrollment_name from resolved r group by r.student_id,r.currency) select g.student_id,g.currency,g.total_debt,g.enrollment_name,(date '2026-11-17'-g.oldest_due_date) as debt_age_days,per.name,per.last_name,per.dni,count(*) over () from grouped g join student s on s.id=g.student_id join person per on per.id=s.person_id order by (date '2026-11-17'-g.oldest_due_date) desc nulls last,g.student_id,g.currency limit 25 offset 0;
\o
explain (analyze, buffers) with overdue as (select i.id as invoice_id, i.currency, i.due_date, greatest(i.outstanding_amount,0) as outstanding, (select l.course_enrollment_id from invoice_line l where l.invoice_id=i.id and l.course_enrollment_id is not null order by l.line_number limit 1) as course_enrollment_id, (select l.program_enrollment_id from invoice_line l where l.invoice_id=i.id and l.program_enrollment_id is not null order by l.line_number limit 1) as program_enrollment_id from invoice i where i.payment_status='OVERDUE'), resolved as (select o.invoice_id,o.currency,o.due_date,o.outstanding,coalesce(ce.student_id,pe.student_id) as student_id, case when ce.id is not null then c.name else p.name end as enrollment_name from overdue o left join course_enrollment ce on ce.id=o.course_enrollment_id left join course c on c.id=ce.course_id left join program_enrollment pe on pe.id=o.program_enrollment_id and ce.id is null left join program p on p.id=pe.program_id where ce.id is not null or pe.id is not null), grouped as (select r.student_id,r.currency,sum(r.outstanding) as total_debt,min(r.due_date) as oldest_due_date,(array_agg(r.enrollment_name order by r.due_date,r.invoice_id))[1] as enrollment_name from resolved r group by r.student_id,r.currency) select g.student_id,g.currency,g.total_debt,g.enrollment_name,(date '2026-11-17'-g.oldest_due_date) as debt_age_days,per.name,per.last_name,per.dni,count(*) over () from grouped g join student s on s.id=g.student_id join person per on per.id=s.person_id order by (date '2026-11-17'-g.oldest_due_date) desc nulls last,g.student_id,g.currency limit 25 offset 0;
drop index tmp_c6;

\echo ##### C7 attendance(program_section_enrollment_id, status) cubriente para el informe de ausencias (Q8)
\echo --- antes
\o /dev/null
explain (analyze, buffers) select a.program_section_enrollment_id, count(a.id), sum(case when a.status in ('PRESENT','LATE') then 1 else 0 end), sum(case when a.status = 'ABSENT' then 1 else 0 end) from attendance a where a.program_section_enrollment_id in (select id from subject_section_enrollment where program_section_id = 14011) group by a.program_section_enrollment_id;
\o
explain (analyze, buffers) select a.program_section_enrollment_id, count(a.id), sum(case when a.status in ('PRESENT','LATE') then 1 else 0 end), sum(case when a.status = 'ABSENT' then 1 else 0 end) from attendance a where a.program_section_enrollment_id in (select id from subject_section_enrollment where program_section_id = 14011) group by a.program_section_enrollment_id;
\echo --- despues
create index tmp_c7 on attendance (program_section_enrollment_id, status);
analyze attendance;
\o /dev/null
explain (analyze, buffers) select a.program_section_enrollment_id, count(a.id), sum(case when a.status in ('PRESENT','LATE') then 1 else 0 end), sum(case when a.status = 'ABSENT' then 1 else 0 end) from attendance a where a.program_section_enrollment_id in (select id from subject_section_enrollment where program_section_id = 14011) group by a.program_section_enrollment_id;
\o
explain (analyze, buffers) select a.program_section_enrollment_id, count(a.id), sum(case when a.status in ('PRESENT','LATE') then 1 else 0 end), sum(case when a.status = 'ABSENT' then 1 else 0 end) from attendance a where a.program_section_enrollment_id in (select id from subject_section_enrollment where program_section_id = 14011) group by a.program_section_enrollment_id;
drop index tmp_c7;
