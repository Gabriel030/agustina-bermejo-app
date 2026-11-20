# Seguridad de producción: diagnóstico y planes (Épica 29, bloque G)

> Verificado: 2026-11-14 contra la rama base de este PR (`release: 18`, migraciones hasta V230 en este árbol). Alcance: diagnóstico de G2 a G5 y endurecimiento de bajo riesgo. Los planes de las secciones 1 y 2 **no están implementados**: esperan aprobación.

## 0. Resumen

| Tema | Estado hoy | Qué hace este PR | Qué queda (aprobación de Gabriel) |
|---|---|---|---|
| a) Tokens y sesión | Access JWT en memoria; refresh de 7 días en `localStorage` | Nada | Plan G2 (sección 1) |
| b) CSP | Cabecera en Caddy, estricta salvo `style-src 'unsafe-inline'` | Test que la fija; `X-Frame-Options: DENY` | Plan G3 (sección 2): hace falta nonce, no alcanza con quitar la palabra |
| c) Auditoría | Parcial, por dominio; sin tabla general | Nada | Plan G4 (sección 3) |
| d) Matriz de autorización | Completa para los endpoints de controllers | Nada | Huecos chicos listados (sección 4) |
| Límites de login | Ya existen (rate limit por IP y bloqueo de cuenta) | Nada | Sin cambios |

## 1. Tokens y sesión (G2)

### Estado real

- Access token: JWT de 15 minutos (`jwt.access-token-expiration-minutes`), guardado **solo en memoria** (`frontend/src/api/client.js:63`, `setAccessToken`). Se manda como `Authorization: Bearer`.
- Refresh token: 7 días (`jwt.refresh-token-expiration-days`), **en `localStorage`** bajo la clave `refreshToken` (`frontend/src/context/AuthContext.jsx:26,33,49,61,111,188`; `frontend/src/api/client.js:150,161`). En base se guarda su hash SHA-256; la rotación es atómica (`RefreshTokenRotator`, `rotateIfActive`) con ventana de gracia ante reuso (`RefreshReuseGracePolicy`).
- Se envía en el **cuerpo JSON** de `POST /auth/refresh` (`RefreshRequest`) y `POST /auth/logout` (`LogoutRequest`); las respuestas de login, registro, Google y refresh devuelven ambos tokens en el cuerpo (`AuthResponse`).
- `localStorage.refreshToken` se usa además como **marca de sesión**: el arranque (`AuthContext.jsx:111`) decide si intenta restaurar y la caché offline (`offlinePersistence.js:49`) se borra si no está.
- CSRF está deshabilitado (`SecurityConfig.java:89`), válido porque no hay cookies de sesión. CORS ya tiene `allowCredentials(true)` y `Authorization, Content-Type, Idempotency-Key, If-Match` como cabeceras permitidas.
- Producción es de **mismo origen**: Caddy sirve el front y proxya `/api/*` quitando el prefijo (`frontend/Caddyfile`). No hay CORS en el camino real.
- Caché offline cifrada: la clave vive en IndexedDB por usuario (`createIdbKeyVault`, `offlinePersistence.js:26`) y **no depende del refresh token**; solo depende de él la marca de existencia.

Riesgo que se quiere cerrar: un XSS lee `localStorage.refreshToken` y obtiene una sesión de 7 días. Mitigantes actuales: CSP con `script-src` sin `unsafe-inline`, sin `dangerouslySetInnerHTML`, token de acceso solo en memoria.

### Plan de migración a cookie HttpOnly (no implementado)

Diseño objetivo: el refresh token sale del cuerpo y de `localStorage` y viaja en una cookie `refresh_token; HttpOnly; Secure; SameSite=Strict; Path=/api/auth; Max-Age=7d`. El access token sigue en memoria.

Detalle a decidir: Caddy le saca `/api` antes del backend, así que el backend ve `/auth/*` pero el navegador ve `/api/auth/*`. El `Path` de la cookie es el del navegador (`/api/auth`), y el backend lo emite con esa ruta configurable (`app.auth.refresh-cookie-path`). En desarrollo (front en 5173, API en 8080, orígenes distintos) `SameSite=Strict` con `Secure` falla en http: hace falta proxy de Vite a `/api` para igualar el origen, o `SameSite=Lax` solo en perfil local.

Pasos, en orden, cada uno desplegable por separado:

1. **Backend, compatible hacia atrás.** `/auth/login`, `/register`, `/google`, `/google/register` y `/auth/refresh` además de devolver el cuerpo actual emiten `Set-Cookie`. `/auth/refresh` y `/auth/logout` aceptan el token desde la cookie o, si falta, desde el cuerpo. `/auth/logout` borra la cookie (`Max-Age=0`). Tests: cookie con los cuatro atributos, rotación reemplaza la cookie, logout la borra, el cuerpo sigue funcionando.
2. **Contramedida CSRF en `/auth/refresh` y `/auth/logout`.** Con `SameSite=Strict` y mismo origen el riesgo es bajo, pero se agrega defensa en profundidad: exigir una cabecera propia (`X-Requested-With: fetch`) y rechazar `Origin` que no esté en `app.cors.allowed-origins`. El resto de la API sigue sin CSRF porque no usa cookie. Test de que un `Origin` ajeno recibe 403.
3. **Frontend.** `client.js` y `AuthContext.jsx` dejan de leer/escribir `localStorage.refreshToken`; los `fetch` a `/auth/*` usan `credentials: 'include'` y no mandan cuerpo con token. La marca de sesión pasa a una clave no secreta (`sessionHint=1`) solo para evitar un `/auth/refresh` inútil en visitantes anónimos y para la limpieza de la caché offline. Migración transparente: si hay `refreshToken` viejo en `localStorage`, se envía una última vez en el cuerpo (paso 1 lo acepta), la respuesta fija la cookie y se borra la clave.
4. **Contrato.** Quitar `refreshToken` de `AuthResponse`/`RefreshRequest`/`LogoutRequest` (ruptura de contrato: requiere el flujo `contract-break-approved` y regenerar `openapi`/`error-catalog`) una vez que no queden clientes viejos (la PWA puede tener versiones en caché hasta que el service worker actualice: esperar al menos 7 días de la versión del paso 3).
5. **Cierre.** Prueba de intrusión interna (G7) sobre el flujo y retiro del aceptador de cuerpo.

Riesgos y mitigaciones:

| Riesgo | Mitigación |
|---|---|
| Sesiones cerradas a todos al desplegar | Los pasos 1 y 3 son compatibles hacia atrás; la migración de la clave vieja es transparente |
| CSRF reintroducido en `/auth/refresh` y `/auth/logout` | `SameSite=Strict`, cabecera propia, chequeo de `Origin`, tests |
| Dev con orígenes distintos rompe la cookie | Proxy de Vite a `/api` o perfil local con `SameSite=Lax` sin `Secure` |
| Ruta de cookie desalineada por el prefijo `/api` de Caddy | `Path` configurable y test de integración con Caddy en el smoke de despliegue |
| PWA offline y service worker | El SW no maneja `/auth/*`; sin red no hay refresh en ningún esquema (hoy tampoco). La caché cifrada no depende del token. H3 (sesión vencida offline) queda igual |
| Detrás de varios proxies `Secure` se omite | Emitir `Secure` por configuración (`app.auth.refresh-cookie-secure=true` en producción) y test del perfil prod |
| XSS sigue pudiendo *usar* la sesión mientras la pestaña está abierta | Es inherente; la cookie solo evita el robo persistente. Se combina con G3 |
| Pestañas concurrentes y rotación | La ventana de gracia de reuso (`RefreshReuseGracePolicy`) y el `refreshInFlight` del cliente ya lo cubren; se agrega test con dos refresh simultáneos |

Tamaño estimado: 2 paquetes (backend + front), el segundo con migración transparente. Sin migración de base.

## 2. CSP (G3)

### Estado real

- Producción: Caddy envía la CSP completa en `frontend/Caddyfile` (bloque `security_headers`); el build de Docker usa `VITE_CSP_IN_HEADER=true`, que quita la `<meta>` de `index.html` y saca el script del tema a `theme-init.js` (`frontend/vite.config.js`, `frontend/csp.js`).
- Desarrollo y build sin Docker: `<meta http-equiv="Content-Security-Policy">` en `frontend/index.html` con el origen de la API como placeholder `__API_ORIGIN__`.
- Política actual (Caddy): `default-src 'self'`; `script-src 'self' https://accounts.google.com/gsi/client`; `style-src 'self' 'unsafe-inline' https://accounts.google.com/gsi/style`; `font-src 'self'`; `img-src 'self' data: blob:`; `connect-src 'self' https://accounts.google.com/gsi/`; `frame-src https://accounts.google.com/gsi/`; `manifest-src 'self'`; `worker-src 'self'`; `object-src 'none'`; `base-uri 'self'`; `form-action 'self'`; `frame-ancestors 'none'`.
- Conclusión: scripts ya están estrictos. **Lo único flojo es `'unsafe-inline'` en estilos.**

### Qué rompería quitar `unsafe-inline` de estilos

