# Épica 31 — línea base de llamadas HTTP por pantalla (31.1)

Generado por `perf/e31/measure.mjs` el 2026-11-17T19:27:14.329Z. Red: 4G simulada: 150 ms RTT, 1,6 Mbps bajada, 750 kbps subida; CPU x4; caché desactivada y contexto de navegador nuevo por pantalla (carga en frío). Solo lectura: no se ejecuta ninguna acción que modifique datos.

Definiciones:

- **Peticiones**: llamadas a la API (excluye preflight CORS y estáticos); los estáticos se informan aparte.
- **Duplicadas**: peticiones repetidas con mismo método y URL (cuenta las repeticiones sobrantes).
- **Cascada**: nivel de dependencia temporal. Una petición está en el nivel N+1 si empezó después de que terminó otra de nivel N. Nivel 1 = lo que salió en paralelo en la primera ola; cascada = niveles >= 2.
- **Contenido útil**: LCP del navegador (primer pintado del contenido principal). **Pantalla completa**: hasta que no queda ninguna llamada a la API en vuelo durante 600 ms.
- Los tiempos varían entre corridas; los conteos de peticiones, duplicadas y cascadas son los comparables.

Pantallas medidas: 46 de 49 intentadas.

## 10 pantallas más pesadas

Orden: más peticiones, luego más bytes de API.

### 1. Director `/director/financiero` — 19 peticiones, 3 duplicadas, cascada 6 niveles, 22.7 KB de API, 3.2 s

Duplicadas:
- `GET /me` ×2
- `GET /me/legal-acceptances/pending` ×2
- `GET /financial-dashboard` ×2

En cascada (pide esperar a otra respuesta):
- nivel 2: `GET /me` (después de `POST /auth/refresh`)
- nivel 3: `GET /me/legal-acceptances/pending` (después de `GET /me`)
- nivel 3: `GET /me/modules` (después de `GET /me`)
- nivel 4: `GET /me/notifications/unread-count` (después de `GET /me/legal-acceptances/pending`)
- nivel 4: `GET /me` (después de `GET /me/legal-acceptances/pending`)
- nivel 4: `GET /me/legal-acceptances/pending` (después de `GET /me/legal-acceptances/pending`)
- nivel 4: `GET /financial-dashboard/debtors/page?sortBy=DEBT_AGE&page=0&size=25` (después de `GET /me/legal-acceptances/pending`)
- nivel 4: `GET /financial-dashboard` (después de `GET /me/legal-acceptances/pending`)
- nivel 4: `GET /programs` (después de `GET /me/legal-acceptances/pending`)
- nivel 4: `GET /courses` (después de `GET /me/legal-acceptances/pending`)
- nivel 4: `GET /billing/metrics/first-month` (después de `GET /me/legal-acceptances/pending`)
- nivel 5: `GET /billing/metrics/on-time-payment?from=2025-12&to=2026-11&currency=ARS` (después de `GET /me/notifications/unread-count`)
- … y 6 más

Respuestas más grandes: `GET /billing/metrics/issued-invoices?from=2025-12&to=2026-11&currency=ARS` 1.7 KB, `GET /billing/metrics/collections-stock?from=2025-12&to=2026-11&currency=ARS` 1.7 KB, `GET /billing/metrics/receipts?from=2025-12&to=2026-11&currency=ARS` 1.5 KB.

### 2. Director `/director/oferta-academica` — 18 peticiones, 2 duplicadas, cascada 5 niveles, 17.2 KB de API, 2.1 s

Duplicadas:
- `GET /me` ×2
- `GET /me/legal-acceptances/pending` ×2

