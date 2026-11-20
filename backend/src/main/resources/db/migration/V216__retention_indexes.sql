create index if not exists ix_login_attempt_attempted_at on login_attempt (attempted_at);
create index if not exists ix_notification_created_at on notification (created_at);
create index if not exists ix_notification_delivery_scheduled_for on notification_delivery (scheduled_for);
create index if not exists ix_refresh_token_expires_at on refresh_token (expires_at);
drop index if exists idx_notification_recipient;
