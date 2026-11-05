# Usuarios de prueba y casos de uso

> Verificado: 2026-10-31, commit `cd1ede80`.

Credenciales **solo de desarrollo local** (nunca prod/staging). El campo de login acepta username o email. Frontend en `http://localhost:5173`, backend en `http://localhost:8080`.

## 1. Cuentas demo (una por rol)

| Rol | Username | Email | Landing tras login |
|---|---|---|---|
| Alumno | `alumno.demo` | `alumno.demo@demo.local` | `/alumno/inicio` |
| Docente | `docente.demo` | `docente.demo@demo.local` | `/docente/inicio` |
| Preceptor | `preceptor.demo` | `preceptor.demo@demo.local` | `/preceptor/inicio` |
| Administrativo | `administrativo.demo` | `administrativo.demo@demo.local` | `/administrativo/inicio` |
| Director | `director.demo` | `director.demo@demo.local` | `/director/inicio` |

**Contraseña según el entorno** (ambas son válidas, en bases distintas):

| Entorno | Contraseña | Por qué |
|---|---|---|
| Base creada por `LocalSeedRunner` (perfil `local`, base nueva) | `Test1234` | `LocalSeedRunner.java:23` |
| Base local de desarrollo del usuario (`agustina_bermejo`, ya existente) | `Demo1234!` | hash reseteado a mano, ver Obsidian `Usuarios de prueba.md` |

Si una no funciona, probar la otra. Para uniformar en una base existente, el comando de reseteo está en la nota de Obsidian `Usuarios de prueba.md` (ubicación en [specs.md](specs.md)); en tests de Karate/JUnit las credenciales viven en `karate-config.js` y en cada test.

Arranque local: `JWT_SECRET="dev-secret-local-solo-para-pruebas" SPRING_PROFILES_ACTIVE=local ./mvnw spring-boot:run` (ver [debugging.md](debugging.md)) y `npm --prefix frontend run dev`.

## 2. Datos que tiene cada cuenta

- Las 5 cuentas del seeder son **solo `Person` + rol**: sin `Student`/`Instructor` ni datos académicos. Login, layout y ruteo por rol funcionan; las pantallas que dependen de datos propios (mis carreras, pagos, comisiones del docente) salen vacías con `alumno.demo` y `docente.demo`.
- **Alumnos con datos más completos** (existen en la base local del usuario, contraseñas desconocidas): `agustin.torres`, `camila.rodriguez`, `mateo.lopez`, `sofia.herrera`, `nicolas.fernandez`. No hace falta loguearse con ellos: se ven desde el padrón como `preceptor.demo`/`administrativo.demo`/`director.demo`.
- **Alumno nuevo con flujo real:** `/registrarse` crea una persona con `Student` confirmado, sin rol hasta la primera inscripción (sirve para probar catálogo e inscripción). Alta de prospecto: `alumno/nuevo` como Preceptor o Administrativo, y verificación de email en `GET /verify-email`.
- **Con perfil `local,demo`** hay alumnos y docente con datos académicos y clave conocida: ver 2a.

## 2a. Seed educativo demo (perfil `local,demo`)

Con `SPRING_PROFILES_ACTIVE=local,demo` el arranque deja datos educativos reales además de las 5 cuentas (clave `Test1234`). Corre una sola vez (idempotente) y el seed de sistema (E2E, perfil solo `local`) no lo incluye. `DemoLearnerAccountsSeeder` (identity) crea los alumnos y el perfil de docente; `DemoEducationalSeedRunner` (config) arma la estructura llamando a la API HTTP real con las cuentas demo.

| Usuario | Sirve para | Datos |
|---|---|---|
| `alumno.demo` | Alumno regular: Mis carreras, notas, asistencia, plan de estudios | Inscripto a "Tecnicatura en Gestión Cultural (Demo)" y a 2 de sus 3 comisiones, 4 clases con 2 inasistencias (una con comprobante enviado por el alumno, pendiente de aprobación) y un parcial con nota 7. La comisión de Políticas Culturales tiene tope de ausentismo del 25 % (el único dato con `/attendance/risk` calculable) |
| `alumno.egreso.demo` | Egreso: solicitar, verificar deuda (administrativo), aprobar (director) | Inscripto a "Tecnicatura en Producción Escénica (Demo)", cursada completa de su única materia, por eso ya es elegible y el sistema le generó la solicitud de egreso en estado REQUESTED |
| `alumno.cursos.demo` | Mis cursos con módulos | Inscripto a "Taller de Fotografía Digital (Demo)" con 3 módulos: 2 aprobados y 1 pendiente |
| `alumno.inscripcion.demo`, `alumno.cupo.demo`, `alumno.notas.demo` | Cuentas de los E2E de inscripción y notas (`e2e/specs/demo`): sin inscripciones propias, cada test les crea una carrera y comisión nueva por API | Sin datos; no usarlas a mano |
| `docente.demo` | Tomar asistencia, configurar evaluación, cargar notas | Titular (LEAD) de las 3 comisiones del ciclo; ya tiene perfil de docente |
| `preceptor.demo` | Aprobar o rechazar la justificación pendiente, oferta académica | Ve en `/attendance/justifications` la justificación de `alumno.demo` |
| `administrativo.demo` | Aprobar o rechazar la justificación pendiente, verificar deuda de egreso, crear la estructura académica | Bandeja `/administrativo/solicitudes-de-asistencia` con 1 justificación |
| `director.demo` | Aprobaciones de dirección | Ciclo "Ciclo lectivo 2026 (Demo)" con inscripción abierta |

