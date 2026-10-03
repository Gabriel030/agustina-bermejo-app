# Entorno demo — base separada con datos de una institución completa

Para mostrar la app, hacer QA funcional o probar pantallas con datos realistas sin tocar la base de desarrollo. Vive en la rama **`demo/seed-data`** (rama reutilizable, no se mergea a develop): para usarlo sobre otra rama, traer sus commits con `git cherry-pick` o rebasear la rama sobre develop.

## Dos bases locales que no se mezclan

| | Base de desarrollo | Base demo |
|---|---|---|
| Servicio de `docker-compose.yml` | `postgres` | `postgres-demo` (solo con `--profile demo`) |
| Puerto / base | `5432` / `agustina_bermejo` | `5433` / `agustina_bermejo_demo` |
| Volumen | `postgres_data` | `postgres_demo_data` |
| Perfil de Spring | `local` | `local,demo` |
| De dónde sale la URL | `DB_URL` (`application.properties`) | `DEMO_DB_URL` (`application-demo.properties`); **ignora `DB_URL` a propósito** |

La separación la decide el perfil: con `demo` activo, `application-demo.properties` pisa la URL de la base, así que un `DB_URL` olvidado en la terminal no puede mandar los datos demo a la base de desarrollo. Sin el perfil `demo`, todo sigue igual que siempre. En producción el perfil `demo` nunca se activa: el seed no existe ahí.

## Levantarlo

Desde la raíz del repo, en una rama que tenga los commits de `demo/seed-data`:

```bash
docker compose --profile demo up -d
cd backend && JWT_SECRET=dev-secret-local SPRING_PROFILES_ACTIVE=local,demo ./mvnw spring-boot:run
npm --prefix frontend run dev
```

El `mvnw` está en `backend/`, no en la raíz. Si el `docker compose` no levanta un contenedor `postgres-demo`, la rama en la que estás no tiene el servicio: cambiá a `demo/seed-data`.

La primera vez el seed tarda ~30 s y lo informa en el log (`Seed demo listo en ... ms`). Las veces siguientes no hace nada (`Seed demo: ya hay datos cargados`). Para empezar de cero: `docker compose --profile demo down` y borrar el volumen con `docker volume rm <proyecto>_postgres_demo_data`.

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
- **Facturación:** 9 perfiles (al día, una cuota vencida, suspendido, pago anticipado, beca, comprobante pendiente, comprobante rechazado, Mercado Pago y promoción). Toda cuota impaga vencida queda vencida con recargo, y el estado de cuenta (al día, moroso o suspendido) se calcula desde las cuotas reales.
- **Casos borde:** lista de espera, nombre muy largo, docente sin comisiones, multirol, aspirante sin rol, entre otros.

## Auditoría de coherencia

`docs/context/demo-auditoria.sql` corre 37 reglas sobre la base demo (notas contra condición, correlativas, mesas, asistencia, cuotas, egresos y excepciones) y devuelve cuántos casos viola cada una. Después de tocar el seed, cargarlo en una base vacía y correr la auditoría: todas tienen que dar 0.

```bash
docker exec -i <proyecto>-postgres-demo-1 psql -U postgres -d agustina_bermejo_demo -F' | ' -A < docs/context/demo-auditoria.sql
```

## Usuarios (contraseña de todos: `Test1234`)

| Usuario | Para qué |
|---|---|
| `alumno.demo` | Lucía Fernández, 3er año del Profesorado de Educación Inicial: 10 de 18 aprobadas (dos recursadas: Pedagogía y Alfabetización Académica), Literatura Infantil regular con el final pendiente, 7 materias en curso (una en riesgo de quedar libre) y una cuota vencida |
| `docente.demo` | 22 comisiones, configuraciones de evaluación, avisos, asistencia |
| `preceptor.demo` | Padrón, oferta académica con ciclos en los 4 estados, mesas |
| `administrativo.demo` | Comprobantes pendientes, cuenta corriente, banco de materias |
| `director.demo` | Bandeja de aprobaciones cargada y tablero financiero |
| `aspirante.demo`, `espera.demo`, `nombrelargo.demo`, `docente.sincomisiones`, `multirol.demo` | Casos borde |

Cualquier otra persona del padrón entra con `nombre.apellido` (sin tildes) y `Test1234`; por ejemplo `tomas.aguirre` (mora profunda). El detalle de cada caso borde está en `DemoEdgeCaseSeeder.java`.
