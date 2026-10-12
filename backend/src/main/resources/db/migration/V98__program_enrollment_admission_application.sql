alter table program_enrollment add column admission_application_id bigint references program_enrollment_application (id);

update program_enrollment e
set admission_application_id = linked.application_id
from (
    select distinct on (l.program_enrollment_id) l.program_enrollment_id as enrollment_id, a.id as application_id
    from program_enrollment_application a
    join invoice_line l on l.invoice_id = a.admission_invoice_id
    where l.program_enrollment_id is not null
    order by l.program_enrollment_id, a.id
) linked
where linked.enrollment_id = e.id;

create unique index uk_program_enrollment_admission_application on program_enrollment (admission_application_id)
    where admission_application_id is not null;

alter table program_enrollment_application add column enrollment_failure_notified_at timestamp(6);
