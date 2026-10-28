# Entorno demo — base separada con datos de una institución completa

Rama **`demo/seed-data-v2`** (sobre develop, nunca se mergea a develop). Base Postgres propia (puerto 5433) y perfil Spring `local,demo`; no toca la base de desarrollo.

## Levantarlo (comandos exactos)

Docker Desktop tiene que estar corriendo. Desde la raíz del repo:

```bash
docker compose --profile demo up -d postgres-demo
cd backend && JWT_SECRET=dev-secret-local SPRING_PROFILES_ACTIVE=local,demo ./mvnw spring-boot:run
npm --prefix frontend run dev
```

El `mvnw` está en `backend/`. La primera vez el seed académico tarda entre 6 y 11 minutos (log `Seed demo listo en ... ms`) y después el seed de facturación unos 2-3 minutos más (`Seed demo de facturacion listo en ... ms`); recién ahí hay datos de plata. Las veces siguientes no hace nada. Para empezar de cero: `docker compose --profile demo down` y `docker volume rm <proyecto>_postgres_demo_data`. Para sembrar solo lo académico: `-Dspring-boot.run.jvmArguments=-Ddemo.skip-billing=true`.

Login por API: `POST http://localhost:8080/auth/login` con `{"username":"director.demo","password":"Test1234"}` devuelve `accessToken` (usar `Authorization: Bearer ...`).

## Cómo se siembra la facturación (`DemoBillingSeeder`)

Corre como `ApplicationRunner` (orden 200) después del seed académico y usa un reloj de negocio mutable (`DemoClock`, que reemplaza al `Clock` en el perfil `demo`). Recorre marzo a la fecha actual de 2026: cada mes viaja al día 2 y corre `MonthlyInstallmentIssuanceJob.run(YearMonth)`, el día 8 registra pagos en efectivo con `RegisterCashPaymentOperation` a través de `BillingOperationPipeline` (con la vista previa y su fingerprint reales) y el día 20 corre `InvoiceOverdueSweepService.run(fecha)`, que detecta vencidas y genera las notas de débito por mora. Los estados (pagada, vencida, recargo, saldo de cuenta) los calcula el motor; no hay estados fijados a mano.

Por SQL / publicación directa (no hay operación de servicio equivalente sin pasar por autorización o pantalla): las versiones de configuración (mora global y calendario anual por carrera, con `ConfigurationVersion.publish`) y los precios (`price` y `price_version` insertados por SQL, igual que `PriceFixtures` de los tests). Los 6 comportamientos de pago (al día x3, deja de pagar en julio, no paga el último mes, nunca paga) se reparten por orden entre los alumnos con carrera.

## Qué carga el seed

Código en `backend/src/main/java/com/red_escuela/backend/config/demo/` (`DemoSeedRunner` + un `Demo*Seeder` por dominio), activo solo con `@Profile("local & demo")`. No corre si la base ya tiene una institución.

**Principio: los estados no se inventan, los calcula el sistema.** Cada cursada tiene un resultado planificado (`DemoOutcome`), pero las notas se cargan con `GradeItemService.loadProgramEnrollmentGrade`, el mismo método que usa el docente, y es el motor de evaluación el que decide si la materia queda promocionada, regular, aprobada o desaprobada.

