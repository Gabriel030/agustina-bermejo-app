alter table announcement_recipient add column read_at timestamp(6);

create index ix_announcement_recipient_unread on announcement_recipient (announcement_id) where read_at is null;
