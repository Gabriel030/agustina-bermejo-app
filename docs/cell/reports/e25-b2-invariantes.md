# E25 B2 - S1: relevamiento de invariantes que solo protege el código

Medido sobre `origin/develop` @ `ab0456ad1` (máximo `V433`; `origin/master` llega a `V278`) el 23-nov-2026. Solo documentación: no hay cambios de código ni migraciones. El objetivo es decidir qué constraints sumar como segunda red de integridad antes de agregar caché.

## Método

1. Se leyeron las migraciones de `backend/src/main/resources/db/migration` y las entidades y servicios de inscripciones, facturación, autorizaciones, certificados, configuración y precios.
2. Para saber qué constraints existen de verdad (y no qué dicen las migraciones sueltas, que se renombran y reemplazan: p. ej. `V91` renombró `invoice` a `legacy_invoice`), se aplicaron las 291 migraciones en orden sobre un Postgres 16 vacío y se consultó `pg_indexes` y `pg_constraint`.
3. Las queries de verificación se corrieron contra esa base. Resultado: 0 filas con la base vacía; con un dataset sintético (5.000 inscripciones limpias más violaciones plantadas, dentro de una transacción con rollback) cada query detecta exactamente lo plantado. Ver "Datos y límites".

## Resumen

| # | Candidata | Estado | Prioridad | Violaciones en seed |
|---|---|---|---|---|
| R1 | `program_enrollment` única activa por (student_id, program_id) | CANDIDATA RECOMENDADA | media | 0 (base vacía) |
| R2 | CHECK `WITHDRAWN` implica `withdrawal_effective_date` (program y course) | CANDIDATA RECOMENDADA | media | 0 (base vacía) |
| R3 | CHECK `COMPLETED` implica `completion_date` (program) | CANDIDATA RECOMENDADA, condicionada a datos | baja | 0 (base vacía) |
| R4 | `regular_certificate_request` única `PENDING_STAFF` por inscripción | CANDIDATA RECOMENDADA | baja | 0 (base vacía) |
| R5 | FK compuesta que ata `student_id` de la inscripción a materia/comisión con el de su inscripción padre | CANDIDATA RECOMENDADA | baja | 0 (base vacía) |
| P1 | `course_enrollment` única activa por (student_id, course_id) | YA PROTEGIDA (V272) | - | - |
| P2 | Inscripción a comisión única | YA PROTEGIDA (V1) | - | - |
| P3 | Una factura por (inscripción, período, concepto) | YA PROTEGIDA (V91) | - | - |
| P4 | Una solicitud de inscripción `PENDING` por alumno y carrera | YA PROTEGIDA (V22) | - | - |
| P5 | Plan de pago abierto, adelanto anual abierto, comprobante pendiente | YA PROTEGIDAS (V43, V153, V130) | - | - |
| P6 | Versión de precio, borrador de plantilla y constancia vigente | YA PROTEGIDAS (V105, V191, V273) | - | - |
| D1 | Una autorización pendiente por factura | DESCARTADA | - | - |
| D2 | Una versión de configuración vigente por tema | DESCARTADA | - | - |
| D3 | Opción de plan de pago duplicada | DESCARTADA | - | - |
| D4 | Firmante de certificado duplicado | DESCARTADA | - | - |
| D5 | `(enrollment_id, section_id)` único en comisión | DESCARTADA (redundante con P2) | - | - |
| D6 | Cupo de comisión y coherencia plantilla-carrera | DESCARTADA | - | - |

"Violaciones en seed" es 0 en todas porque la base del relevamiento está vacía: no representa producción. Con el dataset sintético las queries detectan lo plantado (ver "Datos y límites"). Las cifras reales hay que sacarlas corriendo los SELECT de este informe sobre una réplica de producción antes de adoptar cada migración.

## YA PROTEGIDAS (no se toca)

Se confirmaron sobre la base migrada.

