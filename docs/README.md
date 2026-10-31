# Índice de la documentación

> Verificado: 2026-10-31, commit `cd1ede80`.

Una línea por documento. Estados: **vigente** (verificado contra el código), **generado** (lo escribe un test; no se edita a mano), **obsoleto** (se conserva hasta que se decida borrarlo). Un test (`DocsIndexTest`) falla si un `.md` de `docs/` no figura acá o si un enlace relativo interno apunta a algo inexistente.

Punto de entrada: [README.md](../README.md) (qué es, cómo levantarlo) y [CLAUDE.md](../CLAUDE.md) (reglas de código y proceso).

## Convenciones para desarrollar (`docs/context/`)

| Documento | Para qué | Estado |
|---|---|---|
| [backend.md](context/backend.md) | Convenciones de backend: entidades, casos de uso, servicios, controllers, migraciones | vigente |
| [frontend.md](context/frontend.md) | Convenciones de frontend, flujo de diseño con `ux-advisor`, rutas por rol | vigente |
| [components.md](context/components.md) | Qué componente usar y cómo listarlos en una spec | vigente |
| [modules.md](context/modules.md) | Límites entre módulos con Spring Modulith, línea base de violaciones | vigente |
| [testing.md](context/testing.md) | Mapa de herramientas de test y criterio de qué test escribir | vigente |
| [debugging.md](context/debugging.md) | Correr todo local y resolver arranques fallidos | vigente |
| [workflow.md](context/workflow.md) | Git, CI local, commits, PRs, migraciones y backport | vigente |
| [test-users.md](context/test-users.md) | Usuarios de prueba y con cuál probar cada flujo | vigente |
| [specs.md](context/specs.md) | Dónde viven las specs y el roadmap (Obsidian) | vigente |

## Decisiones de arquitectura (`docs/adr/`)

| Documento | Para qué | Estado |
|---|---|---|
| [adr/README.md](adr/README.md) | Lista numerada de ADRs | vigente |
| [adr/0002-patron-de-modulos.md](adr/0002-patron-de-modulos.md) | Patrón de módulos: api, events, model | aceptado |
| [adr/0003-outbox-de-eventos-sin-broker.md](adr/0003-outbox-de-eventos-sin-broker.md) | Eventos con Outbox, sin broker | aceptado |
| [adr/0004-value-objects-money-y-percentage.md](adr/0004-value-objects-money-y-percentage.md) | Money y Percentage compartidos | aceptado |
| [adr/0005-venta-por-instituto-y-modulos-activables.md](adr/0005-venta-por-instituto-y-modulos-activables.md) | Venta por instituto, módulo `platform`, `PLATFORM_ADMIN` | aceptado, sin implementar |
| [adr/0006-modo-espejo-de-facturacion.md](adr/0006-modo-espejo-de-facturacion.md) | Modo espejo de facturación | propuesto |

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
