# Workflow — git, CI local, commits, PRs

Reglas generales del usuario: `~/.claude/instructions/workflow-git.md`. Acá el detalle operativo de este repo.

## 1. Ramas

Todo cambio va en su propia rama, nunca commit ni push directo a `develop` ni `master` (ni "solo docs", ni una línea).

```bash
git fetch origin
git checkout -b feature/<slug> origin/develop
```

Prefijos: `feature/`, `fix/`, `refactor/`, `docs/` (siempre desde `origin/develop`), `release/<version>` (desde `develop`, PR contra `master`) y `hotfix/<slug>` (desde `origin/master`, PR contra `master`). Nunca trabajar parado en `develop`/`master`.

**Sesiones concurrentes:** puede haber otras sesiones de Claude en worktrees (`.claude/worktrees/`). Antes de tocar una rama o commitear: `git worktree list`, `git status`, `git log origin/<rama>..`. Si otra sesión ya pusheó lo mismo, verificar y no rehacer. Nunca `git stash` sin etiqueta única (el stash es compartido).

**Antes de empezar una fase del roadmap y otra vez antes de abrir el PR, correr `gh pr list`.** Mirar solo `develop` no alcanza: un PR puede estar abierto horas antes de mergearse, y en ese lapso las dos sesiones no se ven.

```bash
gh pr list --state open --json number,title,headRefName
```

Pasó de verdad el 2026-10-01 con la Fase 5.5 de avisos: dos sesiones la implementaron entera en paralelo —una con un `Dialog`, la otra con un wizard— y se enteraron recién cuando la primera mergeó. El PR #109 estuvo abierto **nueve horas** antes de que se abriera el #110. Ninguna de las dos había corrido `gh pr list`; las dos habían verificado `develop` y las ramas remotas, y en el momento en que lo hicieron la verificación era correcta.

Si la fase ya la está haciendo otra sesión, avisarle con `SendMessage` en vez de duplicarla, y dejar escrito quién la toma.

## 2. Aprobaciones

- Mostrar archivos tocados + mensaje de commit propuesto y esperar un "sí" explícito **antes de cada commit**.
- Aprobación **aparte antes de cada push**. También para fixes urgentes de CI.
- No abrir PR sin que el usuario lo haya pedido/aprobado. El merge lo hace el Líder Técnico de la célula (dominio delegado por Gabriel el 17-nov, release a master incluido), con CI verde y las revisiones que correspondan.
- Mensajes de commit en español, explicando el "por qué"; un commit por unidad coherente.

## 3. CI local ANTES de abrir el PR (obligatorio)

**Excepción autorizada (15-oct-2026) para este repo, que es público (runners estándar gratis: el costo real de la CI es el tiempo de espera y el tope de 20 jobs concurrentes, no los minutos):** no hace falta correr la suite completa en local; PIT ya no corre en el CI: para cambios de plata correrlo en local (`cd backend && ./mvnw -P mutation test-compile org.pitest:pitest-maven:mutationCoverage`). Alcanza con compilar y correr los tests focalizados del cambio (más ArchUnit/Modulith si se tocan paquetes); la suite completa corre en GitHub (ver `testing.md`, sección "PIT en local" para la mutación). El PR se abre en borrador mientras se itera y se marca listo para disparar la CI.

Cuando el repo vuelva a tener minutos facturables, rige lo siguiente: GitHub Actions cobra minutos: no se abre un PR sin haber corrido en local lo mismo que corre `.github/workflows/ci.yml` y haberlo visto pasar.

| Área tocada | Comandos (desde la raíz) |
|---|---|
| Backend | `cd backend && ./mvnw clean verify` (Java 21; necesita Docker por Testcontainers) |
| Frontend | `cd frontend && npm ci && npm run lint && npm run build:check && npm run test` (Node 22, como el CI) |
| Solo docs/`.md` | Ninguno, pero verificar que los links y comandos citados existan |