| Invariante | Constraint | Migración |
|---|---|---|
| Inscripción a curso activa única por alumno y curso (`ENROLLED`, `IN_PROGRESS`, `WAITLISTED`) | `uk_course_enrollment_student_course_active` en `course_enrollment (student_id, course_id)` parcial | V272 |
| Un alumno una vez por comisión (materia y curso). Cubre el candidato (d) | `uk_prog_sec_enr_student_section` en `subject_section_enrollment (student_id, program_section_id)` y `uk_course_sec_enr_student_section` en `course_section_enrollment (student_id, course_section_id)`; son únicos totales, sin parcial por estado | V1 |
| Una factura por inscripción, período y concepto | `uk_invoice_line_charge_key` en `invoice_line (charge_key)` donde `charge_key_released_at is null`; `uk_invoice_number` en `invoice`. Tras `V91` el índice viejo `uk_invoice_billable_period` quedó en `legacy_invoice` | V91 |
| Solicitud de inscripción pendiente única por alumno y carrera | `uk_program_enrollment_application_pending` en `(student_id, program_id)` donde `status = 'PENDING'`. Pese a que `ProgramApplicationRegistrar:56-58` también chequea por código | V22 |
| Plan de pago abierto único | `uk_payment_plan_selection_open` (con `coalesce(...,0)` sobre ambas inscripciones) donde `effective_to is null` | V43 |
| Adelanto anual abierto, comprobante bancario pendiente | `uk_annual_upfront_request_open`, `uk_bank_transfer_receipt_pending` | V153, V130 |
| Versión de precio propuesta, programada o vigente | `uk_price_version_open_proposal`, `uk_price_version_effective_date` (V105), `uk_price_version_lective_year` (V199) | V105, V199 |
| Borrador único de plantilla y número de versión | `uq_certificate_template_single_draft`, `uq_certificate_template_version_number` | V273 |
| Constancia regular vigente única por inscripción | `uq_regular_cert_program_valid`, `uq_regular_cert_course_valid` | V191 |
| Solicitud de graduación activa, excepción de constancia abierta, recargo por período | `uk_graduation_request_active`, `uq_certificate_exception_open_per_enrollment`, `uk_late_fee_charge_origin_period_start` más exclusión `ex_late_fee_charge_no_overlapping_days` (gist) | V21, V271 , V110 y V145 |
| Inscripción de admisión única por solicitud | `uk_program_enrollment_admission_application` | V98 |

La extensión `btree_gist` ya está en uso (`ex_late_fee_charge_no_overlapping_days`), útil para la subtarea de exclusión de aulas.

## CANDIDATAS RECOMENDADAS

### R1 - `program_enrollment` activa única por alumno y carrera

- Tabla e índice: `create unique index uk_program_enrollment_student_program_active on program_enrollment (student_id, program_id) where status in ('ENROLLED', 'PAUSED');`
- Existe equivalente: no. El gemelo de cursos sí existe (V272). `program_enrollment` solo tiene `uk_program_enrollment_admission_application`.
- Evidencia en código:
  - Validación: `ProgramEnrollmentService.createEnrollment` líneas 135-139 (`findByStudentIdAndProgramIdAndStatusIn` con `ENROLLED` y `PAUSED`, lanza `AlreadyEnrolledInProgramException`) y `validateStudentCanEnrollInProgram` líneas 255-259.
  - Lock actual: `studentFinder.requireByIdForUpdate(studentId)` en la línea 118, antes del chequeo; serializa las altas por alumno y cubre `enroll`, `enrollAfterAdmissionPayment` y `enrollApplicantAfterAdmissionPayment`.
  - Otro check-then-act: `ProgramEnrollmentAdmissionPaymentListener.enrollOnce` líneas 47-52 (sin lock propio, pero delega en `createEnrollment`, que revalida con lock).
  - Ruta sin esa red: `HistoricalEnrollmentRecorder` (alta histórica, línea 157 `setStatus(historical.status())`) no pasa por `createEnrollment`; un histórico `ENROLLED` o `PAUSED` duplicado no se frena.
- Carrera real demostrable: no con las rutas actuales (el lock del alumno la cierra). Es red de defensa para la ruta histórica, SQL manual y futuros caminos. Prioridad media.
- Riesgo de datos existentes: bajo si no hubo cargas históricas duplicadas. Query:

```sql
select student_id, program_id, count(*) as active_rows, array_agg(id order by id) as ids
from program_enrollment
where status in ('ENROLLED', 'PAUSED')
group by student_id, program_id
having count(*) > 1
order by student_id, program_id;
```

