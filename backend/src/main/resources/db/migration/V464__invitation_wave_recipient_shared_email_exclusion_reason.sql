alter table invitation_wave_recipient drop constraint invitation_wave_recipient_exclusion_reason_enum;
alter table invitation_wave_recipient add constraint invitation_wave_recipient_exclusion_reason_enum check (exclusion_reason in ('NO_EMAIL', 'ALREADY_ACTIVE', 'NOT_ELIGIBLE', 'IN_OTHER_WAVE', 'WAVE_CANCELLED', 'SHARED_EMAIL'));