Reglas:
- Tocás backend **o** frontend: corré esa área completa. Tocás migraciones, config, `application.properties`, seguridad, dependencias o el contexto de Spring: corré backend completo aunque el cambio parezca de otra área.
- Verificar con datos reales: `./mvnw -q` no imprime nada si todo pasa, así que confirmar el exit code y contar los `target/surefire-reports/*.txt` (fecha de ahora, 0 failures, 0 errors). Los `ERROR ... duplicate key` en los logs de tests son esperados (tests de constraints), no fallas.
- Antes de abrir el PR: `git fetch` y traer `origin/develop` a la rama; si entró algo nuevo, volver a correr la verificación. Dos ramas pueden pasar solas y romper juntas.
- Un PR abierto con el CI en rojo es un error de proceso: cada re-push gasta minutos. Si falla en GitHub y pasó en local, entender la diferencia de entorno antes de re-pushear.

## 4. Migraciones Flyway (fuente de colisiones)

Ya chocaron dos veces (V59, V60) y una rompió el CI de todo `develop`. Antes de crear o renombrar una migración:

```bash
git fetch origin
git ls-tree --name-only origin/develop backend/src/main/resources/db/migration/ | sort -V | tail -5
git ls-tree --name-only origin/master  backend/src/main/resources/db/migration/ | sort -V | tail -5
```

Revisar también los PRs abiertos que agreguen migraciones (`gh pr list --state open --json number,files --jq '.[] | select(.files[].path | test("db/migration")) | .number'`). Usar el siguiente número libre considerando ambas ramas y esos PRs. Una migración ya aplicada en `master` **no se renumera**: se renumera la nueva.

## 5. Pull Requests

- Siempre `gh pr create --base develop` explícito. Título corto; cuerpo con Summary y Test plan (marcar lo verificado en local).
- Tras abrirlo, monitorear con `gh pr checks <n> --watch` hasta que termine, y avisar verde o rojo. No pedir merge con checks pendientes o en rojo.
- Al cerrar una fase: reporte de QA en Obsidian, linkeado desde el PR (ver [testing.md](testing.md)).
- Verificar al cierre de cada fase que toda rama pusheada tenga su PR abierto o mergeado.

## 6. Releases, hotfixes y Backport

Claude puede abrir PRs contra `master`. Cadencia obligatoria de releases: con 15 PRs mergeados a `develop` desde el último release, o al cierre de cada jornada si hay al menos 5, o antes de abrir una ventana de migraciones, se abre un PR `release/*` y se prioriza mergearlo para que `develop` y `master` no se acumulen (en el release de noviembre de 2026 la distancia llegó a 1.909 commits y 63 migraciones). Cada release corre antes el smoke de migración (`docs/release/migration-smoke.md`). Hay dos caminos:
- **Release (develop → master):** rama `release/<version>` cortada desde `origin/develop`, PR contra `master`. No hay PR automático develop → master.
- **Hotfix (urgente, solo master):** rama `hotfix/<slug>` cortada desde `origin/master`, PR contra `master` (con CI local previo, como cualquier PR).

**Backport automático master → develop:** `.github/workflows/backport.yml` se dispara en cada push a `master` y decide por el **nombre de la rama del PR mergeado** (los merges a `master` son squash, así que comparar historias no sirve):
- Rama `release/*` → **no hace nada**: su contenido ya viene de `develop`.
- Cualquier otra (típicamente `hotfix/*`) → crea la rama `backport/<sha>` desde `origin/develop`, le aplica el commit de `master` (cherry-pick) y abre el PR **"Backport: master → develop"** contra `develop`, si no hay ya uno abierto. Se mergea siempre, con el CI en verde.
- Si el cherry-pick tiene conflictos, el workflow falla (rojo, con aviso): se resuelve a mano en una rama `backport/*` desde local. Si el cambio ya estaba en `develop`, termina sin PR.
- Por eso los merges a `master` deben venir siempre de una rama `release/*` o `hotfix/*`.
- Requiere activar *Settings → Actions → General → "Allow GitHub Actions to create and approve pull requests"*. Los PRs creados con el token de Actions no disparan el CI por sí solos: corre al pushear a `develop` tras el merge.
- Antes de un release, `git diff origin/master origin/develop` para detectar features perdidas en refactors masivos (la verificación de email desapareció así de `develop`).

## 7. CI de GitHub (costo)