- Costo: tabla chica (una fila por alumno y carrera, miles). El `create unique index` común toma SHARE y bloquea escrituras unos milisegundos; no hace falta `CONCURRENTLY` (además Flyway lo corre en transacción). Mismo criterio que V272.
- Efecto en código: las rutas de alta deben traducir la violación del índice (`DataIntegrityViolationException`) a `AlreadyEnrolledInProgramException`, igual que se hizo con cursos. Subtarea aparte.

### R2 - `WITHDRAWN` exige `withdrawal_effective_date`

- Predicados (dos CHECK, uno por tabla):
  - `alter table program_enrollment add constraint ck_program_enrollment_withdrawn_has_effective_date check (status <> 'WITHDRAWN' or withdrawal_effective_date is not null) not valid;`
  - `alter table course_enrollment add constraint ck_course_enrollment_withdrawn_has_effective_date check (status <> 'WITHDRAWN' or withdrawal_effective_date is not null) not valid;`
- Existe equivalente: solo el sentido inverso (V115: `ck_*_withdrawal_date_only_when_withdrawn`, fecha no nula implica `WITHDRAWN`). Falta el directo.
- Evidencia en código: `ProgramEnrollment.markWithdrawn` (línea 109) y `CourseEnrollment.markWithdrawn` (línea 71) asignan `status` y `withdrawalEffectiveDate` juntos, con la fecha que resuelve `EnrollmentWithdrawalDateResolver`. Excepción: `HistoricalEnrollmentRecorder` líneas 163-166 usa `withdrawalDate()` y cae a `completionDate()`; si ambos vienen nulos queda `WITHDRAWN` sin fecha, y todo lector (`ProgramEnrollmentFinder:44`, `CourseEnrollmentFinder:41`, resumen post-baja) asume que existe.
- Carrera: no aplica (no es concurrencia, es integridad de estado). Prioridad media: protege la facturación de baja (recargos, resumen post-baja).
- Riesgo de datos: el backfill de V115 solo completó filas con `completion_date` no nulo. Filas `WITHDRAWN` anteriores o históricas con ambos nulos violarían.

```sql
select 'program_enrollment' as tbl, count(*) as violating_rows, min(id) as first_id
from program_enrollment where status = 'WITHDRAWN' and withdrawal_effective_date is null
union all
select 'course_enrollment', count(*), min(id)
from course_enrollment where status = 'WITHDRAWN' and withdrawal_effective_date is null;
```

- Si hay violaciones: la corrección (por ejemplo `coalesce(completion_date, enrollment_date)::date`) afecta recargos y resúmenes; es decisión de producto, va al líder como `NECESITO DECISIÓN` con los ids.
- Costo: `ADD ... NOT VALID` es instantáneo (ACCESS EXCLUSIVE hasta el commit, por eso con app parada, ver `docs/context/backend.md`, "CHECK en dos migraciones"); `VALIDATE` recorre la tabla sin bloquear escrituras. Tablas chicas.

### R3 - `COMPLETED` exige `completion_date` (carrera)

- Predicado: `check (status <> 'COMPLETED' or completion_date is not null) not valid` en `program_enrollment` (`ck_program_enrollment_completed_has_completion_date`).
- Existe equivalente: no.
- Evidencia: `ProgramEnrollment.complete` (líneas 84-89) asigna `completionDate`; la excepción es `HistoricalEnrollmentRecorder` líneas 160-162, que solo la asigna si viene informada.
- Prioridad baja: no hay consumidor que rompa. Se recomienda solo si la query da 0 en producción; si hay filas históricas, descartar o completar a mano.

```sql
select count(*) as violating_rows, min(id) as first_id
from program_enrollment where status = 'COMPLETED' and completion_date is null;
```

### R4 - una solicitud de constancia `PENDING_STAFF` por inscripción

- Índice: `create unique index uq_regular_cert_request_pending_per_enrollment on regular_certificate_request (kind, (coalesce(program_enrollment_id, course_enrollment_id))) where outcome = 'PENDING_STAFF';`
- Existe equivalente: no. Hay `uq_regular_cert_program_valid` sobre la constancia emitida, pero la solicitud pendiente no tiene.
- Evidencia: `RegularCertificateIssuer.pendingRequestOf` línea 150 y `findPendingRequest` línea 156 (`findFirst...OutcomeOrderByRequestedAtDesc`, uso en 128 y 218). Es un check-then-act sin lock de inscripción visible.
- Carrera real: plausible (doble clic del alumno o dos pestañas), con efecto benigno (dos filas pendientes; `findFirst` toma la más nueva y la otra queda huérfana). Prioridad baja.
- La expresión `coalesce(...)` es la misma que `uq_certificate_exception_open_per_enrollment` (V271).

