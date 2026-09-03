-- Épica 1 Fase 2 (Login con Google): identifica de forma estable la cuenta de Google vinculada
-- a una Person, ya sea porque se registró directo con Google o porque se auto-vinculó una cuenta
-- LOCAL existente al loguearse con Google por primera vez con el mismo email verificado.
alter table person add column google_subject_id varchar(255);
alter table person add constraint uk_person_google_subject_id unique (google_subject_id);