En cascada (pide esperar a otra respuesta):
- nivel 2: `GET /me` (después de `POST /auth/refresh`)
- nivel 3: `GET /me/legal-acceptances/pending` (después de `GET /me`)
- nivel 3: `GET /me/modules` (después de `GET /me`)
- nivel 4: `GET /me/notifications/unread-count` (después de `GET /me/legal-acceptances/pending`)
- nivel 4: `GET /me` (después de `GET /me/legal-acceptances/pending`)
- nivel 4: `GET /me/legal-acceptances/pending` (después de `GET /me/legal-acceptances/pending`)
- nivel 4: `GET /programs?status=ACTIVE` (después de `GET /me/legal-acceptances/pending`)
- nivel 4: `GET /courses?status=ACTIVE` (después de `GET /me/legal-acceptances/pending`)
- nivel 5: `GET /academic-offering-cycles?programId=9` (después de `GET /me/notifications/unread-count`)
- nivel 5: `GET /academic-offering-cycles?programId=3` (después de `GET /me/notifications/unread-count`)
- nivel 5: `GET /academic-offering-cycles?programId=4` (después de `GET /me/notifications/unread-count`)
- nivel 5: `GET /academic-offering-cycles?programId=5` (después de `GET /me/notifications/unread-count`)
- … y 5 más

Respuestas más grandes: `GET /courses?status=ACTIVE` 1.4 KB, `GET /programs?status=ACTIVE` 1.3 KB, `POST /auth/refresh` 1.3 KB.

### 3. Alumno `/alumno/pagos` — 14 peticiones, 2 duplicadas, cascada 5 niveles, 13.2 KB de API, 4.3 s

Duplicadas:
- `GET /me` ×2
- `GET /me/legal-acceptances/pending` ×2

En cascada (pide esperar a otra respuesta):
- nivel 2: `GET /me` (después de `POST /auth/refresh`)
- nivel 3: `GET /me/legal-acceptances/pending` (después de `GET /me`)
- nivel 3: `GET /me/modules` (después de `GET /me`)
- nivel 4: `GET /me/notifications/unread-count` (después de `GET /me/legal-acceptances/pending`)
- nivel 4: `GET /me` (después de `GET /me/legal-acceptances/pending`)
- nivel 4: `GET /me/legal-acceptances/pending` (después de `GET /me/legal-acceptances/pending`)
- nivel 4: `GET /me/program-enrollment-applications` (después de `GET /me/legal-acceptances/pending`)
- nivel 4: `GET /me/credit-balance` (después de `GET /me/legal-acceptances/pending`)
- nivel 4: `GET /me/course-enrollments` (después de `GET /me/legal-acceptances/pending`)
- nivel 4: `GET /me/program-enrollments` (después de `GET /me/legal-acceptances/pending`)
- nivel 4: `GET /me/enrollment-payment-status` (después de `GET /me/legal-acceptances/pending`)
- nivel 5: `GET /me/program-enrollments/1/annual-upfront/quote` (después de `GET /me/notifications/unread-count`)
- … y 1 más

Respuestas más grandes: `POST /auth/refresh` 1.3 KB, `GET /me` 1.1 KB, `GET /me` 1.1 KB.

### 4. Alumno `/alumno/mis-carreras` — 12 peticiones, 2 duplicadas, cascada 6 niveles, 13.0 KB de API, 5.1 s

Duplicadas:
- `GET /me` ×2
- `GET /me/legal-acceptances/pending` ×2

En cascada (pide esperar a otra respuesta):
- nivel 2: `GET /me` (después de `POST /auth/refresh`)
- nivel 3: `GET /me/legal-acceptances/pending` (después de `GET /me`)
- nivel 3: `GET /me/modules` (después de `GET /me`)
- nivel 4: `GET /me/notifications/unread-count` (después de `GET /me/legal-acceptances/pending`)
- nivel 4: `GET /me` (después de `GET /me/legal-acceptances/pending`)
- nivel 4: `GET /me/legal-acceptances/pending` (después de `GET /me/legal-acceptances/pending`)
- nivel 5: `GET /me/program-enrollments` (después de `GET /me/notifications/unread-count`)
- nivel 5: `GET /me/section-enrollments` (después de `GET /me/notifications/unread-count`)
- nivel 6: `GET /me/program-enrollments/1/progress` (después de `GET /me/program-enrollments`)
- nivel 6: `GET /me/regular-certificates/eligibility?kind=PROGRAM&enrollmentId=1` (después de `GET /me/program-enrollments`)
- nivel 6: `GET /programs/1/subjects` (después de `GET /me/program-enrollments`)

