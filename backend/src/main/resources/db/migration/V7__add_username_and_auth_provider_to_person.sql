-- Spec JWT y login. username como identificador de login, separado de email (decisión
-- explícita del usuario). Nullable + unique, mismo criterio que password hoy: hay personas
-- con grant_system_access = false que no necesitan poder loguearse.
alter table person add column username varchar(255) unique;

-- auth_provider deja un seam para un futuro login con Google (feature aparte, no
-- implementada acá) sin tener que volver a migrar esta tabla. Not null porque, a
-- diferencia de username/password, siempre tiene un valor por defecto sensato.
alter table person add column auth_provider varchar(20) not null default 'LOCAL';
