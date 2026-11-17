select 'students' k, count(*) from student where id between 20001 and 25000
union all select 'sections', count(*) from subject_section where id between 14001 and 14300
union all select 'enrollments', count(*) from subject_section_enrollment where id >= 100000
union all select 'attendance', count(*) from attendance where id >= 1000000
union all select 'notifications', count(*) from notification where message like 'Notificación perf %'
union all select 'audit_log', count(*) from audit_log where reason like 'perf %'
union all select 'invoices', count(*) from invoice where id >= 500000
union all select 'debtors', count(distinct student_current_account_id) from invoice where id >= 500000 and payment_status='OVERDUE'
union all select 'checksum', (sum(id*enrollment_hash))::bigint from (select id, hashtext(status||program_section_id::text) enrollment_hash from subject_section_enrollment where id>=100000) x;
