# Testing — estrategia

Principio general del proyecto: **la cobertura numérica no es el objetivo, los
casos de uso reales sí lo son.** Un test que solo existe para subir el % de
cobertura sin verificar un comportamiento que alguien podría romper no vale nada.
Priorizá: (1) casos de uso de negocio con sus bordes, (2) flujos críticos de punta
a punta, y recién después mirá cobertura como termómetro de qué quedó sin tocar.

## Mapa de herramientas: qué pregunta responde cada una

JUnit es solo el motor que ejecuta los tests. Antes de escribir un test, elegí la
herramienta por la **pregunta de riesgo** que tiene que responder: cada tipo de
prueba encuentra bugs que los demás no ven.

| Herramienta | Tipo de prueba | Pregunta que responde | Estado |
|---|---|---|---|
| JUnit 5 | Unitario | ¿Esta regla puntual (Policy, Value Object, cálculo) funciona con sus bordes? | En uso |
| JUnit 5 + Testcontainers (Postgres real) | Integración | ¿Funciona contra la base real, con transacciones, CHECKs, triggers e índices únicos? | En uso |
| PIT (Pitest, perfil `-P mutation`) | Mutation testing | ¿Mis tests detectarían un bug si alguien lo introduce? (umbral 90% en el dominio puro de billing) | En uso, **en GitHub** (job `mutation`, PR; push a `develop` y diario en `mutation-full.yml`) |
| jqwik | Tests de propiedades | ¿Se cumplen los invariantes con cualquier combinación de datos, no solo con los casos que se me ocurrieron? | En uso |
| `MutableClock` + `BillingInvariantChecker` | Escenarios temporales | ¿Funciona con el paso del tiempo (días, meses, años lectivos) y los invariantes se sostienen en cada paso? | En uso |
| Hilos reales contra Postgres | Concurrencia | ¿Dos operaciones simultáneas (pagos, emisiones, imputaciones) no duplican ni corrompen nada? | En uso |
| ArchUnit | Arquitectura | ¿La estructura del código no se degrada (sin setters de estado, sin clases en paquetes congelados, validaciones solo en Policies)? | En uso |
| Spring Modulith (`ModularityTest`) | Límites de módulos | ¿Ningún módulo accede a internos de otro ni aparecen dependencias o ciclos nuevos? | En uso |
| `BillingDecisionCoverageTest` | Cobertura de decisiones | ¿Cada decisión de negocio vigente (D-xxx del registro) tiene al menos un test con su `@Tag`? | En uso |
| JUnit sobre HTTP (`characterization/`) | Caracterización | ¿Quedó fijado el comportamiento actual antes de refactorizar, para detectar cambios no buscados? | En uso |
| Karate | API (regresión HTTP) | ¿La API responde lo esperado (status, body, códigos de error) en los flujos de punta a punta? | En uso |
| Awaitility | Procesos asíncronos | ¿El efecto en segundo plano ocurrió? (esperar siempre por el propio eventId, nunca por conteos globales) | En uso |
| Vitest + RTL + MSW | Front unitario/componente | ¿El componente muestra y reacciona bien ante cada respuesta del backend? | En uso |
| Playwright | End-to-end en navegador | ¿El usuario real puede completar el flujo crítico en el navegador (CORS, permisos por rol, ruteo)? | Propuesto |
| OpenAPI + validación en ambos lados | Contrato front-back | ¿El front y el back hablan el mismo idioma, o un cambio del backend rompe al front? | En uso (lado backend: instantánea `contracts/openapi.json` + `oasdiff`); falta la validación de los mocks del front (Q13b) |
| Gatling o k6 | Carga y rendimiento | ¿Aguanta el volumen real (corrida mensual de miles de alumnos, pipeline nocturno, locks por cuenta)? | Propuesto |
| OWASP Dependency-Check / ZAP + matriz de autorización por rol | Seguridad | ¿Hay vulnerabilidades conocidas o algún endpoint accesible por un rol que no corresponde? | Dependency-Check y `npm audit`: en uso (solo local); ZAP y matriz por rol: propuesto |

## Criterio: qué tests escribir para un cambio

Se decide en dos pasos: el **tipo de cambio** fija la base obligatoria, y cada **señal de riesgo** que tenga el cambio suma su herramienta. Un PR que no cubre lo que piden estas dos tablas se devuelve en la revisión.

### Paso 1: base obligatoria según qué cambiaste

| Cambiaste… | Mínimo obligatorio | Por qué |
|---|---|---|
| Una regla de negocio pura (Policy, Value Object, cálculo, máquina de estados) | JUnit puro con bordes (cero, negativo, límite, redondeo) y **cada transición válida e inválida** | Es barato y ahí viven la mayoría de los bugs de lógica |
| Algo que guarda o lee de la base (servicio, repository) | Integración con Testcontainers (Postgres real) | Constraints, triggers, índices únicos y cascadas solo aparecen contra la base real |
| Un endpoint (controller, DTO, código de error) | MockMvc con status, JSON y `code` de error; un escenario de Karate si es un flujo de punta a punta | El front depende del contrato exacto |
| Una pantalla o un componente | Vitest + RTL + MSW con cada respuesta posible del backend: ok, error, vacío y cargando | Testea lo que ve el usuario, no la implementación |
| Una migración que transforma datos existentes | Integración que carga datos con la forma vieja, migra y verifica el resultado | Un esquema vacío no prueba una migración de datos |
| Un refactor grande de algo que ya funciona | Caracterización **antes** de tocar, y que siga verde después | Cualquier diferencia de comportamiento sale a la luz |

### Paso 2: señales de riesgo (cada una suma su herramienta)

| Si el cambio… | Sumá | Pregunta que responde |
|---|---|---|
| Mueve plata o toca la cuenta corriente | `BillingInvariantChecker` construido **antes** de escribir, `@Tag("D-xxx")` de la decisión y revisión independiente del PR | ¿Los saldos cierran después de cada operación? |
| Depende de fechas (vencimiento, mora, corrida mensual, año lectivo) | `MutableClock` con escenarios que **avanzan el tiempo** y bordes de fecha (fin de mes, febrero, 31 de diciembre) | ¿Sigue funcionando el día después y el mes que viene? |
| Combina varios valores en un invariante (saldos, imputaciones, descuentos) | jqwik | ¿Se cumple con combinaciones que no se me ocurrieron? |
| Puede ejecutarse dos veces o en paralelo (pagos, webhooks, jobs, eventos reintentados) | Hilos reales contra Postgres y un test de **idempotencia**: la misma entrada dos veces produce un solo efecto | ¿Un reintento duplica o pierde algo? |
| Es asíncrono (evento, listener, notificación) | Awaitility esperando por el **propio** id de evento | ¿El efecto ocurrió, sin depender de otros tests? |
| Es lógica de dominio de facturación | PIT (umbral 90%): lo corre el job `mutation` del CI; en local solo compilar y correr los tests focalizados del área | Si alguien rompe esto, ¿algún test se entera? |
| Agrega clases, paquetes o dependencias entre módulos | ArchUnit y Modulith (corren en CI) | ¿La estructura se degrada? |
| Cambia permisos o roles | Matriz rol × endpoint: 403 para cada rol que no corresponde | ¿Un rol puede hacer algo que no debería? |
| Es un flujo crítico del usuario (inscribirse, pagar, aprobar) | Playwright (propuesto) | ¿Un usuario real lo completa en el navegador? |
| Cambia la forma de una respuesta que consume el front | Contrato OpenAPI (propuesto) | ¿El cambio rompe al front sin que nadie se entere? |
| Procesa volumen (corrida mensual, pipeline nocturno) | k6 o Gatling (propuesto) | ¿Aguanta miles de alumnos en el tiempo disponible? |

