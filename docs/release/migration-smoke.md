# Smoke de migracion: base de master a develop

Objetivo: comprobar, con Flyway real, que la cadena de migraciones de `develop` (o de la rama `release/*`) aplica sobre una base en el esquema de `master`, con datos, y que `pre-migration-checks.sql` detecta lo que aborta o descarta datos. Los tres archivos de este directorio (`pre-migration-checks.sql`, `legacy-sample-data.sql` y este procedimiento) se actualizan con cada migracion que descarta, transforma o puede abortar por datos.

Resultado de la corrida del 26-nov-2026 (Postgres 16 local, Flyway 12.4.0 con las mismas librerias que usa el backend, base de master truncada en V445, migraciones de `origin/develop` hasta V471):

| Escenario | Resultado |
|---|---|
| V1..V445 de `origin/master` sobre base vacia | OK, `applied=297 schemaVersion=445`, 5,3 s |
| `legacy-sample-data.sql` sobre esa base V445 | OK con `ON_ERROR_STOP` |
| `pre-migration-checks.sql` sobre la base V445 vacia y sobre la cargada | Corre completo, sin errores ni `current transaction is aborted`. Con los datos de ejemplo lista: 1 egreso ENABLED + 1 PENDING_REVIEW (V446/V451), 4 franjas (V463), 1 constancia de examen con codigo repetido (V465), 0 solapamientos (V470) |
| V446..V471 sobre la base V445 cargada | OK, `applied=20 schemaVersion=471`, 0,3 s. `graduation_request` 9001 = ENABLED y 9002 = PENDING_REVIEW; `verifiable_document` con 1 egreso + 1 constancia de examen (la de codigo repetido se omite, como avisa el diagnostico); franjas con `occupies/active/time_span` calculados (la de la comision INACTIVE queda en `f`); proyeccion `section_schedule_slot_instructor` con 3 filas; 4 plantillas por tipo de documento |
| Idem, con una franja extra que pisa el aula de otra comision ACTIVE | El diagnostico `[V470]` lista 2 filas y V470 ABORTA: `FlywayMigrateException: Failed to execute script V470__exclude_schedule_slot_overlaps.sql` |
| Base V1..V471 vacia + `legacy-sample-data.sql` | OK: el script tambien carga sobre el esquema actual (los triggers de V463/V470 calculan las franjas; `certificate_review_status` queda en `NOT_REQUIRED` por el default) |
| `pre-migration-checks.sql` sobre una base V110 | No aborta la sesion; ejecuta los bloques `[V111]`..`[V428]`. Los bloques de migraciones cuyas tablas todavia no existen en V110 (V266, V428, V452, V465) dan un `relation ... does not exist` aislado, sin efecto en las demas consultas |

No verificado en esta corrida: Postgres y Flyway por Docker (no habia daemon de Docker: se uso un Postgres 16 instalado y el lanzador Java del paso 1), arranque del backend contra la base migrada (paso 6), dump real de produccion (paso 5), los bloques `[V111]`..`[V428]` con datos que disparen cada aborto.

## 0. Requisitos

- git, `psql`/`createdb` y un Postgres 16 (Docker o instalacion local); JDK 21+ con `~/.m2` poblado por una compilacion previa del backend (para el lanzador de Flyway) o Docker para la imagen oficial de Flyway. Todos los comandos desde la raiz del repo.
- Directorio temporal: `export SMOKE=$(mktemp -d)`.
- Version de master de partida: `export MASTER_V=$(git ls-tree --name-only origin/master backend/src/main/resources/db/migration/ | sed -E 's/.*\/V([0-9]+)__.*/\1/' | sort -n | tail -1)`. Si el release parte de una version anterior a la ultima de master (produccion sigue en V445, por ejemplo), fijar `MASTER_V=445`: el paso 1 trunca las migraciones a esa version.

## 1. Postgres y esquema de master

