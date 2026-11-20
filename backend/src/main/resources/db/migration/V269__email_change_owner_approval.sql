alter table email_verification_token add column awaiting_owner_approval boolean not null default false;
alter table email_verification_token add column owner_approved_at timestamp(6);
alter table email_verification_token add constraint ck_email_verification_token_owner_approval
    check (not (awaiting_owner_approval and owner_approved_at is not null));
