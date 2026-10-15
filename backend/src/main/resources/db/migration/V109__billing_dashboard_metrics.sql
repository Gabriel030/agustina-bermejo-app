create index ix_invoice_issuing_reason_issue_date on invoice (issuing_reason, issue_date);
create index ix_invoice_status_change_to_status_business_date on invoice_status_change (to_status, business_date);
create index ix_bank_transfer_receipt_uploaded_at on bank_transfer_receipt (uploaded_at);

alter table configuration_version drop constraint configuration_version_scope_check;
alter table configuration_version add constraint configuration_version_scope_check check (scope in ('YEAR_TERMS', 'COHORT_ENTRY', 'LATE_PAYMENT', 'ISSUANCE',
    'ALLOCATION', 'ANNUAL_UPFRONT', 'COURSE_TERMS', 'IRREGULAR_TERMS', 'COLLECTION_REMINDERS', 'BILLING_DASHBOARD_GOALS'));

alter table configuration_version drop constraint ck_configuration_version_topic;
alter table configuration_version add constraint ck_configuration_version_topic check (
    (scope in ('YEAR_TERMS', 'ANNUAL_UPFRONT') and program_id is not null and term_id is not null and course_id is null and cohort_id is null)
    or (scope = 'COHORT_ENTRY' and cohort_id is not null and program_id is null and term_id is null and course_id is null)
    or (scope = 'COURSE_TERMS' and course_id is not null and program_id is null and term_id is null and cohort_id is null)
    or (scope in ('LATE_PAYMENT', 'ISSUANCE', 'ALLOCATION', 'IRREGULAR_TERMS', 'COLLECTION_REMINDERS', 'BILLING_DASHBOARD_GOALS')
        and program_id is null and term_id is null and course_id is null and cohort_id is null));

create temporary table dashboard_goals_default_actor as
select coalesce(
    (select p.id from person p join person_role pr on pr.person_id = p.id join role r on r.id = pr.role_id where r.name = 'DIRECTOR' order by p.id limit 1),
    (select min(id) from person)) as person_id;

insert into configuration_version (scope, topic_key, config_values, status, effective_from, change_reason, proposed_by_id, proposed_at,
                                   decided_by_id, decided_at, direct_publication)
select 'BILLING_DASHBOARD_GOALS', 'BILLING_DASHBOARD_GOALS',
       '{"type":"BILLING_DASHBOARD_GOALS","onTimePaymentAtLeast":{"fraction":0.850000},"overdueRatioAtMost":null,"receiptReviewBusinessDaysAtMost":null,"reminderPaidWithin72HoursAtLeast":null}'::jsonb,
       'EFFECTIVE', current_date, 'Versión inicial de las metas del tablero', a.person_id, now(), a.person_id, now(), true
from dashboard_goals_default_actor a
where a.person_id is not null;

insert into configuration_version_status_change (configuration_version_id, from_status, to_status, event, actor_process, changed_at, reason)
select id, null, 'EFFECTIVE', 'PUBLISH_FOR_TODAY', 'DashboardGoalsMigration', proposed_at, change_reason
from configuration_version
where scope = 'BILLING_DASHBOARD_GOALS';

drop table dashboard_goals_default_actor;
