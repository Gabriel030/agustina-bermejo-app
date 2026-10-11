alter table configuration_version drop constraint configuration_version_scope_check;
alter table configuration_version add constraint configuration_version_scope_check
    check (scope in ('YEAR_TERMS', 'COHORT_ENTRY', 'LATE_PAYMENT', 'ISSUANCE', 'ALLOCATION', 'ANNUAL_UPFRONT', 'COURSE_TERMS', 'IRREGULAR_TERMS', 'COLLECTION_REMINDERS'));

alter table configuration_version drop constraint ck_configuration_version_topic;
alter table configuration_version add constraint ck_configuration_version_topic check (
    (scope in ('YEAR_TERMS', 'ANNUAL_UPFRONT') and program_id is not null and term_id is not null and course_id is null and cohort_id is null)
    or (scope = 'COHORT_ENTRY' and cohort_id is not null and program_id is null and term_id is null and course_id is null)
    or (scope = 'COURSE_TERMS' and course_id is not null and program_id is null and term_id is null and cohort_id is null)
    or (scope in ('LATE_PAYMENT', 'ISSUANCE', 'ALLOCATION', 'IRREGULAR_TERMS', 'COLLECTION_REMINDERS') and program_id is null and term_id is null and course_id is null and cohort_id is null));

create temporary table migration_default_actor as
select coalesce(
    (select p.id from person p join person_role pr on pr.person_id = p.id join role r on r.id = pr.role_id where r.name = 'DIRECTOR' order by p.id limit 1),
    (select min(id) from person)) as person_id;

with inserted as (
    insert into configuration_version (scope, topic_key, config_values, status, effective_from, change_reason, proposed_by_id, proposed_at,
                                       decided_by_id, decided_at, direct_publication)
    select 'COLLECTION_REMINDERS', 'COLLECTION_REMINDERS',
           jsonb_build_object('type', 'COLLECTION_REMINDERS', 'emissionReminderEnabled', true, 'daysAfterIssueReminder', 5, 'daysBeforeDueReminder', 1,
               'daysAfterDueReminder', 10, 'daysBeforeSuspensionReminder', 1, 'suspensionNoticeEnabled', true, 'postWithdrawalSummaryMonths', 6,
               'minDaysBetweenEmails', 3, 'maxReminderEmailsPerMonth', 4, 'emailWindowStart', '09:00:00', 'emailWindowEnd', '11:00:00',
               'dailyEmailCap', 250, 'contactListDaysOverdue', 14, 'contactListOverdueInstallments', 2),
           'EFFECTIVE', current_date, 'Versión inicial migrada', a.person_id, now(), a.person_id, now(), true
    from migration_default_actor a
    where a.person_id is not null
    returning id, proposed_at, change_reason
)
insert into configuration_version_status_change (configuration_version_id, from_status, to_status, event, actor_process, changed_at, reason)
select id, null, 'EFFECTIVE', 'PUBLISH_FOR_TODAY', 'LegacyConfigurationMigration', proposed_at, change_reason
from inserted;

drop table migration_default_actor;
