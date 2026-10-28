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
| Nota de crédito emitida | `ISSUE_CREDIT_NOTE` por error de facturación, emitida directamente por el director | alumno que dejó de pagar |
| Pago online simulado | `REGISTER_GATEWAY_PAYMENT` con `MERCADO_PAGO` y estado `approved` (sin pasarela real) | último alumno que dejó de pagar |
| Contracargo con reversión | `REGISTER_CHARGEBACK` y `REVERSE_CHARGEBACK` | alumno al día |
| Contracargo vigente | contracargo sin revertir | otro alumno al día |
| Beca 50% | descuento individual otorgado el 12/08/2026 desde sep 2026 hasta mar 2027; baja el importe de las cuotas de sep y oct | alumno al día |
| Promoción de cohorte | definición `COHORT_PROMOTION` con cupo 10, creada el 15/02/2026 antes de emitir las matrículas; 2 matrículas adquiridas (cupo parcialmente usado) y su descuento (20%) aparece en la factura de matrícula | 2 alumnos de la primera cohorte |
| Baja con deuda | `ProgramEnrollmentService.withdraw` el 12/08/2026 (con `DemoClock`) de un alumno que nunca pagó; el planificador genera 2 resúmenes post-baja | 1 alumno |

Avisos de cobranza: se publican por publicación directa de `ConfigurationVersion` el aviso global (`COLLECTION_REMINDERS`) y el cronograma inicial de carreras y cursos, y desde agosto se corre `CollectionReminderPlanningJob.run(fecha)` día por día con el `DemoClock`. Resultado: unos 170 recordatorios y unos 150 mails simulados (`notification_delivery`, estados `SENT` y `QUEUED`), y la lista de contacto de morosos.

## Guion de demo por rol (rutas verificadas en el navegador el 27/10/2026)

**Director (`director.demo`)**
- `/director/financiero`: tablero con selector de mes (Octubre 2026 por defecto), pago a término, facturas emitidas, recargos, deuda total, alumnos con deuda vencida (16 de 38) y gráficos de cobranza, emisión, comprobantes.
- `/director/aprobaciones`: tarjetas de tramites; "Operaciones de facturación" tiene 3 pendientes y "Ver los 3 pendientes" lleva a `/director/aprobaciones/facturacion` (pestañas Pendientes e Historial). Cada fila abre `/director/aprobaciones/facturacion/{id}` con el detalle y los botones Rechazar y Aprobar.
- `/director/alumnos/{id}?view=financiero` (ficha del alumno > Financiero): matrícula, saldo a favor, reembolsable ahora y la tabla de Reembolsos. Ejemplos: alumno 1 (Lucía Fernández, reembolso pendiente de aprobación), alumno 8 (Sofía Castro, reembolso "No se pudo"), alumno 2 (aprobado), alumno 3 (completado). Las cuotas no se listan en esta pestaña.
- `/director/facturacion` > Estado de cuenta > buscar al alumno por apellido (por ejemplo "Juárez") > abre el estado de cuenta con todas las cuotas (con recargo por mora y descuentos) y al pie "Pagos del alumno", donde se hacen contracargos y revocaciones. Juárez, Lucas tiene un pago contracargado vigente; Coronel un contracargo revertido.
- `GET /collection-reminders/contact-list` (sin pantalla verificada) y `GET /notification-deliveries/simulated` para ver los mails simulados.

**Administrativo (`administrativo.demo`)**
- `/administrativo/aprobaciones`: "Mis pedidos (3)" (nota de crédito, cancelación de factura y reembolso, con el botón Retirar) e Historial.
- `/administrativo/facturacion?tab=comprobantes`: 1 comprobante pendiente de revisión (Lautaro Carrizo) con Ver comprobante, Aprobar y Rechazar. En Pagos del alumno se ven los comprobantes ya aprobado (Luna) y rechazado (Herrera). El archivo del comprobante es un PNG mínimo real (`students/{id}/comprobantes_demo-*.png` bajo `app.uploads.directory`); `GET /bank-transfer-receipts/{id}/file` devuelve 200 `image/png`.
- `/administrativo/facturacion` > Estado de cuenta: Cobrar en efectivo y Pedir reembolso (queda pendiente de autorización).

**Alumno (`alumno.demo`)** y **preceptor/docente**: no recorridos en el navegador en esta corrida.

## Verificado por API
`POST /auth/login` (director y administrativo), `GET /authorization-requests?status=PENDING` (3 pedidos en ambos), `GET /students/{id}/refunds`, `GET /collection-reminders/contact-list`, `GET /billing/metrics/{panel}?from=2026-03&to=2026-10`, `GET /notification-deliveries/simulated`, `GET /bank-transfer-receipts/{id}/file`.

## Sin pantalla / alternativa por curl
Descuentos y promociones (definiciones de cohorte), cronograma de avisos de cobranza, precios y períodos: usar los controllers de `billing/*` con el token del director. Swagger no se verificó.

## Qué no está sembrado
Cursos facturados (solo carreras), años anteriores a 2026, alertas de configuración (`billing_configuration_alert`) y un pago online real contra la pasarela (el pago de Mercado Pago es simulado por la operación, sin webhook). La matrícula de marzo de todos los alumnos se paga el día 8, seis días después de vencida, por eso aparece un recargo por mora chico en marzo incluso para los alumnos al día.
