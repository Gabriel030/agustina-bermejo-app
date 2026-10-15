do $$ begin
    if exists (select 1 from configuration_version where scope = 'COLLECTION_REMINDERS' and status in ('PROPOSED', 'SCHEDULED')) then
        raise exception 'Hay una propuesta o versión programada de Avisos de cobranza: resolverla antes de migrar (D-368)';
    end if;
end $$;

alter table configuration_version drop constraint configuration_version_scope_check;
alter table configuration_version add constraint configuration_version_scope_check check (scope in ('YEAR_TERMS', 'COHORT_ENTRY', 'LATE_PAYMENT', 'ISSUANCE',
    'ALLOCATION', 'ANNUAL_UPFRONT', 'COURSE_TERMS', 'IRREGULAR_TERMS', 'COLLECTION_REMINDERS', 'BILLING_DASHBOARD_GOALS', 'PROGRAM_REMINDER_SCHEDULE',
    'COURSE_REMINDER_SCHEDULE'));

alter table configuration_version drop constraint ck_configuration_version_topic;
alter table configuration_version add constraint ck_configuration_version_topic check (
    (scope in ('YEAR_TERMS', 'ANNUAL_UPFRONT') and program_id is not null and term_id is not null and course_id is null and cohort_id is null)
    or (scope = 'COHORT_ENTRY' and cohort_id is not null and program_id is null and term_id is null and course_id is null)
    or (scope = 'COURSE_TERMS' and course_id is not null and program_id is null and term_id is null and cohort_id is null)
    or (scope in ('LATE_PAYMENT', 'ISSUANCE', 'ALLOCATION', 'IRREGULAR_TERMS', 'COLLECTION_REMINDERS', 'BILLING_DASHBOARD_GOALS')
        and program_id is null and term_id is null and course_id is null and cohort_id is null)
    or (scope = 'PROGRAM_REMINDER_SCHEDULE' and term_id is null and course_id is null and cohort_id is null)
    or (scope = 'COURSE_REMINDER_SCHEDULE' and program_id is null and term_id is null and cohort_id is null));

create temporary table reminder_schedule_actor as
select coalesce(
    (select p.id from person p join person_role pr on pr.person_id = p.id join role r on r.id = pr.role_id where r.name = 'DIRECTOR' order by p.id limit 1),
    (select min(id) from person)) as person_id;

create temporary table reminder_schedule_source as
select coalesce(
    (select config_values from configuration_version
     where scope = 'COLLECTION_REMINDERS' and topic_key = 'COLLECTION_REMINDERS' and status in ('SCHEDULED', 'EFFECTIVE', 'SUPERSEDED')
       and effective_from <= current_date
     order by effective_from desc, decided_at desc, id desc limit 1),
    '{"emissionReminderEnabled": true, "daysAfterIssueReminder": 5, "daysBeforeDueReminder": 1, "daysAfterDueReminder": 10, "daysBeforeSuspensionReminder": 1}'::jsonb
) as legacy_values;

create temporary table reminder_schedule_template as
select jsonb_build_object('followsGroupSchedule', false, 'sendingOverrides', '{}'::jsonb, 'notices',
    (case when (legacy_values ->> 'emissionReminderEnabled')::boolean then
        jsonb_build_array(jsonb_build_object('noticeKey', 'issuance', 'messageType', 'ISSUANCE_NOTICE', 'milestone', 'ISSUANCE', 'offsetDays', 0,
            'channels', jsonb_build_array('IN_APP'), 'repetition', null::jsonb))
     else '[]'::jsonb end)
    || jsonb_build_array(
        jsonb_build_object('noticeKey', 'after-issue', 'messageType', 'AFTER_ISSUE_REMINDER', 'milestone', 'ISSUANCE',
            'offsetDays', (legacy_values ->> 'daysAfterIssueReminder')::int, 'channels', jsonb_build_array('IN_APP', 'EMAIL'), 'repetition', null::jsonb),
        jsonb_build_object('noticeKey', 'before-due', 'messageType', 'BEFORE_DUE_REMINDER', 'milestone', 'DUE_DATE',
            'offsetDays', -(legacy_values ->> 'daysBeforeDueReminder')::int, 'channels', jsonb_build_array('IN_APP', 'EMAIL'), 'repetition', null::jsonb),
        jsonb_build_object('noticeKey', 'after-due', 'messageType', 'AFTER_DUE_REMINDER', 'milestone', 'DUE_DATE',
            'offsetDays', (legacy_values ->> 'daysAfterDueReminder')::int, 'channels', jsonb_build_array('IN_APP', 'EMAIL'), 'repetition', null::jsonb),
        jsonb_build_object('noticeKey', 'before-suspension', 'messageType', 'BEFORE_SUSPENSION_REMINDER', 'milestone', 'SUSPENSION',
            'offsetDays', -(legacy_values ->> 'daysBeforeSuspensionReminder')::int, 'channels', jsonb_build_array('IN_APP', 'EMAIL'), 'repetition', null::jsonb))
) as schedule
from reminder_schedule_source;

insert into configuration_version (scope, topic_key, config_values, status, effective_from, change_reason, proposed_by_id, proposed_at,
                                   decided_by_id, decided_at, direct_publication)
select groups.scope, groups.scope, jsonb_build_object('type', groups.scope, 'schedule', template.schedule),
       'EFFECTIVE', current_date, 'Versión inicial migrada (D-368)', actor.person_id, now(), actor.person_id, now(), true
from (values ('PROGRAM_REMINDER_SCHEDULE'), ('COURSE_REMINDER_SCHEDULE')) as groups(scope)
cross join reminder_schedule_template template
cross join reminder_schedule_actor actor
where actor.person_id is not null;

insert into configuration_version_status_change (configuration_version_id, from_status, to_status, event, actor_process, changed_at, reason)
select id, null, 'EFFECTIVE', 'PUBLISH_FOR_TODAY', 'LegacyConfigurationMigration', proposed_at, change_reason
from configuration_version
where scope in ('PROGRAM_REMINDER_SCHEDULE', 'COURSE_REMINDER_SCHEDULE');

update configuration_version
set config_values = config_values - 'emissionReminderEnabled' - 'daysAfterIssueReminder' - 'daysBeforeDueReminder' - 'daysAfterDueReminder'
    - 'daysBeforeSuspensionReminder'
where scope = 'COLLECTION_REMINDERS';

drop table reminder_schedule_template;
drop table reminder_schedule_source;
drop table reminder_schedule_actor;
