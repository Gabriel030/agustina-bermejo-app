# Smoke de migracion V110 a V182

Objetivo: comprobar que la cadena de migraciones de `develop` aplica sobre una base en el esquema de `master` (V110), vacia y con datos legacy, y que BILLING queda apagado al arrancar el backend.

Resultado de la corrida del 5-nov-2026 (aplicando los `.sql` con `psql` en orden numerico, Postgres 16, sin Flyway):

| Escenario | Resultado |
|---|---|
| V1..V110 de `origin/master` sobre base vacia | OK (22 s) |
| V111..V182 de `origin/develop` sobre esa base vacia | OK. Queda `platform_feature` BILLING = enabled, mode FULL, version 0, `feature_change_log` vacio |
| V111..V182 sobre base V110 con datos de `legacy-sample-data.sql` | ABORTA en V146: `invoice_discount has 1 row(s) without student_discount_id; link or remove them before enforcing D-199` |
| Igual, sacando la fila de `invoice_discount` y reanudando en V146 | OK. V172 borro 3 calificaciones (ids 9101, 9102, 9105); el periodo sin fechas quedo con `start_date = end_date = enrollment_start = enrollment_end = momento de la migracion` |

No verificado en esta corrida: ejecucion real con Flyway (checksums y `flyway_schema_history`), arranque del backend contra la base migrada.

## 0. Requisitos

- Docker, git, un checkout de este repo. Todos los comandos desde la raiz del repo.
- Directorio de trabajo temporal: `export SMOKE=$(mktemp -d)`.

## 1. Postgres y esquema de master (V110)

```bash
docker rm -f smoke-pg 2>/dev/null
docker run -d --name smoke-pg -e POSTGRES_PASSWORD=postgres -e POSTGRES_DB=smoke -p 5544:5432 postgres:16-alpine
git fetch origin
mkdir -p "$SMOKE/master-mig"
git archive origin/master backend/src/main/resources/db/migration | tar -x -C "$SMOKE/master-mig"
```

Opcion A, con Flyway (la que da checksums e historial reales):

```bash
docker run --rm --network host -v "$SMOKE/master-mig/backend/src/main/resources/db/migration:/flyway/sql" \
  flyway/flyway:10 -url=jdbc:postgresql://localhost:5544/smoke -user=postgres -password=postgres migrate
```

Opcion B, con `psql` (la usada en la corrida del 5-nov; no deja `flyway_schema_history`):

```bash
cat > "$SMOKE/apply.sh" <<'SCRIPT'
#!/bin/bash
dir=$1; from=$2; to=$3
for f in $(ls "$dir" | sed -E 's/^V([0-9]+)__.*/\1 &/' | sort -n | awk -v a="$from" -v b="$to" '$1>=a && $1<=b {print $2}'); do
  docker exec -i smoke-pg psql -U postgres -d "${DB:-smoke}" -v ON_ERROR_STOP=1 -q -1 < "$dir/$f" >/dev/null 2>"$dir/../err.txt" \
    || { echo "FAIL $f"; head -5 "$dir/../err.txt"; exit 1; }
done
echo "OK $from..$to"
SCRIPT
chmod +x "$SMOKE/apply.sh"
"$SMOKE/apply.sh" "$SMOKE/master-mig/backend/src/main/resources/db/migration" 1 110
```

Verificacion: `docker exec smoke-pg psql -U postgres -d smoke -Atc "select max(version::int) from flyway_schema_history"` (solo opcion A) debe dar 110.

## 2. Congelar el punto V110 y preparar las dos variantes

```bash
docker exec smoke-pg psql -U postgres -c "create database smoke_empty template smoke" -c "create database smoke_legacy template smoke"
```

(`template` exige que nadie este conectado a `smoke`.)

## 3. Corrida vacia V110 a ultima

Con la rama a probar en el checkout actual (`release/*` o `develop`):

```bash
MIG=backend/src/main/resources/db/migration
DB=smoke_empty "$SMOKE/apply.sh" "$MIG" 111 182
```

Con Flyway: el mismo comando de la opcion A apuntando a `smoke_empty` y montando `$MIG`.

Esperado: `OK 111..182`. Verificar:

```bash
docker exec smoke-pg psql -U postgres -d smoke_empty -Atc "select code, enabled, mode, version from platform_feature where code = 'BILLING'; select count(*) from feature_change_log;"
```

Esperado `BILLING|t|FULL|0` y `0`: la migracion deja BILLING encendido; lo apaga el arranque del backend (paso 6).

## 4. Corrida con datos legacy