```bash
docker rm -f smoke-pg 2>/dev/null
docker run -d --name smoke-pg -e POSTGRES_PASSWORD=postgres -p 5544:5432 postgres:16-alpine
export PGHOST=localhost PGPORT=5544 PGUSER=postgres PGPASSWORD=postgres
git fetch origin
mkdir -p "$SMOKE/master-mig"
git archive origin/master backend/src/main/resources/db/migration | tar -x -C "$SMOKE/master-mig"
(cd "$SMOKE/master-mig/backend/src/main/resources/db/migration" && for f in V*.sql; do n=${f#V}; n=${n%%__*}; [ "$n" -gt "$MASTER_V" ] && rm "$f"; done)
createdb smoke
```

Si ya hay un Postgres local, saltear el `docker` y exportar `PGHOST/PGPORT/PGUSER/PGPASSWORD` de ese servidor.

Opcion A1, lanzador Java con las librerias del backend (la usada el 26-nov):

```bash
cat > "$SMOKE/Mig.java" <<'JAVA'
import org.flywaydb.core.Flyway;

public class Mig {
    public static void main(String[] a) {
        var f = Flyway.configure()
                .dataSource("jdbc:postgresql://" + System.getenv("PGHOST") + ":" + System.getenv("PGPORT") + "/" + a[0],
                        System.getenv("PGUSER"), System.getenv("PGPASSWORD"))
                .locations("filesystem:" + a[1])
                .target(a.length > 2 ? a[2] : "latest")
                .load();
        var r = f.migrate();
        System.out.println("applied=" + r.migrationsExecuted + " schemaVersion=" + r.targetSchemaVersion);
    }
}
JAVA
M2=~/.m2/repository
export SMOKE_CP="$M2/org/flywaydb/flyway-core/12.4.0/flyway-core-12.4.0.jar:$M2/org/flywaydb/flyway-database-postgresql/12.4.0/flyway-database-postgresql-12.4.0.jar:$M2/org/postgresql/postgresql/42.7.14/postgresql-42.7.14.jar:$M2/com/fasterxml/jackson/core/jackson-databind/2.21.7/jackson-databind-2.21.7.jar:$M2/com/fasterxml/jackson/core/jackson-core/2.21.7/jackson-core-2.21.7.jar:$M2/com/fasterxml/jackson/core/jackson-annotations/2.21/jackson-annotations-2.21.jar:$M2/tools/jackson/core/jackson-databind/3.1.7/jackson-databind-3.1.7.jar:$M2/tools/jackson/core/jackson-core/3.1.7/jackson-core-3.1.7.jar"
java -cp "$SMOKE_CP" "$SMOKE/Mig.java" smoke "$SMOKE/master-mig/backend/src/main/resources/db/migration"
```

Las versiones de las librerias salen de `~/.m2` (las resuelve `backend/pom.xml`); si cambiaron, ajustar los numeros de los `.jar`. Esperado con `MASTER_V=445`: `applied=297 schemaVersion=445`.

Opcion A2, imagen oficial de Flyway (no verificada el 26-nov):

```bash
docker run --rm --network host -v "$SMOKE/master-mig/backend/src/main/resources/db/migration:/flyway/sql" \
  flyway/flyway:12 -url=jdbc:postgresql://localhost:5544/smoke -user=postgres -password=postgres migrate
```

Verificacion: `psql smoke -Atc "select max(version::int) from flyway_schema_history where success"` debe dar `$MASTER_V`.

## 2. Congelar el punto de master y preparar las variantes

```bash
psql postgres -c "create database smoke_empty template smoke" -c "create database smoke_legacy template smoke"
```

(`template` exige que nadie este conectado a `smoke`.)

## 3. Corrida vacia, master a develop

Con la rama a probar en el checkout actual (`release/*` o `develop`, despues de `git fetch`):

```bash
MIG=$PWD/backend/src/main/resources/db/migration
java -cp "$SMOKE_CP" "$SMOKE/Mig.java" smoke_empty "$MIG"
```

