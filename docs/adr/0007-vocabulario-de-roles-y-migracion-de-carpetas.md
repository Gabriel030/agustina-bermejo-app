# ADR 0007: vocabulario de roles y migración de carpetas del backend

Estado: aceptado (7-nov-2026, decisiones de Gabriel). Enmienda al [ADR 0002](0002-patron-de-modulos.md) en lo que toca a `service`, `Port` y `package-info.java`; no lo reemplaza.

## Contexto

Tres análisis de solo lectura (7-nov) midieron el backend: `service/` mezcla especialistas, fachadas, adaptadores, jobs y listeners; el sufijo `Service` nombra dos roles (304 implementaciones de caso de uso y 72 especialistas); `Port` significa dos cosas; `usecase/<subdominio>` repite el nombre del módulo en `attendance` y `announcement`; los 36 `package-info.java` no documentan nada.

## Decisión

1. **`service/` pasa a `specialist/`** en todos los módulos, sin sacar antes los jobs y listeners (todos son especialistas). Un módulo por PR, billing al final y por subdominio.
2. **`Service` significa una sola cosa**: la implementación de un caso de uso (`XxxService implements XxxUseCase`, con `@UseCase`). Los especialistas que hoy se llaman `*Service` pasan a un sustantivo de rol: `Finder`, `Reader`, `Recorder`, `Calculator`, `Resolver`, `Loader`, `Builder`, `Checker`, `Exporter`, `Submitter`.
3. **`Port` solo para interfaces invertidas** en `api` (las implementa otro módulo). Su implementación se llama `*PortAdapter` y vive en `specialist/` del módulo superior. Los especialistas concretos de `api` no llevan `Port` ni `Adapter`.
4. **`usecase/`**: el subpaquete nunca repite el nombre del módulo; si el módulo tiene un único flujo, `usecase/` va plano (`academicrecord`). Se quita `@NamedInterface("api")` de los `usecase/package-info.java`; en `enrollment` queda hasta que billing deje de usar sus casos de uso (quitarlo hoy sube `billing -> enrollment` de 143 a 153 accesos, y la línea base no puede crecer).
5. **Documentación primero**: cada paquete lleva `package-info.java` con cuatro rótulos en castellano (Qué es / Expone / No hacer / Ejemplo). Se escribe con cada módulo que se migra. La excepción a "cero comentarios y cero javadoc" vale SOLO para `package-info.java`; la hace cumplir `JavadocOnlyInPackageInfoTest` (regla N6).
6. **Herramienta**: `scripts/refactor/migrate_module.py` (modo `--dry-run`) hace `git mv`, reescribe package/imports en main y test, las tiendas congeladas, la línea base de Modulith y las referencias por nombre completo, y verifica compilación y ArchUnit sin líneas congeladas nuevas. Los mapas por módulo están en `scripts/refactor/maps/`.
7. **Reglas ArchUnit en transición**: R1, R3, R4, R10 y `plainServicesAreNotUseCases` aceptan `..service..` y `..specialist..`. Mantienen la descripción original (`.as(...)`) para que `stored.rules` no cambie de clave. Al cerrar la migración se deja solo `specialist` y se re-indexan las descripciones una vez, con autorización.

## Plan de migración

| PR | Contenido | Tamaño |
|---|---|---|
| 0 (este) | Docs, herramienta, reglas en transición, N6, cambios chicos | ~100k |
| 1 | `academicrecord` (28 archivos movidos) + `announcement` | ~50-70k |
| 2 | `attendance` (renombres de clase y `usecase/record`) | ~60-80k |
| 3 | `platform` + `notification` | ~50-70k |
| 4 | `evaluation` + `graduation` | ~70-90k |
| 5 | `identity` (el más importado) | ~60-80k |
| 6 | `academicstructure` | ~70-90k |
| 7 | `enrollment` (incluye `usecase` y la salida de billing) | ~70-90k |
| 8-15 | `billing` por subdominio (8 paquetes) | ~60-90k c/u |
| 16 | Reglas N1-N5 y N7-N8 con cuenta cero; reglas solo `specialist` | ~50-70k |

Cada PR de módulo incluye los `package-info.java` de ese módulo y su diagrama en `docs/context/modules.md`. Criterio de aceptación: el conteo neto de líneas de `archunit-frozen/` no puede crecer ni aparecer una tienda nueva. Los renombres reescriben líneas (cambia el nombre de la clase o del paquete), así que el `git diff` puede mostrar líneas agregadas y quitadas; lo que no puede pasar es que el total aumente.