El front tiene 25 usos de `style={{...}}` en 16 archivos. Esos **no** son el problema: React aplica `style` con la API de DOM (`element.style.x = ...`), que la CSP no bloquea. Lo que sí rompe son los `<style>` y atributos `style` creados como texto:

- `react-style-singleton` (usado por `react-remove-scroll`, que usan los Dialog/Sheet/Dropdown de Radix): crea un `<style>` en runtime para bloquear el scroll. Su contenido cambia (compensación de ancho de scrollbar), así que **no se puede fijar con hash**; solo admite `nonce`.
- `sonner`: inyecta su CSS con un `<style>` en runtime (verificado por `createElement("style")` en `node_modules/sonner/dist/index.mjs`). Su contenido es fijo por versión (hash posible, frágil al actualizar).
- `https://accounts.google.com/gsi/style`: ya listado por URL, sin cambios.
- A verificar con el front real (Chrome, consola con la CSP puesta): `cmdk`, `motion`, `recharts` (en el análisis de `node_modules` no aparecieron `<style>` dinámicos, pero hay que confirmarlo en pantallas reales: Recharts animado, gráficos del dashboard, selects, calendarios).

### Plan (no implementado)

1. Pasar `style-src` a `'self' 'nonce-<n>' https://accounts.google.com/gsi/style`. El nonce debe ser **distinto por respuesta**: Caddy lo puede generar con la directiva `templates` sobre `index.html` (`{{uuid}}`) y escribir la misma cabecera y un `<meta name="csp-nonce" content="...">`. `index.html` hoy se sirve con `try_files` y `file_server`; pasa a `templates` solo para esa ruta y mantiene `Cache-Control: no-cache`.
2. En el arranque del front, leer el meta y llamar `setNonce` de `react-style-singleton` (y la opción equivalente de `sonner`, si existe en la versión instalada; si no, hash de su CSS o reemplazarlo por un toast propio).
3. Primero en modo **Report-Only** (`Content-Security-Policy-Report-Only`) durante una pasada manual por todas las pantallas (matriz de roles), leyendo `securitypolicyviolation` en consola; recién después se activa.
4. Si el service worker sirve `index.html` desde la caché de precarga, el nonce quedaría congelado: hay que excluir `index.html` de la precarga o servirlo network-first con nonce. **Este es el riesgo principal para la PWA.** Verificar `injectManifest` en `vite.config.js`.
5. Test: ya existe la guarda (`frontend/src/caddyHeaders.test.js`) que obliga a que `unsafe-inline` aparezca solo en `style-src`; al migrar se cambia esa expectativa a «ninguno».

Riesgo: medio (puede dejar pantallas sin estilos si falla un nonce). Mitigación: Report-Only primero y reversa en una línea de Caddy.

## 3. Auditoría (G4)

### Qué se registra hoy en base

| Qué | Dónde | Datos |
|---|---|---|
| Intentos de login (exitosos y fallidos) | `login_attempt` (V11), `LoginService.recordAttempt` | usuario, persona si existe, éxito, IP, momento |
| Bloqueo de cuenta por fallos | `person.locked_until` / `failed_login_attempts` | estado, sin histórico propio (queda en `login_attempt`) |
| Cambios de contacto del perfil | `identity_contact_change_log` (V173) | actor, antes/después |
| Aceptación legal | `legal_acceptance` (V224) | quién, qué versión, cuándo |
| Cambios de configuración del instituto | `institute_setting_change_log` (V193) | actor, motivo |
| Alta de instituto / módulos por el dueño | `platform_provisioning_log` (V170) | actor, motivo |
| Corrección de notas publicadas | `grade_item_audit` (V188) | quién, motivo, antes/después |
| Cierre de comisión, reapertura, excepciones | `commission_closure_audit`, `commission_closure_exception` (V205, V207) | acción, quién, motivo |
| Facturación | versiones de configuración y precios con autor; pagos por su propio registro | autor y fecha |

Nota: la épica nombra `import_batch_event`; **esa tabla no existe en este árbol** (no hay referencias en `backend/src`). Hay que confirmar si pertenece a una rama de importaciones aún no integrada.

### Qué falta

| Acción sensible | Hoy | Hueco |
|---|---|---|
| Login fallido | Sí (`login_attempt`) | No hay alerta ni pantalla; la IP es la del proxy salvo que `X-Forwarded-For` esté bien (G1 verificado) |
| Cambio de rol o de permisos | Solo `log.info` | Sin registro persistente |
| Baja y reactivación de personal | `log.info` (`WithdrawStaffService`, `StaffReactivator`) | Sin quién/cuándo/motivo persistente |
| Reset de clave de alumno o personal | `log.info` | Sin registro persistente |
| Baja de alumno, inscripciones y bajas manuales, cambio de justificaciones | Parcial por dominio | Sin registro uniforme |
| Prender/apagar módulos del instituto | `platform_provisioning_log` | Verificar que cubra cada cambio de switch |
| Exportaciones y descargas masivas | Nada | Sin registro de quién exportó qué y cuándo |
| Cambio de clave propia / email | `identity_contact_change_log` solo contacto | Cambio de contraseña sin registro |

