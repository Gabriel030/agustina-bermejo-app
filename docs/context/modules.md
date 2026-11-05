# Módulos (Spring Modulith)

> Verificado: 2026-10-31, commit `cd1ede80`.

Cada paquete de primer nivel de `com.red_escuela.backend` es un módulo de Spring Modulith 1.4.13. Leer este doc antes de tocar cualquier módulo. Decisión de fondo: [ADR 0002](../adr/0002-patron-de-modulos.md). Los límites se declaran en el `package-info.java` de cada módulo y se verifican con `ModularityTest`.

## Capas

Un módulo solo puede depender de los de capas inferiores y de `shared` (módulo abierto). De abajo hacia arriba:

`shared` < `identity` < `academicstructure` < `enrollment` < `attendance` < `evaluation` < `billing` < `graduation` < `announcement` < `notification`

`academicrecord` es un módulo de composición que puede usar a todos. `config` (seguridad, filtros, scheduler) no tiene restricción de salida. Las dependencias que hoy van hacia arriba son violaciones congeladas en la línea base y las bajan las fases M3, M4 y M5.

## Qué expone un módulo

Decisión vigente: [ADR 0002](../adr/0002-patron-de-modulos.md) (aceptado 30-oct-2026). Un módulo expone tres paquetes y nada más:

| Interfaz | Contenido | Cómo se declara |
|---|---|---|
| `api` | **especialistas concretos**, sin interfaz, y los records de lectura que devuelven (más `Current*Service` de identity y academicstructure); única excepción de interfaz: los `*Port` (ver abajo) | `@NamedInterface("api")` en `<modulo>/api/package-info.java` o a nivel de tipo |
| `events` | eventos con ids y valores planos, nunca entidades | `@NamedInterface("events")` en `<modulo>/events/package-info.java` |
| `model` | entidades que otro módulo referencia por relación JPA | `@NamedInterface("model")` a nivel de tipo en la entidad |

Todo lo demás (`usecase`, `service`, `repository`, `entity` no expuestas, `dto`, `policy`, `exception`) es interno. **Los casos de uso no se exponen**: desde afuera solo se llega a los especialistas de `api`. Los métodos públicos de `api` solo reciben y devuelven tipos de `java.*`, records de la propia `api`, tipos `model` y eventos; nunca una entidad no expuesta. En `billing`, `@NamedInterface("api")` queda fuera de `billing.usecase` hasta que V2-02 lo reubique; la API pública de billing es `billing.events`. Los consumidores existentes que todavía llaman a un `*UseCase` o `*Queries` de otro módulo se migran por pasos (ver la sección de migración del ADR).

### Figuras y cadena de llamadas

- **Coordinador** (`*Controller`, `@Scheduled`, listeners, receivers): llama solo a casos de uso de su módulo.
- **Caso de uso** (`XxxUseCase` + `XxxService`): llama solo a especialistas de su módulo. **Nunca a otro caso de uso.**
- **Especialista** (`service/` y los expuestos en `api/`): usa herramientas propias y la `api` de módulos inferiores. No depende de `usecase`.
- **Herramienta** (`repository`, mappers, clientes): nadie más la toca.

La cadena es coordinador -> caso de uso -> especialista -> herramienta, sin saltear escalones ni ir al revés.

### `@Transactional`

Vive en el especialista (incluido el de `..api..`), que es el compositor más externo de las escrituras, y no en el caso de uso (D4 del ADR). La regla tiene ratchet: los casos de uso nuevos no pueden declararlo y los existentes (~100 de 235) quedan congelados y se migran con el tiempo. Cuidado: un caso de uso que orquesta varias escrituras necesita un especialista compositor que abra la transacción, o pierde atomicidad.

