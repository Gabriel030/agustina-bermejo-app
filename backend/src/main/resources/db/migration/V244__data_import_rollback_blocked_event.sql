alter table import_batch_event drop constraint import_batch_event_type_enum;
alter table import_batch_event add constraint import_batch_event_type_enum
    check (type in ('CREATED', 'FILE_UPLOADED', 'VALIDATED', 'COMMITTED', 'RETRIED', 'ROLLED_BACK', 'SEALED', 'ROLLBACK_BLOCKED'));
