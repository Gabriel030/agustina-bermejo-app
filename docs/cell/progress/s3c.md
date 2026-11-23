# E24 S3-c Bulkhead (#1147)

- [x] Bulkhead + BulkheadFactory + BulkheadSettings, fallback a resilience.default.*
- [x] Lleno = CircuitOpenException (subclase privada), sin tocar handler ni catalogo, no cuenta como fallo del CB
- [x] Metricas resilience.bulkhead.rejected / available
- [x] Composicion Bulkhead.executeProtected (bulkhead por fuera del CB)
- [x] Propiedades y defaults mail 4, whatsapp-meta 4, mercadopago 4, google 8
- [x] application-prod.properties con RESILIENCE_ENABLED=true (prod y staging), deploy/.env.example
- [x] Docs overload-protection, runbook de deploy, docs/README
- [x] Tests unitarios y de factory/perfiles; sabotaje del finally verificado
- [ ] Aplicar a integraciones: S3-d
