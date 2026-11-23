# Re-medición de /director/financiero tras #1243, #1261 y #1308 (Issue #1172)

Medido el 2026-11-23 sobre develop en `375ab22fe`, con `perf/e31` (solo rol Director), backend con perfil `perf`, seed grande y frontend de producción apuntando a ese backend. Red 4G simulada y CPU x4 como en `report.md`. Carga de la máquina al medir: load average 0,3. No se regenera `report.md`: es la corrida completa de los cuatro roles y esta fue solo del Director.

## Antes y después

| `/director/financiero` | Base (`report.md`, 2026-11-17) | Hoy, una moneda | Hoy, dos monedas (ARS y USD) |
|---|---|---|---|
| Peticiones | 19 | 20 | 22 |
| Duplicadas (mismo método y URL) | 3 | 0 | 0 |
| Niveles de cascada | 6 | 5 | 5 |
| Respuestas 400 | 0 | 0 | 5 (`CURRENCY_REQUIRED`) |
| Pantalla completa | 3,2 s | 4,6 s | 2,1 s |

Los tiempos no son comparables entre corridas (cambia la carga y la caché del backend); los conteos sí.

- Se fueron las 3 duplicadas (`/me`, `/me/legal-acceptances/pending`, `/financial-dashboard`) y bajó un nivel.
- Las 20 peticiones de hoy incluyen `GET /me/contact-consents`, que no estaba en la base; la corrida de dos monedas sumó además `/me/notifications/unread-count`, que la de una moneda no llegó a pedir antes de quedar en reposo.
- Las métricas (`/billing/metrics/*`) y el dashboard salen todos juntos en el nivel 4, justo después de la carcasa: ya no esperan al dashboard.

## Ola de 400 con varias monedas

Con una segunda cuenta corriente en USD (dato temporal en la base local de perf, ya borrado), las 5 métricas que dependen de moneda (`on-time-payment`, `issued-invoices`, `late-fees`, `collections-stock`, `receipts`) salen sin `currency` en el nivel 4 y responden 400 `CURRENCY_REQUIRED`. Cuando llega el dashboard se repiten con `currency=ARS` y responden 200. Son 5 peticiones perdidas y un nivel de cascada extra.

Con una sola moneda no hay 400, pero las mismas 5 métricas igual se piden dos veces (sin y con `currency=ARS`). No cuentan como duplicadas porque la URL cambia, y son 5 de las 20 peticiones. Es el margen que queda para #1172.

## Presupuestos

`perf/budget.json` ya está en lo medido por `frontend-screen-budget` (`directorScreenRequests.integration.test.jsx`) más 3 de holgura para las 20 pantallas del Director, y `PERF_SCOPE=screen python3 perf/check_budget.py` pasa. No hay valores para bajar, por eso no se modifica.

Los 20 conteos reales de `perf/e31` quedan por encima de los del test porque incluyen la carcasa (`/auth/refresh`, `/me`, módulos, avisos legales, consentimientos), que el test no cuenta; no se usan para fijar presupuestos.

## Para reproducir

El usuario `director.demo` queda detrás de la pantalla "Antes de seguir, aceptá los avisos de la institución" (consentimiento de avisos), que bloquea cualquier ruta del Director: sin aceptarla una vez, toda pantalla mide solo 5 peticiones de la carcasa. Aceptarla una vez desde el navegador antes de `npm run measure`.