Respuestas más grandes: `GET /programs/1/subjects` 1.7 KB, `POST /auth/refresh` 1.3 KB, `GET /me/section-enrollments` 1.2 KB.

### 5. Director `/director/aprobaciones` — 12 peticiones, 2 duplicadas, cascada 4 niveles, 11.3 KB de API, 1.9 s

Duplicadas:
- `GET /me` ×2
- `GET /me/legal-acceptances/pending` ×2

En cascada (pide esperar a otra respuesta):
- nivel 2: `GET /me` (después de `POST /auth/refresh`)
- nivel 3: `GET /me/legal-acceptances/pending` (después de `GET /me`)
- nivel 3: `GET /me/modules` (después de `GET /me`)
- nivel 4: `GET /me/notifications/unread-count` (después de `GET /me/legal-acceptances/pending`)
- nivel 4: `GET /me` (después de `GET /me/legal-acceptances/pending`)
- nivel 4: `GET /me/legal-acceptances/pending` (después de `GET /me/legal-acceptances/pending`)
- nivel 4: `GET /section-evaluation-configs?status=PENDING_APPROVAL` (después de `GET /me/legal-acceptances/pending`)
- nivel 4: `GET /graduation-requests?status=ADMIN_VERIFIED` (después de `GET /me/legal-acceptances/pending`)
- nivel 4: `GET /director-approval-requests` (después de `GET /me/legal-acceptances/pending`)
- nivel 4: `GET /authorization-requests?status=PENDING&sort=EXPIRES_ON&page=0&size=50` (después de `GET /me/legal-acceptances/pending`)
- nivel 4: `GET /academic-offering-cycles?status=PENDING_APPROVAL` (después de `GET /me/legal-acceptances/pending`)

Respuestas más grandes: `POST /auth/refresh` 1.3 KB, `GET /me` 1.1 KB, `GET /me` 1.1 KB.

### 6. Alumno `/alumno/inicio` — 12 peticiones, 2 duplicadas, cascada 5 niveles, 11.2 KB de API, 2.7 s

Duplicadas:
- `GET /me` ×2
- `GET /me/legal-acceptances/pending` ×2

En cascada (pide esperar a otra respuesta):
- nivel 2: `GET /me` (después de `POST /auth/refresh`)
- nivel 3: `GET /me/legal-acceptances/pending` (después de `GET /me`)
- nivel 3: `GET /me/modules` (después de `GET /me`)
- nivel 4: `GET /me/notifications/unread-count` (después de `GET /me/legal-acceptances/pending`)
- nivel 4: `GET /me` (después de `GET /me/legal-acceptances/pending`)
- nivel 4: `GET /me/legal-acceptances/pending` (después de `GET /me/legal-acceptances/pending`)
- nivel 5: `GET /me/program-enrollment-applications` (después de `GET /me/notifications/unread-count`)
- nivel 5: `GET /me/schedule` (después de `GET /me/notifications/unread-count`)
- nivel 5: `GET /me/enrollment-payment-status` (después de `GET /me/notifications/unread-count`)
- nivel 5: `GET /me/course-enrollments` (después de `GET /me/notifications/unread-count`)
- nivel 5: `GET /me/announcements` (después de `GET /me/notifications/unread-count`)

Respuestas más grandes: `POST /auth/refresh` 1.3 KB, `GET /me` 1.1 KB, `GET /me` 1.1 KB.

### 7. Administrativo `/administrativo/descuentos` — 11 peticiones, 2 duplicadas, cascada 4 niveles, 12.4 KB de API, 2.0 s

Duplicadas:
- `GET /me` ×2
- `GET /me/legal-acceptances/pending` ×2

