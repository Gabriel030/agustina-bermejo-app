alter table attendance_change_request add column reverted_by_id bigint;
alter table attendance_change_request add column reverted_at timestamp(6);
alter table attendance_change_request add column revert_reason varchar(500);
alter table attendance_change_request
    add constraint fk_acr_reverted_by foreign key (reverted_by_id) references person;
create index if not exists ix_acr_reverted_by on attendance_change_request (reverted_by_id);
