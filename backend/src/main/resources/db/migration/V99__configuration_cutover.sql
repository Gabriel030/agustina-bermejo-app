create temporary table migration_default_actor as
select coalesce(
    (select p.id from person p join person_role pr on pr.person_id = p.id join role r on r.id = pr.role_id where r.name = 'DIRECTOR' order by p.id limit 1),
    (select min(id) from person)) as person_id;

insert into configuration_version (scope, topic_key, program_id, term_id, config_values, status, effective_from, change_reason, proposed_by_id, proposed_at,
                                   decided_by_id, decided_at, direct_publication)
select 'YEAR_TERMS', 'YEAR_TERMS:P' || t.program_id || ':T' || t.term_id, t.program_id, t.term_id,
       jsonb_build_object('type', 'YEAR_TERMS', 'recurringFeeInvoicesCount', t.recurring_fee_invoices_count, 'enrollmentFeeCount', t.enrollment_fee_count,
           'firstEnrollmentFeeMonth', t.first_enrollment_fee_month, 'secondEnrollmentFeeMonth', t.second_enrollment_fee_month,
           'enrollmentFeeLeadMonths', t.enrollment_fee_lead_months, 'lateEnrollmentFeeDueDays', t.late_enrollment_fee_due_days,
           'recurringFeeDueDays', t.recurring_fee_due_days, 'withdrawSuspendedAtTermEnd', t.withdraw_suspended_at_term_end,
           'paysWhilePaused', t.pays_while_paused, 'blocksEnrollment', t.blocks_enrollment, 'blocksExamRegistration', t.blocks_exam_registration,
           'blocksCertificates', t.blocks_certificates,
           'requireEnrollmentFeeForFinalExamOnlyStudents', t.require_enrollment_fee_for_final_exam_only_students),
       'EFFECTIVE', t.created_at::date, 'Versión inicial migrada', t.created_by_id, t.created_at::timestamptz, t.created_by_id, t.created_at::timestamptz, true
from program_year_billing_template t
where not exists (select 1 from configuration_version v where v.scope = 'YEAR_TERMS' and v.topic_key = 'YEAR_TERMS:P' || t.program_id || ':T' || t.term_id);

insert into configuration_version (scope, topic_key, cohort_id, config_values, status, effective_from, change_reason, proposed_by_id, proposed_at,
                                   decided_by_id, decided_at, direct_publication)
select 'COHORT_ENTRY', 'COHORT_ENTRY:C' || c.id, c.id,
       jsonb_build_object('type', 'COHORT_ENTRY', 'discount', jsonb_build_object('fraction', c.entry_fee_discount)),
       'EFFECTIVE', c.created_at::date, 'Versión inicial migrada', c.created_by_id, c.created_at::timestamptz, c.created_by_id, c.created_at::timestamptz, true
from program_cohort c
where c.entry_fee_discount > 0
  and not exists (select 1 from configuration_version v where v.scope = 'COHORT_ENTRY' and v.topic_key = 'COHORT_ENTRY:C' || c.id);

insert into configuration_version (scope, topic_key, course_id, config_values, status, effective_from, change_reason, proposed_by_id, proposed_at,
                                   decided_by_id, decided_at, direct_publication)
select 'COURSE_TERMS', 'COURSE_TERMS:C' || c.id, c.id, '{"type":"COURSE_TERMS","monthlyInstallmentDueDays":10}'::jsonb,
       'EFFECTIVE', current_date, 'Versión inicial migrada', a.person_id, now(), a.person_id, now(), true
from course c, migration_default_actor a
where a.person_id is not null
  and not exists (select 1 from configuration_version v where v.scope = 'COURSE_TERMS' and v.topic_key = 'COURSE_TERMS:C' || c.id);

insert into configuration_version (scope, topic_key, program_id, term_id, config_values, status, effective_from, change_reason, proposed_by_id, proposed_at,
                                   decided_by_id, decided_at, direct_publication)
select 'ANNUAL_UPFRONT', 'ANNUAL_UPFRONT:P' || t.program_id || ':T' || t.term_id, t.program_id, t.term_id,
       jsonb_build_object('type', 'ANNUAL_UPFRONT', 'requestDeadline', to_jsonb(tm.start_date::date - 1), 'paymentDeadlineHours', 48),
       'EFFECTIVE', least(tm.start_date::date, t.created_at::date), 'Versión inicial migrada', t.created_by_id, t.created_at::timestamptz,
       t.created_by_id, t.created_at::timestamptz, true
from program_year_billing_template t join term tm on tm.id = t.term_id
where tm.start_date is not null
  and not exists (select 1 from configuration_version v where v.scope = 'ANNUAL_UPFRONT' and v.topic_key = 'ANNUAL_UPFRONT:P' || t.program_id || ':T' || t.term_id);

insert into configuration_version_status_change (configuration_version_id, from_status, to_status, event, actor_process, changed_at, reason)
select v.id, null, 'EFFECTIVE', 'PUBLISH_FOR_TODAY', 'LegacyConfigurationMigration', v.proposed_at, v.change_reason
from configuration_version v
where not exists (select 1 from configuration_version_status_change c where c.configuration_version_id = v.id);

drop table migration_default_actor;

alter table program_year_billing_template
    drop column recurring_fee_invoices_count,
    drop column enrollment_fee_count,
    drop column first_enrollment_fee_month,
    drop column second_enrollment_fee_month,
    drop column enrollment_fee_lead_months,
    drop column late_enrollment_fee_due_days,
    drop column recurring_fee_due_days,
    drop column withdraw_suspended_at_term_end,
    drop column pays_while_paused,
    drop column blocks_enrollment,
    drop column blocks_exam_registration,
    drop column blocks_certificates,
    drop column require_enrollment_fee_for_final_exam_only_students;

alter table program_cohort
    drop column enrollment_fee_month,
    drop column entry_fee_discount;

drop table late_payment_settings;