En cascada (pide esperar a otra respuesta):
- nivel 2: `GET /me` (después de `POST /auth/refresh`)
- nivel 3: `GET /me/legal-acceptances/pending` (después de `GET /me`)
- nivel 3: `GET /me/modules` (después de `GET /me`)
- nivel 4: `GET /me/notifications/unread-count` (después de `GET /me/legal-acceptances/pending`)
- nivel 4: `GET /me` (después de `GET /me/legal-acceptances/pending`)
- nivel 4: `GET /me/legal-acceptances/pending` (después de `GET /me/legal-acceptances/pending`)
- nivel 4: `GET /authorization-requests?status=PENDING&sort=EXPIRES_ON&page=0&size=1` (después de `GET /me/legal-acceptances/pending`)
- nivel 4: `GET /discount-definitions` (después de `GET /me/legal-acceptances/pending`)
- nivel 4: `GET /programs` (después de `GET /me/legal-acceptances/pending`)
- nivel 4: `GET /courses` (después de `GET /me/legal-acceptances/pending`)

Respuestas más grandes: `GET /discount-definitions` 1.5 KB, `GET /courses` 1.4 KB, `GET /programs` 1.3 KB.

### 8. Director `/director/descuentos` — 11 peticiones, 2 duplicadas, cascada 4 niveles, 12.3 KB de API, 1.6 s

Duplicadas:
- `GET /me` ×2
- `GET /me/legal-acceptances/pending` ×2

En cascada (pide esperar a otra respuesta):
- nivel 2: `GET /me` (después de `POST /auth/refresh`)
- nivel 3: `GET /me/legal-acceptances/pending` (después de `GET /me`)
- nivel 3: `GET /me/modules` (después de `GET /me`)
- nivel 4: `GET /me/notifications/unread-count` (después de `GET /me/legal-acceptances/pending`)
- nivel 4: `GET /me` (después de `GET /me/legal-acceptances/pending`)
- nivel 4: `GET /me/legal-acceptances/pending` (después de `GET /me/legal-acceptances/pending`)
- nivel 4: `GET /authorization-requests?status=PENDING&sort=EXPIRES_ON&page=0&size=1` (después de `GET /me/legal-acceptances/pending`)
- nivel 4: `GET /discount-definitions` (después de `GET /me/legal-acceptances/pending`)
- nivel 4: `GET /programs` (después de `GET /me/legal-acceptances/pending`)
- nivel 4: `GET /courses` (después de `GET /me/legal-acceptances/pending`)

Respuestas más grandes: `GET /discount-definitions` 1.5 KB, `GET /courses` 1.4 KB, `GET /programs` 1.3 KB.

### 9. Administrativo `/administrativo/egresos-por-verificar` — 11 peticiones, 2 duplicadas, cascada 4 niveles, 10.7 KB de API, 2.0 s

Duplicadas:
- `GET /me` ×2
- `GET /me/legal-acceptances/pending` ×2

En cascada (pide esperar a otra respuesta):
- nivel 2: `GET /me` (después de `POST /auth/refresh`)
- nivel 3: `GET /me/legal-acceptances/pending` (después de `GET /me`)
- nivel 3: `GET /me/modules` (después de `GET /me`)
- nivel 4: `GET /me/notifications/unread-count` (después de `GET /me/legal-acceptances/pending`)
- nivel 4: `GET /me` (después de `GET /me/legal-acceptances/pending`)
- nivel 4: `GET /me/legal-acceptances/pending` (después de `GET /me/legal-acceptances/pending`)
- nivel 4: `GET /graduation-requests?status=REQUESTED` (después de `GET /me/legal-acceptances/pending`)
- nivel 4: `GET /graduation-requests?status=ADMIN_VERIFIED` (después de `GET /me/legal-acceptances/pending`)
- nivel 4: `GET /graduation-requests?status=APPROVED` (después de `GET /me/legal-acceptances/pending`)
- nivel 4: `GET /graduation-requests?status=REJECTED` (después de `GET /me/legal-acceptances/pending`)