### Estado G4 (etapas 1 a 3 implementadas)

- Etapa 1: tabla `audit_log` (V243), puerto `AuditTrail.record` (obligatoriamente dentro de la transacción de la acción), lectura `GET /audit-log` solo DIRECTOR, bajas y reactivaciones de personal.
- Etapa 2 (V246): pedido y cambio efectivo de contraseña (`PASSWORD_RESET_REQUESTED`, `PASSWORD_RESET_COMPLETED`), cambio de contraseña propio (`PASSWORD_CHANGED`), baja y reactivación de alumno (`STUDENT_WITHDRAWN`, `STUDENT_REACTIVATED`; se guarda el código del motivo, nunca el detalle libre de la baja) y bajas/cambios de comisión hechos por el personal (`ENROLLMENT_WITHDRAWN_BY_STAFF`, `ENROLLMENT_SECTION_CHANGED_BY_STAFF`).
- Etapa 3 (V246): importación inicial, por lote (`DATA_IMPORT_CREATED`, `_COMMITTED`, `_RETRIED`, `_SEALED`, `_ROLLED_BACK`, `_ROLLBACK_BLOCKED`) y descarga del reporte CSV (`DATA_IMPORT_REPORT_DOWNLOADED`). Es la única exportación masiva que existe hoy: los certificados y comprobantes son individuales.
- Alta de personal (V247): `STAFF_CREATED` con el rol inicial en `after_value`, sin datos personales. El cambio de rol no se audita porque todavía no existe esa operación.
- Lectura para el dueño: `GET /platform/audit-log` (solo PLATFORM_ADMIN; el dueño sigue sin entrar a `/audit-log`).
- Borrado de personas: la FK `audit_log.actor_person_id` es `ON DELETE SET NULL` (V246): un registro de auditoría no impide borrar una persona (solo ocurre en pruebas, en producción no se borran) y tampoco se pierde; solo queda sin actor.
- Retención: `AuditLogRetentionJob`, apagado por defecto, plazo configurable con default de 24 meses; el plazo final lo decide Gabriel (ver `docs/context/data-retention.md`).
- Sin cubrir: cambio de rol. Hoy no existe una operación que cambie el rol de una persona (`UpdateStaffService` no toca roles; el rol se asigna solo al dar de alta en `CreateStaffService`, que tampoco se audita todavía). Si se agrega esa operación, debe llamar a `AuditTrail.record` en la misma transacción.

### Plan G4 (diseño original)

Tabla `audit_log` general: `id`, `occurred_at`, `actor_person_id`, `actor_role`, `institution_id`, `action` (enum cerrado con CHECK), `entity_type`, `entity_id`, `before_json`, `after_json`, `reason`, `request_id`, `ip`. Alta solo por un puerto `AuditTrail.record(...)` del módulo `shared` (sin AOP al principio: llamadas explícitas desde los use cases, más fáciles de revisar y de probar). Etapas: (1) tabla + puerto + personal/roles/resets, (2) bajas de alumno/inscripciones/justificaciones, (3) exportaciones y descargas masivas. Lectura solo para DIRECTOR y PLATFORM_ADMIN con una fila nueva en `authorization-matrix.csv`. Migración nueva (siguiente número libre en `origin/develop` y `origin/master`). Retención alineada con `IdentityDataRetentionJob`. Riesgo bajo, tamaño 2 paquetes.

## 4. Matriz de autorización (G5)

### Cobertura real

- `backend/src/test/resources/security/authorization-matrix.csv`: 512 filas (método, ruta, anónimo y 6 roles, ownership, cuerpo mínimo).
- `AuthorizationMatrixCompletenessTest`: cada mapping de `RequestMappingHandlerMapping` tiene **exactamente una** fila, cada fila tiene mapping, y las columnas `ownership` y `OWNER` son coherentes. Un endpoint nuevo sin fila **ya hace fallar** el test.
- `AuthorizationMatrixTest`: recorre cada celda rol por endpoint contra el resultado esperado. `AuthorizationMatrixOwnershipTest` y `OwnershipFailuresLookLikeMissingResourcesTest`: el dueño pasa y el ajeno recibe 404 uniforme. `PlatformAdminIsolationTest`, `RoleSmokeIT`, `BillingOffEndpointsIT` y `BillingMirrorEndpointsIT` cubren el aislamiento del dueño de plataforma y el módulo de facturación.
- La nota de la épica pedía «que falle si aparece un endpoint sin regla»: **ya está cubierto** por `everyHandlerMappingHasExactlyOneMatrixRow`.