Los horarios de las comisiones son texto libre (`schedule`), no franjas con aula.

## 2b. Seed de sistema (E2E)

`SystemTestApplication` (solo `src/test`, nunca en el jar) levanta el backend con Postgres de Testcontainers nuevo en cada arranque: las 5 cuentas `.demo` con `Test1234` (vía `LocalSeedRunner`), más un `Student` para `alumno.demo` con dos cuotas administrativas abiertas de ARS 80.000. Se usa desde `scripts/system-tests.sh`.

## 3. Casos de uso y con qué usuario probarlos

Rutas reales de `ROLE_CONFIG` (`frontend/src/routes/roleConfig.js`) y `FEATURE_ROUTES` (`frontend/src/routes/featureRoutes.jsx`). "Necesita datos" = la pantalla se ve vacía con la cuenta demo pelada.

| Caso de uso | Usuario | Ruta | Necesita datos |
|---|---|---|---|
| Login, logout, layout y menú por rol | cualquier `.demo` | `/login` | No |
| Recuperar contraseña | cualquiera (email de una `Person`) | `/olvide-mi-contrasena` | No |
| Registro propio | sin cuenta | `/registrarse` | No |
| Ver y editar mi perfil / preferencias de notificación | cualquier `.demo` | `/perfil` | No |
| Modo oscuro / tema | cualquier `.demo` | switch en el header | No |
| Explorar catálogo e inscribirme a carrera o curso | `alumno.demo` o cuenta registrada | `/catalogo` | Carreras/cursos cargados |
| Ver mis solicitudes | `alumno.demo` | `/catalogo?tab=mis-solicitudes` | Solicitudes previas |
| Mis carreras, notas, asistencia, plan de estudios | alumno con `Student` e inscripciones | `/alumno/mis-carreras` | Sí (alumno con datos) |
| Pagos: ver cuotas, pagar por transferencia u online | alumno con inscripciones y facturas | `/alumno/pagos` | Sí |
| Tomar asistencia | `docente.demo` | `/docente/tomar-asistencia` | Comisión asignada |
| Configurar evaluación de una comisión | `docente.demo` | `/docente/mis-comisiones/:sectionId` | Comisión asignada |
| Padrón de alumnos y legajo | `preceptor.demo`, `administrativo.demo`, `director.demo` | `/<rol>/alumnos` | Alumnos cargados |
| Alta de alumno (wizard), confirmar prospecto | `preceptor.demo`, `administrativo.demo` | `/<rol>/alumnos/nuevo` | No |
| Oferta académica: carreras, cursos, calendario, nuevo ciclo | `preceptor.demo` | `/preceptor/oferta-academica` | Estructura académica |
| Mesas de examen | `preceptor.demo` | `/preceptor/mesas-de-examen` | Mesas/alumnos |
| Gestión de personal (altas, bajas, reset de contraseña) | `administrativo.demo`, `director.demo` | `/<rol>/personal` | No |
| Facturación: cuotas, comprobantes, estados | `administrativo.demo` | `/administrativo/facturacion` | Facturas generadas |
| Solicitudes de asistencia | `administrativo.demo` | `/administrativo/solicitudes-de-asistencia` | Solicitudes |
| Aprobaciones: configuración de evaluación, egresos, excepciones | `director.demo` | `/director/aprobaciones` | Trámites pendientes |
| Esquemas de evaluación | `director.demo` | `/director/esquemas-evaluacion` | No |
| Resumen financiero y morosos | `director.demo` | `/director/financiero` | Facturas |

Convención: al agregar una pantalla o flujo nuevo, sumar su fila acá (usuario, ruta, datos necesarios).