Respuestas más grandes: `POST /auth/refresh` 1.3 KB, `GET /me` 1.1 KB, `GET /me` 1.1 KB.

### 10. Administrativo `/administrativo/precios` — 10 peticiones, 2 duplicadas, cascada 4 niveles, 11.8 KB de API, 1.6 s

Duplicadas:
- `GET /me` ×2
- `GET /me/legal-acceptances/pending` ×2

En cascada (pide esperar a otra respuesta):
- nivel 2: `GET /me` (después de `POST /auth/refresh`)
- nivel 3: `GET /me/legal-acceptances/pending` (después de `GET /me`)
- nivel 3: `GET /me/modules` (después de `GET /me`)
- nivel 4: `GET /me/notifications/unread-count` (después de `GET /me/legal-acceptances/pending`)
- nivel 4: `GET /me` (después de `GET /me/legal-acceptances/pending`)
- nivel 4: `GET /me/legal-acceptances/pending` (después de `GET /me/legal-acceptances/pending`)
- nivel 4: `GET /prices` (después de `GET /me/legal-acceptances/pending`)
- nivel 4: `GET /programs` (después de `GET /me/legal-acceptances/pending`)
- nivel 4: `GET /courses` (después de `GET /me/legal-acceptances/pending`)

Respuestas más grandes: `GET /prices` 1.8 KB, `GET /courses` 1.4 KB, `GET /programs` 1.3 KB.

## Todas las pantallas

