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
- No abrir PR sin que el usuario lo haya pedido/aprobado. El merge lo hace el usuario.
- Mensajes de commit en español, explicando el "por qué"; un commit por unidad coherente.

## 3. CI local ANTES de abrir el PR (obligatorio)

**Excepción autorizada (15-oct-2026) para este repo, donde GitHub Actions es gratis (público):** no hace falta correr la suite completa ni PIT en local. Alcanza con compilar y correr los tests focalizados del cambio (más ArchUnit/Modulith si se tocan paquetes); la suite completa y PIT corren en GitHub (job `mutation` en paralelo a los shards, requisito de merge vía el agregador `backend`). El PR se abre en borrador mientras se itera y se marca listo para disparar la CI.

Cuando el repo vuelva a tener minutos facturables, rige lo siguiente: GitHub Actions cobra minutos: no se abre un PR sin haber corrido en local lo mismo que corre `.github/workflows/ci.yml` y haberlo visto pasar.

| Área tocada | Comandos (desde la raíz) |
|---|---|
| Backend | `cd backend && ./mvnw clean verify` (Java 21; necesita Docker por Testcontainers) |
| Frontend | `cd frontend && npm ci && npm run lint && npm run build && npm run test` (Node 22, como el CI) |
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

Solo el usuario abre PRs contra `master`. Hay dos caminos:
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

`ci.yml` corre en PRs y en push a `develop`/`master`, cancela corridas viejas del mismo PR y **no corre si el cambio es solo `.md` o `docs/`**. Si se activan checks requeridos en la protección de rama, revisar que el filtro de paths no deje el check "omitido" y bloquee el merge de PRs de docs.
