# Progreso E24 F6-front (frontend-1): Sentry en el front, inerte por defecto

Ticket: #1147. Rama: `feature/f6-front-sentry`.

## Criterios de aceptación
- [pasa] Sin `VITE_SENTRY_DSN` el SDK no se importa ni se hace ningún fetch (test con spy sobre `fetch`, `sendBeacon` y el import del módulo).
- [pasa] Sin DSN el build no emite ningún chunk ni referencia a `@sentry` en `dist/` (verificado con `npm run build:check`).
- [pasa] Con DSN, el reporter de `reportClientError` envía los errores al SDK (`captureException`).
- [pasa] `beforeSend` descarta request, url, query, headers, user, extra, contexts, tags y breadcrumbs; enmascara email, DNI, JWT, tokens en URLs y trunca a 2000 caracteres (tests).
- [pasa] `beforeBreadcrumb` devuelve siempre null; `defaultIntegrations: false`, `sendDefaultPii: false`, `tracesSampleRate: 0`.
- [pasa] Sabotaje: quitar el scrubber de `beforeSend` hace fallar los tests de enmascarado.
- [pasa] `ErrorBoundary.test.jsx` sigue verde.
- [pasa] `window.onerror` agregado como listener de `error` (no duplica: React 19 reporta los errores de boundary por `componentDidCatch`).
- [pasa] SDK fijado exacto `@sentry/react@11.11.0` (publicado 2026-11-05, más de 2 semanas antes del corte). `npm audit --omit=dev --audit-level=high`: 0 vulnerabilidades.
- [pasa] `VITE_SENTRY_DSN` documentado en `deploy/.env.example` y `frontend/.env.example`; ARG/ENV en `frontend/Dockerfile` y build arg en `docker-compose.prod.yml`. `check-env-example.py` al día.
- [pasa] Service worker: `src/sw.js` solo cachea `/assets/` del mismo origen y la navegación; no intercepta el ingest cross-origin.
- [pendiente] Tráfico real contra Sentry: bloqueado por la CSP (connect-src) hasta F6-csp, decisión de Gabriel. CSP no tocada (Caddyfile, index.html, csp.js).
- [pendiente] Configuración del proyecto Sentry del lado servidor: desactivar almacenamiento de IP y data scrubbing avanzado.

## Medidas de bundle (KiB)
- Línea base (develop): entry `index-*.js` 342,1 KiB (350.321 bytes); mayor chunk `recharts` 366,9 KiB.
- Sin DSN: entry 342,2 KiB (350.410 bytes, +89 bytes). Sin chunk de Sentry.
- Con DSN dummy: entry 343,9 KiB (352.177 bytes); chunk `esm-*.js` con el SDK 442,7 KiB (453.325 bytes), gzip aprox. 146,6 KiB. Por debajo del tope de 600 KiB. Se carga solo con DSN.

## Notas de progreso
- Hecho: wrapper `lib/monitoring/` (mask, scrub, opciones, carga dinámica, arranque), cableado en `main.jsx` y `vite.config.js` (define de `VITE_SENTRY_DSN`), tests.
- Decisión técnica: el guard `if (import.meta.env.VITE_SENTRY_DSN)` está en `main.jsx`, no solo dentro de la función; así el `import()` se elimina del build cuando el DSN es vacío.
- Desvío: el chunk del SDK pesa 442,7 KiB porque `import('@sentry/react')` trae `@sentry/browser` completo. Alternativa a evaluar: importar solo `@sentry/browser` (init y captureException).
- Desvío: se descartan `tags` y `extra`, así que el `scope` de `ErrorBoundary` y el `componentStack` no llegan a Sentry. Pendiente de decisión si se quiere conservar un `scope` fijo.
- Desvío: `requestId` (de `ApiError`) tampoco se envía.
- Riesgo residual: nombres propios dentro del texto del error no se enmascaran (solo email, DNI, JWT, tokens). Limitación conocida.
- Entorno: `environment` = `import.meta.env.MODE` (en Docker siempre `production`); no se agregó `VITE_APP_ENV`.
- Bloqueo: hasta F6-csp, un DSN configurado queda bloqueado por la CSP. Con DSN vacío no hay tráfico.

## Revisión de privacidad (ronda 2)
- Enmascarado: el texto se trunca a 2000 caracteres antes y después de enmascarar; cuantificadores acotados (email, JWT, tokens); unicode en email (`\p{L}\p{N}`). Tests de 200 KB sin espacios en tiempo acotado (<500 ms).
- Enmascara: todo query string (también relativo), email, DNI/CUIL/CUIT con separadores (7 a 11 dígitos), teléfonos argentinos, tokens opacos de 32+ caracteres, ids numéricos en path.
- `ApiError`: se envía solo `status` y `code` (el mensaje libre no sale; puede traer nombres). Se toma `reportClientError` con `status` y `code`.
- Tags allowlist: `scope` (enmascarado) y `request_id` solo si es UUID. Se descartan contexts, user, request, breadcrumbs, extra y el resto de tags.
- Frames: `filename` sin query ni fragmento.
- `sendClientReports: false`. El listener `window` `error` se registra solo con DSN (`main.jsx`), así que sin DSN no cambia el comportamiento.
- Guard: `if (import.meta.env.VITE_SENTRY_DSN)` en `main.jsx`. Se garantiza por medición del build (sin DSN no hay chunk ni referencia a `@sentry` en `dist/`), no por test unitario: `main.jsx` renderiza la app completa.
- Test con el cliente real del SDK (`sentryClientEnvelope.test.js`): init con transport falso y evento serializado completo que pasa por `beforeSend`.
- Sabotajes verificados: regex de email (4 tests fallan) y allowlist de `contexts` (2 tests fallan). Restaurado.

## Requisitos previos a poner un DSN (en el proyecto GlitchTip/Sentry, antes de configurar VITE_SENTRY_DSN)
- Desactivar almacenamiento de IP.
- No reenviar `X-Forwarded-For` desde el proxy al ingest.
- Activar data scrubbing avanzado con los patrones de email, DNI y tokens.
- Mantener `sendDefaultPii` en false (ya está en el código).

## Deuda aparte (no se toca en este PR)
- 7 archivos de otros dominios flakean bajo carga en la suite completa y pasan aislados: `DirectorOfertaCicloDetalle`, `FinancialDashboardPage`, `AdministrativoFacturacion`, `DetalleSolicitud`, `SolicitudesDeIngreso`, `StaffGraduationCertificates`, `PermissionsPage`.

## Medidas finales (KiB)
- Sin DSN: entry 343,3 KiB (351.509 bytes); cero referencias a `@sentry` en `dist/`.
- Con DSN dummy: entry 345,9 KiB (354.210 bytes); chunk del SDK 442,7 KiB (453.325 bytes).
- Decisiones: se mantiene `@sentry/react` (solo se carga con DSN). Evaluar `@sentry/browser` en F6-csp con medición. `environment` = `import.meta.env.MODE`.
