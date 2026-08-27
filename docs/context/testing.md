# Testing — estrategia

Principio general del proyecto: **la cobertura numérica no es el objetivo, los
casos de uso reales sí lo son.** Un test que solo existe para subir el % de
cobertura sin verificar un comportamiento que alguien podría romper no vale nada.
Priorizá: (1) casos de uso de negocio con sus bordes, (2) flujos críticos de punta
a punta, y recién después mirá cobertura como termómetro de qué quedó sin tocar.

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

**Convención de nombres de test**: describe el escenario + el resultado esperado
en una sola palabra compuesta en español, ej.
`putMeActualizaSoloElPropioPersonYNuncaElDni`,
`getMeSinAuthDevuelve401OForbidden`. No `test1`, no `shouldReturnX` en inglés.

Correr todo: `./mvnw test` desde `backend/`. Necesita Docker corriendo (Testcontainers
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

## Antes de dar una tarea por terminada

Si tocaste backend: `./mvnw test` en verde. Si tocaste frontend: `npm run test`
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
5. **`./mvnw test` completo** (no solo los tests del área tocada) — un fix
   de alcance amplio (ej. un `@ExceptionHandler` nuevo) puede tener efectos
   secundarios en código no relacionado; correr toda la suite es la única
   forma de confirmarlo, no asumirlo.