## Consecuencias

- Una carpeta dice la figura de la cadena y el sufijo dice el rol exacto.
- Durante la transición conviven `service` y `specialist`; las reglas lo aceptan.
- Las reglas nuevas N1 (Service solo en casos de uso), N2 (sin `..service..`), N3 (sufijo por carpeta), N4 (subpaquete de usecase), N5 (Policy en `policy/`), N7 (package-info obligatorio) y N8 (`PortAdapter`) quedan para los PRs de módulo y el cierre; N6 ya está activa.

## Estado de la migración

| PR | Estado | Qué se hizo |
|---|---|---|
| 1 | hecho | `academicrecord`: `service` a `specialist` (6 archivos), `usecase/studentrecord` aplanado a `usecase` (20 archivos), `AcademicRecordEnrollmentOwnership` a `...Checker`. `announcement`: `service` a `specialist` (incluye `audience`), `usecase/announcement` a `usecase/broadcast` (el subpaquete ya no repite el módulo), seis renombres de rol (`AudienceService` a `AudienceSegmentResolver`, `SectionMembership` a `...Checker`, `ScheduledAnnouncementRowProcessor` a `...RowDispatcher` con `process` a `dispatchRow`, `StudentAnnouncementReadTimes` a `...Reader`, `CurrentAnnouncementAuthor` a `...Finder`, `AnnouncementSectionNames` a `...Resolver`). `package-info.java` con los cuatro rótulos en todos los paquetes de `announcement`. |
| 2 | hecho | `attendance`: `service` a `specialist` (21 clases), `usecase/attendance` a `usecase/record` (74 archivos; el subpaquete ya no repite el módulo) y `usecase/attendancechangerequest` a `usecase/changerequest` (11 archivos). Cuatro renombres de rol: `AttendanceExportService` a `AttendanceExporter`, `AttendanceChangeRequestSubmission` a `...Submitter`, `AttendanceSectionAccess` a `...Checker`, `EnrollmentAbsenceCounts` (en `api`) a `...View`. `AttendanceSameDayCorrectionPolicy` pasa a `attendance/policy`; para cumplir `policiesArePure` se le sacó `@Service` y `Clock` (recibe `today` por parámetro) y `SectionAttendanceRecorder` la instancia con `new`, como hace `enrollment` con sus políticas. `package-info.java` con los cuatro rótulos en `specialist`, `policy`, `usecase/record` y `usecase/changerequest`. |
| 3 | hecho | `platform`: `service` a `specialist` (20 archivos), `ProvisioningRequestFingerprint` a `...Calculator`. `notification`: `service`, `delivery/service` y `reporting/service` a `specialist`; las 73 clases de la raíz repartidas en `specialist` (listeners, compositores, despachador, canales, fuentes de despacho) y `domain` (las 30 `XxxNotification`, destinatarios, tema, evento); `NotificationService` a `NotificationInboxManager`, `NotificationDeliveryService` a `...Enqueuer`, `EmailDispatchPlanningService` a `...PlanRecorder`, `CollectionReminderMetricQuery` a `...Reader`. `abbreviateToColumnLength` pasa a `Notification` (rompía el ciclo `delivery` y `specialist`). `package-info.java` con los cuatro rótulos en todos los paquetes con clases. |

Cambios en la herramienta: el renombre de clases ahora alcanza imports y nombres completos (antes dejaba intactas las referencias precedidas por un punto). No hizo falta mover una clase suelta a otro paquete, así que ese soporte queda para el módulo que lo pida. Las tiendas congeladas y la línea base de Modulith no crecieron. PR 2: el mapa admite `move a.b.Clase -> a.c` para mover una clase suelta a otro paquete (reescribe su `package`, los nombres completos y agrega el `import` a los que antes la veían por estar en el mismo paquete; los imports que necesita la clase movida se completan a mano).

PR 3: los listeners de `notification` conservan su nombre porque `*_HANDLER` (clave persistida de idempotencia) lo incluye. La herramienta tuvo que corregirse a mano en un punto: con varios `move` a destinos distintos dejaba el `package` de todos en el último destino. El controller `MeNotificationController` sigue llamando directo a un especialista (violación congelada); partirlo en dos casos de uso agregaría una línea al almacén.
