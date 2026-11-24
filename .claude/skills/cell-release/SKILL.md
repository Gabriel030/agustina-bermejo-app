---
name: cell-release
description: Abrir y mergear un release de develop a master (líder técnico, con el coordinador). Fija el orden: cadencia y puerta de despliegue, rama release/<n>-rc cortada en un SHA verde de develop, dossier (commits, migraciones, smoke con Flyway, contrato, riesgos, rollback, variables), etiqueta contract-break-approved, merge y verificación del backport. Usar en "abrí el release", "cortá el release", "mergeá el release", "cell-release".
---

# cell-release (líder técnico; la etiqueta y los avisos los maneja el coordinador)

Entrada: develop acumuló trabajo desde el último release. Salida: un PR `release/<n>-rc` contra `master` con dossier, mergeado con CI verde. Abrir o mergear NO despliega: el despliegue es de Gabriel. Marco general en `docs/context/workflow.md` §6 y puerta de despliegue en `docs/context/cell.md` regla 17.

## 1. Condición de cadencia y puerta de despliegue
1. Cadencia (`workflow.md` §6): 15 PRs mergeados a `develop` desde el último release, o al cierre de la jornada con al menos 5, o antes de abrir una ventana de migraciones. Contar: `git fetch origin && git log --first-parent --oneline origin/master..origin/develop | wc -l`.
2. Puerta de despliegue (`cell.md` regla 17): si el release anterior no está desplegado, NO abrir uno nuevo, salvo decisión expresa de Gabriel (pedirla al coordinador y dejarla escrita en el PR). Un feature de backend sin pantalla ni uso real no cuenta como entregado.
3. Antes de seguir: `gh pr list --state open --base master --json number,title,headRefName` (no puede haber otro release abierto sin cerrar) y `git worktree list`.

## 2. Elegir el SHA verde y cortar la rama
1. Elegir un SHA de `develop` con el CI completo en verde en el run de `push` (no el de PR): `gh run list --workflow ci.yml --branch develop --event push --json headSha,conclusion,databaseId,createdAt --limit 20`. Verde = todos los jobs en `success` (un `skipped` legítimo cuenta; un run con REUSE también vale si terminó en verde). No elegir la punta si su run está pendiente o en rojo.
2. Cortar la rama en ESE SHA, con git:
   ```bash
   git fetch origin
   git push origin <sha>:refs/heads/release/<n>-rc
   ```
   NUNCA con la herramienta de GitHub de crear rama: en el release 30 cortó la rama desde la punta de `develop`, no desde el SHA verde, y obligó a abrir otra rama. Verificar: `git rev-parse origin/release/<n>-rc` debe dar `<sha>`.
3. El nombre debe empezar con `release/`: `.github/workflows/backport.yml` decide por el nombre de la rama del PR mergeado y con `release/*` no abre backport. Con otro prefijo se dispara un PR "Backport: master → develop" indebido.
4. Abrir el PR: `gh pr create --base master --head release/<n>-rc` con el dossier (paso 3) en el cuerpo. Si entra algo a `develop` después del SHA elegido, NO se trae: el release es ese SHA.

