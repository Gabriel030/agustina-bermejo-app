alter table grade_item_audit alter column changed_by_id drop not null;

alter table grade_item_audit drop constraint grade_item_audit_kind_enum;

alter table grade_item_audit
    add constraint grade_item_audit_kind_enum check (kind in ('CORRECTION', 'FINAL_REVERT', 'RECOVERY', 'SYSTEM_REVERT'));