- **Oferta:** 7 carreras (6 terciarias de 3 años y la Licenciatura en Gestión Educativa de 4) y 7 cursos (online, presenciales de 3, 6 y 10 meses, híbridos), visibles en el catálogo, con precios en `price_revision` y cohortes 2027 con admisión abierta. El Profesorado de Educación Inicial y la Tecnicatura en Análisis de Sistemas tienen historia desde 2023; el resto arranca en 2026.
- **Esquemas de evaluación:** por posición en el año, alternan promocional (2 parciales y 3 TP, promociona con 7 en cada parcial) y con final obligatorio (2 parciales y 2 TP, regulariza con promedio 4); las prácticas usan su propio esquema (1 parcial y 3 TP, regulariza con 6, coloquio final). Un solo intento de final regular por materia (`maxFinalExamAttempts = 1`).
- **Historia académica:** cada cursada terminada tiene todos sus parciales y TP con fechas dentro del año. Quien desaprueba recursa al año siguiente, y nadie cursa una materia sin tener aprobada su correlativa. Las cursadas 2026 tienen solo las notas que ya pasaron (el segundo parcial es en noviembre).
- **Mesas:** mesas históricas de diciembre y febrero, cerradas, con las inscripciones calificadas y cada nota de final vinculada a su mesa. Mesas de noviembre 2026 abiertas solo para alumnos REGULAR (los únicos que el backend deja inscribir).
- **Asistencia:** de todas las cursadas desde 2023, con una cantidad de faltas acorde al resultado (quien promocionó nunca supera el máximo de inasistencias).
- **Egresos:** la cohorte 2023 completó el plan en 2025: un egresado con certificado y solicitudes de egreso en cada estado. Los egresados dejan de recibir cuotas al terminar el plan.
- **Excepciones:** salen de casos reales del seed (dispensa de correlativa para quien la tiene sin aprobar, mesa fuera de término para un regular).
- **Facturación:** ver sección anterior (cuotas de marzo a la fecha, pagos en efectivo, vencidas y recargos).
- **Casos borde:** lista de espera, nombre muy largo, docente sin comisiones, multirol, aspirante sin rol, entre otros.

## Auditoría de coherencia

`docs/context/demo-auditoria.sql` corre 33 reglas sobre la base demo (notas contra condición, correlativas, mesas, asistencia, cuotas, egresos y excepciones) y devuelve cuántos casos viola cada una. Después de tocar el seed, cargarlo en una base vacía y correr la auditoría: todas tienen que dar 0.

```bash
docker exec -i <proyecto>-postgres-demo-1 psql -U postgres -d agustina_bermejo_demo -F' | ' -A < docs/context/demo-auditoria.sql
```

## Usuarios (contraseña de todos: `Test1234`)

| Rol | Usuario |
|---|---|
| Director | `director.demo` |
| Administrativo | `administrativo.demo` |
| Preceptor | `preceptor.demo` |
| Docente | `docente.demo` (22 comisiones), `docente.sincomisiones` |
| Alumno | `alumno.demo` (Lucía Fernández, Profesorado de Educación Inicial) |
| Casos borde | `aspirante.demo`, `espera.demo`, `nombrelargo.demo`, `multirol.demo` |

Cualquier persona del padrón entra con `nombre.apellido` (sin tildes) y `Test1234`.

## Escenarios sembrados (`DemoBillingScenarios`, con operaciones reales del pipeline)

Corren al final del seed de facturación con el usuario correspondiente en el `SecurityContext` (administrativo o director). Cada uno se registra en el log como `Seed demo escenario ok|fallido: ...`; si uno falla, el resto sigue.

| Escenario | Qué queda en la base | Alumnos |
|---|---|---|
| Transferencia pendiente de revisión | comprobante subido, pago pendiente | 1 alumno que nunca paga |
| Transferencia aprobada | pago confirmado por el administrativo | otro alumno que nunca paga |
| Transferencia rechazada | pago rechazado con motivo | otro más |
| Reembolso pendiente de autorización | pedido del administrativo en la bandeja del director | alumno al día con saldo a favor (cobro extra de 80.000) |
| Reembolso emitido (aprobado) | emitido por el director | otro alumno |
| Reembolso completado | `COMPLETE_REFUND` | otro alumno |
| Reembolso fallido | `FAIL_REFUND` con motivo | otro alumno |
| Cancelación de cuota (pedido) | `CANCEL_INVOICE` iniciado por el administrativo, pendiente | alumno que dejó de pagar en julio |
| Nota de crédito (pedido) | `ISSUE_CREDIT_NOTE` iniciado por el administrativo, pendiente | otro alumno que dejó de pagar |
| Contracargo con reversión | `REGISTER_CHARGEBACK` y `REVERSE_CHARGEBACK` | alumno al día |
| Contracargo vigente | contracargo sin revertir | otro alumno al día |
| Beca 50% | descuento individual (nov 2026 a mar 2027) | alumno al día |
| Promoción de cohorte | definición `COHORT_PROMOTION` con cupo 10 y 2 matrículas adquiridas (cupo parcialmente usado) | 2 alumnos de la primera cohorte |
| Baja con deuda | `ProgramEnrollmentService.withdraw` de un alumno que nunca pagó | 1 alumno |

