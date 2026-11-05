# ADR 0002 — Patrón de módulos con Spring Modulith

- **Estado:** aceptado 30-oct-2026
- **Fecha:** 2026-10-30
- **Reemplaza:** las secciones "Qué expone un módulo" y "Agregar una consulta pública" de `docs/context/modules.md` (ya reescritas).
- **Referencia:** ADR 0001 de ms-users (coordinador, caso de uso, especialista, herramienta, máquina).
- **Decisiones D1-D5:** aprobadas por Gabriel el 30-oct-2026 (ver el final del documento).

## Por qué

M4 y M5 chocaron con cuatro reglas de ArchUnit distintas y llegaron a cinco capas para una consulta entre módulos (caso de uso, colaborador, puerto, adaptador, repositorio), con cero reducción medida en la fase 1. La causa: se mezclaron dos tradiciones. Nuestro `backend.md` viene de buckpal (interfaz más implementación, puertos y adaptadores) y el ADR de ms-users viene de otra (coordinador, caso de uso, especialista, herramienta; "sin interfaz por especialista"). Este ADR elige una sola y la hace cumplir con ArchUnit.

## Figuras (se mantienen los nombres de nuestro repo)

| Figura | Qué es en nuestro repo | Puede llamar a |
|---|---|---|
| Coordinador | `*Controller`, `@Scheduled`, listeners de eventos, receivers | solo casos de uso de **su** módulo |
| Caso de uso | `XxxUseCase` + `XxxService` (`@UseCase`), el guion de un flujo | solo especialistas de su módulo; nunca otro caso de uso |
| Especialista | colaboradores de `service/` (`TermFinder`, `AttendanceSectionAccess`) y los especialistas expuestos de `api/` | herramientas propias y `api` de módulos inferiores |
| Herramienta | `repository`, mappers, clientes | nadie más |
| Máquina | jobs que despiertan un caso de uso | casos de uso de su módulo |

Regla de la cadena: el coordinador invoca casos de uso, el caso de uso orquesta especialistas, el especialista usa herramientas. Nunca al revés, nunca salteando un escalón. Se conserva `XxxUseCase` (interfaz) más `XxxService`: son 235 clases y no se renombran.

## Superficie de un módulo (estrictamente separada)

Un módulo expone tres paquetes y nada más:

- **`<modulo>.api`**: **especialistas concretos**, sin interfaz, y los records de lectura que devuelven. Es lo único que otro módulo puede llamar para pedir o hacer algo.
- **`<modulo>.events`**: eventos con ids y valores planos, nunca entidades (ya existe la regla `domainEventsCarryNoEntities`).
- **`<modulo>.model`**: entidades que otro módulo referencia por relación JPA (`@NamedInterface("model")`, como hoy `Person`, `Student`, `Program`).

Todo lo demás (`usecase`, `service`, `repository`, `entity`, `dto`, `policy`) es interno. **Los casos de uso de un módulo no se exponen**: desde afuera solo se llega a sus especialistas.

Los métodos públicos de `api` solo reciben y devuelven: tipos de `java.*`, records de la propia `api`, tipos `model` y eventos. Nunca una entidad no expuesta.

## Dirección de dependencias

- **Hacia abajo** (módulo superior usa al inferior, según el orden de capas de `modules.md`): el especialista del superior llama directo a la `api` del inferior. Sin interfaz, sin adaptador.
- **Hacia arriba** (el inferior necesita algo del superior) **y hacia billing y notification**: evento, o puerto invertido. Solo ahí se admite una interfaz: se declara en la `api` del módulo inferior con sufijo `Port` y la implementa el superior (como `OutstandingDebtPort`, D-185). Una interfaz en `api` que no sea un `*Port` con implementador en otro módulo no se admite.

## Cómo queda una consulta, ejemplo

`attendance` necesita saber si un docente da una comisión: `AttendanceController` llama a `LoadAttendanceUseCase` (de attendance), que llama a `AttendanceSectionAccess` (especialista propio en `attendance.service`), que llama a `SectionTeachingFinder` (especialista expuesto en `academicstructure.api`), que usa su repositorio. Cuatro pasos, cero interfaces, cero adaptadores.

## Reglas de ArchUnit (todas congeladas al entrar, con test de fixture)

