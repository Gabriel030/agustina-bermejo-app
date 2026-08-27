# Backend — convenciones (Spring Boot)

Leé esto antes de tocar `backend/`. Todo acá está verificado contra código real del
repo, no es aspiracional.

## Estructura por capa

`controller/` → `service/` → `repository/` (Spring Data JPA, interfaces sin
implementación) → `entity/`. DTOs de request/response en `dto/` como `record`,
nunca se expone una `entity` directo en un controller. Cada DTO de response tiene
un factory estático `from(Entity)` (ver `PersonResponse.from`, `AuthResponse.from`).

- **Un controller = un `@RequestMapping` de recurso.** No hay controllers "god" que
  agrupen dominios distintos. Los endpoints de autoservicio del rol autenticado
  van bajo `/me/...` en su propio controller (`MeEnrollmentController`,
  `MeInvoiceController`, etc.) — nunca reutilizan el controller del recurso
  administrado por staff.
- **La persona autenticada nunca se resuelve de un id de la URL.** Se resuelve del
  `sub` del JWT vía `SecurityContextHolder.getContext().getAuthentication().getName()`
  — ver `CurrentPersonService.resolveCurrentPerson()` (reusable) y el patrón
  equivalente en `PersonProfileService`. Esto es lo que hace imposible, incluso por
  error de implementación, que un endpoint de autoservicio afecte a otra persona.
- **Autorización de rol grueso vía `@PreAuthorize("hasRole('X')")`** a nivel de
  clase o método del controller. Cuando la regla de autorización depende de datos
  (ej. "Administrativo puede crear Docente/Preceptor pero no Administrativo"), esa
  regla fina vive en el **service**, no en la anotación — ver
  `StaffService` (mapa `actorRole -> rolesQuePuedeCrear`).

## Excepciones

Una excepción de dominio por caso, con nombre descriptivo del problema
(`InvalidPasswordPolicyException`, `RoleNotAssignableByActorException`, nunca un
`BusinessException` genérico con un string). Todas se registran en
`exception/GlobalExceptionHandler.java`, agrupadas por status HTTP
(`handleBadRequest`, `handleNotFound`, `handleConflict`, `handleForbidden`, etc.).

**Los handlers no devuelven body — solo fijan el status.** No hay un DTO de error
con `message` en el JSON de respuesta; el cliente (frontend) arma el mensaje al
usuario a partir del código HTTP, no de un body. No agregues un body de error sin
antes confirmar con el usuario que se quiere ese cambio de contrato — es una
decisión tomada, no un olvido.

## Migraciones (Flyway)

`src/main/resources/db/migration/V{N}__descripcion_en_snake_case.sql`, `N`
correlativo (mirá el último `V*.sql` existente antes de crear uno nuevo — no asumas
el número). `spring.jpa.hibernate.ddl-auto=validate`: el schema SIEMPRE se define en
la migración, nunca se deja que Hibernate lo genere. Constraints de integridad
(NOT NULL, unique, índices parciales de Postgres para reglas tipo "solo un X activo
por Y") van en la migración SQL, no solo a nivel de validación Java.

## Seguridad

`SecurityConfig.java`: todo exige JWT salvo `/auth/**` y `POST /prospects`
(altas públicas). CORS restringido a `app.cors.allowed-origins` (default
`http://localhost:5173`, el puerto de Vite). Rate limiting en memoria (Bucket4j)
solo en `/auth/login` y `/auth/forgot-password` — no hay rate limiting genérico de
API, el resto de los endpoints ya exige JWT válido como barrera.

`JWT_SECRET` no tiene default en `application.properties` a propósito — el boot
falla explícito si falta. Para correr local ver
[docs/context/debugging.md](debugging.md).

## Dominio y DDD

El proyecto ya sigue objetos de dominio reales (`Person`, `Role`,
`SubjectSection`, `AcademicOfferingCycle`-tipo de conceptos), no DTOs anémicos
con lógica en un "manager". Antes de agregar un service nuevo, primero mirá si la
regla de negocio pertenece a un método de la entidad (invariante propio) o si
necesita coordinar varias entidades (ahí sí es un domain service). Para elegir
qué patrón táctico DDD aplicar en un caso concreto, usá la skill `design-patterns`.

## Antes de asumir que un endpoint existe

Varias specs de roadmap ya se escribieron asumiendo endpoints que después
resultaron no existir (ejemplo real: `GET /me` no existía, solo había `PUT /me`,
se agregó recién en la Fase 1 del frontend). **Grepeá el controller real antes de
escribir contra un endpoint** — no confíes en lo que diga una spec vieja de
Obsidian sin verificar.