```sql
select kind, coalesce(program_enrollment_id, course_enrollment_id) as enrollment_id,
       count(*) as pending_rows, array_agg(id order by id) as ids
from regular_certificate_request
where outcome = 'PENDING_STAFF'
group by kind, coalesce(program_enrollment_id, course_enrollment_id)
having count(*) > 1
order by 1, 2;
```

- Si hay duplicados, se resuelven a `REJECTED` o se dejan solo la más nueva antes del índice (decisión de producto). Efecto en código: capturar la violación y devolver la pendiente existente.

### R5 - coherencia del alumno entre inscripción a comisión e inscripción padre

- Regla: `subject_section_enrollment.student_id` debe ser el `student_id` de su `program_enrollment`; `course_section_enrollment.student_id`, el de su `course_enrollment`. Hoy ambas columnas son `NOT NULL` y se llenan por código.
- Constraint: agregar `unique (id, student_id)` en `program_enrollment` y `course_enrollment` y una FK compuesta `(program_enrollment_id, student_id) references program_enrollment (id, student_id)` (y su gemela de cursos), `NOT VALID` y luego `VALIDATE`. Es el mismo patrón que ya usa billing (`uk_payment_id_account`, `uk_invoice_id_account`).
- Existe equivalente: no. Las dos columnas de alumno son independientes.
- Evidencia: `HistoricalEnrollmentRecorder` líneas 200-203 arma `SubjectSectionEnrollment` con el alumno que le pasan y la inscripción por separado; `uk_prog_sec_enr_student_section` se apoya en ese `student_id`, así que un valor equivocado rompe la unicidad real de P2.
- Prioridad baja: no hay incidente conocido; es defensa de datos.

```sql
select 'subject_section_enrollment' as tbl, count(*) as violating_rows, min(sse.id) as first_id
from subject_section_enrollment sse join program_enrollment pe on pe.id = sse.program_enrollment_id
where sse.student_id <> pe.student_id
union all
select 'course_section_enrollment', count(*), min(cse.id)
from course_section_enrollment cse join course_enrollment ce on ce.id = cse.course_enrollment_id
where cse.student_id <> ce.student_id;
```

- Costo: es la más cara del conjunto. `VALIDATE` de una FK recorre `subject_section_enrollment`, la tabla más grande de las académicas (el seed de perf carga cientos de miles de filas); no bloquea escrituras (SHARE UPDATE EXCLUSIVE sobre ambas tablas), pero conviene en ventana tranquila. Los `unique (id, student_id)` agregan un índice por tabla.

## DESCARTADAS

| Id | Candidata | Motivo |
|---|---|---|
| D1 | Una autorización pendiente por factura | La regla vive en dos tablas: `authorization_request.status` y el vínculo `authorization_request_invoice (authorization_request_id, invoice_id)`. Un índice parcial no puede mirar el estado de otra tabla; haría falta desnormalizar `status` en el vínculo. La consulta de lectura es `AuthorizationRequestRepository.findPendingRequestLinksOfInvoices` (línea 30). Costo y riesgo mayores que el beneficio. |
| D2 | Una versión de configuración `EFFECTIVE` por tema, o `(scope, topic_key, effective_from)` único | El diseño admite empates de `effective_from` y los resuelve por `decided_at` e `id` (`ConfigurationVersionActivator.RESOLUTION_ORDER`, líneas 20-23; queries `order by effective_from desc, decided_at desc, id desc`). Ya existe `uk_configuration_version_open_topic` (una `PROPOSED` por tema). Distinto de precios, donde sí hay unicidad por fecha (P6). |
| D3 | Opción de plan de pago única por curso o carrera y cadencia | No hay regla de negocio ni validación en código (`CoursePaymentPlanOptionCreation:32` y `ProgramPaymentPlanOptionCreation:32` solo hacen `save`); pueden existir varias opciones con distinta descripción o descuento. Sería inventar una regla de producto. |
| D4 | Firmante de certificado único por nombre y cargo | Sin regla ni validación en código; `certificate_signatory` solo tiene el CHECK de texto no vacío. Pedir unicidad cambia producto. |
| D5 | Único `(enrollment_id, section_id)` en comisión | Redundante: P2 ya garantiza un alumno una vez por comisión, y la inscripción padre es única por alumno (R1 y V272). |
| D6 | Cupo de comisión; plantilla de facturación de la inscripción pertenece a la carrera | El cupo depende de contar filas (no es un CHECK ni un único); la plantilla cruza `program_enrollment`, `program_year_billing_template` y `program`. Se resuelven con lock o con FK compuesta que obligaría a denormalizar; fuera del alcance de esta red. |

