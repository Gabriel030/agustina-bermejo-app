alter table commission_closure add column closed_automatically boolean not null default false;

alter table commission_closure drop constraint ck_commission_closure_closed_has_report;
alter table commission_closure add constraint ck_commission_closure_closed_has_report check (
    status <> 'CLOSED'
    or (report_number is not null and closed_at is not null
        and (closed_automatically
             or (book is not null and folio is not null and closed_by_id is not null)))) not valid;

alter table commission_closure add constraint ck_commission_closure_automatic_has_no_actor check (
    not closed_automatically or closed_by_id is null) not valid;

alter table commission_closure_audit alter column actor_person_id drop not null;

alter table commission_closure_audit drop constraint ck_commission_closure_audit_action;
alter table commission_closure_audit add constraint ck_commission_closure_audit_action check (
    action in ('CONFIRMED_BY_TEACHER', 'CLOSED', 'CLOSED_WITHOUT_TEACHER_CONFIRMATION', 'REOPENED',
               'EXCEPTION_GRANTED', 'EXCEPTION_REVOKED', 'CLOSED_AUTOMATICALLY')) not valid;

alter table commission_closure_audit add constraint ck_commission_closure_audit_actor_required check (
    actor_person_id is not null or action = 'CLOSED_AUTOMATICALLY') not valid;
