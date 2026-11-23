# F6-back: Sentry inerte por defecto

- [x] SDK `io.sentry:sentry` 8.63.0 (core, captura manual; sin starter de Spring Boot 4)
- [x] DSN vacío: `Sentry.isEnabled()==false` (SentryInertTest)
- [x] Scrubber beforeSend (SentryEventScrubberTest, sabotaje verificado)
- [x] Captura en catch-all, ExternalServiceException y DataIntegrityBrokenException; 4xx/409 no (GlobalExceptionHandlerSentryTest)
- [x] SENTRY_DSN en deploy/.env.example, docs/deploy/sentry.md, docs/README.md
- [ ] dependency-check no corrido (perfil nocturno, requiere NVD)
