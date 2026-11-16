# Plan: <ticket> — <título corto>

Estado del plan: borrador | aprobado | en ejecución | cerrado
Líder: <sesión> · Fecha: <AAAA-MM-DD> · Spec: <ruta/sección>

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
- Migraciones: `V<n>__<descripcion>.sql` (reservada; último en develop: V<x>, master: V<y>)
- Eventos: `<NombreEvento>` — payload — productor => consumidor
- Puertos `<modulo>.api`: ...
- Mocks del front / `domainErrorMessages.js`: ...

## Subtareas
| ID | Título | Rol | Archivos/paquetes (sin solapamiento) | Depende de | D-xxx | Tope | Plata/contrato/seguridad |
|---|---|---|---|---|---|---|---|
| T#1 | | backend-1 | | — | | 100k | no |

### T#1 — <título>
- Valor observable: después de este PR, <X puede hacer Y>.
- Cómo probarlo: <pasos o test>
- Tests esperados: <clases/casos, incluido el borde>
- Notas de diseño: <patrón y por qué, una línea>

## Orden de merge
1. T#1 => 2. T#2 ...

## Revisión
| PR | Revisor(es) | Veredicto |
|---|---|---|

## Estado (tablero)
| Subtarea | Dueño | Rama | PR | Estado | Tokens/tope |
|---|---|---|---|---|---|

## Decisiones registradas
- <fecha> <decisión> (billing: D-xxx en el registro)
