# Índice de la documentación

> Verificado: 2026-10-31, commit `cd1ede80`.

Una línea por documento. Estados: **vigente** (verificado contra el código), **generado** (lo escribe un test; no se edita a mano), **obsoleto** (se conserva hasta que se decida borrarlo). Un test (`DocsIndexTest`) falla si un `.md` de `docs/` no figura acá o si un enlace relativo interno apunta a algo inexistente.

Punto de entrada: [README.md](../README.md) (qué es, cómo levantarlo) y [CLAUDE.md](../CLAUDE.md) (reglas de código y proceso).

## Convenciones para desarrollar (`docs/context/`)

| Documento | Para qué | Estado |
|---|---|---|
| [backend.md](context/backend.md) | Convenciones de backend: entidades, casos de uso, servicios, controllers, migraciones | vigente |
| [data-retention.md](context/data-retention.md) | Retención de datos operativos: jobs, plazos `app.retention.*` y qué se conserva | vigente |
| [frontend.md](context/frontend.md) | Convenciones de frontend, flujo de diseño con `ux-advisor`, rutas por rol | vigente |
| [design-system.md](context/design-system.md) | Sistema de diseño: cómo registrar un diseño nuevo cambiando solo los tokens | vigente |
| [components.md](context/components.md) | Qué componente usar y cómo listarlos en una spec | vigente |
| [modules.md](context/modules.md) | Límites entre módulos con Spring Modulith, línea base de violaciones | vigente |
| [overload-protection.md](context/overload-protection.md) | Rate limit, IP real detrás de proxy, timeouts, pool, circuit breakers, bulkhead, límites de entrada y dead letter de la cola de eventos | vigente |
| [testing.md](context/testing.md) | Mapa de herramientas de test y criterio de qué test escribir | vigente |
| [debugging.md](context/debugging.md) | Correr todo local y resolver arranques fallidos | vigente |
| [workflow.md](context/workflow.md) | Git, CI local, commits, PRs, migraciones y backport | vigente |
| [test-users.md](context/test-users.md) | Usuarios de prueba y con cuál probar cada flujo | vigente |
| [specs.md](context/specs.md) | Dónde viven las specs y el roadmap (Obsidian) | vigente |
| [cell.md](context/cell.md) | Célula de agentes: roles, arranque de sesiones, flujo de un ticket, local vs nube | vigente |
| [plans/TEMPLATE.md](cell/plans/TEMPLATE.md) | Plantilla de plan de la célula (`docs/cell/plans/<ticket>.md`) | vigente |
| [plans/920.md](cell/plans/920.md) | Cierre de la Fase 1 de comunicación masiva: residual derivado | vigente |
| [plans/921.md](cell/plans/921.md) | Residual de la lista de espera: vencimiento de colas de materias y auditoría de promoción automática | vigente |
| [plans/932.md](cell/plans/932.md) | Residual de la Fase 2: variables faltantes en la vista previa, contrato y OR por clave | vigente |
| [plans/933.md](cell/plans/933.md) | Residual de la Fase 3: render por destinatario, baja y retención definitiva | vigente |
| [plans/949.md](cell/plans/949.md) | Residual del consentimiento al crear la cuenta: contrato y decisiones | vigente |
| [plans/951.md](cell/plans/951.md) | Cierre de 24-A: tabla final de los hallazgos de la auditoría del 3-nov | vigente |
| [plans/954.md](cell/plans/954.md) | Cierre de 33.4: conteo de consultas en /me y asistencia, con N>50 | vigente |
| [plans/955.md](cell/plans/955.md) | Cierre de 24-B: el dueño aprueba el cambio de email pedido por el personal | vigente |
| [plans/956.md](cell/plans/956.md) | Cierre de 24-D: límites de tamaño de subidas probados con servidor real | vigente |
| [plans/1196.md](cell/plans/1196.md) | E29-D11: invitaciones por oleadas a alumnos importados | vigente |
| [plans/1103-piloto.md](cell/plans/1103-piloto.md) | Piloto por capas de #1103: constancia de materias aprobadas (M1-b), plantilla publicada en todos los documentos (T1d) y excepción por deuda (D1c) | vigente |
| [plans/1103.md](cell/plans/1103.md) | Lo entregado de #1103: M1-b, plantilla publicada en los documentos (T1d) y excepción por deuda (D1c), con cobertura y pendientes | vigente |
| [handoffs/1103-piloto.md](cell/handoffs/1103-piloto.md) | Handoff del piloto por capas de #1103: horas de merge de P0 a P15, denegaciones del clasificador, choques y qué queda | vigente |
| [plans/e22-11-12.md](cell/plans/e22-11-12.md) | E22 22.11 y 22.12: eventos síncronos entre módulos (síncronos a propósito, probados; billing con compuerta) | vigente |
| [reports/e18-violaciones.md](cell/reports/e18-violaciones.md) | E18 M0: violaciones de Modulith medidas, ciclos, dependencias hacia arriba y procesos a cortar | vigente |
| [reports/e25-b2-invariantes.md](cell/reports/e25-b2-invariantes.md) | E25 B2 S1: invariantes que solo protege el código, candidatas a único parcial o CHECK, con queries de verificación | vigente |
| `cell/progress/` | Estado de cada subtarea de la célula; se genera por rama y no se lista uno por uno | vigente |
| [progress/921-1.md](cell/progress/921-1.md) | Estado de la subtarea 921#1 (listas de espera, consulta del personal) | vigente |
| [progress/e2-2-4a.md](cell/progress/e2-2-4a.md) | Estado de E2 2.4a (mapa curricular del alumno: correlativas completas e inscripción vigente) | vigente |
| [progress/22-9d2.md](cell/progress/22-9d2.md) | Estado de T22.9d-2 (attendance: cargar asistencia y cancelar clase al compositor) | vigente |
| [progress/22-9d1.md](cell/progress/22-9d1.md) | Estado de la subtarea T22.9d-1 (attendance: pedidos de cambio y justificaciones al compositor) | vigente |

