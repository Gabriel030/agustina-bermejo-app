alter table permission_discrepancy_counter add column direction varchar(30);

update permission_discrepancy_counter counter
set direction = case
    when exists (
        select 1 from role_permission grant_row
        where grant_row.role = counter.role and grant_row.permission_id = counter.permission_id
    ) then 'LEGACY_DENIED_GRID_ALLOWED'
    else 'LEGACY_ALLOWED_GRID_DENIED'
end;

alter table permission_discrepancy_counter alter column direction set not null;

alter table permission_discrepancy_counter drop constraint uk_permission_discrepancy_counter;
alter table permission_discrepancy_counter
    add constraint uk_permission_discrepancy_counter unique (role, permission_id, direction);
alter table permission_discrepancy_counter
    add constraint permission_discrepancy_counter_direction_enum
    check (direction in ('LEGACY_ALLOWED_GRID_DENIED', 'LEGACY_DENIED_GRID_ALLOWED'));
