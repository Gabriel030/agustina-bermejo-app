# Rotación de secretos

Procedimiento por secreto para producción (E24 G8, #1152). Inventario verificado contra
`backend/src/main/resources/application.properties`, `deploy/.env.example`, `deploy/staging/.env.staging.example`,
`docker-compose.prod.yml`, `deploy/backup/docker-compose.backup.yml`, `deploy/monitoring/docker-compose.monitoring.yml`
y `PlatformAdminProvisioner` (21-nov-2026). Todos los valores de este documento son placeholders: nunca se escribe un
secreto real en el repo, en un ticket ni en un chat.

Generar un secreto nuevo: `openssl rand -base64 48 | tr -d '\n'`.

## Alias usados (los tres apuntan al mismo `deploy/.env`)

```bash
export COMPOSE_FILE=docker-compose.prod.yml
alias dc='docker compose --env-file deploy/.env'
alias dcb='docker compose --env-file deploy/.env -f docker-compose.prod.yml -f deploy/backup/docker-compose.backup.yml'
alias dcm='docker compose --env-file deploy/.env -f docker-compose.prod.yml -f deploy/backup/docker-compose.backup.yml -f deploy/monitoring/docker-compose.monitoring.yml'
```

`dc` (el de `docs/deploy/README.md`) solo conoce `docker-compose.prod.yml`: **no ve ni recrea** el servicio `backup`
ni los de monitoreo (`uptime-check`, `alertmanager`, `grafana`), que son overlays aparte (`docs/deploy/backup-and-restore.md`,
`docs/deploy/monitoring.md`). Un secreto que ellos usen hay que aplicarlo con `dcb` / `dcm` y verificarlo: si se olvida,
siguen corriendo con el valor viejo y fallan sin que el backend lo note.

## Reglas comunes

1. Cambiar un secreto por vez, fuera del horario de clases y con un backup reciente.
2. Guardar el valor viejo en el gestor de contraseñas hasta cerrar la verificación (es el rollback).
3. Probar primero en staging (checklist al final) con secretos de staging, nunca los de producción.
4. Después de cada `up -d`, verificar con `dc ps` (backend `healthy`) y `dc logs --tail=100 backend`.
5. No pasar valores por la línea de comandos (`-c "...'valor'"`, `export X=valor`): queda en el historial del shell y
   en `ps`. Editar `deploy/.env` con el editor y, donde haga falta teclear la clave, usar el prompt interactivo.

## Qué pasa con los tokens emitidos con el secreto viejo

Verificado en `identity/infrastructure/JwtService.java`: el backend carga **una sola clave** de firma (SHA-256 de
`JWT_SECRET`) y no acepta dos en una ventana de rotación.

- Todo **access token** (15 minutos) firmado con el secreto viejo se rechaza apenas arranca el backend con el nuevo
  (`SignatureException`, el front recibe 401).
- El **refresh token** no es un JWT: es un valor opaco cuyo hash SHA-256 está en la tabla `refresh_token`
  (`token_hash`, `expires_at`, `revoked`) y vive 7 días (`jwt.refresh-token-expiration-days`). Rotar `JWT_SECRET` **no**
  lo invalida: el front, ante el 401, pide un access token nuevo con el refresh y el usuario sigue adentro. Hay que
  separar dos cosas: rotar `JWT_SECRET` corta los access tokens (un 401 por usuario, resuelto solo por el front), **no**
  cierra las sesiones.
- Las confirmaciones pendientes de cambio de funcionalidades (`FeatureTransitionConfirmationSigner`) usan
  `platform.feature-confirmation.secret` y, si no está definida (no figura en `deploy/.env.example`), caen en `jwt.secret`:
  las que estén abiertas al rotar quedan inválidas y hay que reiniciar la confirmación.
- No hay doble clave y no se implementa en esta rebanada (sería código de producción). Propuesta si se necesita rotar
  sin ningún 401: aceptar `JWT_SECRET_PREVIOUS` solo para verificar, durante una ventana igual a la expiración del
  access token más un margen, y firmar siempre con el nuevo.

### Cómo cerrar de verdad todas las sesiones (ante una fuga)

Rotar el secreto no alcanza: hay que invalidar también los refresh tokens, en este orden:

1. Rotar `JWT_SECRET` (corta los access tokens).
2. Revocar los refresh tokens (tabla y columnas de `V8__refresh_token.sql`; no lleva ningún secreto):

   ```bash
   dc exec postgres sh -c 'psql -U "$POSTGRES_USER" -d "$POSTGRES_DB" -c "update refresh_token set revoked = true where revoked = false"'
   ```

   Un token revocado así no tiene `rotated_at`, por lo que `RefreshTokenRotator` lo trata como inválido salvo la ventana
   de gracia de reuso; **no hay test automático de este camino**: confirmarlo en el ensayo de staging.
3. Alternativa sin tocar el secreto: sumar uno a `token_version` de todas las personas
   (`update person set token_version = token_version + 1`); el access token con otro `tv` se rechaza
   (`AccessTokenVerifier`). Combinada con el paso 2, cierra todo.

### Cómo comunicarlo

Rotación normal: aviso a secretaría y docentes el día anterior ("a las HH:MM el sistema se actualiza; si ves un cartel de
sesión vencida, volvé a entrar"). Duración del corte: lo que tarda el backend en reiniciar; **no medida**, tomarla en el
ensayo de staging. Cierre total de sesiones (pasos 1 a 3): todos tienen que volver a iniciar sesión; avisar por el canal
institucional.

## Inventario

| Secreto | Obligatorio | Lo usa | Cadencia recomendada |
|---|---|---|---|
| `JWT_SECRET` | Sí (sin default; el arranque falla si falta o tiene menos de 32 caracteres) | `JwtService`; fallback de `FeatureTransitionConfirmationSigner` y, fuera de prod, de `UnsubscribeTokenSigner` | 12 meses y ante fuga o baja de un administrador |
| `BROADCAST_UNSUBSCRIBE_SECRET` | Sí en perfil `prod` (el arranque falla sin él) | `UnsubscribeTokenSigner` | 24 meses o ante fuga; rotarlo rompe los enlaces ya enviados |
| `POSTGRES_PASSWORD` (= `DB_PASSWORD` en el backend, `PGPASSWORD` en el backup) | Sí (`docker-compose.prod.yml` falla sin ella) | Contenedor `postgres`, backend y servicio `backup` | 12 meses y ante baja de quien la conocía |
| `BREVO_SMTP_KEY` / `BREVO_API_KEY` | Opcionales (con proveedor `mock` no se usan; `BREVO_API_KEY` es obligatoria con `TRANSACTIONAL_MAIL_PROVIDER=brevo`) | `spring.mail.password` y cliente HTTP de Brevo | 6 meses o ante fuga |
| `MP_ACCESS_TOKEN`, `MP_WEBHOOK_SECRET` | No mientras billing y la pasarela estén apagados (D-226) | Pasarela de Mercado Pago | Solo documentado |
| `WHATSAPP_META_ACCESS_TOKEN` | Obligatoria solo con `BROADCAST_WHATSAPP_PROVIDER=meta` | Proveedor WhatsApp (Meta) | Según la política del token en Meta (no verificada); mientras tanto, 6 meses |
| `ACTUATOR_PASSWORD` | Opcional para el backend; **obligatoria para el perfil de monitoreo** (Prometheus falla sin ella) | `/actuator` y el scraping de Prometheus | 12 meses |
| `app.platform-admin.username` / `.email` / `.password` | Opcionales (`@ConditionalOnProperty`) | `PlatformAdminProvisioner` | Ver su sección |
| `ALERT_WEBHOOK_URL`, `ALERT_TELEGRAM_BOT_TOKEN` | Opcionales | Servicio `backup`, `uptime-check`, `alertmanager` | 12 meses o ante fuga |
| `BACKUP_HEARTBEAT_URL`, `UPTIME_HEARTBEAT_URL` | Opcionales | `backup` y `uptime-check` (ping a healthchecks.io o similar) | Ante fuga (la URL es el secreto) |
| `GRAFANA_ADMIN_PASSWORD` | Opcional, **si falta cae a `admin`** | `grafana` | 12 meses |
| `BACKUP_S3_ACCESS_KEY_ID` / `BACKUP_S3_SECRET_ACCESS_KEY` | Opcionales (sin bucket no se sube nada) | Servicio `backup` | 12 meses |
| Clave `age` de cifrado de backups (`BACKUP_AGE_RECIPIENT` pública; la privada fuera del servidor) | Recomendada | Servicio `backup` cifra; la privada solo la usa `verify-restore.sh` | Ante fuga o baja de quien la tenía; ver su sección |
| Credenciales de Google | **No hay secreto**: `GOOGLE_OAUTH_CLIENT_ID` / `VITE_GOOGLE_CLIENT_ID` son identificadores públicos (el backend solo verifica el `aud` del idToken). Cambiar el client id exige `dc up -d --build` y cambiarlo en la consola de Google | `GoogleIdTokenService` | No aplica |

## JWT_SECRET

- **Impacto**: ver la sección de tokens. Access tokens vigentes inválidos (401, el front renueva con el refresh);
  confirmaciones de funcionalidades pendientes perdidas; sin cambio en base; las sesiones **no** se cierran.
- **Pasos**:
  1. Avisar (ver "Cómo comunicarlo").
  2. Generar el valor nuevo y guardar el viejo aparte.
  3. Editar `JWT_SECRET` en `deploy/.env`.
  4. `dc up -d backend`.
- **Verificación**: `dc ps` con backend `healthy`; un access token viejo guardado devuelve 401 en `GET /api/me/sessions`;
  iniciar sesión con un usuario de prueba y abrir una pantalla protegida; un enlace de baja enviado antes sigue abriendo.
- **Rollback**: volver al valor viejo y `dc up -d backend`; los tokens emitidos con el nuevo dejan de valer (mismo efecto).
- **Prueba automática**: ver "Qué cubre cada test".

## BROADCAST_UNSUBSCRIBE_SECRET

- **Qué lo usa**: firma de los enlaces de baja de las comunicaciones masivas. Es distinto de `JWT_SECRET` para que rotar el
  JWT no invalide los enlaces ya enviados.
- **Impacto**: rotarlo invalida **todos** los enlaces de baja ya enviados (el destinatario ve enlace inválido). No hay doble
  clave. Riesgo legal menor (baja de comunicaciones): no rotarlo sin motivo; hacerlo justo antes de una comunicación nueva
  y mantener un canal de baja alternativo (secretaría).
- **Pasos**: generar el valor nuevo, editar `deploy/.env`, `dc up -d backend`.
- **Verificación**: el backend arranca (sin el secreto falla en prod); comunicación de prueba a una casilla propia y abrir su
  enlace de baja.
- **Rollback**: volver al valor viejo restaura los enlaces viejos y rompe los emitidos con el nuevo.

## POSTGRES_PASSWORD / DB_PASSWORD

- **Qué lo usa**: el contenedor `postgres`, el backend (`DB_PASSWORD: ${POSTGRES_PASSWORD}`) y el servicio `backup`
  (`PGPASSWORD`, solo con `dcb`). Es una sola variable; el usuario no cambia.
- **Impacto**: la contraseña vive dentro de Postgres. Cambiar `POSTGRES_PASSWORD` en `.env` **modifica el servicio
  `postgres`** (`docker-compose.prod.yml` la declara en su `environment`), por lo que `dc up -d` también **lo recrea**
  (reinicio de segundos, con el volumen intacto); el backend se recrea también, y se reconecta. Entre el `ALTER USER`
  y el reinicio del backend, **las conexiones nuevas del pool fallan** (las abiertas siguen vivas): por eso los pasos 2 a 4
  se hacen seguidos, con el `.env` ya preparado.
- **Pasos**:
  1. Backup reciente y verificado.
  2. Editar `POSTGRES_PASSWORD` en una copia (`deploy/.env.new`) sin pisar `deploy/.env` todavía.
  3. Cambiar la clave en Postgres con el prompt interactivo (no queda en el historial ni en `ps`):
     `dc exec postgres sh -c 'psql -U "$POSTGRES_USER" -d "$POSTGRES_DB"'` y dentro `\password agustina`
     (usar el usuario real de `POSTGRES_USER`), luego `\q`.
  4. Inmediatamente: `mv deploy/.env.new deploy/.env` y `dcb up -d` (recrea `postgres`, `backend` y `backup`; el `-f` del
     backup evita dejarlo con la clave vieja).
  5. Si hay monitoreo, `dcm up -d` (no usa la clave de base, pero es el mismo `.env`).
- **Verificación**: `dc ps` con backend `healthy`; login de prueba; el servicio `backup` con el valor nuevo:
  `dcb exec backup backup.sh` y confirmar que termina bien y que el último backup nuevo aparece en `/backups`. **Si el backup
  no se recrea con `dcb`, sigue con la clave vieja y falla en silencio** hasta la próxima alerta de antigüedad.
- **Rollback**: `\password` con el valor viejo, restaurar `.env`, `dcb up -d`.
- **Prueba automática**: ninguna posible sin infraestructura; el compose falla con `POSTGRES_PASSWORD es obligatoria` y se
  verifica en el ensayo de staging.

## BREVO_SMTP_KEY y BREVO_API_KEY

- **Qué lo usa**: mails transaccionales (`spring.mail.password`, `app.mail.brevo.api-key`).
- **Impacto**: entre el reinicio y la clave válida los envíos fallan. No se verificó en el repo que haya reintento
  automático: tratar los mails de ese intervalo como posiblemente perdidos y reenviar los críticos a mano.
- **Pasos**: crear la clave nueva en Brevo (sin revocar la vieja), editar `.env`, `dc up -d backend`, enviar un mail de
  prueba (recuperación de contraseña a una casilla propia), recién entonces revocar la vieja en Brevo.
- **Rollback**: restaurar la clave vieja (por eso no se revoca hasta verificar).

## MP_ACCESS_TOKEN y MP_WEBHOOK_SECRET (solo documentado, D-226)

Billing y la pasarela están apagados (D-226): no se ensaya ni se rota en producción hasta encender pagos. Cuando se
enciendan: rotar primero en el panel de Mercado Pago, editar `.env`, `dc up -d backend`; verificar con un pago de prueba y la
recepción de su webhook firmado. Los webhooks entre el cambio en el panel y el reinicio fallan la verificación de firma
(reintento por parte de Mercado Pago: no verificado). Rollback: valores viejos en `.env` y en el panel. Ensayar antes en
staging con cuentas de prueba (B4 del estado de auditoría).

## app.platform-admin (usuario, email y contraseña)

- **Qué lo usa**: `PlatformAdminProvisioner`, solo si las tres propiedades están definidas y **solo crea** el usuario si no
  existe ninguno con el rol de plataforma (`existsByRoles_Name`): al arrancar con el usuario ya creado, no hace nada.
- **Impacto**: cambiar la variable después del primer arranque **no cambia** la contraseña del usuario existente.
- **Pasos para rotar la contraseña**: cambiarla desde la pantalla de cambio de contraseña del propio usuario (o el flujo de
  recuperación) y luego borrar la variable de `.env` o actualizarla para que no quede un valor engañoso; `dc up -d backend`
  solo si se editó `.env`.
- **Verificación**: iniciar sesión con la contraseña nueva; la vieja se rechaza.

## Clave age de cifrado de backups (el caso más delicado)

- **Qué es**: `BACKUP_AGE_RECIPIENT` es la clave **pública** (el servidor solo cifra); la privada se guarda fuera del
  servidor y solo la usa `deploy/backup/verify-restore.sh` (`AGE_IDENTITY_FILE`).
- **Regla**: **nunca borrar la privada vieja.** Los backups ya existentes (`.age`) solo se descifran con la privada con la que
  se cifraron; perderla es perder esos backups.
- **Pasos**:
  1. Generar un par nuevo en una máquina de confianza (`age-keygen -o nueva.txt`); guardar la privada nueva fuera del servidor
     y junto a la vieja, rotuladas con la fecha de corte.
  2. Poner la pública nueva en `BACKUP_AGE_RECIPIENT` y recrear el backup: `dcb up -d backup`.
  3. Correr un backup manual (`dcb exec backup backup.sh`) y **descifrar y restaurar** ese backup con la privada nueva en otra
     máquina (`verify-restore.sh`, ver `deploy/backup/tests/README.md`).
  4. Mantener la privada vieja mientras exista algún backup cifrado con ella dentro de la retención.
- **Rollback**: volver a la pública vieja y `dcb up -d backup`.
- **Cadencia**: ante fuga de la privada o baja de quien la tenía.

## Otros secretos opcionales

- `WHATSAPP_META_ACCESS_TOKEN`: generar el token nuevo en Meta, editar `.env`, `dc up -d backend`, enviar una comunicación de
  prueba, revocar el viejo.
- `ACTUATOR_PASSWORD`: la usa el backend (`dc up -d backend`) y el monitoreo (Prometheus, `dcm up -d prometheus`); cambiar
  ambos seguidos, porque el scraping falla entre uno y otro.
- `GRAFANA_ADMIN_PASSWORD`: **si no está definida, Grafana arranca con usuario y contraseña `admin`**: definirla siempre. Solo
  se aplica en el primer arranque del volumen: para rotarla, cambiarla desde la UI de Grafana y después actualizar `.env`.
- `ALERT_TELEGRAM_BOT_TOKEN`: lo consumen `backup`, `uptime-check` y `alertmanager`. Token nuevo con BotFather, editar `.env`,
  `dcm up -d backup uptime-check alertmanager`, forzar una alerta de prueba por cada uno.
- `ALERT_WEBHOOK_URL`, `BACKUP_HEARTBEAT_URL`, `UPTIME_HEARTBEAT_URL`: la URL es la credencial; generar una nueva en el
  servicio externo, editar `.env`, `dcm up -d backup uptime-check alertmanager`, confirmar que llega el ping o el aviso, y
  borrar la vieja en el servicio externo.
- `BACKUP_S3_*`: crear la clave nueva en el proveedor, editar `.env`, `dcb up -d backup`, backup manual y confirmar el
  objeto nuevo en el bucket; revocar la vieja.

## Qué cubre cada test

| Test | Qué prueba | Frente a los existentes |
|---|---|---|
| `SecretsRotationTest.accessTokenSignedWithOldSecretIsRejectedAfterRotationButAcceptedByOldSecret` | Un access token del secreto viejo vale con el viejo y falla con el nuevo (`SignatureException`) | Repite el caso de `JwtServiceTest.tokenSignedWithAnotherSecretFailsValidation` pero con el par viejo/nuevo y la contraparte positiva |
| `SecretsRotationTest.unsubscribeLinkSignedBeforeJwtRotationStillVerifiesAfterIt` | En perfil prod, un enlace emitido antes de cambiar `jwt.secret` verifica después con el contexto nuevo (propiedades reales de Spring) | Nuevo: `UnsubscribeTokenTest.productionUsesOwnSecretNotTheJwtOne` prueba la independencia con constructores, no la rotación entre dos arranques |
| `SecretsRotationTest.unsubscribeLinkIsRejectedWhenItsOwnSecretChanges` | Cambiar el secreto propio invalida el enlace (el riesgo documentado) | Nuevo |
| `SecretsRotationTest.productionBootFailsWithoutJwtSecret` / `...WithoutUnsubscribeSecret` / `...WithAJwtSecretShorterThanTheMinimum` | En perfil prod el contexto falla y la causa nombra el secreto faltante o el largo mínimo | Complementa `JwtServiceBootstrapTest` (sin perfil, solo JWT), `JwtSecretPolicyTest` (la política aislada) y `UnsubscribeTokenTest.productionWithoutOwnSecretFailsToStart` (el signer aislado): acá se prueba el arranque conjunto con perfil `prod` |
| `SecretsRotationTest.productionBootSucceedsWithAllRequiredSecrets` | Control positivo: con todos los secretos el contexto arranca | Nuevo |

No hay prueba automática de `POSTGRES_PASSWORD` (lo cubre el compose y el ensayo), de la revocación de refresh por SQL ni de
los secretos opcionales.

## Checklist de ensayo en staging (lo corre Gabriel)

Usa `deploy/.env.staging` y el alias `sc` de `docs/deploy/README.md` ("Entorno de ensayo"). Solo secretos de staging, nunca los
de producción. Para el backup, agregar el overlay: `scb` = `sc` más `-f deploy/backup/docker-compose.backup.yml`.

- [ ] Staging arriba y sano: `sc ps` con backend `healthy`.
- [ ] Iniciar sesión con un usuario de prueba y guardar el access token de la respuesta de `POST /api/auth/login` en `OLD_TOKEN`
  (sin pegarlo en tickets). **Control previo**:
  `curl -s -o /dev/null -w '%{http_code}\n' -H "Authorization: Bearer $OLD_TOKEN" https://<dominio-staging>/api/me/sessions`
  debe devolver `200`. Si ya da 401, el token venció (15 minutos) y la prueba no sirve: repetir el login. Hacer el paso
  siguiente dentro de los 15 minutos.
- [ ] Generar una comunicación masiva de prueba (proveedor `mock`: tomar el enlace de baja del log del backend) y guardarlo
  como `OLD_LINK`.
- [ ] **JWT_SECRET**: editar `JWT_SECRET` en `deploy/.env.staging`, `sc up -d backend`.
  - [ ] `sc ps` vuelve a `healthy`; anotar cuántos segundos tardó el reinicio.
  - [ ] El mismo `curl` con `$OLD_TOKEN` ahora devuelve `401` (y no por vencimiento: el control previo dio `200` minutos antes).
  - [ ] El front renueva solo la sesión con el refresh; el login nuevo funciona.
  - [ ] `OLD_LINK` de baja sigue funcionando.
  - [ ] Cierre total: ejecutar el `update refresh_token ...` de "Cómo cerrar de verdad todas las sesiones"; el refresh de
    la sesión abierta debe fallar y el front pedir login. Anotar el resultado (camino sin test automático).
  - [ ] Rollback: valor anterior, `sc up -d backend`, el login funciona.
- [ ] **BROADCAST_UNSUBSCRIBE_SECRET**: cambiarlo, `sc up -d backend`; `OLD_LINK` da enlace inválido (esperado) y un enlace nuevo
  funciona. Vaciarlo y `sc up -d backend`: el backend no arranca (`sc logs backend` muestra
  `broadcast.unsubscribe.secret es obligatorio en el perfil prod`); restaurarlo.
- [ ] **JWT_SECRET faltante**: vaciarlo, `sc up -d backend`: falla el arranque; restaurarlo.
- [ ] **POSTGRES_PASSWORD**: backup previo; preparar `.env.staging.new`; `\password` interactivo dentro de `psql`; mover el
  archivo; `scb up -d`; anotar cuánto duró el intervalo con conexiones nuevas fallando; backend `healthy`; `scb exec backup backup.sh`
  OK. Probar también que omitirla da `POSTGRES_PASSWORD es obligatoria`.
- [ ] **Clave age** (si staging cifra backups): par nuevo, `scb up -d backup`, backup manual y restauración con la privada nueva.
- [ ] **Brevo** (solo si se probó con casilla de prueba): rotar la clave y mail de prueba.
- [ ] Anotar fecha, quién lo corrió, tiempos medidos y desvíos al pie de este documento antes de repetirlo en producción.