## Release (`docs/release/`)

| Documento | Para qué | Estado |
|---|---|---|
| [release/migration-smoke.md](release/migration-smoke.md) | Pasos para probar la cadena de migraciones de master (V110) a develop sobre Postgres en Docker | vigente |
| [release/institution-sms-credential.md](release/institution-sms-credential.md) | Qué hacer si `institution.sms_password` tenía una credencial cargada antes de V214 | vigente |
| [release/permission-modes-runbook.md](release/permission-modes-runbook.md) | Modos LEGACY/SHADOW/ENFORCE de la grilla de permisos: rollback sin deploy, lectura de discrepancias y criterio para salir de SHADOW | vigente |
| [deploy/README.md](deploy/README.md) | Runbook de despliegue de producción: primer despliegue, actualización con migraciones, rollback, secretos y logs | vigente |
| [security/plan-produccion.md](security/plan-produccion.md) | Seguridad de producción (Épica 29, bloque G): diagnóstico de sesión, CSP, auditoría y matriz de autorización; planes de cookie HttpOnly, CSP con nonce y audit log | vigente |
| [security/csp-estilos-inventario.md](security/csp-estilos-inventario.md) | Inventario de estilos inline para una CSP estricta (#919): política actual del Caddyfile, orígenes de `style` con conteos reproducibles, qué librerías inyectan `<style>` y propuesta de etapas | vigente |
| [security/audit-2026-11-03-status.md](security/audit-2026-11-03-status.md) | Estado de los hallazgos de la auditoría del 3-nov-2026: 12 cerrados y 4 abiertos (parciales) | vigente |
| [security/pentest-interno.md](security/pentest-interno.md) | Plan de pentest interno (E29 G7 / 24.6a): reglas de juego, casos por flujo, criterio de aprobación, perfiles ZAP, plantilla de hallazgos y qué queda para el externo | vigente |
| [security/pentest-informe-2026-11.md](security/pentest-informe-2026-11.md) | Informe final del pentest interno (E29 G7-d): alcance, método, resultados L1 a L6, hallazgos con su remediación (#1400, #1403) y riesgos abiertos | vigente |
| [security/secrets-rotation.md](security/secrets-rotation.md) | Procedimiento de rotación de secretos de producción (JWT, base, backups, proveedores): impacto, pasos, verificación, rollback y checklist de ensayo en staging | vigente |
| [security/audit-24-4-batch-1-auth.md](security/audit-24-4-batch-1-auth.md) | Auditoría 24.4 lote 1 (identidad: H6 login, H7 JWT, H20 registro): qué se verificó sin cambio y qué se corrigió (audiencia del JWT, clave con caracteres distintos) | vigente |
| [legal/derechos-de-las-personas.md](legal/derechos-de-las-personas.md) | Procedimiento para pedidos de acceso, rectificación y supresión de datos personales (Épica 29, C5): inventario de datos, qué no se puede borrar, plantillas y paquetes futuros; pendiente de abogado | borrador |
| [deploy/backup-and-restore.md](deploy/backup-and-restore.md) | Backups de Postgres, runbook de restauración, prueba automática de restauración, RPO/RTO y variables | vigente |
| [deploy/monitoring.md](deploy/monitoring.md) | Alerta de caída (uptime-check), perfil Prometheus/Alertmanager/Grafana y qué hacer ante una caída | vigente |
| [deploy/glitchtip.md](deploy/glitchtip.md) | Overlay de GlitchTip autoalojado: versión y fuente de los nombres, acceso solo por loopback, primer arranque, retención, actualización, privacidad y recursos (no medidos) | vigente |
| [deploy/sentry.md](deploy/sentry.md) | Reporte de errores 5xx del backend a Sentry: cómo activarlo, qué se depura y advertencia de privacidad | vigente |
| [deploy/backup-status.schema.json](deploy/backup-status.schema.json) | JSON Schema (draft 2020-12, `schemaVersion` 1) del archivo de estado que escriben los scripts de backup y lee `GET /backups/status` | vigente |
| [deploy/backup-and-restore.md](deploy/backup-and-restore.md) | Backups de Postgres, runbook de restauración, prueba automática de restauración, RPO/RTO y variables | vigente |
| [deploy/monitoring.md](deploy/monitoring.md) | Alerta de caída (uptime-check), perfil Prometheus/Alertmanager/Grafana y qué hacer ante una caída | vigente |
| [../perf/README.md](../perf/README.md) | Como reproducir la medicion de rendimiento: Postgres con pg_stat_statements, perfil `perf`, presupuestos y k6 | vigente |

## Decisiones de arquitectura (`docs/adr/`)

| Documento | Para qué | Estado |
|---|---|---|
| [adr/README.md](adr/README.md) | Lista numerada de ADRs | vigente |
| [adr/0002-patron-de-modulos.md](adr/0002-patron-de-modulos.md) | Patrón de módulos: api, events, model | aceptado |
| [adr/0003-outbox-de-eventos-sin-broker.md](adr/0003-outbox-de-eventos-sin-broker.md) | Eventos con Outbox, sin broker | aceptado |
| [adr/0004-value-objects-money-y-percentage.md](adr/0004-value-objects-money-y-percentage.md) | Money y Percentage compartidos | aceptado |
| [adr/0005-venta-por-instituto-y-modulos-activables.md](adr/0005-venta-por-instituto-y-modulos-activables.md) | Venta por instituto, módulo `platform`, `PLATFORM_ADMIN` | aceptado, sin implementar |
| [adr/0006-modo-espejo-de-facturacion.md](adr/0006-modo-espejo-de-facturacion.md) | Modo espejo de facturación | propuesto |
| [adr/0007-vocabulario-de-roles-y-migracion-de-carpetas.md](adr/0007-vocabulario-de-roles-y-migracion-de-carpetas.md) | Vocabulario de roles (`service` a `specialist`) y migración de carpetas | aceptado |

## Arquitectura de módulos (`docs/architecture/`)

| Documento | Para qué | Estado |
|---|---|---|
| [architecture/README.md](architecture/README.md) | Cómo se regenera esta carpeta (`ModuleDocumentationTest`) | generado |

El resto de la carpeta son artefactos generados, sin documento `.md`: `components.puml` y un par `module-<módulo>.puml` y `module-<módulo>.adoc` por cada módulo (academicrecord, academicstructure, announcement, attendance, billing, config, enrollment, evaluation, graduation, identity, notification, shared).

## Facturación (`docs/billing/`)

| Documento | Para qué | Estado |
|---|---|---|
| [billing/decision-registry.md](billing/decision-registry.md) | Registro de decisiones D-xxx; entrada obligatoria de specs y tests de billing | vigente (lo valida `BillingDecisionRegistryTest`) |
| [billing/policy-catalog.md](billing/policy-catalog.md) | Catálogo de validaciones de facturación | generado (`PolicyCatalogTest`) |
| [billing/mutation-exclusions.md](billing/mutation-exclusions.md) | Mutantes equivalentes excluidos de PIT | vigente |
| [billing/mirror-mode-endpoint-classification.md](billing/mirror-mode-endpoint-classification.md) | Qué endpoints de billing sirven en MIRROR (lecturas) y cuáles solo en FULL (escrituras) | vigente (lo valida `BillingEndpointModeClassificationTest`) |

## API (`docs/api/`)

| Documento | Para qué | Estado |
|---|---|---|
| [api/error-catalog.md](api/error-catalog.md) | Catálogo de códigos de error, reintentables, política de reintentos e idempotencia de las escrituras | generado (`ErrorCatalogTest`) |

## Pruebas

| Documento | Para qué | Estado |
|---|---|---|
| [testing/local-test-log.md](testing/local-test-log.md) | Bitácora de herramientas pesadas que corren solo en local | vigente |

## Otros archivos

| Archivo | Para qué | Estado |
|---|---|---|
| [DDL-v2.png](DDL-v2.png) | Diagrama del esquema DDL v2 anterior al motor de facturación v2 | obsoleto (no se borra; decide Gabriel) |
| [../diagrama_entidades.puml](../diagrama_entidades.puml) | Diagrama de entidades PlantUML de la raíz | obsoleto (no se borra; decide Gabriel) |
| [../contracts/openapi.json](../contracts/openapi.json) | Contrato HTTP versionado, verificado en CI | vigente |
