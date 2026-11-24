---
name: spec-writing
description: Escribe una spec técnica/funcional nueva como UN Issue de GitHub (rebanadas con alcance, criterios verificables, dependencias y decisiones de Gabriel) antes de implementar. Usar cuando falte spec de una épica o rebanada, o "spec-writing", "escribí la spec", "armá el Issue de la épica".
---

# spec-writing

Entrada: una épica, un ítem de backlog o una rebanada sin spec. Salida: UN Issue de GitHub que el líder puede planificar con `cell-plan` y lanzar a workers. Modelo de buen resultado: Issue #1407 (spec 26-D).

## Cuándo usarla
- Hay que escribir una spec nueva antes de implementar.
- Una épica o rebanada no tiene spec, o la que hay está desactualizada contra el código.

## Qué NO hace
- No implementa, no escribe en el vault de Obsidian (regla 14 de `docs/context/cell.md`: solo el coordinador), no abre ramas ni PRs.
- No cambia contratos (`contracts/openapi.json`, eventos, puertos): la spec solo los declara. Sin cambio de contrato salvo que la spec lo diga explícitamente, y entonces va listado.

## Cómo reconstruir el alcance
Contrastar contra lo real, no contra la memoria:
1. Código en `origin/develop` (`git fetch origin`, grep de símbolos, archivos y migraciones; último `V*.sql` si hay migración).
2. Issues relacionados y sus comentarios (`gh api repos/<owner>/<repo>/issues/N` y `/comments`; en la nube no andan `gh issue view|list`, ver `docs/context/cell.md`, «`gh` en sesiones de la nube»), incluido el comentario ESTADO VIVO.
3. PRs mergeados y abiertos (`gh api .../pulls`): qué ya está hecho y qué está en vuelo.
4. Specs del vault (ubicación en `docs/context/specs.md`), solo lectura.
5. Si toca billing, `docs/billing/decision-registry.md`.

Lo que no se pueda verificar se marca **PROPUESTA** (con su justificación); nunca se presenta como hecho.

## Formato del Issue
- Etiquetas: `cell` y `cell:<issue>` (el Issue de la épica de la que cuelga).
- Título: `E<n>-<letra>: <resumen>`.
- Cuerpo, en castellano y sin relleno:
  1. Una línea de contexto (`Refs #…`) y si hay o no cambio de contrato/backend.
  2. **Contexto verificado** (con fecha y base `origin/develop`): qué está hecho, qué hay hoy en el repo.
  3. **Decisiones de Gabriel**: tabla `# | Decisión | Opciones | Recomendación | Bloquea`, solo para lo que únicamente él decide (producto, plata, prioridades). Cada una con opciones y una recomendación. Se aclara que sin respuesta los workers no avanzan en lo que bloquea.
  4. **Grafo de dependencias y paralelismo**: qué corre en paralelo, qué espera a qué, qué archivos comparten las rebanadas.
  5. **Una sección por rebanada** (ver abajo).
  6. Reglas comunes a todas las rebanadas (identificadores y nombres de tests en inglés, cero comentarios en código nuevo, textos de usuario en castellano, título de PR, etiquetas `cell:<issue>` y `role:<puesto>`).

## Cada rebanada
- **Tamaño:** estimado ≤100k tokens y un solo objetivo con valor observable.
- **Depende de / Paralelo con.**
- **Objetivo.**
- **Archivos permitidos** (un dueño por archivo entre rebanadas).
- **FUERA DE ALCANCE.**
- **Criterios de aceptación** numerados y verificables (comando, grep con resultado esperado, test que falla si se rompe a propósito).
- **Verificación:** comando exacto. Backend: `backend/dev-test` (o el comando de `docs/context/testing.md` que corresponda). Frontend: Vitest (`npx vitest run <ruta>`), lint/build y Playwright cuando haya e2e.

## Reglas
- Identificadores de código y nombres de tests en inglés (regla 3 de `CLAUDE.md`); textos de usuario en castellano.
- Las preguntas de negocio no se resuelven solas: van a «Decisiones de Gabriel».
- Un solo Issue por spec; sin duplicar uno existente (buscar antes de crear).
- Al terminar, un solo aviso con el número del Issue (formatos en `cell-report`).
