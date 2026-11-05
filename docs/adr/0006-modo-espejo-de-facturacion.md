# ADR 0006 — Modo espejo (`MIRROR`) de facturación

> Verificado: 2026-10-31, commit `cd1ede80`.

- **Estado:** propuesto. La decisión de incluir el modo está tomada por Gabriel (31-oct-2026), pero el formato de importación externa no está definido y su implementación está bloqueada.
- **Fuentes:** `Decisiones del apagado de billing (31-oct)` (pregunta Q1) y la auditoría de la Épica 19. No hay una nota específica del modo espejo en el vault.

## Decisión tomada

Billing tiene tres modos por instituto: `OFF` (módulo apagado), `FULL` (emite y cobra) y `MIRROR` (solo muestra datos importados de otro sistema, sin emitir ni cobrar). Se aplican desde la pantalla de Plataforma, que persiste en base y rige al instante, con motivo y auditoría (ver ADR 0005).

## Consecuencias conocidas

- `MIRROR` agrega la importación externa y pantallas de espejo (fase 19C-5, estimada en unos 275k tokens de plataforma sobre el costo del apagado).
- Las pantallas de espejo muestran datos importados y no generan deuda ni cobranza.

## Falta confirmar

- Formato de importación del proveedor externo (sub-fase C5d, bloqueada).
- Qué endpoints de billing siguen disponibles en `MIRROR` y cómo se declaran en la guardia.
- Si el asistente de corte entre `MIRROR` y `FULL` entra alguna vez (hoy fuera de alcance; sin backfill).
