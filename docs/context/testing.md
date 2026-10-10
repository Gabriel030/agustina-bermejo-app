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
| PIT (Pitest, perfil `-P mutation`) | Mutation testing | ¿Mis tests detectarían un bug si alguien lo introduce? (umbral 90% en el dominio puro de billing) | En uso, **solo local** |
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
| OWASP Dependency-Check / ZAP + matriz de autorización por rol | Seguridad | ¿Hay vulnerabilidades conocidas o algún endpoint accesible por un rol que no corresponde? | Propuesto (parcial) |

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
| Es lógica de dominio de facturación | PIT en local (umbral 90%) y su fila en `docs/testing/local-test-log.md` | Si alguien rompe esto, ¿algún test se entera? |
| Agrega clases, paquetes o dependencias entre módulos | ArchUnit y Modulith (corren en CI) | ¿La estructura se degrada? |
| Cambia permisos o roles | Matriz rol × endpoint: 403 para cada rol que no corresponde | ¿Un rol puede hacer algo que no debería? |
| Es un flujo crítico del usuario (inscribirse, pagar, aprobar) | Playwright (propuesto) | ¿Un usuario real lo completa en el navegador? |
| Cambia la forma de una respuesta que consume el front | Contrato OpenAPI (propuesto) | ¿El cambio rompe al front sin que nadie se entere? |
| Procesa volumen (corrida mensual, pipeline nocturno) | k6 o Gatling (propuesto) | ¿Aguanta miles de alumnos en el tiempo disponible? |

### Dónde corre cada cosa y cómo queda constancia

GitHub Actions tiene un tope de minutos, así que en el CI solo corre lo barato: `./mvnw clean verify` (JUnit, Testcontainers, Karate, ArchUnit, Modulith) y el front (lint, build, Vitest), cada uno solo si cambió su área. **PIT, Playwright, k6 y OWASP Dependency-Check corren solo en local**, mientras se programa, con el semáforo de CI.

Cada corrida local se anota en [`docs/testing/local-test-log.md`](../testing/local-test-log.md): fecha, PR, commit, área, herramienta, comando y resultado. Antes de modificar un área, buscá ahí qué herramientas pesadas la cubren y volvé a correrlas. Si el PR toca código que PIT muta (los paquetes de `targetClasses` del perfil `mutation`: `shared/domain`, `billing/operation` y los `domain`/`policy` de billing) o el `pom.xml`, y no agrega su fila de PIT, el job `local-tests-log` del CI falla. Si cambiás `targetClasses`, actualizá también el patrón de ese job.

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
