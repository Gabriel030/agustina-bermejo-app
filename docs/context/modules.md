# Módulos (Spring Modulith)

Cada paquete de primer nivel de `com.red_escuela.backend` es un módulo de Spring Modulith 1.4.13. Leer este doc antes de tocar cualquier módulo. Los límites se declaran en el `package-info.java` de cada módulo y se verifican con `ModularityTest`.

## Capas

Un módulo solo puede depender de los de capas inferiores y de `shared` (módulo abierto). De abajo hacia arriba:

`shared` < `identity` < `academicstructure` < `enrollment` < `attendance` < `evaluation` < `billing` < `graduation` < `announcement` < `notification`

`academicrecord` es un módulo de composición que puede usar a todos. `config` (seguridad, filtros, scheduler) no tiene restricción de salida. Las dependencias que hoy van hacia arriba son violaciones congeladas en la línea base y las bajan las fases M3, M4 y M5.

## Qué expone un módulo

| Interfaz | Contenido | Cómo se declara |
|---|---|---|
| `api` | `*UseCase` con sus `Command` y resultados, `Current*Service` de identity y academicstructure | `@NamedInterface("api")` en `usecase/package-info.java` o a nivel de tipo |
| `events` | eventos que publica el módulo | `@NamedInterface("events")` en `<modulo>/events/package-info.java` |
| `model` | entidades que otro módulo referencia por relación JPA | `@NamedInterface("model")` a nivel de tipo en la entidad |

Todo lo demás (`repository`, `service`, `entity` no expuestas, `dto`, `exception`) es interno. En `billing`, `@NamedInterface("api")` queda fuera de `billing.usecase` hasta que V2-02 lo reubique; la API pública de billing es `billing.events`.

Entidades en `model` y motivo (se referencian por relación JPA y cambiarlas por un id exigiría migración): `Person`, `Student` (identity); `Institution`, `Program`, `Course`, `Subject`, `SubjectSection`, `CourseSubjectSection`, `Instructor`, `Term`, `CourseSubject` (academicstructure; además los enums `CurricularTerm`, `InstructorRole` y `RequirementType`, que viven en `academicstructure.dto`); `ProgramEnrollment`, `CourseEnrollment`, `SubjectSectionEnrollment`, `CourseSubjectSectionEnrollment` (enrollment). Regla: si la relación se puede cambiar por un id sin migración y sin perder una consulta necesaria, no se expone.

## Comunicación entre módulos

| Mecanismo | Cuándo | Transacción | Ejemplo |
|---|---|---|---|
| Llamada a la API pública | Necesito respuesta o que pase en el mismo momento | La misma | un controller o use case que llama a un `*UseCase` de un módulo inferior |
| Evento síncrono (`@EventListener`) | El inferior no debe conocer al superior y el efecto es atómico | La misma | notificaciones in-app de `notification` ante eventos de negocio |
| Evento async con registro | Efectos secundarios y canales externos | Nueva, tras el commit | emails (se introduce en V2-00c1) |

Todo lo que mueve plata o el estado de una inscripción es síncrono. Los eventos llevan ids y valores, nunca entidades JPA.

## Agregar una consulta pública

1. Definir el método en un `*UseCase` (o `*Queries`) del paquete `usecase` del módulo proveedor, devolviendo records de lectura y no entidades.
2. El paquete `usecase` ya es `api`; si el tipo vive en otro paquete, anotarlo con `@NamedInterface("api")` a nivel de tipo.
3. En el consumidor, depender solo de ese tipo. Verificar con `ModularityTest`.
4. Los puertos de `academicstructure` van en `usecase` con sufijo `Queries`, solo devuelven records de `dto` (con `@NamedInterface("api")` a nivel de tipo) y no nombran tipos de `entity` ni de `exception` en su firma (`ArchitectureRules.java:230-239`). identity, enrollment y billing difieren: siguen en `api` con `*Lookup`/`*Query`. Para asignar una relación JPA, el consumidor valida con `requireExists` y usa `entityManager.getReference(...)` de un tipo `model`.

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

1. enrollment declara en su interfaz `api` (paquete `enrollment.usecase`) un puerto con nombre de dominio, por ejemplo `EnrollmentPaymentEligibilityPort` con `isEligibleToEnroll(studentId)`.
2. billing lo implementa (`billing -> enrollment :: api`, dirección permitida) y Spring lo inyecta donde enrollment lo use.
3. enrollment solo conoce el puerto, nunca un tipo de billing.

El puerto todavía no existe: se crea en V2-09 junto con el caso de uso que lo necesita. Se verificó que `ModularityTest` no prohíbe el patrón (una interfaz en `enrollment.usecase` implementada por un componente de billing no agrega violaciones).

Deuda a invertir en V2-09 / M3b: hoy hay accesos enrollment -> billing (72 violaciones en `COUNT billing 72` de `modulith-baseline/enrollment.txt`, en 22 clases): el gate de matrícula (`EnrollmentFeeRequirementService`, `GetEnrollmentFeeStatusService`, `GrantEnrollmentFeeExemptionService`), `EnrollmentBillingStanding`, `EnrollmentDebtGuard` (puerto de deuda, ya por `billing.api`), la alta de cohortes y plantillas (`ProgramCohort`, `ProgramYearBillingTemplate`), la emisión de cargos (`ChargeIssuer`) y los comprobantes (`PaymentReceipt`, `UploadPaymentReceiptUseCase`). Cada reemplazo por un puerto o un evento debe bajar ese conteo.

## Documentación generada

`ModuleDocumentationTest` escribe por defecto en `backend/target/modulith-docs/` (no ensucia el árbol). Para regenerar lo versionado en `docs/architecture/` (`components.puml`, `module-*.puml`, `module-*.adoc`) correr `./mvnw -f backend/pom.xml test -Dtest=ModuleDocumentationTest -DargLine=-Dmodulith.docs.update=true`. La salida es determinista (aristas ordenadas): si no cambió la arquitectura, no hay diff.
