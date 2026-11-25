# Plan: #1103 — piloto por capas: M1-b, T1d y D1c

Se escribe UNA vez al planificar; el estado vive en GitHub (issue, PRs con `cell:1103`, Project "Célula"), no acá.
Líder: líder-seguridad-2 · Fecha: 2026-11-25 · Issue: #1103 · Spec: comentario de plan del 21-nov en #1103 y handoff de líder-5 (23-nov). Complementa `docs/cell/plans/1103.md` (plan de la épica, que vive en el comentario del Issue).

## Objetivo
Después de esto, el alumno puede pedir y bajar su constancia de materias aprobadas con código verificable, todos los documentos usan la plantilla PUBLICADA que edita el Director (con todos sus marcadores) y la excepción por deuda emite R1, M1, N1 y X1. El piloto mide el armado por capas con hasta 7 workers.

## Tipo de ticket
A (solo backend): diseño y front ya mergeados (F-V, F-X, F-P, F-T); no hay pantalla nueva ni contrato nuevo salvo un campo nullable (P0).

## Preguntas al usuario (agrupadas)
| # | Pregunta | Opciones | Recomendación | Bloquea |
|---|---|---|---|---|
| Q1 | ¿Cómo se fija la plantilla usada por cada documento emitido, para regenerar el mismo PDF y el mismo hash? | (A) columna `template_version_id` nullable en las 4 tablas (como `graduation_certificate`, V274); NULL = diseño BUILT_IN histórico. (B) congelar textos y firmantes dentro del snapshot JSON, sin migración | **A**: coherente con egreso, el motor ya documenta esa convención y permite auditar qué versión se usó. Es la única migración del piloto | P1, P10, P11 |
| Q2 | Nota no representable de una materia aprobada (`Grade.of` lanza `InvalidGradeException` con notas legacy, ver `docs/cell/progress/1103-M1a.md`) | (A) rechazar la emisión con error controlado y registrar qué materia; (B) mostrar «—» en esa fila y promediar el resto; (C) excluir la materia | **A**: es un documento con valor legal; un promedio que omite filas en silencio no es defendible | P2, P6 |
| Q3 | Constancias anteriores del mismo alumno al pedir una nueva | (A) cada pedido emite una nueva, la anterior sigue VALID hasta vencer (límite diario del tipo = 1 la acota); (B) agregar estado REPLACED (migración extra de CHECK) | **A**: sin migración; la lista de materias cambia poco | P6 |
| Q4 | Retiro del endpoint legacy `GET /me/program-enrollments/{id}/certificates/approved-subjects` y de `ProgramCertificateIssuer.issueApprovedSubjectsCertificate` | (A) conservarlos en el piloto y retirarlos en un PR aparte tras verificar que el front no los usa; (B) retirarlos en P8 (ruptura de contrato) | **A** | nada del piloto; queda como seguimiento |

## Definition of Ready
- [x] Hueco confirmado contra `origin/develop` (25-nov): `RequestApprovedSubjectsCertificateService` y `RevokeApprovedSubjectsCertificateService` lanzan `DocumentOperationPendingException`; `GenerateOwnDocumentService` l.23-33 lanza Pending para el tipo; `DebtExceptionDocumentIssuers` sin adaptadores en main (solo `FakeIssuer` de test); `CertificateMarkers.valueOf` no expande recipient, average, completionPercent, subjectName, examDate; solo egreso resuelve la versión publicada (`GraduationCertificateIssuer` l.93-98).
- [x] No toca `billing/**`: usa `platform.api.BillingModeGate`, `OverdueDebtProbe`, `PaymentStandingResolver` ya existentes. Contrastar con `docs/billing/decision-registry.md` solo si Q2/Q3 cambian reglas de deuda.
- [x] Sin PRs abiertos que lo dupliquen: líder-egresos-2 cede T1d, M1-b y D1c (decisión de Gabriel, 25-nov); sigue con 22.10 y no toca `graduation/certificate/**` mientras dure el piloto.
- [ ] Verificar al arrancar cada subtarea: que no exista ya un puerto con nota y fecha de aprobación por materia (ver P4).

## Diseño
No aplica (tipo A). Backend que arranca ya: todo.

