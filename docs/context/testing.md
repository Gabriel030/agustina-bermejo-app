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

## QA de contrato HTTP (Bruno) — `qa/bruno/`

Los tests de JUnit/Vitest verifican que el CÓDIGO se comporta como el código
espera — no necesariamente que el CONTRATO HTTP real (status code exacto, body
vacío en errores, endpoint público vs. protegido) sea el correcto, ni que un
flujo de varios pasos siga siendo consistente cuando se lo corre contra el
backend real, sin el paraguas de una transacción de test. Encontramos un caso
real de esto en Épica 7 Fase 2: un test JUnit `@Transactional` a nivel de clase
daba falso positivo en una aserción de rol otorgado, porque toda la prueba
corría en una única transacción — nunca hubo un commit real de por medio. Correr
la misma prueba contra el servidor real (con Bruno) sí lo atrapó.

**`qa/bruno/` es una colección de [Bruno](https://www.usebruno.com/)** (CLI:
`bru`, instalado con `npm install -g @usebruno/cli`; GUI opcional:
`brew install --cask bruno`) con un request por caso de prueba — no un request
por endpoint. Estructura:

```
qa/bruno/
  bruno.json
  environments/Local.bru        # baseUrl=http://localhost:8080
  00 - Setup/                   # bootstrap compartido (resuelve institutionId, etc.)
  Auth/                         # un caso de prueba = un archivo .bru
  <Dominio> - <sub-dominio>/
    GET/POST sueltos ...        # request por endpoint, casos borde propios
    Caso de uso - <flujo>/      # secuencia numerada (seq: 1, 2, 3...) que
                                 # encadena varios requests + verifica estado
                                 # final vía la API (no SQL directo — así corre
                                 # en cualquier entorno)
```

**Convención de un `.bru`**: `docs` explica el caso de uso y el criterio de
aceptación que prueba (no solo "qué" — el "por qué" de ese caso puntual,
incluidos los bordes: campos faltantes, `null` explícito, tipos incorrectos,
ids inexistentes, duplicados). `tests` afirma el status code exacto observado
contra el backend real (nunca `[200, 201]` genérico salvo que el código
realmente pueda variar por diseño) y las partes del body que importan.
`script:post-response` encadena datos entre requests (`bru.setVar(...)`) para
secuencias multi-paso.

**Correr:**
```bash
bru run "qa/bruno/00 - Setup" "qa/bruno/Auth" -r --env Local
bru run "qa/bruno/<carpeta de caso de uso>" -r --env Local
bru run qa/bruno -r --env Local   # toda la colección
```
Necesita el backend local corriendo (`mvn spring-boot:run`, perfil `local` para
tener las cuentas demo sembradas — `preceptor.demo`/`administrativo.demo`/etc.,
password `Test1234`) y Postgres arriba. **Cuidado con el rate limiter**: correr
la colección completa varias veces seguidas en poco tiempo agota el bucket de
`/auth/login`/`/auth/register` (capacidad 10-20/min, compartida en memoria para
todo el proceso) y produce `429` en cascada que no son bugs reales — si eso
pasa, esperar ~1 minuto o reiniciar el backend.

**Cuándo escribir casos nuevos acá**: cualquier endpoint nuevo o tocado en una
fase, con su happy path + los bordes reales del dominio (no solo "campo
faltante" genérico — pensar como QA senior: ¿qué pasa si mando un id que
pertenece a otra entidad? ¿un duplicado por una vía que el código no valida
explícito pero que un constraint de DB sí rechaza? ¿el status code de "sin
sesión" es el mismo en todos los endpoints protegidos, o depende de si hay un
`AuthenticationEntryPoint` configurado?). Un caso de uso que involucra alta +
baja + verificación de estado va en su propia subcarpeta "Caso de uso - ...",
no como requests sueltos.

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
