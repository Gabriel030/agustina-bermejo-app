# Handoff: piloto por capas de #1103 (M1-b, T1d y D1c)

Cierre del piloto el 25-nov-2026. Plan: [../plans/1103-piloto.md](../plans/1103-piloto.md). Resumen de lo entregado: [../plans/1103.md](../plans/1103.md). Issue: #1103.

## Tiempos
Arranque: 25-nov 11:13 UTC (08:13 Buenos Aires), ola 1 lanzada; el plan se mergeó en #1490 a las 11:21.

| Subtarea | PR | Mergeado (UTC, 25-nov) | Desde el arranque |
|---|---|---|---|
| P4 puerto de nota y fecha | #1493 | 17:26 | 6 h 13 min |
| P2 gates de M1 | #1507 | 18:04 | 6 h 51 min |
| P3 marcadores extra | #1492 | cerrado sin merge; incorporado a develop por #1512 (18:25) | 7 h 12 min |
| P9 ruta genérica de render | #1512 | 18:25 | 7 h 12 min |
| P5 use cases de M1 | #1513 | 18:26 | 7 h 13 min |
| P6 emisor de M1 | #1514 | 18:50 | 7 h 37 min |
| P0 contrato `studentName` nullable | #1508 | 18:50 | 7 h 37 min |
| P1 V471 `template_version_id` | #1509 | 18:50 | 7 h 37 min |
| P10 R1 y N1 con plantilla publicada | #1515 | 19:03 | 7 h 50 min |
| P7 descarga, revocación, vencimiento y verificación | #1518 | 19:21 | 8 h 08 min |
| P11 X1 y M1 con plantilla publicada | #1519 | 19:22 | 8 h 09 min |
| P12 adaptadores R1 y N1 de excepción por deuda | #1516 | 19:35 | 8 h 22 min |
| P8 cableado de M1 en trámites | #1517 | 19:51 | 8 h 38 min |
| P14 IT de punta a punta de M1 | #1520 | 19:59 | 8 h 46 min |
| P13 adaptadores M1 y X1 de excepción por deuda | #1521 | 20:02 | 8 h 49 min |
| P15 ITs de cierre y docs | este PR | ver `merged_at` del PR | |

El plan estimaba la ola 1 en paralelo y el resto apilado; en la práctica casi todo mergeó entre las 18:04 y las 20:02, porque las olas 1 y 2 quedaron frenadas por las denegaciones de la primera hora (abajo).

## Choques y denegaciones del clasificador
- 11:09, plan: el clasificador denegó commit y push del líder; el plan salió por el PR #1490 con la autorización de Gabriel.
- 11:14 a 11:25, P0, P1 y P2: denegado el primer commit y push de cada rama. Los tres workers siguieron escribiendo sin commitear; Gabriel autorizó a las 16:53 y recién entonces hubo ramas y PRs en borrador (#1507, #1509). Fue la pérdida de tiempo mayor del piloto, unas 5 h 40 min en esas tres subtareas.
- 17:03, P3: denegado actualizar el cuerpo del PR, comentar, pasar a listo y activar auto-merge. #1492 quedó sin mergear y su contenido entró por P9.
- 17:07, P2 (segundo tramo) y P4: denegado el commit y push de P2 y el auto-merge de P4. P4 lo mergeó Gabriel (17:26); P2 se mergeó directo con su autorización explícita (18:04), porque el auto-merge devolvió 422 (PR ya clean).
- 17:32, P1: denegado el commit del arreglo del test (datos inválidos en el test de migración).
- 18:25, P5: PR limpio, auto-merge 422 y merge directo denegado.
- 18:49 a 19:51, P6, P7, P8, P10, P12 y P14: denegado el auto-merge o el merge directo; los PRs con CI verde los mergeó coordinador-5 o Gabriel, sin rodear el clasificador.
- 19:35, P13: denegado commit y push; Gabriel lo aprobó a las 19:47 (#1521) y el merge directo volvió a ser denegado; se mergeó a las 20:02.
- Regla general: nunca se rodeó una denegación; cada una se comentó como BLOQUEADO en #1103.

Choques técnicos:
- P2 y `procedure`: `ProcedureIneligibilityCode` vive en `graduation.procedure.dto` y habría cerrado un ciclo entre slices; se resolvió con enum local de bloqueos en `approvedsubjects` (decisión A).
- P5 y P6: los servicios de P5 piden 8 puertos que nadie implementaba hasta P6, y el contexto de Spring no arrancaba (CI roja en develop); P5 dejó los servicios sin `@UseCase` y el flip lo hicieron P6 a P8.
- P14: CI roja por `CERTIFICATE_PDF_INTEGRITY_MISMATCH` (409) en la descarga tras pedir la constancia; no era del IT sino de P6/P7/P8 y se resolvió al mergear P8.
- Cambio de regla a las 18:04: los tests pesados los corre la CI; desde entonces no hizo falta `GO MAVEN`.

## Qué queda
1. Retiro del endpoint legacy `GET /me/program-enrollments/{id}/certificates/approved-subjects` y de `ProgramCertificateIssuer.issueApprovedSubjectsCertificate` (Q4 = A): PR aparte, después de confirmar que el front no los usa; implica ruptura de contrato (`contract-break-approved`).
2. Decisiones abiertas de P2: enum local de bloqueos y código «sin materias aprobadas».
3. Producto: R1 y N1 emiten con el diseño publicado en todo entorno desde V462; R1 no imprime fecha de vigencia. Confirmar con el Director.
4. Verificar en la CI de este PR los tres tests nuevos de M1 con plantilla publicada (`pdfOfOwn` con versión publicada): la nota de P13 decía que `pdfOfOwn` de M1 regenera sin assets de plantilla publicada; si el hash no coincide, es un bug de P7/P11 y queda como BLOQUEADO en #1103.
