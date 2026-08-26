-- Spec Gestión de personas (RF-AL-13 necesita poder editar teléfono, pero Person no
-- tenía ese campo). Nullable, sin default: las Person existentes quedan sin teléfono
-- cargado hasta que lo editen.
alter table person add column phone_number varchar(30);