No se analizó (fuera de este relevamiento): el solapamiento de aulas (otra subtarea) y los ciclos `academic_offering_cycle`, cuyo único parcial (`uk_cycle_*_term_active`) cubre solo `DRAFT` y `PENDING_APPROVAL`.

## Datos y límites de las queries

- Base: Postgres 16 (contenedor propio), 291 migraciones aplicadas en orden con `psql -1`. No se levantó el stack de perf (`perf/docker-compose.perf.yml` más `seed-large.sql`): ese seed necesita las tablas ya cargadas con datos demo por el backend (programas, términos, materias), es decir arrancar Spring con el perfil `demo`, un costo mayor al valor de este relevamiento.
- Con la base vacía, las 7 queries devuelven 0 filas.
- Con un dataset sintético en una transacción con rollback (5.000 inscripciones `ENROLLED` sin violaciones más violaciones plantadas: 2 pares de inscripciones activas duplicadas, 1 baja de carrera sin fecha, 1 baja de curso sin fecha, 1 carrera `COMPLETED` sin fecha, 2 solicitudes `PENDING_STAFF` de la misma inscripción, 1 inscripción a materia con alumno distinto del padre) cada query reporta exactamente lo plantado. Eso prueba que las queries funcionan, no cuántas filas violan en producción.
- Ni el seed demo ni el de perf reflejan la historia real (cargas históricas, importaciones, datos previos a V115), que es donde viven las violaciones probables de R2, R3 y R5. Hay que correr las queries sobre una réplica de producción antes de armar las migraciones.

## Orden de adopción y migraciones

Numeración: la próxima libre hoy es `V434` (máximo en `origin/develop` `V433`; `origin/master` `V278`). Es solo orientativa: revalidar `git fetch`, `origin/develop` y `gh pr list` al mergear cada una, y usar el número que el líder reserve.

1. Antes de todo: correr las queries de R1, R2, R3, R4 y R5 en producción (réplica) y agregar el bloque correspondiente a `docs/release/pre-migration-checks.sql`. Si alguna da filas, el líder decide la corrección de datos.
2. M1 (la más segura y de mayor valor): `program_enrollment` activa única (R1). Un solo `create unique index`, sin par NOT VALID/VALIDATE porque un índice único no admite `NOT VALID`; falla si hay duplicados, por eso el chequeo previo. Mismo patrón que V272. Subtarea de código en el mismo PR: traducir la violación a `AlreadyEnrolledInProgramException`.
3. M2 y M3 (par NOT VALID/VALIDATE): CHECK de baja con fecha (R2), y R3 si la query dio 0. M2 agrega los dos o tres CHECK con `not valid`; M3 los valida. Archivos separados, como V209/V210 y V211/V212.
4. M4: `uq_regular_cert_request_pending_per_enrollment` (R4), junto con la captura de la violación en `RegularCertificateIssuer`. Independiente de las anteriores.
5. M5 y M6 (opcional, última): R5. M5 agrega `unique (id, student_id)` en `program_enrollment` y `course_enrollment` y las FK compuestas `not valid`; M6 las valida en una ventana tranquila.

Cada migración con CHECK sobre una columna enum persistida sigue la regla de `docs/context/backend.md` (CHECK `<tabla>_<columna>_enum` y alta en `docs/release/pre-migration-checks.sql`); estas no agregan valores de enum, así que no la disparan. Los tests de cada migración deben sabotear la regla (insertar la violación y esperar el rechazo de la base) además de la prueba por código.