1. Antes de migrar, el diagnostico de solo lectura (no modifica nada; corre en una transaccion `read only` con `rollback`):

   ```bash
   docker exec -i smoke-pg psql -U postgres -d smoke_legacy < docs/release/pre-migration-checks.sql
   ```

   Contra una base real, usar el mismo archivo con `psql` y guardar la salida en el ticket del release.
2. Cargar los datos de ejemplo: `docker exec -i smoke-pg psql -U postgres -d smoke_legacy -v ON_ERROR_STOP=1 < docs/release/legacy-sample-data.sql`. El script usa `session_replication_role = replica` para insertar sin armar las cadenas de claves foraneas; es solo para el smoke, nunca para una base real. Cubre: periodos sin fechas (V174/V176), calificaciones duplicadas (V172), una fila de `invoice_discount` (V146).
3. Re-correr el diagnostico: debe listar 3 calificaciones a borrar, 2 periodos con fechas a inventar y 1 fila de `invoice_discount` sin `student_discount_id`.
4. Migrar: `DB=smoke_legacy "$SMOKE/apply.sh" "$MIG" 111 182`. Esperado en esta variante: `FAIL V146...` con el mensaje de la tabla de arriba.
5. Para seguir y ver V172/V174/V176: `docker exec smoke-pg psql -U postgres -d smoke_legacy -c "delete from invoice_discount"` y `DB=smoke_legacy "$SMOKE/apply.sh" "$MIG" 146 182`. Esperado `OK 146..182`.
6. Contar despues: `select id, type, sequence_number from grade_item order by id;` (quedan los ids mayores de cada grupo) y `select id, start_date, end_date, enrollment_start, enrollment_end from term where id >= 9000;`.

Extender el ejemplo con casos de abortos de V123, V130, V132 y V139 (becas ACTIVE, promociones con cuotas pendientes, asientos con `payment_id` huerfano) requiere armar las filas dependientes de cada tabla; el diagnostico de `pre-migration-checks.sql` replica exactamente las condiciones de esas guardas.

## 5. Con un dump real

```bash
pg_dump -Fc -h <host> -U <user> <db> > prod-v110.dump
createdb -h localhost -p 5544 -U postgres smoke_real
pg_restore -h localhost -p 5544 -U postgres -d smoke_real --no-owner prod-v110.dump
psql -h localhost -p 5544 -U postgres -d smoke_real -f docs/release/pre-migration-checks.sql
```

Si el diagnostico marca algo que aborta o borra, resolverlo en la base real (con aprobacion de quien corresponda) antes de migrar. Luego migrar `smoke_real` con el paso 3 y comparar conteos de antes y despues.

## 6. Arranque del backend y estado de BILLING

```bash
cd backend
SPRING_DATASOURCE_URL=jdbc:postgresql://localhost:5544/smoke_empty SPRING_DATASOURCE_USERNAME=postgres SPRING_DATASOURCE_PASSWORD=postgres \
JWT_SECRET=smoke-secret-smoke-secret-smoke-secret-1234 ./mvnw spring-boot:run
```

`JWT_SECRET` debe tener al menos 32 caracteres: con uno mas corto el backend no arranca (`JwtSecretPolicy`). Verificar el secreto de produccion antes de cortar el release.

Sin el perfil `local` y sin `PLATFORM_PROVISIONING_INITIAL_BILLING_MODE`, `InitialBillingModeRunner` aplica el default `OFF` de `application.properties:188` sobre BILLING si nunca fue tocado (version 0 y sin filas en `feature_change_log`) y registra un cambio con actor SERVICE y motivo "Aprovisionamiento inicial del instituto". Verificar tras el arranque:

```bash
docker exec smoke-pg psql -U postgres -d smoke_empty -Atc "select code, enabled, mode, version from platform_feature where code = 'BILLING'; select actor_kind, reason, after_json from feature_change_log;"
```

Esperado: `BILLING|f|OFF|1` y una fila `SERVICE`. Si el entorno de produccion define `PLATFORM_PROVISIONING_INITIAL_BILLING_MODE=FULL` o activa el perfil `local`, BILLING queda encendido. Este paso no se ejecuto el 5-nov.

## 7. Limpieza

```bash
docker rm -f smoke-pg
```

## 8. Requisitos de despliegue de la verificación pública de certificados

- `FORWARD_HEADERS_STRATEGY=native` y `TRUSTED_PROXIES_REGEX` exacto a la IP del proxy; sin eso el rate limit de `/public/certificates/{code}` es un único bucket global (ver `docs/context/overload-protection.md`).
- Desactivar o enmascarar los logs de acceso de Tomcat y del proxy para `/public/certificates/` y `/verificar/` (la URL lleva el código de verificación).
- La migración V194 regeneró los códigos: los PDFs viejos quedan huérfanos en disco como `<códigoViejo>.pdf` con datos personales; pendiente un runbook o job de limpieza.