Entidades en `model` y motivo (se referencian por relación JPA y cambiarlas por un id exigiría migración): `Person`, `Student` (identity); `Institution`, `Program`, `Course`, `Subject`, `SubjectSection`, `CourseSubjectSection`, `Instructor`, `Term`, `CourseSubject` (academicstructure; además los enums `CurricularTerm`, `InstructorRole` y `RequirementType`, que viven en `academicstructure.domain`, igual que `SectionKind`); `ProgramEnrollment`, `CourseEnrollment`, `SubjectSectionEnrollment`, `CourseSubjectSectionEnrollment` (enrollment). Regla: si la relación se puede cambiar por un id sin migración y sin perder una consulta necesaria, no se expone.

## Comunicación entre módulos

| Mecanismo | Cuándo | Transacción | Ejemplo |
|---|---|---|---|
| Llamada directa a la `api` del inferior | Necesito respuesta o que pase en el mismo momento, hacia abajo en las capas | La misma | el especialista `AttendanceSectionAccess` (attendance) llama a `SectionTeachingFinder` (`academicstructure.api`) |
| Puerto invertido (`*Port`) | El inferior necesita algo del superior, o de billing/notification | La misma | `OutstandingDebtPort` (D-185, ver abajo) |
| Evento síncrono (`@EventListener`) | El inferior no debe conocer al superior y el efecto es atómico | La misma | notificaciones in-app de `notification` ante eventos de negocio |
| Evento async con registro | Efectos secundarios y canales externos | Nueva, tras el commit | emails (se introduce en V2-00c1) |

Hacia arriba y hacia billing y notification se usa evento o puerto invertido; nunca una llamada directa. Un caso de uso nunca llama a un caso de uso de otro módulo (ni del propio). Todo lo que mueve plata o el estado de una inscripción es síncrono. Los eventos llevan ids y valores, nunca entidades JPA.

## Agregar una consulta pública

Ejemplo: `attendance` necesita saber si un docente da una comisión.

1. En el módulo proveedor, crear un especialista **concreto** en `academicstructure.api` (`SectionTeachingFinder`), sin interfaz, que usa su repositorio y devuelve records de lectura de la propia `api`, nunca entidades no expuestas.
2. Anotar el paquete `api` con `@NamedInterface("api")` si todavía no lo está.
3. En el consumidor, un especialista propio (`attendance.service.AttendanceSectionAccess`) inyecta `SectionTeachingFinder` y lo llama directo.
4. El caso de uso (`LoadAttendanceUseCase`) llama a `AttendanceSectionAccess`, y `AttendanceController` llama al caso de uso. Cadena: controller -> caso de uso -> especialista propio -> especialista de `api` -> repositorio. Cero interfaces, cero adaptadores. Verificar con `ModularityTest`.

Si la consulta va hacia arriba o hacia billing/notification, no se expone un especialista: se publica un evento o se declara un `*Port` (única interfaz admitida en `api`) que implementa el módulo superior.

### Reglas de ArchUnit que lo hacen cumplir

Cada una entra con `FreezingArchRule` y su test negativo de fixture (como `transactionRuleDetectsATransactionalControllerAndAllowsAService`); entran **congeladas** con las violaciones actuales y se descongelan cuando su cuenta llega a cero. Una violación nueva no se congela: se arregla.

- **R2**: ningún caso de uso depende de otro `*UseCase`.
- **R3**: un especialista (`service`) no depende de `usecase`.
- **R4**: un controller depende solo de casos de uso de su módulo (y DTOs); no de `repository` ni de `service`.
- **R5**: una clase de `api` no depende de `usecase` ni de controllers.
- **R6**: los métodos públicos de `api` no mencionan tipos de `entity` salvo los `model`.
- **R7**: una interfaz en `api` se llama `*Port` y la implementa una clase de otro módulo.
- **R10** (`@Transactional`, D4): los casos de uso nuevos no pueden declararlo; `..api..` se agrega a `TRANSACTION_BOUNDARY_PACKAGES`. Los ~100 existentes quedan congelados (el almacén se re-indexa con ~100 líneas nuevas que son deuda declarada, no violaciones escondidas).