## 3. Dossier (en el cuerpo del PR)
1. Commits y archivos desde master: `git log --first-parent --oneline origin/master..<sha>` y `git diff --stat origin/master <sha>`. Revisar también `git diff origin/master <sha>` para detectar features perdidas en refactors masivos (`workflow.md` §6).
2. Migraciones nuevas V<a>..V<b>: `git diff --name-status origin/master <sha> -- backend/src/main/resources/db/migration`. Verificar: sin números duplicados (`ls ... | sed -E 's/^V([0-9]+)__.*/\1/' | sort -n | uniq -d` debe quedar vacío), contiguas con lo que ya hay en master, y ninguna migración ya presente en master con estado `M` (modificada) o `D`: una migración aplicada no se toca, cambiaría el checksum y `flyway validate` falla en producción.
3. Smoke con Flyway real sobre Postgres, siguiendo `docs/release/migration-smoke.md` (los rangos del doc son de la corrida del 5-nov: ajustar a V<master>..V<b>). Base = esquema de `origin/master`, luego las migraciones nuevas del SHA, con la opción A (Flyway `migrate`) y después `flyway validate` (checksums y `flyway_schema_history`). Correr también el diagnóstico `docs/release/pre-migration-checks.sql` y la variante con datos legacy (`docs/release/legacy-sample-data.sql`). Pegar la salida en el PR. Si no se pudo correr, decirlo explícitamente.
4. Comparación de contrato: `scripts/contract-diff.sh <master> <sha>`. Ese script lo está creando otra tarea y puede no existir todavía: `ls scripts/contract-diff.sh`. Si no existe, avisar al coordinador y usar de reemplazo `git show origin/master:contracts/openapi.json > $TMPDIR/base.json; git show <sha>:contracts/openapi.json > $TMPDIR/new.json; scripts/oasdiff-breaking.sh $TMPDIR/base.json $TMPDIR/new.json` (es lo que corre el job `backend-contract`).
5. Riesgos de migración: leer cada migración nueva buscando
   - `ADD CONSTRAINT ... CHECK/FOREIGN KEY` seguido de `VALIDATE CONSTRAINT` en la MISMA transacción: el ADD toma `ACCESS EXCLUSIVE` y la validación lo mantiene durante el escaneo de toda la tabla. Lo correcto es `NOT VALID` y la validación en una migración aparte (ver recuperación de V429 en `migration-smoke.md` §9);
   - `CREATE INDEX` sin `CONCURRENTLY` sobre tablas grandes (bloquea escrituras; `CONCURRENTLY` no corre dentro de la transacción de Flyway, hay que resolverlo);
   - migraciones destructivas: `DROP TABLE/COLUMN`, `DELETE`, y especialmente borrado de grants o permisos;
   - `docs/release/pre-migration-checks.sql` desactualizado: cada guarda `RAISE EXCEPTION`/descarte de datos nuevo debe tener su bloque `[V<n>]` (hoy el último es `[V428]`; si hay migraciones posteriores con abortos o descartes sin bloque, agregarlo antes de mergear). Si una migración exige un procedimiento manual, debe existir un runbook en `docs/release/` (ejemplo: `institution-sms-credential.md`).
6. Rollback: Flyway no hace down. El rollback es restaurar el backup previo a la primera migración nueva más la imagen anterior del backend; anotarlo en el PR junto con qué migraciones son irreversibles (las destructivas).
7. Variables de entorno nuevas: `git diff origin/master <sha> -- backend/src/main/resources/application.properties backend/src/main/resources/application-prod.properties deploy/ docker-compose*.yml` y listar cada `${VAR}` nueva con su valor esperado en producción (ver `docs/deploy/README.md` y el §6 de `migration-smoke.md` para `JWT_SECRET` y `PLATFORM_PROVISIONING_INITIAL_BILLING_MODE`).

## 4. `contract-break-approved`
- Solo si el diff de contrato es aditivo o la ruptura está declarada, y con autorización expresa de Gabriel. La etiqueta la pone el coordinador, no el líder.
- El workflow solo lee la etiqueta en `opened`, `synchronize`, `reopened` y `ready_for_review`: un rerun no la re-evalúa. Después de ponerla, cerrar y reabrir el PR (`gh pr close <n> && gh pr reopen <n>`); es el único caso en que se hace.
- Si el diff NO es aditivo y Gabriel no autorizó: sacar el cambio del release (no del SHA verde: elegir otro SHA) o frenar.

## 5. Merge
1. Mergear solo con el CI en verde sobre el head real del PR: `gh pr checks <n>` y `gh pr view <n> --json headRefOid`. Verde de un head anterior no vale.
2. Con las revisiones que correspondan (plata, contrato, seguridad, migraciones) y el dossier completo. El líder tiene el dominio del merge a `master` (`workflow.md` §2).
3. Aclarar al coordinador y en el PR: abrir o mergear el release NO despliega nada. El despliegue lo hace Gabriel según `docs/deploy/README.md`.

## 6. Lo que queda en manos de Gabriel
- Ventana de despliegue con la app parada.
- Backup previo a la primera migración nueva (es el rollback del paso 3.6).
- Confirmaciones de las migraciones destructivas (borrados de tablas, columnas, grants o datos; ver `pre-migration-checks.sql` y los runbooks de `docs/release/`).
- Variables de entorno nuevas y rotación de credenciales si algún runbook lo pide.

## 7. Después del merge
1. Verificar que no se disparó un backport indebido: `gh run list --workflow backport.yml --limit 3` debe mostrar el run en verde con el log "Merge de una rama release (release/<n>-rc)", y `gh pr list --base develop --state open --json number,title,headRefName` no debe tener un PR `backport/*` nuevo. Si apareció, cerrarlo y avisar al coordinador (la rama no empezaba con `release/`).
2. Avisar al coordinador (formato de `cell-report`): release mergeado, SHA, migraciones incluidas y que falta el despliegue de Gabriel. El siguiente release arranca desde `develop` (nuevo SHA verde), respetando la puerta de despliegue del paso 1.
