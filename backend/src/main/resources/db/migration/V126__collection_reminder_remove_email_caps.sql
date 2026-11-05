update configuration_version
set config_values = config_values - 'minDaysBetweenEmails' - 'maxReminderEmailsPerMonth'
where scope = 'COLLECTION_REMINDERS';

update configuration_version
set config_values = jsonb_set(config_values, '{schedule,sendingOverrides}', (config_values #> '{schedule,sendingOverrides}') - 'minDaysBetweenEmails' - 'maxReminderEmailsPerMonth')
where scope in ('PROGRAM_REMINDER_SCHEDULE', 'COURSE_REMINDER_SCHEDULE')
  and jsonb_typeof(config_values #> '{schedule,sendingOverrides}') = 'object';