### Dónde corre cada cosa y cómo queda constancia

El repo es público (los runners estándar no cobran minutos; lo que cuesta es el tiempo de espera y el tope de 20 jobs concurrentes), así que en el CI corre: `./mvnw clean verify` (JUnit, Testcontainers, Karate, ArchUnit, Modulith) y el front (lint, build, Vitest), cada uno solo si cambió su área. **PIT corre en GitHub** como job `mutation`, en paralelo a los shards de `backend-tests`, y es parte del agregador `backend` (requisito de merge). **Playwright, k6 y OWASP Dependency-Check siguen corriendo solo en local**, mientras se programa, con el semáforo de CI.

### PIT en GitHub

El job `mutation` corre solo si el PR toca `backend/pom.xml` o algo bajo `shared/domain` o `billing` (main o test). Si no corrió por el filtro de paths cuenta como OK en el agregador `backend`. Ejecuta `./mvnw -P mutation test-compile org.pitest:pitest-maven:mutationCoverage` con el umbral 90% y los `targetClasses` del perfil `mutation`, usa el historial incremental de PIT en `backend/target/pit-history` (NO usar `withHistory` en el perfil: pisa las rutas explícitas con un archivo en `/tmp` que la cache nunca guarda) y sube el reporte como artifact `pit-report`. Cómo corre según el evento:

| Evento | PIT | Historial | Bloquea |
|---|---|---|---|
| Pull request | Incremental, solo si toca el alcance de PIT (job `mutation` de `ci.yml`) | Restaura `pit-history-pr-<N>-` y, si no hay, `pit-history-<rama base>-`; guarda el del PR | Sí (agregador `backend`) |
| Push a `develop` | **`ci.yml` no corre PIT.** Lo hace aparte `mutation-develop-cold.yml` (disparado por `push`, filtrado por paths del alcance de PIT) | En frío (sin restaurar nada); guarda `pit-history-develop-cold-<run>` en el scope de `develop`, que es donde lo restauran los PRs | No bloquea ni demora el CI principal; su propio run queda rojo si baja del 90 % |
| Push a `master` (release) | En frío, dentro de `ci.yml` | Guarda `pit-history-master-<run>` | Sí: es el gate de la release |
| `mutation-full.yml` | En frío sobre `develop`, umbral 90 % | `pit-history-develop-full-<run>` | Su propio run |

`mutation-develop-cold.yml` usa `concurrency` propia por ref con `cancel-in-progress: true` (un push nuevo cancela el PIT en frío en curso; el último push que toca el alcance es el que termina) y se salta si el árbol de `HEAD` ya fue validado en frío por un run anterior (artifact `cold-verified-tree`, se busca entre los últimos 10 runs verdes). **Esta es la compensación real del riesgo del historial incremental.**

`mutation-full.yml` tiene `schedule` diario (06:00 UTC, solo si `develop` cambió desde el último run verde; busca el artifact `mutation-full-verified` en los últimos 10 runs) y `workflow_dispatch`, pero **hoy no se puede disparar**: GitHub solo dispara `schedule` y permite `workflow_dispatch` para workflows que están en la rama por defecto (`master`). Empezará a servir cuando llegue a `master` en el próximo release; hasta entonces la compensación es `mutation-develop-cold.yml`. Hace checkout de `develop`.

El job `mutation` baja los plugins de PIT en cada corrida (la cache `maven-` unificada la guarda el shard 0 de un push a `develop` y no incluye pitest): son unos segundos.

Si se agregan paquetes a `targetClasses` fuera de `shared/domain` y `billing`, ampliar el filtro de mutación de `.github/scripts/detect-changes.sh` y los `paths` de `mutation-develop-cold.yml`. La CI es la fuente de verdad de PIT: ya no se anota su corrida en el log local.

**Riesgo aceptado del historial incremental** (verificado en `IncrementalAnalyser`, pitest 1.20.4): (a) un KILLED se reusa aunque cambie una clase de producción colaboradora no mutada; (b) un TIMED_OUT se reusa siempre que la clase mutada no haya cambiado, aunque cambien los tests; (c) los helpers de test fuera de la jerarquía del test (builders, object mothers, fixtures) no entran en el hash: si se debilitan, un KILLED anterior se sigue reusando. Compensación: el PIT en frío de `mutation-develop-cold.yml` en cada push a `develop` que toca el alcance (y el push a `master`) vuelve a verificar el 90 % contra todo. Costo: un falso verde puede entrar a `develop` y descubrirse recién en ese run (rojo, aparte del CI), y los PRs siguientes que toquen el alcance de PIT heredan los sobrevivientes y fallan hasta que se arregle. Qué hacer: arreglar `develop` primero.

### Qué corre en un push a `develop`

- **Detección de cambios.** `.github/scripts/detect-changes.sh` (con tests en `test_detect_changes.py`) diferencia contra el `headSha` del último push exitoso de `develop` (`gh run list`); si no hay (o `gh` falla, o el commit ya no existe por un force-push) no hay base y corre todo. Backend, PIT, frontend y `audit:prod` (solo si cambió `frontend/package-lock.json`) se saltan si el diff no los toca. Un push a `master` siempre corre todo.
- **Dedupe por árbol.** Si el árbol de `HEAD` coincide con el árbol que verificó el run verde del PR del segundo padre (`HEAD^2`, artifact `tested-tree` que sube cada run de PR), se saltan los tests de esa área que el PR sí corrió, el agregador `backend` queda verde con «reusado del PR #N» y el baseline de tiempos se hereda de los `shard-reports-*` del run del PR. Si el árbol difiere (por ejemplo porque `develop` avanzó después del run del PR), si el merge fue squash o si el push es directo, corre todo. Auditoría: el resumen del run muestra «Reused from PR #N», el árbol y los minutos de cómputo ahorrados. Consecuencia: el grupo `docs` (`ModuleDocumentationTest`), que solo corre en push, no se ejecuta en los merges deduplicados.
- **Concurrencia.** `cancel-in-progress: true` en PRs y en `develop`: cada push cancela al anterior y el run que sobrevive diferencia contra el último verde, así que cubre también los cambios de los runs cancelados. El baseline de tiempos lo publica el run que sobrevive. `master` no cancela.
- **Caches.** `~/.m2/repository` y `~/.m2/wrapper` bajo una sola clave `maven-<os>-<hash del pom>`: todos los jobs la restauran (`actions/cache/restore`), solo el shard 0 de un push a `develop` la guarda. `TESTCONTAINERS_RYUK_DISABLED=true` en todo el workflow y `postgres:16-alpine` se baja en segundo plano al empezar cada shard (Ryuk no se baja: está desactivado).
- **Frontend.** `npm ci --no-audit --no-fund --prefer-offline`; Vitest en 2 shards (`frontend-tests (1)` y `(2)`, `--shard=N/2`) en paralelo con `frontend-checks` (audita si cambió el lockfile, lint y build); el job `frontend` solo agrega ambos resultados.

