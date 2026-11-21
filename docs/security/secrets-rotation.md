# Rotación de secretos

Procedimiento por secreto para producción (E24 G8, #1152). Inventario verificado contra
`backend/src/main/resources/application.properties`, `deploy/.env.example`,
`deploy/staging/.env.staging.example`, `docker-compose.prod.yml` y `deploy/backup/docker-compose.backup.yml`
(21-nov-2026). Todos los valores de este documento son placeholders: nunca se escribe un secreto real
en el repo, en un ticket ni en un chat.

Convenciones: `dc` es el alias de `docker compose --env-file deploy/.env -f docker-compose.prod.yml`
(ver `docs/deploy/README.md`). Generar un secreto nuevo: `openssl rand -base64 48 | tr -d '\n'`.

## Reglas comunes

1. Cambiar de a un secreto por vez, fuera del horario de clases y con un backup reciente (`deploy/backup`).
2. Guardar el valor viejo en el gestor de contraseñas hasta cerrar la verificación (es el rollback).
3. Probar primero en staging (checklist al final) con secretos de staging, nunca los de producción.
4. Después de `dc up -d`, verificar con `dc ps` (backend `healthy`) y `dc logs --tail=100 backend`.

## Qué pasa con los tokens emitidos con el secreto viejo

Verificado en `identity/infrastructure/JwtService.java`: el backend carga **una sola clave** de firma
(derivada con SHA-256 de `JWT_SECRET`) y no acepta dos en una ventana de rotación. Consecuencias:

- Todo access token (15 minutos) firmado con el secreto viejo se rechaza apenas arranca el backend con el nuevo
  (`SignatureException`, el front recibe 401).
- El refresh token **no es un JWT**: es un valor opaco cuyo hash SHA-256 se guarda en base
  (`RefreshTokenRotator`), así que rotar `JWT_SECRET` no lo invalida. El front, al recibir el 401, pide un
  access token nuevo con el refresh y el usuario sigue adentro sin volver a iniciar sesión. Quien tenga el
  refresh vencido o revocado sí tiene que entrar de nuevo.
- Las confirmaciones pendientes de cambio de funcionalidades (`FeatureTransitionConfirmationSigner`) usan
  `platform.feature-confirmation.secret` y, si no está definida (hoy no figura en `deploy/.env.example`), caen en
  `jwt.secret`: las que estén abiertas al rotar quedan inválidas y hay que reiniciar la confirmación.
- No hay doble clave y no se implementa en esta rebanada (cambio de código de producción). Si más adelante se
  necesita rotar sin ningún 401, propuesta: aceptar `JWT_SECRET_PREVIOUS` solo para verificar, durante una
  ventana igual a la expiración del access token más un margen, y firmar siempre con el nuevo.

Cómo comunicarlo: aviso a secretaría y docentes el día anterior ("a las HH:MM el sistema se actualiza; si ves
un cartel de sesión vencida, volvé a entrar"). Duración esperada del corte: lo que tarda el backend en levantar
(menos de 2 minutos). Para cortar todas las sesiones a propósito (por ejemplo, ante una fuga) basta rotar
el secreto; para revocar también los refresh tokens hay que invalidarlos en base (fuera del alcance de este
documento, ver `docs/security/plan-produccion.md`).

## Inventario

| Secreto | Obligatorio | Lo usa | Cadencia |
|---|---|---|---|
| `JWT_SECRET` | Sí (sin default; el arranque falla si falta o tiene menos de 32 caracteres) | `JwtService` (access tokens); fallback de `FeatureTransitionConfirmationSigner` y, fuera de prod, de `UnsubscribeTokenSigner` | Cada 12 meses y ante sospecha de fuga o baja de un administrador |
| `BROADCAST_UNSUBSCRIBE_SECRET` | Sí en perfil `prod` (el arranque falla sin él) | `UnsubscribeTokenSigner` (enlaces de baja de comunicaciones masivas) | Cada 24 meses o ante fuga; rotarlo rompe los enlaces ya enviados |
| `POSTGRES_PASSWORD` (= `DB_PASSWORD` en el backend) | Sí (`docker-compose.prod.yml:10` falla sin ella) | Contenedor `postgres`, backend (`DB_PASSWORD`) y backup (`PGPASSWORD`) | Cada 12 meses y ante baja de quien la conocía |
| `BREVO_SMTP_KEY` / `BREVO_API_KEY` | Opcionales (con proveedor `mock` no se usan) | `spring.mail.password` y cliente HTTP de Brevo | Cada 6 meses o ante fuga |
| `MP_ACCESS_TOKEN`, `MP_WEBHOOK_SECRET` | No mientras billing y la pasarela estén apagados (D-226) | Pasarela de Mercado Pago | Solo documentado; ver su sección |
| `WHATSAPP_META_ACCESS_TOKEN` | Opcional | Proveedor de WhatsApp (Meta) | Según vencimiento del token en Meta (el de sistema no vence; rotar cada 6 meses) |
| `ACTUATOR_PASSWORD` | Opcional (sin ella el actuator protegido queda sin credencial) | Autenticación de `/actuator` para el monitoreo | Cada 12 meses |
| `GRAFANA_ADMIN_PASSWORD`, `ALERT_TELEGRAM_BOT_TOKEN` | Opcionales | Monitoreo y alertas | Cada 12 meses o ante fuga |
| `BACKUP_S3_ACCESS_KEY_ID` / `BACKUP_S3_SECRET_ACCESS_KEY` | Opcionales (sin bucket no se sube nada) | `deploy/backup` (copia a S3 compatible) | Cada 12 meses |
| Credenciales de Google | **No hay secreto**: `GOOGLE_OAUTH_CLIENT_ID` / `VITE_GOOGLE_CLIENT_ID` son identificadores públicos (el backend solo verifica el `aud` del idToken). Rotar el client id exige `dc up -d --build` y cambiarlo en la consola de Google | `GoogleIdTokenService` | No aplica |

## JWT_SECRET

- **Qué lo usa**: firma y validación de los access tokens; ver arriba por los fallbacks.
- **Impacto**: todos los access tokens vigentes dejan de valer (401 y renovación automática con el refresh);
  confirmaciones de funcionalidades pendientes se pierden. Sin cambio en base. Downtime: el reinicio del backend.
- **Pasos**:
  1. Avisar (ver "Cómo comunicarlo").
  2. Generar el valor nuevo y guardar el viejo aparte.
  3. Editar `JWT_SECRET` en `deploy/.env`.
  4. `dc up -d backend`.
- **Verificación**: `dc ps` con backend `healthy`; iniciar sesión con un usuario de prueba y abrir una pantalla
  protegida; con un access token viejo guardado, `GET /api/me/sessions` devuelve 401; un enlace de baja enviado antes de
  rotar sigue funcionando (ver el siguiente secreto).
- **Rollback**: volver a poner el valor viejo y `dc up -d backend`. Los tokens emitidos en el intervalo con el
  valor nuevo dejan de valer (mismo efecto).
- **Prueba automática**: `security/SecretsRotationTest` y `JwtServiceTest.tokenSignedWithAnotherSecretFailsValidation`.

## BROADCAST_UNSUBSCRIBE_SECRET

- **Qué lo usa**: firma de los enlaces de baja que van en las comunicaciones masivas. Es distinto de
  `JWT_SECRET` justamente para que rotar el JWT no invalide los enlaces ya enviados (probado en
  `SecretsRotationTest.unsubscribeLinkSignedBeforeJwtRotationStillVerifiesAfterIt`).
- **Impacto**: rotarlo invalida **todos** los enlaces de baja ya enviados (el destinatario ve el error de enlace
  inválido). No hay doble clave. Es un riesgo legal menor (baja de comunicaciones): mantener el canal de baja
  alternativo (respuesta al mail o secretaría) y no rotarlo sin motivo.
- **Pasos**: generar el valor nuevo, editar `deploy/.env`, `dc up -d backend`. Si se puede, hacerlo justo antes de
  una comunicación nueva para minimizar enlaces viejos en circulación.
- **Verificación**: el backend arranca (sin el secreto, falla en prod); generar una comunicación de prueba a una
  casilla propia y abrir el enlace de baja.
- **Rollback**: volver al valor viejo restaura los enlaces viejos y rompe los emitidos con el nuevo.
- **Prueba automática**: `SecretsRotationTest.productionBootFailsWithoutUnsubscribeSecret`,
  `unsubscribeLinkIsRejectedWhenItsOwnSecretChanges` y `UnsubscribeTokenTest`.

## POSTGRES_PASSWORD / DB_PASSWORD

- **Qué lo usa**: el contenedor `postgres`, el backend (`DB_PASSWORD: ${POSTGRES_PASSWORD}`) y el backup
  (`PGPASSWORD`). Es una sola variable; el usuario no cambia.
- **Impacto**: la contraseña vive dentro de Postgres: cambiar solo `.env` deja al backend sin acceso. Hay un corte
  corto entre el `ALTER USER` y el reinicio del backend.
- **Pasos**:
  1. Backup reciente y verificado.
  2. `dc exec postgres psql -U <usuario> -c "ALTER USER <usuario> PASSWORD '<nueva>'"` (no pegar el valor en
     el historial del shell compartido).
  3. Editar `POSTGRES_PASSWORD` en `deploy/.env`.
  4. `dc up -d` (recrea backend y backup; Postgres no se recrea y conserva la nueva por el paso 2).
  5. Si el backup corre como servicio aparte (`deploy/backup/docker-compose.backup.yml`), recrearlo con el mismo `.env`.
- **Verificación**: backend `healthy`; login de prueba; correr un backup manual y confirmar que termina bien.
- **Rollback**: `ALTER USER` con el valor viejo, restaurar `.env`, `dc up -d`.
- **Prueba automática**: ninguna posible sin infraestructura (el compose falla con `POSTGRES_PASSWORD es obligatoria`; se
  verifica en el ensayo de staging).

## BREVO_SMTP_KEY y BREVO_API_KEY

- **Qué lo usa**: envío de mails transaccionales (`spring.mail.password`, `app.mail.brevo.api-key`).
- **Impacto**: ninguno si se cambia en Brevo y en `.env` seguidos; entre el reinicio y la clave válida los mails
  fallan (hay reintento del outbox de notificaciones).
- **Pasos**: crear la clave nueva en Brevo (sin revocar la vieja), editar `.env`, `dc up -d backend`, enviar un mail
  de prueba (recuperación de contraseña a una casilla propia), recién entonces revocar la vieja en Brevo.
- **Rollback**: restaurar la clave vieja (por eso no se revoca hasta verificar).
- **Cadencia**: 6 meses o ante fuga.

## MP_ACCESS_TOKEN y MP_WEBHOOK_SECRET (solo documentado, D-226)

Billing y la pasarela están apagados (D-226): no se ensaya ni se rota en producción hasta encender pagos.
Cuando se enciendan: rotar primero en el panel de Mercado Pago (credenciales y firma del webhook), editar `.env`,
`dc up -d backend`; verificar con un pago de prueba y la recepción de su webhook firmado. Los webhooks que lleguen
entre el cambio en el panel y el reinicio fallan la verificación de firma y Mercado Pago los reintenta. Rollback:
valores viejos en `.env` y en el panel. Antes de encender pagos, ensayar en staging con cuentas de prueba (B4 del estado de auditoría).

## Otros secretos opcionales

- `WHATSAPP_META_ACCESS_TOKEN`: generar el token nuevo en Meta, editar `.env`, `dc up -d backend`, enviar una
  comunicación de prueba; revocar el viejo al verificar.
- `ACTUATOR_PASSWORD`: cambiar en `.env` y en el monitoreo en el mismo momento (el scraping falla entre ambos).
- `GRAFANA_ADMIN_PASSWORD`: solo toma efecto en el primer arranque del volumen de Grafana; cambiarla desde la UI de Grafana y actualizar `.env`.
- `ALERT_TELEGRAM_BOT_TOKEN`: nuevo token con BotFather, editar `.env`, recrear el servicio de backup y forzar una alerta de prueba.
- `BACKUP_S3_*`: crear la clave nueva en el proveedor, editar `.env`, recrear el servicio de backup, correr un backup manual y confirmar el objeto nuevo en el bucket; revocar la vieja.

## Qué cubren las pruebas automáticas

| Garantía | Test |
|---|---|
| Token firmado con el secreto viejo se rechaza con el nuevo | `SecretsRotationTest.accessTokenSignedWithOldSecretIsRejectedAfterRotationButAcceptedByOldSecret`, `JwtServiceTest.tokenSignedWithAnotherSecretFailsValidation` |
| Rotar `JWT_SECRET` no invalida un enlace de baja | `SecretsRotationTest.unsubscribeLinkSignedBeforeJwtRotationStillVerifiesAfterIt` |
| Prod no arranca sin `JWT_SECRET`, con uno corto o sin el secreto de baja | `SecretsRotationTest.productionBootFails*`, `JwtServiceBootstrapTest`, `JwtSecretPolicyTest`, `UnsubscribeTokenTest.productionWithoutOwnSecretFailsToStart` |

## Checklist de ensayo en staging (lo corre Gabriel)

Usa `deploy/.env.staging` y el alias `sc` de `docs/deploy/README.md` ("Entorno de ensayo"). Solo secretos de
staging, nunca los de producción.

- [ ] Staging arriba y sano: `sc ps` con backend `healthy`; guardar un access token: iniciar sesión con un usuario
  de prueba y copiar el token de la respuesta de `POST /api/auth/login` (variable `OLD_TOKEN`, no pegarlo en tickets).
- [ ] Generar una comunicación masiva de prueba a una casilla propia (proveedor `mock`: tomar el enlace de baja del log
  del backend) y guardar el enlace (`OLD_LINK`).
- [ ] **JWT_SECRET**: editar `JWT_SECRET` en `deploy/.env.staging` y correr `sc up -d backend`.
  - [ ] `sc ps` vuelve a `healthy`.
  - [ ] `curl -s -o /dev/null -w '%{http_code}\n' -H "Authorization: Bearer $OLD_TOKEN" https://<dominio-staging>/api/me/sessions` devuelve `401`.
  - [ ] El front en staging renueva solo la sesión (refresh) o pide login; el login nuevo funciona.
  - [ ] `OLD_LINK` de baja sigue funcionando.
  - [ ] Rollback: valor anterior, `sc up -d backend`, el login funciona.
- [ ] **BROADCAST_UNSUBSCRIBE_SECRET**: cambiarlo, `sc up -d backend`; `OLD_LINK` ahora da enlace inválido (esperado);
  un enlace nuevo funciona. Vaciar el valor y `sc up -d backend`: el backend no arranca (`sc logs backend` muestra
  `broadcast.unsubscribe.secret es obligatorio en el perfil prod`); restaurarlo.
- [ ] **JWT_SECRET faltante**: vaciarlo, `sc up -d backend`: falla el arranque; restaurarlo.
- [ ] **POSTGRES_PASSWORD**: backup previo, `ALTER USER`, editar `.env.staging`, `sc up -d`, backend `healthy`, backup manual OK.
  Probar también el error esperado al omitirla (`POSTGRES_PASSWORD es obligatoria`).
- [ ] **Brevo** (solo si se probó con casilla de prueba): rotar clave y mail de prueba.
- [ ] Anotar fecha, quién lo corrió y desvíos al pie de este documento antes de repetirlo en producción.
