alter table contact_consent drop constraint contact_consent_source_check;

alter table contact_consent add constraint contact_consent_source_check
    check (source in ('PROFILE', 'EMAIL_LINK', 'STAFF_ON_BEHALF', 'IMPORT', 'ACCOUNT_CREATION'));
