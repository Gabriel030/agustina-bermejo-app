alter table notification validate constraint notification_type_enum;
alter table audit_log validate constraint ck_audit_log_action;
alter table audit_log validate constraint ck_audit_log_entity_type;