### Huecos

1. Solo se enumeran handlers de controllers: filtros y cadenas propias (`ActuatorSecurityConfig`, `OpenApiSecurityConfig`, `/error`) no tienen fila. Caddy ya devuelve 404 externo para actuator salvo `health`.
2. La matriz prueba **rol**, no **multi-institución**: no hay una columna que verifique que un director de un instituto no ve datos de otro (el modelo es una infra por instituto, riesgo bajo pero a considerar en G7).
3. No se prueba la **frescura del token tras baja o cambio de rol** (el access token de 15 minutos sigue válido con los roles viejos; hallazgo M2 del análisis del 8-nov). Candidato a test de caracterización y decisión de producto.
4. Las filas con respuesta «conocida» (`MatrixKnownResponses`) esperan estados específicos; no hay verificación de que los cuerpos de error no filtren detalles (cubierto en parte por el catálogo de errores).

Propuesta: sin cambios de código en este PR; los puntos 2 y 3 entran en G7.

## 5. Cabeceras de seguridad: lo implementado

Verificado contra `frontend/Caddyfile`, `frontend/Dockerfile` (`VITE_CSP_IN_HEADER=true`) y `docs/deploy/README.md`:

| Cabecera | Antes | Ahora |
|---|---|---|
| `Strict-Transport-Security` | `max-age=31536000` | igual (ver nota) |
| `X-Content-Type-Options` | `nosniff` | igual |
| `Referrer-Policy` | `no-referrer` | igual |
| `Permissions-Policy` | cámara, micrófono, geolocalización, pagos, USB, FLoC denegados | igual |
| `Cross-Origin-Opener-Policy` | `same-origin-allow-popups` (necesario para el login con Google) | igual |
| CSP `frame-ancestors` | `'none'` | igual |
| `X-Frame-Options` | ausente | `DENY` (navegadores viejos que ignoran `frame-ancestors`) |
| `Server` | oculto | igual |

La mayoría de las cabeceras **ya estaban**; este PR agrega `X-Frame-Options` y un test (`frontend/src/caddyHeaders.test.js`) que lee el `Caddyfile` y falla si se debilita alguna. Se verificó que el test falla al agregar `'unsafe-inline'` a `script-src`.

Notas que requieren decisión: HSTS sin `includeSubDomains` ni `preload` (agregarlo compromete a todos los subdominios del dominio a HTTPS permanente; no se hizo sin confirmar el dominio definitivo). `Cross-Origin-Embedder-Policy` no se agrega: rompería el iframe de Google.

Rate limit y bloqueo de login: **ya existen y no se tocaron**. Rate limit por IP con Bucket4j (login 10/min, forgot 5/min, register 20/min, refresh 30/min, reset 10/min, lecturas 600/min y escrituras 120/min por usuario) y bloqueo de cuenta tras 5 fallos por 15 minutos (`auth.lockout.*`, `LoginService`), más bloqueo por IP de recuperación de clave (`auth.password-reset-lockout.*`). La IP real llega por `X-Forwarded-For` (G1 verificado).

## 6. Decisiones que necesito de Gabriel

1. Aprobar el plan de cookie HttpOnly (sección 1), incluyendo el chequeo de `Origin` y la ventana de migración de 7 días; y si el desarrollo local usa proxy de Vite o `SameSite=Lax`.
2. Aprobar el plan de CSP con nonce (sección 2) o aceptar `unsafe-inline` en estilos como riesgo residual bajo (React no lo necesita; solo Radix y sonner).
3. Aprobar la tabla `audit_log` y las etapas (sección 3), y confirmar qué pasó con `import_batch_event`.
4. Confirmar el dominio definitivo para decidir HSTS `includeSubDomains`/`preload`.

## Decisiones del 14-nov

- Cookie HttpOnly: aprobada para implementar, siguiendo el plan de cinco pasos compatibles hacia atrás.
- CSP de estilos: se acepta `unsafe-inline` en `style-src` como riesgo residual bajo; se revisa después de salir a producción.
- Auditoría: aprobada la tabla general `audit_log` con un puerto único, en tres etapas.
- HSTS `includeSubDomains`/`preload`: pendiente de confirmar el dominio definitivo.