En local, para el dominio de billing, alcanza con compilar y correr los tests focalizados; PIT local (`./mvnw -P mutation test-compile org.pitest:pitest-maven:mutationCoverage`, con el semáforo de CI) queda opcional para investigar sobrevivientes.

Las herramientas que siguen siendo locales (Playwright, k6, Dependency-Check) se anotan en [`docs/testing/local-test-log.md`](../testing/local-test-log.md): fecha, PR, commit, área, herramienta, comando y resultado. Antes de modificar un área, buscá ahí qué herramientas pesadas la cubren y volvé a correrlas.

### Qué clases ejecuta `./mvnw clean verify`

El plugin surefire del `pom.xml` declara `<includes>` explícitos: `Test*`, `*Test`, `*Tests`, `*TestCase` (los defaults), más `*IT` (integración con Testcontainers) y `*Properties` (jqwik). Una clase de test con otro sufijo **no corre** ni en local ni en GitHub, sin avisar. `SurefireDiscoversEveryTestClassTest` escanea `src/test/java` y falla si una clase con `@Test`/`@Property` no matchea esos includes; al nombrar una clase nueva, usá uno de esos sufijos. PIT sigue excluyendo el grupo `integration`.

### Auditoría de dependencias (solo local)

Responde: ¿alguna librería empaquetada tiene un CVE publicado? Corre cuando un PR agrega o sube una dependencia, y antes de cada release.

- **Backend (OWASP Dependency-Check):** desde `backend/`, con el semáforo de CI: `zsh -ic './mvnw -P dependency-check org.owasp:dependency-check-maven:check'`. Falla con CVSS >= 7. La clave del NVD vive solo en el entorno de quien corre (`NVD_API_KEY` exportada en `~/.zshrc`; el `zsh -ic` la hereda). Nunca se escribe en el repo, en logs ni en reportes, y no hay secret en GitHub. Reporte en `backend/target/dependency-check-report.html` y `.json`. La primera corrida baja el NVD (decenas de minutos); después usa `~/.m2/repository/org/owasp/dependency-check-data`.
- **Supresiones:** `backend/dependency-check-suppressions.xml`. Cada `<suppress>` lleva `until="AAAA-MM-DDZ"` (máximo 90 días) y un `<notes>` con el motivo y el link al CVE. Ninguna sin `until`.
- **Front:** `npm run audit:prod` (`npm audit --omit=dev --audit-level=high`, bloqueante al adoptarse) y `npm run audit:all` (incluye devDependencies, informativo). `audit:prod` corre en el job `frontend` del CI de GitHub (queda en verde desde Q11b); `audit:all` y Dependency-Check no están en el CI.
- Anotar cada corrida en `docs/testing/local-test-log.md`.

### Paso 3: un test que nunca falló no prueba nada

Todo test de una regla crítica se ve fallar al menos una vez: romper el código o corromper el dato a propósito y comprobar que se pone rojo; recién después se arregla. PIT lo automatiza para el dominio puro, pero no cubre los tests de integración: ahí se hace a mano (por ejemplo, un test que corrompe `unallocated_credit_amount` y exige que `BillingInvariantChecker` falle).

## Backend (ya establecido, verificado contra el repo real)

**No se mockea la base de datos.** Todo test que toca un repository corre contra
Postgres real vía Testcontainers (`AbstractIntegrationTest`, `@SpringBootTest`,
`@ActiveProfiles("test")`, `@Transactional` para rollback automático entre tests).
Esto aplica incluso a los tests que se llaman `*ServiceTest` — no son unit tests
con mocks de repository, son tests de integración con nombre de service. Es una
decisión deliberada: un mock de repository nunca detecta un problema real de
constraint, cascada o query — solo Postgres real lo hace.

**Excepción real**: lógica pura sin dependencia externa (`JwtService`,
validadores, cálculos) sí tiene unit test puro sin Spring — ver `JwtServiceTest`.
La regla para decidir: si la clase no toca un repository ni el contexto de
Spring, unit test puro; si lo toca, integración con `AbstractIntegrationTest`.

**Controllers**: `@AutoConfigureMockMvc` + `MockMvc`, mismo `AbstractIntegrationTest`
como base — un test de controller verifica el contrato HTTP completo (status,
JSON, headers) contra la app real levantada, no contra el service mockeado.

**Convención de nombres de test**: en inglés, describe el escenario + el resultado
esperado en un solo identificador camelCase, ej.
`putMeUpdatesOnlyOwnPersonAndNeverDni`, `getMeWithoutAuthReturns401OrForbidden`.
No `test1`. No se renombran retroactivamente tests viejos en castellano salvo
pedido explícito.

Correr todo: `./mvnw clean verify` desde `backend/`. Necesita Docker corriendo (Testcontainers
levanta Postgres) — ver [docs/context/debugging.md](debugging.md) si Docker no
está arriba.

## Presupuesto de tiempo y pirámide de tests

El CI del backend corre en 6 shards balanceados por tiempo (`.github/scripts/test-shards.py`). Para que ese tiempo no vuelva a crecer, cada corrida pasa por presupuestos que miden la corrida real.

**Dónde bloquean (decisión de Gabriel, 22-oct-2026):**
- **En un PR** (`--enforcement warn`), los presupuestos (por clase, por shard y por cantidad de contextos de Spring) son **advertencias**. Aparecen en el comentario del reporte y como `::warning::`, pero no ponen el PR en rojo.
- **En un push a `develop`** (`--enforcement block`), bloquean como antes.

Motivo: ~80 % de los rojos de los PRs de billing eran presupuestos (clases de 31-34 s, 25 contextos), no bugs, y cada uno costaba una vuelta más. Si un push a `develop` queda rojo por presupuesto, se corrige en un PR aparte, el mismo día.

### Presupuestos

| Qué | Límite | Quién lo hace cumplir |
|---|---|---|
| Una clase de test | 30 s | `test-budget.py check-shard`, en cada shard |
| Una clase marcada `@SlowTest` | 90 s | ídem |
| Un shard (tiempo de **reloj**: `SHARD_END - SHARD_START`, desde el primer paso del job hasta el fin de los tests) | 300 s | ídem; en un PR bloquea, en un push es solo `::warning::` |
| Contextos de Spring distintos en toda la corrida | 24 | `test-budget.py aggregate`, job `test-report` |
| Reglas ArchUnit sobre los tests | sin violaciones nuevas | `TestSuiteRulesTest` |

