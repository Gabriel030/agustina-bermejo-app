# Plan: #<issue> — <título corto>

Se escribe UNA vez al planificar; el estado vive en GitHub (issue, PRs con `cell:<issue>`, Project "Célula"), no acá.
Líder: <sesión> · Fecha: <AAAA-MM-DD> · Issue: #<N> · Spec: <ruta/sección>

## Objetivo
<Una frase: después de esto, quién puede hacer qué.>

## Preguntas al usuario (agrupadas)
| # | Pregunta | Opciones | Recomendación | Bloquea |
|---|---|---|---|---|

## Definition of Ready
- [ ] Hueco confirmado contra `origin/develop` (grep): <evidencia>
- [ ] Contrastado con `docs/billing/decision-registry.md` (D-xxx: ...) o no toca billing
- [ ] Sin PRs abiertos que lo dupliquen (`gh pr list`)

## Contratos
- Endpoints: `<MÉTODO> <ruta>` — request / response / errores / permisos
- `contracts/openapi.json`: <cambio aditivo | ruptura declarada>
- Migraciones: `V<n>__<descripcion>.sql` (número reservado; último en develop: V<x>, master: V<y>)
- Eventos: `<NombreEvento>` — payload — productor => consumidor
- Puertos `<modulo>.api`: ...
- Mocks del front / `domainErrorMessages.js`: ...

## Subtareas
Una por puesto, ≤100k, un dueño por archivo; contratos definidos antes de lanzar; backend y frontend en paralelo con mocks del contrato.
| ID | Título | Puesto | Archivos/paquetes (un dueño por archivo) | Depende de / en paralelo con | D-xxx | Tope | Plata/contrato/seguridad |
|---|---|---|---|---|---|---|---|
| T#1 | | backend-1 | | — | | 100k | no |

### T#1 — <título>
- Valor observable: después de este PR, <X puede hacer Y>.
- Cómo probarlo: <pasos o test>
- Tests esperados: <clases/casos, incluido el borde>
- Notas de diseño: <patrón y por qué, una línea>

## Orden de merge
1. T#1 => 2. T#2 ... (indicar qué va en paralelo)

## Decisiones registradas
- <fecha> <decisión> (billing: D-xxx en el registro)
