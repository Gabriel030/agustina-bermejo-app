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

## Frontend (a instalar — todavía no hay tests)

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

## Antes de dar una tarea por terminada

Si tocaste backend: `./mvnw test` en verde. Si tocaste frontend: `npm run test`
en verde + `npm run build` sin errores (ver [docs/context/frontend.md](frontend.md)
para dónde va cada tipo de componente/hook nuevo, así el test queda en el lugar
correcto). Si el cambio toca un flujo de la lista de integration tests críticos
de arriba, ese test específico se corre y se muestra el resultado, no se asume.