`ci.yml` corre en PRs y en push a `develop`/`master` y cancela corridas viejas del mismo PR (los push a `develop`/`master` no se cancelan entre sí; ver «Concurrencia» en `testing.md`). Los checks `backend` y `frontend` los exige la protección de rama, así que el workflow siempre corre y `detect-changes.sh` decide qué áreas ejecutan tests; los agregadores dan verde cuando el área se salta legítimamente y rojo si algo requerido falló o se canceló. Reglas en PRs (diff con `--no-renames`, así un movimiento o borrado cuenta por origen y destino): solo `frontend/**`/`perf/` (salvo `perf/seed-large.sql`) no corre backend; solo `backend/**` no corre frontend. Cualquier cambio bajo `docs/`, cualquier `*.md` fuera de carpetas ocultas y `perf/seed-large.sql` corre backend, porque hay tests que leen esos archivos (`ErrorCatalogTest`, `PolicyCatalogTest`, `DocsIndexTest`, `PerfSeedIT`, etc.); `docs/api/error-catalog.md` y `docs/context/testing.md` además corren frontend (`describeError.test.js`, `offlineMatrix.test.js`). Solo `.claude/`, `.gitignore` y `diagrama_entidades.puml` no corren ninguno. Corre TODO si toca `pom.xml`, `mvnw`, `mvnw.cmd`, `package.json`, lockfiles, `backend/.mvn/`, o cualquier ruta que no esté en `classified_scope` (`.github/**`, `contracts/**`, `deploy/**`, `docker-compose*.yml`, `e2e/`, `scripts/`, raíz): ante la duda no se salta nada. `run_demo_seed` no se fuerza en «correr todo»: sigue dependiendo solo de sus rutas (`demo_seed_scope`), igual que antes. `backend-contract` sigue corriendo siempre que el área backend corre. Los push a `develop`/`master` siguen sin correr con cambios solo de `.md` o `docs/`.

### Reutilización del resultado del PR en el push a `develop` (REUSE)

`detect-changes.sh` compara árboles (no SHAs) para no repetir ~6 min de CI en cada merge:

- **Merge commit:** el PR es el segundo padre (`HEAD^2`). **Squash:** se busca con `gh api repos/{repo}/commits/{sha}/pulls` el único PR mergeado a `develop` cuyo `merge_commit_sha` es el commit pusheado, y se toma su `head.sha`.
- Se reutiliza **solo si** se cumplen todas: (1) existe un run `pull_request` de `ci.yml` verde para ese head; (2) todos los check-runs del head (`commits/{head}/check-runs?filter=latest`) terminaron en `success`, `skipped` o `neutral`; (3) el artifact `tested-tree` de ese run registra un árbol (el de `refs/pull/N/merge`, o sea PR + `develop` al momento del run) **idéntico** a `git rev-parse HEAD^{tree}` del push. Cualquier fallo de la API, artifact vencido o dato malformado implica correr todo (nunca se reutiliza por defecto).
- **Cuándo NO reutiliza:** si `develop` avanzó entre el último CI del PR y el merge, el árbol del commit en `develop` difiere del probado y corre el CI completo. Lo mismo con pushes directos, commits sin PR asociado o checks rojos/pendientes en el PR.
- Permisos del job `changes`: `contents: read`, `actions: read`, `pull-requests: read` (listar PRs del commit) y `checks: read` (check-runs).
- Riesgo residual: un check requerido que no sea check-run del head (ej. status externo) no se evalúa; hoy todos los requeridos son de `ci.yml`.

### Seguridad de dependencias y secretos

- `.github/dependabot.yml` abre PRs semanales contra `develop` para Maven, npm y GitHub Actions (máx. 5 abiertos por ecosistema).
- `security-nightly.yml` (07:00 UTC y `workflow_dispatch`) corre `dependency-check` (falla con CVSS >= 7, usa el secreto `NVD_API_KEY`), `npm run audit:prod` (falla con severidad alta; `audit:all` es informativo) y `gitleaks` sobre todo el historial.
- `ci.yml` suma `secret-scan-pr`: gitleaks sobre los commits de cada PR. La configuración es `.gitleaks.toml` (permite solo la contraseña demo `Test1234`).
- Local: `cd backend && ./mvnw -B -ntp -Pdependency-check verify -DskipTests` (lento sin `NVD_API_KEY`), `cd frontend && npm run audit:prod`, `docker run --rm -v "$PWD:/repo" zricethezav/gitleaks:latest detect --source /repo --config /repo/.gitleaks.toml --no-banner`.
- `ProductionProfileHardeningIT` verifica que sin perfil `local`/`demo` no hay seeds, Swagger ni `simulated-emails`, y que el actuator no expone `env`/`heapdump`.
