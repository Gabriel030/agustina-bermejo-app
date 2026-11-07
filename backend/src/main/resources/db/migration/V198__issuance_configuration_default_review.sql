with actor as (
    select coalesce(
        (select p.id from person p join person_role pr on pr.person_id = p.id join role r on r.id = pr.role_id where r.name = 'DIRECTOR' order by p.id limit 1),
        (select min(id) from person)) as person_id
),
seeded as (
    insert into configuration_version (scope, topic_key, config_values, status, effective_from, change_reason, proposed_by_id, proposed_at, decided_by_id, decided_at,
                                       direct_publication)
    select 'ISSUANCE', 'ISSUANCE',
           '{"type":"ISSUANCE","issuanceMode":"REVIEW","continueIssuingWhileOverdue":true,"maxRetryAttempts":5,"retryBackoffHours":[1,3,6,12,24]}'::jsonb,
           'EFFECTIVE', current_date, 'Versión inicial migrada', a.person_id, now(), a.person_id, now(), true
    from actor a
    where a.person_id is not null
      and not exists (select 1 from configuration_version where scope = 'ISSUANCE')
    returning id, proposed_at, change_reason
)
insert into configuration_version_status_change (configuration_version_id, from_status, to_status, event, actor_process, changed_at, reason)
select id, null, 'EFFECTIVE', 'PUBLISH_FOR_TODAY', 'LegacyConfigurationMigration', proposed_at, change_reason
from seeded;
