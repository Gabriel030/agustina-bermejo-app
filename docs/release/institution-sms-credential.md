# Credencial de SMS en `institution` (V214)

La migración V214 borra de `institution` las columnas `facebook`, `tweeter`, `pref_notas`, `pref_calendario`, `pref_asignar_curso`, `sms_url`, `sms_usuario` y `sms_password`. Ningún código las leía ni escribía.

Antes de migrar, correr `docs/release/pre-migration-checks.sql` sobre cada base y mirar la fila del bloque `[V214]`.

Si `sms_password` o `sms_usuario` es mayor a cero, alguien cargó una credencial a mano:

1. Anotar `sms_url` y `sms_usuario` de esas filas antes de migrar (`select id, sms_url, sms_usuario from institution where sms_password is not null`), sin copiar la contraseña a ningún lado.
2. Rotar la credencial en el proveedor de SMS.
3. Recordar que la contraseña puede seguir en backups y snapshots anteriores a V214: rotarla es la única forma de neutralizarla.

Si el conteo es cero en todas las bases, no hay nada que hacer.
