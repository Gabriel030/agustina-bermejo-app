alter table announcement add column status varchar(16) not null default 'SENT';
alter table announcement add column scheduled_for timestamp;
alter table announcement add column sent_at timestamp;
alter table announcement alter column status drop default;

update announcement set sent_at = created_at where sent_at is null;

create index ix_announcement_due on announcement (status, scheduled_for)
    where status = 'SCHEDULED';
