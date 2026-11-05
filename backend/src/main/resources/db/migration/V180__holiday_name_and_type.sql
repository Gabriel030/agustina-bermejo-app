alter table holiday add column name varchar(255);
alter table holiday add column type varchar(30);

update holiday set name = description, type = 'NATIONAL';

alter table holiday alter column name set not null;
alter table holiday alter column type set not null;
alter table holiday alter column description drop not null;