Esperado: `applied=<migraciones posteriores a MASTER_V> schemaVersion=<ultima de la rama>` (26-nov con V445: `applied=20 schemaVersion=471`). Verificar la plataforma:

```bash
psql smoke_empty -Atc "select code, enabled, mode, version from platform_feature where code = 'BILLING'; select count(*) from feature_change_log;"
```

Esperado `BILLING|t|FULL|0` y `0`: la migracion deja BILLING encendido; lo apaga el arranque del backend (paso 6).

## 4. Corrida con datos de ejemplo

1. Cargar los datos: `psql smoke_legacy -v ON_ERROR_STOP=1 -f docs/release/legacy-sample-data.sql`. Son filas validas contra el esquema (ids 9001..9999), sin desactivar triggers ni claves foraneas. Cubren: periodos con `enrollment_start/enrollment_end`, dos solicitudes de egreso APPROVED sin `decided_by_id` (una con certificado de egreso VALID y otra sin: V446/V451), una constancia de examen con el mismo codigo que el certificado de egreso (V465), y cuatro franjas horarias de comisiones ACTIVE e INACTIVE (V463/V470). El script no provoca abortos: esos casos se arman a mano (punto 5).
2. Diagnostico de solo lectura (cada consulta es una sentencia propia; el script setea `default_transaction_read_only = on`):

   ```bash
   psql smoke_legacy -f docs/release/pre-migration-checks.sql
   ```

   El script lee la version de `flyway_schema_history` y corre solo los bloques `[V<n>]` de migraciones pendientes (`n` mayor que la version actual). Los dos bloques `[V470]` corren siempre y van al final porque `SectionScheduleSlotExcludeMigrationTest` los lee de ahi. Esperado sobre `smoke_legacy` en V445: `[V446/V451]` 1 fila ENABLED y 1 PENDING_REVIEW, `[V452]` 0 filas, `[V463]` 4, `[V465]` 1 fila, los dos `[V470]` 0 filas, cierre `===== fin =====` y ningun `ERROR`.
3. Migrar: `java -cp "$SMOKE_CP" "$SMOKE/Mig.java" smoke_legacy "$MIG"`. Esperado: `applied=<n> schemaVersion=<ultima>`.
4. Comprobar las transformaciones:

   ```bash
   psql smoke_legacy -Atc "select id, certificate_review_status from graduation_request order by id; select document_type, count(*) from verifiable_document group by 1; select id, occupies, active, time_span from section_schedule_slot order by id;"
   ```

   Esperado: `9001|ENABLED`, `9002|PENDING_REVIEW`; `GRADUATION_CERTIFICATE|1` y `EXAM_REGISTRATION_CERTIFICATE|1` (la constancia 9002 con codigo repetido no se registra); 3 franjas con `t|t` y la de la comision INACTIVE con `f|f`.
5. Caso que aborta (V470): sobre una copia `smoke_abort` (`create database smoke_abort template smoke`) cargar el ejemplo y agregar `insert into section_schedule_slot (subject_section_id, classroom_id, day_of_week, start_time, end_time) values (9002, 9001, 'MONDAY', '10:00', '12:00');`. El diagnostico `[V470] Franjas de aula` lista 2 filas y la migracion falla en V470. Con el mismo molde se arma el otro aborto vigente desde V445: un codigo repetido entre `graduation_certificate` y `regular_student_certificate` (V452).

Que cubre el diagnostico para las migraciones recientes:

| Migracion | Efecto sobre datos | Bloque |
|---|---|---|
| V446 / V451 | Reclasifica `certificate_review_status` de los egresos importados APPROVED sin `decided_by_id` (ENABLED o PENDING_REVIEW); V447 valida los CHECK y no puede fallar con esos valores | `[V446/V451]` informativo, cantidad por estado resultante |
| V452 | Puebla `verifiable_document` desde los certificados de egreso y de alumno regular; ABORTA si un codigo se repite entre las dos tablas | `[V452]` |
| V463 | Recalcula las franjas horarias con un UPDATE (informativo) | `[V463]` |
| V465 | Registra las constancias de examen; las que repiten un codigo ya registrado se omiten sin avisar | `[V465]` informativo |
| V470 | Crea los EXCLUDE de aula y de docente; ABORTA si hay franjas solapadas entre comisiones ACTIVE | `[V470]` x2 |