Avisos de cobranza: se publican por publicación directa de `ConfigurationVersion` el aviso global (`COLLECTION_REMINDERS`) y el cronograma inicial de carreras y cursos, y desde agosto se corre `CollectionReminderPlanningJob.run(fecha)` día por día con el `DemoClock`. Resultado: unos 170 recordatorios y unos 150 mails simulados (`notification_delivery`, estados `SENT` y `QUEUED`), y la lista de contacto de morosos.

## Guion de demo por rol

**Director (`director.demo`)**
- Tablero financiero con selector de mes (marzo a octubre 2026): paneles issued-invoices, overdue-detected, late-fees, receipts, collections-stock, on-time-payment, discounts.
- Aprobaciones > Operaciones de facturación: bandeja con 3 pedidos pendientes del administrativo (reembolso, nota de crédito sobre una cuota de julio, cancelación de cuota). Aprobar o rechazar desde ahí.
- Ficha del alumno > Financiero: reembolsos en estado aprobado, completado y fallido (alumnos con saldo a favor); cuotas pagadas, vencidas y notas de débito por mora.
- Facturación del staff > Pagos del alumno: contracargo vigente, contracargo revertido y revocación de aprobación de transferencia.
- Lista de morosos para contactar (`GET /collection-reminders/contact-list`, 4 alumnos con 9 cuotas vencidas) y los mails simulados.

**Administrativo (`administrativo.demo`)**
- Aprobaciones: los mismos 3 pedidos que inició, en estado pendiente.
- Comprobantes de transferencia: uno pendiente de revisión para aprobar o rechazar en vivo; hay uno ya aprobado y uno rechazado.
- Ficha del alumno > Financiero: registrar pago en efectivo y pedir un reembolso (queda pendiente de autorización).

**Alumno (`alumno.demo` y cualquiera con carrera)**: sus cuotas pagadas y vencidas con recargo, y su estado de cuenta.

**Preceptor y docente**: lo académico (padrón, oferta, mesas, notas, asistencia).

**Verificado por API** (login real `POST /auth/login`, token en `Authorization: Bearer`): `GET /authorization-requests?status=PENDING` devuelve 3 pedidos tanto con director como con administrativo; `GET /students/{id}/refunds` devuelve reembolsos aprobado y fallido; `GET /collection-reminders/contact-list` devuelve morosos; `GET /billing/metrics/{panel}?from=2026-03&to=2026-10` devuelve 8 meses; `GET /notification-deliveries/simulated` (solo perfiles local y demo) lista los mails simulados con asunto y cuerpo.

**Sin pantalla / alternativa por curl:** descuentos, promociones y definiciones de cohorte, avisos de cobranza (cronograma), precios y períodos: usar los controllers de `billing/*` con el token del director. Swagger no se verificó.

**Qué no está sembrado**: el resumen post-baja (la baja existe pero el resumen no se generó con el reloj de negocio), cursos facturados (solo carreras), años anteriores a 2026, pagos online simulados, notas de crédito ya emitidas (solo el pedido pendiente), alertas de configuración. Los comprobantes de transferencia apuntan a una ruta de archivo que no existe en disco, así que la vista previa del archivo no abre.