## Contratos
- PR de contrato: P0, aditivo; se mergea primero. El diff de `contracts/openapi.json` (campo `studentName` pasa a nullable en el ítem DEBT del lote) lo lista coordinador-5 con `scripts/contract-diff.sh` antes de `contract-break-approved`.
- Endpoints: ninguno nuevo. Se implementan por detrás los ya contratados: `POST /me/approved-subjects-certificates`, `POST /approved-subjects-certificates/{id}/revoke`, `GET /me/procedures/{type}/eligibility`, `/approved` y `/approved/{documentType}/{id}/pdf`.
- Migraciones: `V471__template_version_id_on_issued_documents.sql` (solo si Q1 = A; último en develop V470, `scripts/next-migration.sh` da 471; se revalida con `--check` antes de mergear; cola #1393). No hay backfill: NULL = BUILT_IN.
- Eventos: ninguno nuevo (R1 ya tiene los suyos; M1 solo audita).
- Puertos: `enrollment.api.ApprovedSubjectGradesReader` (nuevo, P4): por inscripción de programa devuelve materia, año, nota y fecha de aprobación. Los puertos internos de M1 viven en `approvedsubjects/port/` (P5). Los gates de pagos y regularidad se reutilizan sin tocar billing.
- Restricciones de arquitectura (`QualityRulesTest.featureSubpackagesAreFreeOfCycles`, congelada): los adaptadores de excepción por deuda van en `graduation.procedure.specialist` (procedure -> certificate y procedure -> debtexception no cierran ciclo); no se mueve `CertificateDocumentType` ni `DocumentOperationPendingException` (≈45 imports de churn evitados). Desde `certificate.*` no se usa `graduation.api.DocumentIssuanceSettings` (api -> certificate cierra ciclo): se usa `documentsettings.specialist.DocumentIssuanceSettingsFinder`.
- Mocks del front y `domainErrorMessages.js`: P0 y cualquier error nuevo (Q2) en el mismo PR que lo introduce.

## Subtareas
Capas, de abajo hacia arriba. Un dueño por archivo; cuando un archivo pasa por dos subtareas se serializan por el orden de merge (columna «Depende de»). Todas Sonnet salvo indicación. Tope 100k de contexto salvo indicación.

| ID | Capa | Título | Archivos permitidos (dueño) | Depende de / paralelo con | Maven | Control |
|---|---|---|---|---|---|---|
| P0 | contrato | `studentName` nullable en ítem DEBT del lote | `contracts/openapi.json`, DTO del ítem DEBT en `graduation/debtexception/dto/`, mocks MSW y `contract-baseline.json` | — / todo | liviano (`OpenApiSnapshotTest`) | auto-merge con CI verde y `contract-break-approved` vía coordinador (regla 20) |
| P1 | migración | V471 `template_version_id` en 4 tablas | `db/migration/V471*`, test de migración nuevo, `docs/release/pre-migration-checks.sql` | Q1=A / paralelo con P0, P2-P4 | pesado (Testcontainers) | cola #1393, mergea el worker con CI verde (regla 20) |
| P2 | dominio puro | Gates de M1: regular, al día (facturación apagada no chequea), límite diario, hay materias aprobadas; vigencia desde settings | `approvedsubjects/policy/**` (nuevo), `approvedsubjects/snapshot/ApprovedSubjectsSnapshotFactory` (validez como parámetro) | — / P0, P1, P3, P4 | liviano (solo tests unitarios) | auto-merge con CI verde (regla 20) |
| P3 | dominio puro | Marcadores: recipient, average, completionPercent, subjectName, examDate | `certificate/engine/CertificateMarkers`, `engine/CertificateSnapshot` (mapa `extraMarkers` con constructor secundario para no tocar quienes lo crean), `template/specialist/DocumentTemplateMarkers` solo si la prueba de paridad lo exige | — / P0, P1, P2, P4 | liviano | auto-merge con CI verde (regla 20) |
| P4 | puerto | `ApprovedSubjectGradesReader` en `enrollment.api` con nota y fecha | `enrollment/api/ApprovedSubjectGradesReader` (nuevo) y su implementación en `enrollment`, tests | — / P0-P3 | liviano | auto-merge con CI verde (regla 20) |
| P5 | use cases con fakes | Request y Revoke de M1 contra puertos, sin Spring | `approvedsubjects/port/**` (nuevo), `approvedsubjects/usecase/**` | P2, P4 | liviano | auto-merge con CI verde (regla 20) |
| P6 | infra | Emisor de M1: bloqueo, código con reintentos, PDF, hash, guardado, registro verificable, auditoría, métrica, `deleteIfRolledBack` | `approvedsubjects/specialist/ApprovedSubjectsCertificateIssuer` (nuevo), `repository/ApprovedSubjectsCertificateRepository` (límite diario, vigente, lock) | P5 | pesado | auto-merge con CI verde (regla 20) |
| P7 | infra | Descarga con hash, revocación, vencimiento y job, fuente de verificación pública | `approvedsubjects/specialist/{PdfProvider,Revoker,Expirer,ExpireJob}` (nuevos), `verification/specialist/ApprovedSubjectsDocumentVerificationSource` (nuevo), `approvedsubjects/controller/ApprovedSubjectsCertificateController` | P6 | pesado | auto-merge con CI verde (regla 20) |
| P8 | cableado | Elegibilidad por trámite, fuente de aprobados, despacho de `/me/documents/{type}` | `procedure/specialist/ApprovedSubjects*` (nuevos), `procedure/usecase/GenerateOwnDocumentService`, `approvedsubjects/controller/MeApprovedSubjectsCertificateController`, `authorization-matrix.csv`, listas de `PermissionGateCoverageTest` | P6 / P7 | liviano | auto-merge con CI verde (regla 20) |
| P9 | motor | Ruta de render genérica con paridad con la preview; `currentPublished(DocumentTemplateType)` | `template/specialist/{CertificateTemplateRenderSource,CertificateTemplatePreviewer}`, `engine/{CertificatePdfRenderer,ApprovalCertificateDesign,BuiltInCertificateTemplate}` | P3 / P5-P8 | pesado | auto-merge con CI verde (regla 20) |
| P10 | emisores | R1 y N1 resuelven la versión publicada, la guardan y regeneran con `byId` | `regular/specialist/{RegularCertificateTemplate,RegularCertificateIssuer,RegularCertificatePdfProvider}`, entidad regular, `procedure/specialist/ExamRegistrationCertificate{Template,Issuer,PdfProvider}`, entidad `examregistration` | P1, P9 | pesado | auto-merge con CI verde (regla 20) |
| P11 | emisores | X1 y M1 resuelven la versión publicada | `exam/specialist/{ExamCertificateTemplate,Issuer,PdfProvider}`, entidad exam, `approvedsubjects/{render/ApprovedSubjectsPdfRenderer,snapshot/*,entity/ApprovedSubjectsCertificate}` y la resolución de plantilla en `ApprovedSubjectsCertificateIssuer` | P1, P7, P9, P10 | pesado | auto-merge con CI verde (regla 20) |
| P12 | excepción por deuda | Adaptadores R1 y N1 (alcance ajeno => rechazo) | `procedure/specialist/{Regular,ExamRegistration}DebtExceptionDocumentIssuer` (nuevos), método de emisión bajo excepción en los emisores R1 y N1 | P10 | pesado | auto-merge con CI verde (regla 20) |
| P13 | excepción por deuda | Adaptadores M1 y X1 | `procedure/specialist/{ApprovedSubjects,Exam}DebtExceptionDocumentIssuer` (nuevos), método análogo en los emisores M1 y X1 | P11, P12 | pesado | auto-merge con CI verde (regla 20) |
| P14 | integración | Emisión de M1 de punta a punta | `ApprovedSubjectsIssuanceIT` (nuevo, patrón `ExamRegistrationIssuanceIT`), `PublicDocumentVerificationIT` (`TYPES_WITHOUT_ISSUER_YET`), `ApprovedProceduresIntegrationTest` (l.296), `docs/api/error-catalog.md`, `frontend/src/api/domainErrorMessages.js` si hay error nuevo | P6, P7, P8 | pesado | auto-merge con CI verde (regla 20) |
| P15 | integración | Plantilla publicada, excepción por deuda y cierre | ITs de T1d (misma plantilla ⇒ mismo hash; republicar no cambia lo emitido) y D1c (los 4 tipos), `docs/cell/plans/1103.md` actualizado, fila en `docs/README.md`, handoff del piloto | P11, P13, P14 | pesado | auto-merge con CI verde (regla 20) |

### Notas por capa
- **Valor observable**: P0 contrato listo para el front; P2 y P3 reglas probadas sin base; P4 datos de nota y fecha; P5 casos de uso probados con fakes; P6-P8 el alumno pide, baja y revoca su constancia y se verifica por código; P9-P11 el Director publica y los PDF emitidos lo reflejan; P12-P13 el Administrativo emite por excepción de deuda los 4 tipos; P14-P15 todo probado de punta a punta.
- **Tests esperados**: patrón de `ExamRegistrationIssuanceIT` (límite diario, rollback con spy de `register`, archivo huérfano, hash regenerado, IDOR `anotherStudentsEnrollmentIsNotFound`, concurrencia), `OwnRegularDocumentIntegrationTest` (facturación OFF/MIRROR/FULL), `VerificationSourcesTest`, `DebtExceptionDocumentIssuersTest` con test de alcance ajeno por cada adaptador concreto (hoy no existe ninguno).
- **Sabotajes obligatorios en cada PR** (el worker los informa en el PR): quitar `register` (fallan los de verificación), invertir el borde del vencimiento, quitar el filtro de IDOR, no marcar usada la excepción, no filtrar marcadores por tipo, `VALIDITY_DAYS` hardcodeado.
- **FUERA DE ALCANCE del piloto**: `billing/**`, front, `archunit-frozen/`, mover `CertificateDocumentType`/`DocumentOperationPendingException`, retirar el endpoint legacy (Q4), analítico foliado, sello, mails, #1104 y #1211 (además de lo que ya cerró egresos-2).
- **Comando de verificación común**: `backend/dev-test <tests de la subtarea>,ModularityTest,ArchitectureTest,QualityRulesTest,JavadocOnlyInPackageInfoTest,OpenApiSnapshotTest,PermissionGateCoverageTest,AuthorizationMatrixTest,DocsIndexTest,ErrorCatalogTest`.

## Orden de merge
Apilado de abajo hacia arriba; cada PR se actualiza con `git merge origin/develop` solo cuando le toca.
1. Ola 1 (en paralelo, 5 workers): P0, P1 (por cola #1393), P2, P3, P4.
2. Ola 2: P5 (tras P2 y P4) ∥ P9 (tras P3).
3. Ola 3: P6 (tras P5) ∥ P10 (tras P1 y P9).
4. Ola 4: P7 (tras P6) → P8 (tras P6) ∥ P12 (tras P10).
5. Ola 5: P11 (tras P1, P7, P9, P10) → P13 (tras P11 y P12).
6. Ola 6: P14 (tras P6-P8) y P15 (tras P11, P13, P14).
Pico: 5 workers en la ola 1 y 4 en las olas 2 a 4; nunca se superan los 7 del piloto.

## Maven (tope 2 en toda la célula)
- Pesado: P1, P6, P7, P9, P10, P11, P12, P13, P14, P15. Liviano: P0, P2, P3, P4, P5, P8.
- Los workers escriben y esperan `GO MAVEN` del líder; se otorga en el orden de merge, nunca más de 2 a la vez (con B2-S4 de backend-5 en vuelo cuenta como uno si usa Maven). Se corre con `dev-test` focalizado y la suite completa queda para el CI.

## Cuota (estimación, sin medir)
- 15 subtareas con tope de 100k de contexto: techo teórico 1,5M tokens de plataforma; lo realista queda por debajo porque P0, P2-P5 y P8 son chicas. No doy tiempos de pared: no están medidos. Medimos tiempos y choques al cierre.

## Decisiones registradas
- 2026-11-25 Gabriel: piloto de atomización sobre M1-b, T1d y D1c, sin medir contrato ni migración (opción A de la consulta a coordinador-5).
- 2026-11-25 líder-seguridad-2: los adaptadores van en `procedure.specialist` y no se mueven tipos a un paquete neutro (evita ≈45 imports y un ciclo de slices); T1d sí incluye una migración (Q1), por lo que el piloto cubre esa capa.
- 2026-11-25 Gabriel: regla 20 (PR #1510): se cancelan todas las revisiones, incluida la doble revisión de plata, contrato y seguridad y la verificación del líder; todo PR del piloto mergea con la CI verde sobre el head real (auto-merge; V471 por la cola #1393; `contract-break-approved` vía coordinador con `scripts/contract-diff.sh`).
