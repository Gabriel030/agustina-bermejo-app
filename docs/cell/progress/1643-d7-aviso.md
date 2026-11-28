# 1643 · D7-aviso (backend-4)

- [x] Aviso in-app al alumno por constancia regular o de materias aprobadas invalidada (baja de carrera, cambio de aprobación o regularidad, equivalencia anulada)
- [x] NotificationType CERTIFICATE_INVALIDATED (V509 NOT VALID + V510 VALIDATE), sin preferencia configurable
- [x] Una notificación por constancia revocada: el evento sale solo cuando la constancia pasa de VALID a REVOKED
- [x] Tests por el camino real, listener, IT asíncrono (idempotencia y rollback), frontend (destino y label)
- [x] Sabotaje: sin publishEvent en el revocador regular, 2 tests de CertificateInvalidatedNoticeIT fallan
- [x] Suite completa backend: 18786 tests, 1 fallo (lista de pre-migration-checks) corregido y reverificado aislado

## Decisiones
- Desacoplado: @ApplicationModuleListener corre después del commit; si el aviso falla no revierte la baja ni la revocación (se reintenta por el registro de publicaciones).
- Revocaciones masivas: un aviso por constancia, nunca por materia ni por disparador.
- Sin bloque nuevo en pre-migration-checks: el CHECK solo agrega un valor, V510 no puede abortar; solo se actualizó la lista de notification.type.