V450, V454, V455, V460, V462, V464, V466..V469 y V471 solo agregan estructura, permisos, plantillas o constraints que aceptan los valores existentes (se reviso su SQL): no tienen bloque.

## 5. Con un dump real

```bash
pg_dump -Fc -h <host> -U <user> <db> > prod.dump
createdb smoke_real
pg_restore -d smoke_real --no-owner prod.dump
psql smoke_real -f docs/release/pre-migration-checks.sql
```

Si el diagnostico marca algo que aborta o borra, resolverlo en la base real (con aprobacion de quien corresponda) antes de migrar. Luego migrar `smoke_real` con el paso 3 y comparar conteos de antes y despues. Guardar la salida del diagnostico en el ticket del release.

## 6. Arranque del backend y estado de BILLING

```bash
cd backend
SPRING_DATASOURCE_URL=jdbc:postgresql://localhost:5544/smoke_empty SPRING_DATASOURCE_USERNAME=postgres SPRING_DATASOURCE_PASSWORD=postgres \
JWT_SECRET=smoke-secret-smoke-secret-smoke-secret-1234 ./mvnw spring-boot:run
```

`JWT_SECRET` debe tener al menos 32 caracteres: con uno mas corto el backend no arranca (`JwtSecretPolicy`). Verificar el secreto de produccion antes de cortar el release.

Sin el perfil `local` y sin `PLATFORM_PROVISIONING_INITIAL_BILLING_MODE`, `InitialBillingModeRunner` aplica el default `OFF` de `application.properties:188` sobre BILLING si nunca fue tocado (version 0 y sin filas en `feature_change_log`) y registra un cambio con actor SERVICE y motivo "Aprovisionamiento inicial del instituto". Verificar tras el arranque:

```bash
psql smoke_empty -Atc "select code, enabled, mode, version from platform_feature where code = 'BILLING'; select actor_kind, reason, after_json from feature_change_log;"
```

Esperado: `BILLING|f|OFF|1` y una fila `SERVICE`. Si el entorno de produccion define `PLATFORM_PROVISIONING_INITIAL_BILLING_MODE=FULL` o activa el perfil `local`, BILLING queda encendido. Este paso no se ejecuto el 26-nov.

## 7. Limpieza

```bash
docker rm -f smoke-pg
```

## 8. Requisitos de despliegue de la verificación pública de certificados

- `FORWARD_HEADERS_STRATEGY=native` y `TRUSTED_PROXIES_REGEX` exacto a la IP del proxy; sin eso el rate limit de `/public/certificates/{code}` es un único bucket global (ver `docs/context/overload-protection.md`).
- Desactivar o enmascarar los logs de acceso de Tomcat y del proxy para `/public/certificates/` y `/verificar/` (la URL lleva el código de verificación).
- La migración V194 regeneró los códigos: los PDFs viejos quedan huérfanos en disco como `<códigoViejo>.pdf` con datos personales; pendiente un runbook o job de limpieza.

## 9. Recuperacion si V429 falla (validacion de annual_upfront_request.payment_id)

V428 aborta con mensaje claro si hay huerfanos; si aun asi V429 falla (por ejemplo, una fila huerfana aparecio entre V428 y V429), la constraint `fk_annual_upfront_request_payment` queda `NOT VALID` y Flyway marca V429 como fallida en `flyway_schema_history`.

Recuperacion:

1. Corregir las filas huerfanas: apuntar `payment_id` al pago correcto o dejarlo en null (no borrar solicitudes). Detectarlas con la consulta [V428] de `docs/release/pre-migration-checks.sql`.
2. Ejecutar `flyway repair` para quitar el registro fallido de V429.
3. Reiniciar el backend: Flyway reaplica V429 y valida la constraint.