| Rol | Pantalla | Peticiones | Duplicadas | Niveles de cascada | Bytes API (KB) | Estáticos (n / KB) | Errores HTTP | Contenido útil (s) | Pantalla completa (s) | Nota |
|---|---|---|---|---|---|---|---|---|---|---|
| Administrativo | /administrativo/alumnos | 8 | 2 | 4 | 8.9 | 63 / 100.5 | 0 | 1.7 | 1.6 |  |
| Administrativo | /administrativo/aprobaciones | 8 | 2 | 4 | 8.1 | 68 / 100.5 | 0 | 1.6 | 1.5 |  |
| Administrativo | /administrativo/cierres-de-comision | 9 | 2 | 4 | 9.6 | 65 / 103.7 | 0 | 2.5 | 2.4 |  |
| Administrativo | /administrativo/constancias | 9 | 2 | 4 | 8.9 | 64 / 100.5 | 0 | 1.9 | 1.9 |  |
| Administrativo | /administrativo/corridas-de-facturacion | 8 | 2 | 4 | 8.1 | 65 / 100.5 | 0 | 1.8 | 1.8 |  |
| Administrativo | /administrativo/descuentos | 11 | 2 | 4 | 12.4 | 74 / 103.7 | 0 | 2.0 | 2.0 |  |
| Administrativo | /administrativo/egresos-por-verificar | 11 | 2 | 4 | 10.7 | 67 / 100.5 | 0 | 2.0 | 2.0 |  |
| Administrativo | /administrativo/facturacion | 10 | 2 | 5 | 10.8 | 89 / 100.5 | 0 | 1.9 | 2.2 |  |
| Administrativo | /administrativo/feriados | 9 | 2 | 4 | 10.1 | 62 / 516.0 | 0 | 3.9 | 3.9 |  |
| Administrativo | /administrativo/gestion-academica | 8 | 2 | 4 | 10.3 | 67 / 100.5 | 0 | 1.6 | 1.6 |  |
| Administrativo | /administrativo/inicio | 8 | 2 | 5 | 8.9 | 63 / 100.5 | 0 | 1.5 | 2.3 | redirige a /administrativo/alumnos |
| Administrativo | /administrativo/listas-de-espera | 8 | 2 | 4 | 8.8 | 64 / 100.5 | 0 | 1.4 | 1.4 |  |
| Administrativo | /administrativo/mesas-de-examen | 8 | 2 | 4 | 8.4 | 65 / 100.5 | 0 | 1.3 | 1.3 |  |
| Administrativo | /administrativo/periodos | 8 | 2 | 4 | 8.6 | 63 / 100.5 | 0 | 1.4 | 1.4 |  |
| Administrativo | /administrativo/personal | 8 | 2 | 4 | 9.4 | 63 / 100.5 | 0 | 1.7 | 1.6 |  |
| Administrativo | /administrativo/precios | 10 | 2 | 4 | 11.8 | 65 / 100.5 | 0 | 1.6 | 1.6 |  |
| Administrativo | /administrativo/solicitudes-de-asistencia | 9 | 2 | 4 | 9.2 | 63 / 100.5 | 0 | 2.2 | 2.2 |  |
| Administrativo | /administrativo/solicitudes-de-ingreso | 8 | 2 | 4 | 8.1 | 67 / 100.5 | 0 | 1.5 | 1.5 |  |
| Alumno | /alumno/inicio | 12 | 2 | 5 | 11.2 | 83 / 100.5 | 0 | 2.4 | 2.7 |  |
| Alumno | /alumno/mis-carreras | 12 | 2 | 6 | 13.0 | 74 / 737.1 | 0 | 4.4 | 5.1 |  |
| Alumno | /alumno/mis-cursos | 8 | 2 | 4 | 8.0 | 63 / 516.0 | 0 | 3.6 | 3.7 |  |
| Alumno | /alumno/pagos | 14 | 2 | 5 | 13.2 | 83 / 519.1 | 0 | 3.9 | 4.3 |  |
| Alumno | /alumno/pagos/estado | 7 | 2 | 4 | 7.2 | 61 / 516.0 | 0 | 3.6 | 3.7 |  |
| Alumno | /alumno/pagos/notas-de-credito | 8 | 2 | 4 | 8.0 | 67 / 516.0 | 0 | 3.6 | 3.6 |  |
| Director | /director/ajustes-del-instituto | 8 | 2 | 4 | 8.1 | 64 / 100.5 | 0 | 1.7 | 1.7 |  |
| Director | /director/alumnos | 8 | 2 | 4 | 8.8 | 63 / 100.5 | 0 | 1.7 | 1.6 |  |
| Director | /director/aprobaciones | 12 | 2 | 4 | 11.3 | 87 / 100.5 | 0 | 1.6 | 1.9 |  |
| Director | /director/cierres-de-comision | 9 | 2 | 4 | 9.5 | 65 / 516.0 | 0 | 4.0 | 4.0 |  |
| Director | /director/constancias | 9 | 2 | 4 | 8.8 | 64 / 100.5 | 0 | 1.5 | 1.4 |  |
| Director | /director/corridas-de-facturacion | 8 | 2 | 5 | 8.0 | 65 / 709.6 | 0 | 4.3 | 4.6 |  |
| Director | /director/descuentos | 11 | 2 | 4 | 12.3 | 74 / 100.5 | 0 | 1.6 | 1.6 |  |
| Director | /director/esquemas-evaluacion | 8 | 2 | 4 | 8.5 | 61 / 100.5 | 0 | 1.5 | 1.5 |  |
| Director | /director/facturacion | 10 | 2 | 4 | 10.8 | 89 / 100.5 | 0 | 1.6 | 1.5 |  |
| Director | /director/financiero | 19 | 3 | 6 | 22.7 | 68 / 103.7 | 0 | 1.9 | 3.2 |  |
| Director | /director/financiero/anios-lectivos | 8 | 2 | 5 | 8.5 | 64 / 100.5 | 0 | 1.5 | 1.9 |  |
| Director | /director/gestion-academica | 8 | 2 | 5 | 8.0 | 63 / 103.7 | 0 | 1.5 | 1.7 |  |
| Director | /director/importacion-inicial | 8 | 2 | 5 | 8.0 | 65 / 707.9 | 0 | 4.4 | 4.6 |  |
| Director | /director/inicio | 8 | 2 | 5 | 8.8 | 63 / 100.5 | 0 | 1.7 | 2.3 | redirige a /director/alumnos |
| Director | /director/listas-de-espera | 8 | 2 | 5 | 8.8 | 64 / 706.2 | 0 | 4.6 | 4.9 |  |
| Director | /director/mesas-de-examen | 8 | 2 | 4 | 8.4 | 65 / 100.5 | 0 | 1.5 | 1.6 |  |
| Director | /director/oferta-academica | 18 | 2 | 5 | 17.2 | 62 / 100.5 | 0 | 1.7 | 2.1 |  |
| Director | /director/oferta-academica/calendario | 8 | 2 | 4 | 8.6 | 64 / 100.5 | 0 | 1.7 | 1.7 |  |
| Director | /director/periodos | 8 | 2 | 4 | 8.6 | 63 / 100.5 | 0 | 1.9 | 1.9 |  |
| Director | /director/personal | 8 | 2 | 4 | 9.4 | 63 / 100.5 | 0 | 2.1 | 2.1 |  |
| Director | /director/precios | 10 | 2 | 4 | 11.8 | 65 / 103.7 | 0 | 1.5 | 1.7 |  |
| Docente | /docente/inicio | 9 | 2 | 5 | 9.1 | 60 / 100.5 | 0 | 1.7 | 2.4 | redirige a /docente/mis-comisiones |
| Docente | /docente/mesas-de-examen | 8 | 2 | 4 | 8.0 | 64 / 516.0 | 0 | 3.7 | 3.7 |  |
| Docente | /docente/mis-comisiones | 9 | 2 | 5 | 9.1 | 60 / 100.5 | 0 | 2.7 | 2.8 |  |
| Docente | /docente/tomar-asistencia | 9 | 2 | 4 | 9.1 | 65 / 100.5 | 0 | 2.0 | 2.2 |  |

