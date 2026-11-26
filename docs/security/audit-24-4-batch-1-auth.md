# Auditoría 24.4, lote 1 (identidad: H6, H7, H20)

Rama `feature/e24-4-audit-batch-1-auth`, 26-nov-2026. Se leyeron antes `audit-2026-11-03-status.md` y `pentest-informe-2026-11.md`: lo ya cerrado se informa como verificado, sin cambio.

| Tema | Hallazgo | Estado | Evidencia |
|---|---|---|---|
| H6 timing | Usuario inexistente, cuenta bloqueada, sin contraseña o inactiva comparan contra un hash dummy del mismo costo que el real | Verificado, sin cambio | `CredentialLoginTransaction.login`, `DummyPasswordHash` |
| H6 enumeración | Mismo 401 y mismo cuerpo para inexistente, bloqueada e inactiva | Verificado, sin cambio | `AuthPentestIT` (L1, L2), #1366 |
| H6 bloqueo por cuenta | `auth.lockout.max-attempts` y `duration-minutes`; límite de login por IP; IP real por `remoteip` de Tomcat con `TRUSTED_PROXIES_REGEX` | Verificado, sin cambio | `CredentialLoginTransaction`, `application.properties:148-151`, `docs/deploy/README.md` |
| H7 jti e iss | El access token lleva `jti` único e `iss`, y se exige el `iss` al validar | Verificado, sin cambio | `JwtServiceClaimsTest` |
| H7 aud | El token no llevaba `aud` ni se exigía | **Corregido**: se emite `aud` (`jwt.audience`, default `agustina-bermejo-app-api`) y se exige al validar | `JwtServiceClaimsTest.tokenForAnotherAudienceIsRejected` (falla sin `requireAudience`, sabotaje hecho) |
| H7 estado y roles por request | `AccessTokenVerifier` revalida estado `ACTIVE` y `tokenVersion` contra `AccountAccessStateCache` (TTL `auth.access-state-cache-seconds`) en cada request. Los roles viajan en el token: no existe flujo que quite o cambie roles de una persona existente (solo `add` al crear), y los retiros revocan sesiones con `SessionRevoker` | Verificado, sin cambio | `AccessTokenVerifier`, `SessionRevoker`, `AccessTokenVerifierTest` |
| H7 clave mínima | Solo se exigía largo 32: `aaaa…` (32 o más) arrancaba | **Corregido**: además se exigen al menos 10 caracteres distintos | `JwtSecretPolicyTest.secretWithFewDistinctCharactersIsRejected` |
| H20 registro | 409 y límites por IP y por IP+usuario ya cerrados (G6, #1146); riesgos residuales aceptados por el Coordinador (opción a) | Verificado, sin cambio | `RegisterEnumerationIT`, `LoginRateLimiterTest` |

Compatibilidad: al desplegar, los access tokens vigentes (15 min) sin `aud` se rechazan con 401 y el front renueva con el refresh; no cambia contrato OpenAPI. Los secretos reales (`openssl rand -base64 48`) cumplen la nueva política.

Pendiente no corregido: ninguno confirmado en este lote.
