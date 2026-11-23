# E24 S3-a: corte temprano con circuit breaker abierto

- [x] `CircuitBreaker.isRejectingCalls()`: OPEN con espera vigente; falso con CLOSED, HALF_OPEN, OPEN vencido y `enabled=false`
- [x] Corte en `EmailDeliveryDispatchJob`, `AnnouncementEmailDispatchJob`, `BroadcastEmailDispatchJob`, `BroadcastWhatsAppDispatchJob`
- [x] Tests por job: CB abierto no toca entregas; cierre a mitad de lote; CB no abierto procesa normal
- [x] Tests del CB: cerrado, abierto, espera vencida, half-open, deshabilitado
- [ ] CI local verde
