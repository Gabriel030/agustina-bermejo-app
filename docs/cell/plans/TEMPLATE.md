# Plan: #<issue> — <título corto>

Se escribe UNA vez al planificar; el estado vive en GitHub (issue, PRs con `cell:<issue>`, Project "Célula"), no acá.
Líder: <sesión> · Fecha: <AAAA-MM-DD> · Issue: #<N> · Spec: <ruta/sección>

## Objetivo
<Una frase: después de esto, quién puede hacer qué.>

## Tipo de ticket
A (solo backend) | B (cambia pantalla existente) | C (pantalla nueva): <tipo y por qué>

## Preguntas al usuario (agrupadas)
| # | Pregunta | Opciones | Recomendación | Bloquea |
|---|---|---|---|---|

## Definition of Ready
- [ ] Hueco confirmado contra `origin/develop` (grep): <evidencia>
- [ ] Contrastado con `docs/billing/decision-registry.md` (D-xxx: ...) o no toca billing
- [ ] Sin PRs abiertos que lo dupliquen (`gh pr list`)

## Diseño (solo B y C)
- Artifact del mockup: <link>
- Revisión `ux-advisor`: <veredicto y hallazgos incorporados>
- OK de Gabriel: <pendiente | aprobado AAAA-MM-DD | con cambios: ...> (sin OK no arrancan contrato ni frontend)
- Backend que puede arrancar antes del OK: <dominio, migraciones, reglas, tests | ninguno>

## Contratos
- PR de contrato (`cell-contract`, se mergea primero): <#PR | pendiente> · depende de: <OK de diseño | —>
- Endpoints: `<MÉTODO> <ruta>` — request / response / errores / permisos
- `contracts/openapi.json`: <cambio aditivo | ruptura declarada>
- Migraciones: `V<n>__<descripcion>.sql` (número reservado; último en develop: V<x>, master: V<y>)
- Eventos: `<NombreEvento>` — payload — productor => consumidor
- Puertos `<modulo>.api`: ...
- Mocks del front / `domainErrorMessages.js`: ...

## Subtareas
Una por puesto, ≤100k, un dueño por archivo; contratos definidos antes de lanzar; backend y frontend en paralelo con mocks del contrato.
| ID | Título | Puesto | Archivos permitidos (un dueño por archivo) | Depende de / en paralelo con | D-xxx | Tope | Plata/contrato/seguridad |
|---|---|---|---|---|---|---|---|
| T#0a | Mockup y OK (solo B/C) | líder | `frontend/src/mockups/<ticket>/` | — | | — | no |
| T#0b | PR de contrato | líder | `contracts/openapi.json`, controllers/DTO, mocks MSW, `domainErrorMessages.js` | T#0a (B/C) | | — | contrato |
| T#1 | | backend-1 | | T#0b | | 100k | no |

### T#1 — <título>
- Valor observable: después de este PR, <X puede hacer Y>.
- Cómo probarlo: <pasos o test>
- Tests esperados: <clases/casos, incluido el borde>
- Notas de diseño: <patrón y por qué, una línea>
- Formato de salida esperado: <PR con ..., checklist de criterios en `docs/cell/progress/<issue>-<n>.md`>
- FUERA DE ALCANCE: <lo que no debe tocar ni resolver>
- Criterios de aceptación verificables: <lista pasa/no pasa>
- Comando de verificación: <comando> (frontend: además verificación en navegador/preview)

## Orden de merge
1. T#1 => 2. T#2 ... (indicar qué va en paralelo)

## Decisiones registradas
- <fecha> <decisión> (billing: D-xxx en el registro)