## Acciones principales (lectura segura): clic → resultado visible

| Rol | Acción | Medida | Clic → resultado visible (s) | Peticiones | KB API |
|---|---|---|---|---|---|
| Director | Alumnos: abrir primer alumno | no medible: tope de 45 s, queda una petición en vuelo que no termina (bug de la medición) | - | 5 | 4.5 |
| Director | Oferta académica: abrir primera carrera | no medible: tope de 45 s, queda una petición en vuelo que no termina (bug de la medición) | - | 5 | 4.3 |
| Director | Aprobaciones: abrir bandeja de facturación | no medible: tope de 45 s, queda una petición en vuelo que no termina (bug de la medición) | - | 4 | 3.2 |
| Administrativo | Alumnos: abrir primer alumno | no medible: tope de 45 s, queda una petición en vuelo que no termina (bug de la medición) | - | 5 | 4.5 |
| Administrativo | Facturación: pestaña Estado de cuenta | sí | 0.1 | 0 | 0.0 |
| Docente | Mis comisiones: abrir primera comisión | no medible: tope de 45 s, queda una petición en vuelo que no termina (bug de la medición) | - | 7 | 5.7 |
| Alumno | Mis carreras: pestaña Notas | no medible: tope de 45 s, queda una petición en vuelo que no termina (bug de la medición) | - | 7 | 6.0 |

## No medido

- Pantallas con parámetros (detalles por id), asistentes de varios pasos y acciones que modifican datos.
- Tiempo clic → resultado de las acciones: 6 de 7 llegaron al tope de 45 s por una petición que la medición ve en vuelo siempre; solo se informan sus peticiones y bytes. Pendiente de corregir en la próxima subtarea.
- Rol Preceptor y Admin de plataforma.
- Rendimiento en dispositivo real: la CPU x4 es una emulación de Chromium, no un teléfono.
- Director `/director/inicio`: redirigió a `/director/alumnos` (sin permiso o sin datos), excluida del ranking.
- Administrativo `/administrativo/inicio`: redirigió a `/administrativo/alumnos` (sin permiso o sin datos), excluida del ranking.
- Docente `/docente/inicio`: redirigió a `/docente/mis-comisiones` (sin permiso o sin datos), excluida del ranking.
