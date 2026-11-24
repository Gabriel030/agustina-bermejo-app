do $$
declare
    notification_literals text;
begin
    select string_agg(distinct quote_literal(m[1]), ', ') into notification_literals
    from pg_constraint c, regexp_matches(pg_get_constraintdef(c.oid), '''([A-Z_]+)''', 'g') as m
    where c.conname = 'notification_type_enum' and c.conrelid = 'notification'::regclass;
    execute 'alter table notification drop constraint notification_type_enum';
    execute 'alter table notification add constraint notification_type_enum check (type in (' || notification_literals
        || ', ''WAITLIST_PAUSED_BY_SCHEDULE_OVERLAP'', ''WAITLIST_EXPIRING_SOON'', ''WAITLIST_REMOVED_BY_STAFF'', ''WAITLIST_ENTRY_EXPIRED'')) not valid';
end $$;
alter table notification validate constraint notification_type_enum;