Ya existentes y sin cambios: R1 (un caso de uso depende solo de su subdominio), R8 (línea base de `ModularityTest`) y R9 (`subdomainsDoNotUseOtherSubdomainRepositories`). Límite: ArchUnit no ve SQL/JPQL nativo que lea tablas de otro módulo.

## Línea base y `ModularityTest`

`ModularityTest` corre `ApplicationModules.detectViolations()` y compara contra `backend/src/test/resources/modulith-baseline/`:

- `<modulo>.txt` por módulo de origen: un mensaje normalizado por línea (sin números de línea).
- Si el módulo de origen o el de destino es `billing` o `enrollment` (los toca el Motor de facturación v2), el archivo guarda líneas `COUNT <destino> <n>` y el test compara cantidad por módulo destino, no texto, hasta que mergee V2-02.
- `cycles.txt`: un par `a<->b` por línea; dos módulos forman par si son mutuamente alcanzables en el grafo de dependencias directas.

Leer un fallo:
- `<origen>: <origen> -> <destino> went from X to Y`: agregaste accesos a tipos no públicos de `<destino>`. Usar su API pública en lugar de agregar líneas a la línea base.
- `<origen>: Module '...' depends on non-exposed type T within module '...'`: `T` no es público; usar un use case, un evento o exponerlo vía API.
- `cycles: new dependency cycle between a<->b`: apareció una dependencia circular nueva.
- Entradas sobrantes: el test pasa y las imprime por stderr; el PR que elimina violaciones tiene que borrarlas o bajarlas regenerando con `./mvnw -f backend/pom.xml test -Dtest=ModularityTest -DargLine=-Dmodulith.baseline.update=true`.

## Inversión de dependencia: enrollment consulta a billing (D-185)

El estado de cobranza de la inscripción vive en billing, pero billing está por encima de enrollment en las capas y ya depende de él (billing -> enrollment). Si enrollment llamara a billing se formaría un ciclo. Se resuelve con un puerto:

1. enrollment declara en su `api` un puerto (sufijo `Port`, la única interfaz admitida en `api`) con nombre de dominio, por ejemplo `EnrollmentPaymentEligibilityPort` con `isEligibleToEnroll(studentId)`.
2. billing lo implementa (`billing -> enrollment :: api`, dirección permitida) y Spring lo inyecta donde enrollment lo use.
3. enrollment solo conoce el puerto, nunca un tipo de billing.

El puerto todavía no existe: se crea en V2-09 junto con el caso de uso que lo necesita. Se verificó que `ModularityTest` no prohíbe el patrón (un puerto en `enrollment.api` implementado por un componente de billing no agrega violaciones).

Deuda a invertir en V2-09 / M3b: hoy hay accesos enrollment -> billing registrados en la línea base (el conteo vigente está en las líneas `COUNT` de `backend/src/test/resources/modulith-baseline/enrollment.txt`; no se copia acá porque cambia con cada PR): el gate de matrícula (`EnrollmentFeeRequirementService`, `GetEnrollmentFeeStatusService`, `GrantEnrollmentFeeExemptionService`), `EnrollmentBillingStanding`, `EnrollmentDebtGuard` (puerto de deuda, ya por `billing.api`), la alta de cohortes y plantillas (`ProgramCohort`, `ProgramYearBillingTemplate`), la emisión de cargos (`ChargeIssuer`) y los comprobantes (`PaymentReceipt`, `UploadPaymentReceiptUseCase`). Cada reemplazo por un puerto o un evento debe bajar ese conteo.

## Documentación generada

`ModuleDocumentationTest` escribe por defecto en `backend/target/modulith-docs/` (no ensucia el árbol). Para regenerar lo versionado en `docs/architecture/` (`components.puml`, `module-*.puml`, `module-*.adoc`) correr `./mvnw -f backend/pom.xml test -Dtest=ModuleDocumentationTest -DargLine=-Dmodulith.docs.update=true`. La salida es determinista (aristas ordenadas): si no cambió la arquitectura, no hay diff.
