create index if not exists ix_notification_recipient_unread on notification (recipient_id) where read = false;