Los límites son constantes de `.github/scripts/test-budget.py` (parámetros `--max-class`, `--max-slow-class`, `--max-shard`, `--max-contexts`). Subirlos es una decisión de producto que va en un PR aparte, con motivo.

**El tope de shard se mide con reloj, no con la suma de las JVM** (decisión de Gabriel del 22-oct-2026; reemplaza la subida de 240 a 300 s hecha en #268). Cada shard corre 2 JVM en paralelo, así que sumar sus tiempos casi duplica el reloj y daba falsos rojos (4 de 10 pushes a `develop`, ninguno por un test). En un push (`--shard-limit warn`) pasarse del tope de shard es una advertencia, no un fallo: así un falso rojo no impide publicar el baseline (círculo vicioso). El tope por clase (30 s, `@SlowTest` 90 s) sigue bloqueando tanto en PRs como en `develop`. Sin `SHARD_START`/`SHARD_END` (corrida local) el tope de shard no se verifica.

### La pirámide de tests

1. **Base, la gran mayoría: tests de dominio puros.** Policies, value objects, cálculos y reglas se testean sin Spring, sin base y sin mocks de infraestructura (JUnit + AssertJ, jqwik donde haya propiedades). Corren en milisegundos. ArchUnit impide que las clases de test de paquetes `..domain..` y `..policy..` usen `@SpringBootTest` o extiendan `AbstractIntegrationTest`.
2. **Medio: tests de integración solo para persistencia e integración real.** Queries, constraints, transacciones, eventos entre módulos, endpoints. Extienden `AbstractIntegrationTest` (Postgres real, aislamiento por clase con `TestDatabaseIsolation`). No repitas ahí lo que ya cubre un test de dominio.
3. **Punta: escenarios largos con `@SlowTest`.** Los que recorren meses simulados, concurrencia o varias etapas del pipeline pueden superar 30 s. Se marcan con `@SlowTest(reason = "...")` (`com.red_escuela.backend.testsupport.SlowTest`, que lleva `@Tag("slow")`). El motivo es obligatorio y tiene que explicar por qué no se puede partir en tests más chicos. Aparecen listados en el reporte de cada PR.

### Tiers de tests: PR y nightly

El tier se elige con la propiedad `test.exploration` (`pr` por defecto, `nightly`): `./mvnw test -Dtest.exploration=nightly`. Surefire la pasa a los forks y el perfil `exploration-nightly` del `pom.xml` sube los intentos de jqwik. Un valor desconocido falla en vez de caer en `pr`.

| | Tier `pr` (cada PR y push) | Tier `nightly` (corrida nocturna y release) |
|---|---|---|
| Secuencias aleatorias (`CurrentAccountAllocationCommandSequencesIT`, `CancellationPropertiesIT`, `CreditNoteCommandSequencesIT`, `LateFeeCorrectionSequencesIT`) | 5 / 5 / 5 / 1, semilla fija | 50 / 40 / 40 / 6, semilla rotativa |
| `LateFeeRandomScenariosIT` | 1 escenario (semilla 3, con gracia) | 10 escenarios |
| jqwik (`@Property`) | 200 intentos (`jqwik.tries.default`), semillas fijas del propio test | 1000 intentos, mismas semillas |
| Tests `@NightlyTest` | se saltean | corren |

- **Cómo se lee el tier en un test:** `ExplorationTier.sequences(prCount, nightlyCount)` y `ExplorationTier.seed(owner, fixedSeed)` (`testsupport/ExplorationTier.java`). En nightly la semilla sale del reloj y se **loguea** (`Exploration seed of <test> in tier nightly: <n>`); para reproducir una falla: `-Dtest.exploration=nightly -Dtest.exploration.seed=<n>`.
- **jqwik:** no pongas `tries =` en `@Property`; el valor sale de `jqwik.tries.default` (200 en `src/test/resources/junit-platform.properties`, 1000 en nightly). jqwik no tiene semilla global: la semilla fija sigue en el `@Property` y por eso en nightly se rota solo en las secuencias propias. PIT conserva 1000 intentos (`jvmArgs` del perfil `mutation`) hasta medir qué mutantes se pierden con menos.
- **`@NightlyTest(reason = "...")`** (`testsupport/NightlyTest.java`, lleva `@Tag("nightly")`) marca tests de volumen o performance (400 facturas vencidas, 24 meses de datos). Se deshabilita solo con `ExecutionCondition`, sin depender de `excludedGroups`, así que ningún workflow puede ejecutarlos por error. Úsalo solo en tests que no llevan ningún `@Tag("D-xxx")` exclusivo (ver regla R5).
- **Grupos que el CI corre solo cuando corresponde** (`detect-changes.sh` decide, `ci.yml` los excluye con `-Dtest.excludedGroups`):

| Grupo | Tests | PR contra `develop` | Push a `develop` | PR/push a `master` | Nightly |
|---|---|---|---|---|---|
| `migration` | los 19 tests `*Migration*` | solo si el diff toca `db/migration/**`, un test `*Migrat*`, `backend/pom.xml`, `scripts/` o `.github/` | siempre | siempre | siempre |
| `karate` | `KarateSuiteTest` | siempre, salvo que el diff sea solo lógica de billing (ver abajo) | siempre | siempre | siempre |
| `docs` | `ModuleDocumentationTest` | nunca | nunca | siempre | siempre |

  Karate cubre endpoints de billing (`procesos-administrativos`: pagos, notas de crédito, cobranza, descuentos, comprobantes), por eso solo se saltea cuando todos los archivos del diff son `.md` o código de billing que **no** está en `controller`, `dto`, `api`, `usecase`, `service`, `events`, `gateway`, `infrastructure`, `entity` ni `repository` (main) ni en `billing/support` (tests). Cualquier otro archivo de backend, `contracts/`, `scripts/` o `.github/` lo activa.

  Cuando el push a `develop` reutiliza los tests de un PR verde (mismo árbol) y ese PR se saltó `migration` o `karate`, el job `backend-extra-tests` corre solo esos grupos. El registro `tested-tree.json` del PR guarda qué grupos corrió; un registro sin esos campos cuenta como «no corrió».
- **Nightly (`nightly-tests.yml`):** corre la suite completa en 6 shards con `-Dtest.exploration=nightly -Dtest.excludedGroups=none` (migration, Karate, docs y tests `@NightlyTest` incluidos). Se saltea si `develop` no cambió desde la última corrida verde. `schedule` solo corre desde la rama por defecto: hasta el próximo release se dispara a mano con `workflow_dispatch`.
- **Regla R5 (`BillingDecisionCoverageTest`):** cada decisión D-xxx vigente implementada o parcial necesita **al menos un test del tier PR**, es decir, con su `@Tag("D-xxx")`, sin `@NightlyTest`, sin `@Tag("nightly")`, sin `@Tag("migration")` y sin `@Disabled`. Si movés el único test de una decisión a nightly, el build falla con `R5 D-xxx ... sin test del tier PR`. Agregá un test liviano de PR en vez de saltear la regla.

### Reglas para no pagar contextos y esperas de más

- **Reutilizá la configuración compartida de mocks.** Cada combinación distinta de `@MockitoBean`/`@MockBean`/`@TestPropertySource`/`@ActiveProfiles` crea un contexto de Spring nuevo (varios segundos de arranque). Si tu test solo necesita mockear el envío de mails o el validador de Google, anotalo con `@SharedMockBeans` (`testsupport/SharedMockBeans.java`): mockea `EmailService` y `GoogleIdTokenService`, y todos los tests que lo usen comparten contexto. Para espiar servicios usá `@SharedSpyBeans` (`testsupport/SharedSpyBeans.java`) y `@Autowired` del bean (es el spy); para propiedades distintas (rate limit bajo, API docs apagada, actuator) `@SharedPropertyOverrides`. Ambos resetean después de cada test (`MockReset.AFTER`): quien cambia el comportamiento de un spy/mock lo restaura solo, y si el tipo falta en la anotación se agrega ahí en vez de declarar un `@MockitoBean`/`@MockitoSpyBean` por clase. Si hace falta un mock común más, se agrega a esa anotación, no se declara de nuevo en cada clase.
- **No uses `Thread.sleep`.** Usá Awaitility (`await().atMost(...).until(...)`). ArchUnit lo bloquea.
- **No uses `@DirtiesContext`.** Tira el contexto cacheado y obliga a recrearlo. Si un test deja estado, limpialo en un `@AfterEach` o usá `TestDatabaseIsolation`.
- **Acotá los procesos globales.** La detección de facturas vencidas y el barrido de mora (`InvoiceOverdueSweepService`), el barrido de cumplimiento (`EnrollmentBillingComplianceSweepService`), la emisión mensual (`MonthlyInstallmentIssuanceJob`), el vencimiento de solicitudes de autorización (`ExpireAuthorizationRequestsJob`), la activación de versiones de configuración (`ActivateDueConfigurationVersionsJob`) y el reenvío de eventos (`EventPublicationResubmissionJob`) recorren toda la base. En un test nuevo, no llames a esos jobs: invocá el caso de uso o el paso que recibe ids explícitos (la cuenta, la factura, la inscripción de tu escenario). ArchUnit lo detecta por llamada directa a esos métodos, con una lista de excepciones legadas que solo puede achicarse. Lo que ArchUnit **no** puede verificar es una llamada indirecta (un helper que invoca el job por reflexión o vía un bean intermedio): ahí manda esta regla y la revisión del PR.

### Listas de excepciones legadas

Las reglas de `TestSuiteRulesTest` usan `FreezingArchRule`, igual que las de `ArchitectureTest`: las violaciones existentes viven en `backend/src/test/resources/archunit-frozen/` y **solo pueden achicarse**. Si arreglás una, el archivo no se actualiza solo (`allowStoreUpdates=false`): borrá la línea a mano en el mismo PR. Nunca agregues líneas para tapar una violación nueva; corregí el test.

**Congelado en CI.** El CI corre con `-Darchunit.freeze.store.default.allowStoreUpdate=false` (la propiedad se llama `allowStoreUpdate`, sin "s"; la `allowStoreUpdates` de `archunit.properties` no existe para ArchUnit y se ignora). Con esa flag, el CI falla si una regla congelada tiene una clave nueva (cambió su lista de paquetes o su `because`, o es una regla nueva) o si el almacén tiene líneas de violaciones ya corregidas. Para regenerar el almacén en local, corré el test **sin** la flag (`./mvnw test -Dtest=<Clase>`): ArchUnit crea las entradas nuevas en `archunit-frozen/` y `stored.rules`; commiteá esos archivos, borrá las líneas obsoletas a mano y revisá en el diff que solo aparezca lo esperado. Para probar como el CI, agregá la flag al comando.

### Cómo leer el reporte del PR

El job `test-report` publica un comentario en el PR (se actualiza en cada push, no se duplica) y el mismo contenido en el resumen del job:

- **Tiempo de pared y suma de tests**, con la diferencia contra la última corrida de `develop` que publicó mediciones.
- **Contextos de Spring distintos** (unión entre shards) contra el tope de 24, con su diferencia.
- **Top 5 de clases más lentas tocadas por el PR** (o del total si el PR no toca clases de test), con su tiempo en `develop`.
- **Clases `@SlowTest`** con su tiempo y motivo.

Los tiempos para repartir clases entre shards no se editan a mano: cada push a `develop` suma sus medidas de surefire al historial de las últimas 8 corridas (`samples.json`, dentro del artifact `test-baseline`) y publica el promedio por clase como `timings.txt`. Una sola corrida es ruidosa: el costo de arrancar cada contexto de Spring lo paga la primera clase del shard que lo usa, así que cambia de clase cuando cambia el reparto. Por eso el reparto solo usa el baseline si tiene al menos 3 corridas de historial; si no (artifact viejo, sin historial, corrupto o con forma inesperada, o ausente) cae al `.github/test-shards/timings.txt` versionado, que es el promedio de 7 corridas. Las clases nuevas sin medición pesan el promedio de las medidas.

### Correr tests focalizados en local: `backend/dev-test`

```bash
backend/dev-test PolicyEvaluationTest,QualityRulesTest        # clases (una sola invocación de Maven)
backend/dev-test com.red_escuela.backend.billing.catalog      # todo un paquete de tests
backend/dev-test --changed                                    # tests tocados + homónimos (XTest/XTests/XIT) de las clases de producción tocadas
```

Arma una única invocación `./mvnw -o test -Dtest=… -Dsurefire.failIfNoSpecifiedTests=false -Darchunit.freeze.store.default.allowStoreUpdate=false`, toma el semáforo `~/.claude/epica18/ci-slot.sh` si existe y lo suelta al salir (también ante error o Ctrl-C). `--changed` compara contra `origin/develop` y el working tree, y solo sugiere: sumá a mano `architecture/` y lo que tu cambio afecte aunque no tenga archivo homónimo. Reglas: siempre `-o`, sin `clean` ni `test-compile` previo, una sola invocación por tanda. Un archivo `.java` cuyo nombre no coincide con su clase hace que Maven recompile todos los tests en cada corrida; `SourceFileNameMatchesTypeTest` lo impide.

### CI de PR: reintento de flaky

En eventos `pull_request` los shards corren con `-Dsurefire.rerunFailingTestsCount=1` (en `push` y nightly no). Un test que falla y pasa en el reintento no rompe el shard, pero el reporte del PR lo lista como flaky (`flakyFailure`/`flakyError` del XML de surefire): estabilizalo, no lo ignores.

### Reproducir una falla de un shard en local

Cada fallo de CI dice en qué shard ocurrió (`backend-tests (N)`). Para correr exactamente las mismas clases, con el mismo algoritmo y los mismos tiempos que la CI:

```bash
python3 .github/scripts/test-shards.py list 2     # clases del shard 2 (de 6)
python3 .github/scripts/test-shards.py run 2      # las corre desde backend/ con ./mvnw test
```

`run` toma el semáforo de CI (`~/.claude/epica18/ci-slot.sh acquire shard-2`) y lo libera al terminar, también si falla; si ese script no existe en la máquina, corre sin él. El reparto usa el `timings.txt` versionado: la CI usa el medido en la última corrida de `develop`, así que si difiere mucho puede variar la composición. Para reproducir con el reparto exacto, bajá el artifact `test-baseline` de esa corrida y corré `python3 .github/scripts/test-shards.py use <carpeta>` (reescribe `timings.txt`; no lo commitees).

### Qué hacer si falla un presupuesto

| Falla | Qué hacer |
|---|---|
| Una clase supera 30 s | Primero buscá por qué: contexto nuevo evitable, `Thread.sleep`, un proceso global sin acotar, datos de prueba de más. Si es un escenario largo legítimo, partilo; si no se puede, `@SlowTest(reason = "...")`. |
| Una `@SlowTest` supera 90 s | Partila. El límite alto no es para que crezca. |
| Un shard supera 300 s de reloj | Casi siempre es una clase lenta nueva (mirá el top del reporte) o un balanceo desactualizado: con cada push a `develop` el promedio móvil se actualiza solo. Un reparto con tiempos estimados parejos puede dar tiempos reales dispares por el costo de los contextos; mirá si fue una sola corrida antes de tocar nada. |
| Más de 24 contextos | Mirá el reporte: tu clase nueva trajo una combinación de mocks/propiedades propia. Pasala a `@SharedMockBeans` o alineá sus propiedades con un contexto existente. |
| ArchUnit en tests | Leé el mensaje: sleep → Awaitility; `@DirtiesContext` → limpiar estado; `@SpringBootTest` en `domain`/`policy` → test puro; job global → caso de uso con ids. |

**Arranque en frío fuera de la medición.** Cada shard corre 2 JVM (`forkCount=2`) y la primera clase de integración de cada una pagaba, dentro de su tiempo, el arranque de Postgres (Testcontainers), las migraciones de Flyway y el snapshot de `TestDatabaseIsolation` (el bloque `static` de `AbstractIntegrationTest`, unos 12-15 s en CI), además del primer contexto de Spring (8-14 s). Eso llevaba a clases triviales a 26-31 s. `testsupport/SharedInfrastructureWarmUp` (un `LauncherSessionListener` registrado en `META-INF/services`) inicializa esa infraestructura al abrir la sesión de JUnit, antes de que arranque cualquier clase, así que el límite de 30 s mide la clase y no el arranque del fork. Solo actúa con la variable `CI` definida (GitHub Actions la define); en local no cambia nada. El primer contexto de Spring de cada fork se sigue midiendo en la clase que lo crea.

Para reproducir en local: `python3 .github/scripts/test-budget.py check-shard --shard 0 --reports backend/target/surefire-reports` contra los reportes de una corrida (`--max-class 5` fuerza el fallo para ver el mensaje; para probar el tope de shard exportá `SHARD_START` y `SHARD_END` en segundos epoch). Los contextos de una corrida quedan en `backend/target/spring-contexts/contexts.txt` (los escribe `SpringContextCensusListener`, una línea por configuración distinta).

## Frontend (Vitest + RTL + MSW, instalado desde la Fase 1)

Stack propuesto: **Vitest** (nativo de Vite, mismo config/transform que el build,
sin duplicar setup) + **React Testing Library** (testea comportamiento visible al
usuario, no detalles de implementación) + **MSW (Mock Service Worker)** para
interceptar `fetch` a nivel de red, no mockear `apiFetch` ni los hooks.

Tres niveles, elegidos con cuidado por qué cubre cada uno — no todo necesita los
tres:

1. **Unit test** — funciones puras sin React (formatters, `apiFetch` en
   aislamiento con MSW simulando responses reales incluyendo 401/429/error
   vacío). Ejemplo obligatorio: `apiFetch` reintenta refresh una vez en 401 y
   after eso dispara `AuthError` — es lógica con bordes reales, no trivial.
2. **Component test** — un componente con lógica propia (validación, estados de
   error, disabled durante submit) renderizado con RTL, sin red real (MSW
   mockea el endpoint que ese componente llama). Ejemplo: `Login.jsx` — usuario
   vacío no envía, credenciales inválidas muestra el mensaje correcto, 429
   muestra el mensaje distinto.
3. **Integration test (selectivo, no todo)** — varios componentes reales
   montados juntos con `AuthProvider` + `MemoryRouter` reales, MSW mockeando
   solo el backend. Se reserva para **flujos que si se rompen, rompen algo caro
   de notar tarde**: login end-to-end con redirect por rol, `ProtectedRoute`
   bloqueando un rol no autorizado, persistencia de sesión al recargar. No se
   escribe un integration test por cada pantalla — se eligen los 3-6 flujos más
   críticos del roadmap (login, inscripción a materias, carga de notas/asistencia,
   aprobación de ciclo, facturación) y se los blinda ahí.

**Qué NO se re-testea en el frontend**: reglas de negocio que el backend ya
verifica exhaustivamente (ej. que no se puede exceder el cupo de una comisión) —
esas viven y se testean en el backend. El frontend testea que **la pantalla haga
la llamada correcta y reaccione bien a la respuesta**, no que reimplemente la
regla de negocio en el test.

**E2E real contra navegador (Playwright)**: no está en el alcance de esta fase —
se evalúa recién cuando haya CI armado para correrlo contra un backend real
levantado (Docker Compose + Postgres). Hasta entonces, el nivel 3 de arriba
(integration test con MSW) es el que blinda los flujos críticos.

## QA de contrato HTTP — dos herramientas, dos propósitos distintos

Los tests de JUnit/Vitest verifican que el CÓDIGO se comporta como el código
espera — no necesariamente que el CONTRATO HTTP real (status code exacto, body
vacío en errores, endpoint público vs. protegido) sea el correcto, ni que un
flujo de varios pasos siga siendo consistente cuando se lo corre sin el
paraguas de una transacción de test. Encontramos esto de forma muy concreta en
Épica 7 Fase 2: un test JUnit `@Transactional` a nivel de clase daba falso
positivo en una aserción de rol otorgado, porque toda la prueba corría en una
única transacción — nunca hubo un commit real de por medio.

### Qué capa cubre qué — regla para no testear lo mismo dos veces

Tres capas pueden pegarle al mismo endpoint: el `*ServiceTest` de integración, un
test de controller con MockMvc, y un `Scenario` de Karate. **Cada caso va en una
sola.** La pregunta que decide no es "¿qué herramienta uso?", es "¿qué estoy
verificando?":

| Lo que quiero verificar | Dónde va |
|---|---|
| Que la **regla de negocio** sea correcta (invariantes, cálculos, transiciones de estado, qué pasa con un dato borde) | `*ServiceTest` — integración con Postgres real |
| Que el **contrato HTTP** sea correcto: status code exacto, forma del JSON, público vs. protegido, autorización por rol, y flujos de varios pasos encadenando respuestas reales | **Karate** |
| Lo que Karate **no puede expresar bien**: concurrencia, rate limiting, control fino del reloj, multipart, o cualquier cosa que necesite manipular el entorno desde Java | **MockMvc** |

**El default del contrato HTTP es Karate, no un test de controller.** MockMvc es la
excepción, y el test tiene que dejar claro por qué no podía ser un `Scenario`.

Los dos MockMvc que hoy **no** están duplicados en Karate son exactamente
`RateLimitingTest` y `EnrollmentConcurrencyTest` — esa es la línea natural: ahí hacía
falta control del reloj y de los hilos que Gherkin no da.

**Por qué existe esta regla.** Se escribió después de medirlo: `AuthControllerTest`
tiene 8 tests con MockMvc y `karate/auth/` tiene 21 escenarios, y el solapamiento es
casi total — login happy path, credenciales inválidas 401, body inválido 400, refresh,
logout, forgot-password, verify-reset-code y reset-password están **en las dos**, sobre
la misma app levantada con el mismo Testcontainers. Se paga dos veces por la misma
cobertura. No pasó por descuido: hasta ahora este documento distinguía Karate de Bruno
pero nunca dijo nada sobre MockMvc, así que no había con qué decidir.

Al mismo tiempo, Karate **no** es redundante en general: es la única cobertura de
contrato HTTP de `inscripciones-academicas` (23 escenarios), `evaluacion-y-asistencia`
(13), `procesos-administrativos` (12), `autoservicio` (7) y `catalogo` (4). Solo 16 de
63 archivos de test Java usan MockMvc, y se concentran en `identity`, `enrollment` y
`academicstructure`.

**No se limpia retroactivamente**: los duplicados que ya existen se dejan salvo pedido
explícito, mismo criterio que el resto de las convenciones de este proyecto. La regla
aplica a lo que se escriba de acá en adelante.

Se usan **dos herramientas, cada una para lo que sirve mejor** — no son
redundantes, hay que mantener las dos actualizadas cuando se agrega o cambia
un endpoint:

- **[Karate](https://karatelabs.github.io/karate/)** (`backend/src/test/resources/karate/`)
  es el gate de regresión **automático**: corre en CI dentro del job `backend`
  existente (`KarateSuiteTest`, `@SpringBootTest` con `RANDOM_PORT`, Postgres
  de Testcontainers propio — ver más abajo por qué propio y no el compartido).
  Es donde vive la cobertura que tiene que sostenerse sola en cada PR, sin que
  nadie la dispare a mano.
- **[Bruno](https://www.usebruno.com/)** (`qa/bruno/`) es para **explorar a
  mano** — CLI (`bru`, `npm install -g @usebruno/cli`) o GUI
  (`brew install --cask bruno`) contra un backend real corriendo. Sirve para
  investigar un bug interactivamente, probar algo puntual sin escribir código,
  o que alguien no-técnico corra un caso reproducible. **No corre en CI** —
  correrla ahí además de Karate pagaría el mismo costo dos veces por la misma
  cobertura (ver "Antes de dar una tarea por terminada" más abajo: agregar a
  ambas, no una u otra).

### Karate — regresión automática en CI

Feature files en `backend/src/test/resources/karate/<dominio>/*.feature`,
corridos por `KarateSuiteTest` (`backend/src/test/java/.../karate/`). Cada
`Scenario` es un caso de uso con su criterio de aceptación en el nombre — la
sintaxis Gherkin (`Given`/`When`/`Then`) es la documentación, no hace falta un
comentario aparte explicando "qué" prueba. `Scenario Outline` + `Examples` para
variantes con la misma aserción (ej. varios formatos de contraseña inválida,
todos esperando 400) — no un Scenario por variante cuando el criterio es
idéntico.

**Por qué `KarateSuiteTest` NO extiende `AbstractIntegrationTest`** (el
Postgres singleton compartido por el resto de la suite): la clase a propósito
no es `@Transactional` — si lo fuera, todos los `.feature` correrían en una
única transacción de test y enmascararían bugs reales de boundary
transaccional, el mismo problema de arriba. Sin rollback, cada fila que un
`.feature` crea queda en la base — y varios tests de otras clases asumen
"cuento todas las filas de la tabla y espero exactamente N", asunción que
dejaría de ser cierta si Karate ensuciara el Postgres compartido (confirmado:
pasó de verdad al probarlo, 4 tests de otras clases empezaron a fallar). Fix:
`KarateSuiteTest` levanta su propio contenedor de Testcontainers, nunca
comparte el de `AbstractIntegrationTest` — nadie necesita limpiar nada
manualmente, el contenedor entero se descarta al terminar la corrida.

**Setup que no tiene sentido hacer por HTTP** (crear un token de staff sin un
flujo de alta completo, sembrar una institución) vive en
`KarateFixtures`/`KarateSpringContext` (puente Java, `Java.type(...)` desde el
`.feature`) — usa los repositories (`.save()`, transaccionales por sí solos),
nunca `EntityManager.persist()` crudo, por el mismo motivo de arriba (sin
`@Transactional` en la clase, no hay transacción externa donde flushear).

**Correr:**
```bash
./mvnw test -Dtest=KarateSuiteTest              # toda la suite de Karate
./mvnw test -Dtest=KarateSuiteTest#testAuth      # una sola carpeta/@Karate.Test
./mvnw clean verify                              # la suite completa, incluye Karate
```
Genera un reporte HTML en `target/karate-reports/karate-summary.html` en cada
corrida.

### Bruno — exploración manual

Un request por caso de prueba (no un request por endpoint), en
`qa/bruno/<Dominio>/`, con `docs{}` explicando el criterio de aceptación y
`tests{}` afirmando el status code exacto. Secuencias multi-paso van en
subcarpetas `Caso de uso - <flujo>/` (`seq:` numerado).

**Correr contra una base descartable, nunca la de desarrollo persistente**
(evita dejar instituciones/carreras/alumnos de prueba mezclados con datos
reales — la mayoría de las entidades no tienen endpoint de borrado):
```bash
docker compose -f docker-compose.qa.yml up -d   # Postgres descartable, puerto 5433
DB_URL=jdbc:postgresql://localhost:5433/agustina_bermejo mvn spring-boot:run -Dspring-boot.run.profiles=local
bru run qa/bruno -r --env Local
docker compose -f docker-compose.qa.yml down    # tira todo, sin dejar rastro
```
**Cuidado con el rate limiter**: correr la colección completa varias veces
seguidas en poco tiempo agota el bucket de `/auth/login`/`/auth/register`
(capacidad 10-20/min, compartida en memoria para todo el proceso) y produce
`429` en cascada que no son bugs reales.

### Cuándo escribir casos nuevos

Cualquier endpoint nuevo o tocado en una fase, con su happy path + los bordes
reales del dominio (no solo "campo faltante" genérico — pensar como QA senior:
¿qué pasa si mando un id que pertenece a otra entidad? ¿un duplicado por una
vía que el código no valida explícito pero que un constraint de DB sí
rechaza? ¿el status code de "sin sesión" es el mismo en todos los endpoints
protegidos?) — **en las dos herramientas**, Karate y Bruno, no solo una. Ver
`qa/bruno/BACKFILL.md` para el estado de cobertura sobre los endpoints
existentes.

### Inconsistencias o limitaciones reales encontradas durante el QA

Si el backfill de un endpoint revela un comportamiento real del backend que
no es lo que "debería" ser (un status code inconsistente con el resto del
sistema, un endpoint que falta para poder armar una feature de frontend,
una semántica de update que no permite lo que parece que debería permitir)
pero **no** es un bug bloqueante para el PR en curso, no se documenta solo
en el PR ni en `BACKFILL.md` — se agrega también como entrada nueva en
[Deuda técnica y gaps de QA](~/Documentos%20No%20SYNC/Obsidian-vault/agustina-bermejo-app/Roadmap%20frontend/Deuda%20técnica%20y%20gaps%20de%20QA.md)
(vault de Obsidian, linkeado desde `Roadmap frontend/Indice.md`) — para que
quede visible en el roadmap y no se pierda en el historial de PRs.

## Contrato OpenAPI (lado backend)

- `contracts/openapi.json` es la instantánea del spec generado por springdoc desde los controllers reales. `OpenApiSnapshotTest` falla si el código cambia el spec sin regenerarlo.
- Regenerar: `cd backend && ./mvnw test -Dtest=OpenApiSnapshotTest#generatedSpecMatchesCommittedSnapshot -Dopenapi.update=true`, y revisar el diff de `contracts/openapi.json` en el PR.
- Cambios incompatibles contra otro spec: `scripts/oasdiff-breaking.sh <base.json> <nuevo.json>` (descarga oasdiff 1.33.0 de la release oficial de GitHub y verifica el sha256 de `checksums.txt`). El CI lo corre en cada PR contra el `contracts/openapi.json` de la rama base; se saltea con la etiqueta `contract-break-approved`.
- Prueba reproducible de que el control detecta un campo borrado: `scripts/oasdiff-probe.sh`.
- `/v3/api-docs` solo existe con `springdoc.api-docs.enabled=true` (perfiles `local` y `test`); en producción la propiedad es `false` y no hay Swagger UI.
- Los componentes de un `record` de respuesta son `required` salvo `Optional` o `@Nullable`; las clases con Lombok no reciben ese tratamiento.

## Antes de dar una tarea por terminada

El CI de GitHub corre `./mvnw clean verify` (backend) y `npm run lint`, `npm run build`,
`npm run test` (frontend). Esa misma verificación se corre en local **antes de abrir el
PR**, ver [workflow.md](workflow.md), sección "CI local".


**Si el comportamiento depende de una variable de entorno que el runner setea
distinto que tu máquina, corré la suite local con esa variable forzada al valor del
runner.** Correr "los mismos comandos" no es correr el mismo entorno. Caso real: la
Fase 5.0 (zona horaria) pasó en local y falló en CI porque la máquina de desarrollo
está en horario de Argentina y el runner en `Etc/UTC` — la verificación correcta era
`TZ=UTC ./mvnw clean verify`. Lo mismo aplicaría a locale o a cualquier default de la
JVM que dependa del host.

**Hay diferencias de entorno que forzar una variable NO cubre, y ahí la defensa es
cómo se escribe la aserción.** Dos casos reales:

- **Precisión del reloj.** `LocalDateTime.now()` da microsegundos en macOS y
  nanosegundos en el runner de Linux, y Postgres guarda `timestamp` con precisión
  de microsegundos. Un test que compara un `LocalDateTime` en memoria contra el
  mismo valor leído de la base pasa en una máquina y falla en la otra. Al persistir
  un instante y compararlo: truncar a `ChronoUnit.MICROS` —la precisión que la base
  guarda de verdad— o comparar dos lecturas de la base entre sí.
- **Unicidad "probabilística".** Un código generado con `System.nanoTime() % N`
  choca o no según la suerte y según cuántas filas acumuló la base compartida de
  la suite. Rompió el CI dos veces con `uk_subject_code`. Para datos con constraint
  de unicidad, usar un contador de proceso —`TestSubjectCodes.next()`— que garantice
  unicidad por construcción, no un random con espacio chico.

En los dos casos el CI local corrido entero y en verde **no puede** atrapar la falla:
no es un comando que falte correr, es una aserción mal escrita.

Si tocaste backend: `./mvnw clean verify` en verde. Si tocaste frontend: `npm run test`
en verde + `npm run build` sin errores (ver [docs/context/frontend.md](frontend.md)
para dónde va cada tipo de componente/hook nuevo, así el test queda en el lugar
correcto). Si el cambio toca un flujo de la lista de integration tests críticos
de arriba, ese test específico se corre y se muestra el resultado, no se asume.

## Checklist de QA obligatorio al cerrar una fase

Antes de pedir el merge de un PR que cierra una fase del roadmap:

1. **Cada endpoint nuevo o tocado tiene su carpeta/requests en `qa/bruno/`**
   (ver arriba) — happy path + casos borde +, si aplica, un "Caso de uso -
   ..." de punta a punta con verificación de estado real (no asumido).
2. **`bru run` de esa carpeta, en verde, contra el backend local real** —
   no alcanza con que los tests de JUnit/Vitest pasen; hay que confirmar el
   contrato HTTP real (status code exacto, body vacío o no en errores,
   público vs. protegido) corriendo contra el servidor real, sin el
   paraguas de una transacción de test.
3. **Reporte de QA de la fase** — nota nueva en Obsidian,
   `QA - <nombre de la fase>.md`, en la carpeta de esa fase, linkeada desde
   su spec técnica. Tabla por endpoint: caso probado → cómo se probó →
   resultado. Sección explícita de gaps (qué NO se probó y por qué) — no
   dar a entender cobertura total si no la hay.
4. **Todo bug real encontrado en el camino** (no solo en el endpoint que se
   estaba construyendo — un bug preexistente que aparece mientras se prueba
   se corrige ahí mismo si el alcance es chico, o se documenta como hallazgo
   explícito si es mayor) queda con: causa raíz, fix, y un caso de prueba
   nuevo (Bruno y/o JUnit/Vitest) que lo hubiera atrapado — explicando por
   qué la cobertura existente no lo atrapaba.
5. **`./mvnw clean verify` completo** (no solo los tests del área tocada) — un fix
   de alcance amplio (ej. un `@ExceptionHandler` nuevo) puede tener efectos
   secundarios en código no relacionado; correr toda la suite es la única
   forma de confirmarlo, no asumirlo.
