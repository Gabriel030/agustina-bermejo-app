# Registro de pruebas locales

Las herramientas pesadas (PIT, Playwright, k6, OWASP Dependency-Check) **no corren en GitHub Actions**: se corren en local mientras se programa, con el semáforo de CI. Este archivo deja constancia de qué se corrió, sobre qué código y con qué resultado, para que quien vuelva a tocar esa área sepa qué tiene que volver a correr.

Reglas:

- Una fila por corrida, la más reciente arriba. No se borran filas.
- Todo PR que toca el dominio de billing (`billing/`, `shared/domain/`) o el `pom.xml` agrega al menos la fila de PIT; el job `local-tests-log` del CI falla si no lo hace.
- **Commit**: el SHA sobre el que se corrió (antes del commit que agrega la fila).
- **Área**: paquetes o flujos que cubre la corrida; es lo que se busca acá antes de modificar algo.
- Si una corrida falla y se arregla, se anotan las dos (la roja dice qué encontró).

| Fecha | PR | Commit | Área | Herramienta | Comando | Resultado | Notas |
|---|---|---|---|---|---|---|---|
| 2026-10-08 | #180 | `d445762d` | billing (webhook de Mercado Pago, pagos, cuenta corriente), enrollment (matrícula de ingreso), shared/domain | PIT | `./mvnw clean verify -P mutation` | ✅ 91% (1051/1150), umbral 90; `./mvnw clean verify` 2026 tests, 0 fallas | Tercera ronda de revisión de V2-02d1 (webhook MP sobre matrícula de ingreso, listener tras commit, D-229/D-230) |
| 2026-10-08 | #180 | `61ed0edb` | billing (invoice, account, operation, policy/issuing), shared/domain | PIT | `./mvnw -P mutation test-compile org.pitest:pitest-maven:mutationCoverage` | ✅ 91% (1051/1150), umbral 90 | Corte V2-02d1, antes de las correcciones de revisión |
