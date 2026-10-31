# ADR 0005 — Venta por instituto: infraestructura separada y módulos activables

> Verificado: 2026-10-31, commit `cd1ede80`.

- **Estado:** aceptado (decisiones de Gabriel del 3-oct-2026; el módulo `platform` y el rol `PLATFORM_ADMIN` como diseño aceptado el 31-oct-2026). **Aún no implementado en código** (Épica 19, olas 1A y 1B pendientes).
- **Fuentes:** Épica 19 del vault, `Decisiones del apagado de billing (31-oct)` y `Auditoría del apagado de billing y plan de la consola (31-oct)`.

## Decisión

1. **Infraestructura separada por instituto, sin excepciones:** cada instituto tiene su app, su base y su dominio. No hay multi-tenant compartido.
2. **Un solo código para todos:** sin forks por instituto; cada despliegue registra qué versión corre para hacer rollout escalonado.
3. **Módulos como add-ons.** Núcleo (siempre activo): identity, academicstructure, enrollment, attendance, evaluation, graduation, announcement, notification. Add-ons: facturación (`billing`), factura electrónica (`fiscal-invoicing`, requiere billing activo) y analytics. Todo módulo opcional se construye asumiendo que puede estar apagado.
4. **Módulo `platform` y rol `PLATFORM_ADMIN`:** solo `PLATFORM_ADMIN` activa o desactiva módulos (el director no, aunque sí configura dentro de un módulo habilitado). El rol no se asigna desde la app del instituto, no accede a datos de alumnos (D-484) y cada cambio queda auditado con motivo (D-483, D-487). Esas decisiones están en el registro del vault y todavía no figuran en `docs/billing/decision-registry.md`.
5. **Mecanismo:** tabla de habilitaciones por instituto (`platform_feature`, con `feature_change_log`), snapshot en memoria con TTL de 30 s, guardia `@RequiresModule` que responde 404 como si el endpoint no existiera, `GET /me/modules` para que el front esconda menús y rutas, y puertos con "modo apagado" (sin facturación, sin deuda). Los datos no se borran al apagar un módulo.
6. **Al prender billing después:** se factura solo hacia adelante desde la fecha de activación, sin backfill.
7. **Webhook de Mercado Pago con billing apagado:** responde 200 y persiste el evento; es la única excepción explícita a la garantía de cero escrituras.
8. `ProgramCohort` pasa al lado académico para que el apagado no impida inscribirse a carreras.

## Consecuencias

- Reglas de ArchUnit R-G1/R-G2 congeladas para frenar el crecimiento de código de billing sin guardia.
- La excepción del webhook debe conciliarse con D-485 del registro de decisiones del vault (pendiente).

## Falta confirmar

- Consola central o pantalla dentro de cada instituto (decisión postergada el 3-oct-2026).
- Cuenta única de `PLATFORM_ADMIN` y su política de MFA (supuesto: la de Gabriel, sin MFA).
- Número de migración y estado real de las olas 1A/1B al momento de leer este ADR.