Convención de la casa: cada regla nueva entra con `FreezingArchRule`, con su test negativo (como `transactionRuleDetectsATransactionalControllerAndAllowsAService`) y se descongela cuando su cuenta llega a cero.

| # | Regla | Violaciones hoy |
|---|---|---|
| R1 | Un caso de uso depende solo de su subdominio: `service`, `policy`, `domain`, `dto`, `events`, `shared` | ya existe (`ArchitectureRules.java:230-239`); **no se toca** |
| R2 | Ningún caso de uso depende de otro `*UseCase` | 6 archivos importan un `*UseCase`; a verificar cuántos son del mismo subdominio |
| R3 | Un especialista (`service`) no depende de `usecase` | 8 clases |
| R4 | Un controller depende solo de casos de uso de su módulo (y DTOs); no de `repository` ni de `service` | 7 de 91 importan repositorios; falta medir `service` |
| R5 | Una clase de `api` no depende de `usecase` ni de controllers | a medir |
| R6 | Los métodos públicos de `api` no mencionan tipos de `entity` salvo los `model` | a medir |
| R7 | Una interfaz en `api` se llama `*Port` y la implementa una clase de otro módulo | hoy hay interfaces `*Lookup` y `*Queries` sin segundo implementador |
| R8 | Dependencias hacia arriba entre módulos: no aumentan (ya lo cubre `ModularityTest` y la línea base de 1.151) | línea base |
| R9 | Ningún módulo usa el `repository` de otro (ya existe `subdomainsDoNotUseOtherSubdomainRepositories`) | congelada |

Límite honesto: ArchUnit mira dependencias entre clases. No ve consultas SQL o JPQL nativas que lean tablas de otro módulo (por ejemplo las consultas de billing sobre tablas de enrollment). Eso exige un test aparte que escanee `@Query` y `JdbcTemplate`, o queda como deuda declarada.

## Qué no cambia

Rutas, contratos HTTP, base de datos, el patrón `XxxUseCase` + `XxxService`, las reglas de `policy`, `operation` y billing v2, `@Transactional` en casos de uso donde ya está (ver decisión D4).

## Migración (cada paso es un PR, ninguno cambia comportamiento)

0. Este ADR, corrección de `modules.md` y reglas R2 a R7 congeladas en modo detección. Sin cambios de producción.
1. **Piloto attendance ↔ academicstructure.** Los 6 puertos `*Queries` de #397 no tienen ningún consumidor mergeado, así que colapsarlos a especialistas concretos de `academicstructure.api` es barato. Se migra attendance y se **mide**: líneas de la línea base que bajan y capas por consulta.
2. Resto de consumidores de academicstructure (announcement, evaluation, enrollment) con el mismo patrón.
3. Quitar las interfaces sin segundo implementador de `enrollment.api`, `identity.api`.
4. billing y notification al final (motor pausado); sus puertos invertidos se quedan.

## Decisiones de Gabriel (30-oct-2026)

- **D1. Aprobada.** Los especialistas expuestos son clases concretas en `<modulo>.api`, sin interfaz.
- **D2. Aprobada.** `@Transactional` permitido en `..api..` (se agrega ese paquete a `TRANSACTION_BOUNDARY_PACKAGES`).
- **D3. Aprobada.** Un caso de uso nunca llama a otro caso de uso; se corrige `modules.md`.
- **D4. Aprobada, distinta de lo recomendado.** `@Transactional` sale de los casos de uso y vive en el especialista (el compositor más externo). Regla nueva con ratchet: los casos de uso nuevos no pueden declararlo y los ~100 existentes (de 235) quedan congelados y se migran con el tiempo. Consecuencia a vigilar: un caso de uso que orquesta varias escrituras necesita un especialista compositor que abra la transacción; si no, pierde atomicidad.
- **D5. Aprobada, distinta de lo recomendado.** Sin piloto: se aplica a todos los consumidores. Se hace igual por pasos y en PRs separados (el riesgo principal del ADR de ms-users es hacerlo de una). El orden obligatorio es: primero el paso 0 (ADR, `modules.md` y reglas congeladas), después los consumidores en paralelo.

## Nota de implementación de D4

El cambio de la regla de `@Transactional` altera su descripción, así que ArchUnit re-indexa su almacén congelado: queda con ~100 líneas nuevas que son la deuda declarada, no violaciones nuevas escondidas. Es la única excepción autorizada a "los almacenes solo pierden líneas" en este ADR.
