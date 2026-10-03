update person set email = null where email is not null and trim(email) = '';

do $$
begin
    if exists (
        select 1 from person
        where email is not null
        group by lower(trim(email))
        having count(*) > 1
    ) then
        raise exception 'Hay personas con emails duplicados al normalizar (lower/trim); resolverlos manualmente antes de migrar';
    end if;
end $$;

update person set email = lower(trim(email)) where email is not null and email <> lower(trim(email));

do $$
begin
    if exists (
        select 1 from person
        where email is not null
          and email !~ '^[^@[:space:]]+@[^@[:space:]]+$'
    ) then
        raise exception 'Hay personas con emails que no tienen formato válido (usuario@dominio); corregirlos manualmente antes de migrar';
    end if;
end $$;

create unique index if not exists uk_person_email_normalized on person (lower(email));
