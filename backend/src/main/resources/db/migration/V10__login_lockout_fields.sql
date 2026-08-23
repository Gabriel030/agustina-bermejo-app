-- RF-AUTH-06: bloqueo temporal tras N intentos fallidos.
alter table person add column failed_login_attempts integer not null default 0;
alter table person add column locked_until timestamp;
