alter table refresh_token add column rotated_at timestamp null;
alter table refresh_token add column rotated_user_agent_hash varchar(64) null;
alter table refresh_token add column grace_used boolean not null default false;
alter table refresh_token add column parent_id bigint null;
alter table refresh_token add constraint fk_refresh_token_parent foreign key (parent_id) references refresh_token (id) on delete set null;
create index ix_refresh_token_parent on refresh_token (parent_id);
