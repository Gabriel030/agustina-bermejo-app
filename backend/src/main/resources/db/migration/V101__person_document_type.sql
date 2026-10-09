alter table person add column document_type varchar(16) not null default 'DNI';
alter table person add constraint ck_person_document_type check (document_type in ('DNI', 'PASSPORT', 'OTHER'));
alter table person drop constraint uk_person_dni;
alter table person add constraint uk_person_document unique (document_type, dni);
