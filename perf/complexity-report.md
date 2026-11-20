# Inventario de complejidad algorítmica del backend (32.1, Épica 32)

Solo mide e informa: no se tocó `backend/src/main`. Consultas, N+1 e índices son de la Épica 33 (`perf/index-report.md`): acá solo se marca el solapamiento.

## Cómo reproducir

```bash
cd backend && ./mvnw -q test -Dtest=AlgorithmicComplexityBenchmarkTest -Dperf.benchmark=true -Djacoco.skip=true | grep BENCH
```

Sin `-Dperf.benchmark=true` el test queda deshabilitado (`@EnabledIfSystemProperty`), así que no corre en el CI normal. Archivo: `backend/src/test/java/com/red_escuela/backend/perf/AlgorithmicComplexityBenchmarkTest.java`.

Método: 3 corridas de calentamiento y mediana de 5, `System.nanoTime`, datos sintéticos en memoria, repositorios mockeados con Mockito `stubOnly`. Máquina: Mac local, Java 23, una sola corrida (el ruido de JIT en tamaños chicos es alto; leer el factor entre 10x y 100x, no el primero). Tamaños 1x = realista hoy (700 alumnos), 10x y 100x.

## Hallazgos medidos

Factor = tiempo(n×10) / tiempo(n). Lineal ≈ 10, cuadrático ≈ 100.

| # | Archivo:línea | Variable de tamaño | Actual | Posible | 1x | 10x | 100x | Crecimiento medido | Esfuerzo | Riesgo | Afecta | Benchmark |
|---|---|---|---|---|---|---|---|---|---|---|---|---|
| 1 | `enrollment/api/ProgramProgressReader.java:44` (`approved.contains(entry)` sobre `List` de records) | materias del plan E | O(E·A) | O(E) con `Set` | 2,3 ms (E=40) | 26,9 ms (E=400) | 2231 ms (E=4000) | x11,5 y luego x83: cuadrático al crecer | Bajo | Bajo | Solo tiempo | B2 |
| 2 | `enrollment/api/ProgramProgressReader.java:83` (`coursed.contains(entry)`) y `:81` | materias del plan E | O(E·C) | O(E) con `Set` | 4,2 ms | 26,8 ms | 2301 ms | x6,4 y luego x85,8 | Bajo | Bajo | Solo tiempo | B3 |
| 3 | `enrollment/service/CourseEnrollmentService.java:309` (módulos × inscripciones, `noneMatch`) | módulos M (e inscripciones I≈M) | O(M·I) | O(M+I) con `Set` de módulos aprobados | 0,027 ms (M=10) | 0,262 ms (M=100) | 22,6 ms (M=1000) | x9,6 y luego x86,4 | Bajo | Bajo | Solo tiempo | B4 |
| 4 | `billing/operation/PendingTransferApprovalOutcome.java:14` (`List.contains` dentro de `anyMatch`) | facturas liquidadas por un comprobante S | O(S·R) | O(S+R) con `Set` | 0,09 ms (S=100) | 3,8 ms (S=1000) | 393 ms (S=10000) | x44 y luego x104: cuadrático | Bajo | Bajo (es plata: cambiar la estructura, no la regla) | Solo tiempo, plata cerca | B6 |
| 5 | `enrollment/service/StudentScheduleOverlapGuard.java:87-91` (franjas × franjas) | franjas por sección T | O(T·O) por par de secciones, repetido por inscripción | OK: T real ≤ ~5 | 0,019 ms (T=3) | 0,27 ms (T=30) | 2,1 ms (T=300) | x14,6 y luego x7,9 | Bajo | Bajo | Solo tiempo | B5 |
| 6 | `notification/broadcast/audience/specialist/SegmentResolver.java:46` y `billing/reporting/service/FinancialDashboardReader.java:50` (`retainAll`/`removeAll` sobre `HashSet`) | personas por segmento | O(n) | O(n) (ya óptimo) | 0,55 ms (n=700) | 4,4 ms (n=7000) | 20,2 ms (n=70000) | x8,1 y x4,5: lineal | Ninguno | Ninguno | Solo tiempo | B7 |
| 7 | `attendance/specialist/AttendanceHistoryBuilder.java:49-61,98` (agrupa por inscripción, mapa por fecha, ordena por nombre) | alumnos de la sección S (×40 clases) | O(S·D + S log S) | O(S·D + S log S) (óptimo) | 1,75 ms (S=100) | 10,8 ms (S=1000) | 110,6 ms (S=10000) | x6,2 y x10,2: lineal | Ninguno | Ninguno | Solo tiempo | B8 |
| 8 | `attendance/api/JustificationDeadlinePolicy.java:33-36` + `academicstructure/api/BusinessDayCalendar.java:8-21` (plazo por fecha distinta) | fechas de clase distintas D | O(D·k) (k = días hábiles del plazo) | idem | 0,11 ms (D=40) | 0,31 ms (D=400) | 6,5 ms (D=4000) | x2,7 y x21: más que lineal, sin explicar (ver abajo) | — | — | Solo tiempo | B1 |

### Lectura de los resultados

- **Cuadráticos confirmados (1, 2, 3, 4).** Ninguno duele con tamaños de hoy: un plan tiene ~40 materias (2–4 ms) y una factura de comprobante tiene pocas facturas. El #1/#2 llegan a 2,2 s recién con 4000 materias por plan, que no es un escenario realista; sí lo es el crecimiento del #4 si algún día un comprobante liquida cientos de facturas (393 ms con 10 000). Son arreglos de una línea (`Set` en vez de `List`) pero con baja prioridad: marcar como mejora oportunista.
- **Lo que sí importa en B2/B3 no es el tiempo de CPU sino las llamadas.** El benchmark contó las llamadas al repositorio: `hasSubjectWithStatus` hace **una consulta por materia del plan** (E consultas; 4000 en el tamaño 100x). Es N+1 por plan, por alumno, en cada `calculateProgress`/`hasOnlyFinalExamsLeft`. Eso es de la Épica 33 y se pasa tal cual (ProgramProgressReader.java:81,100,117-122). La cantidad de llamadas es medida (contador), no estimada; el costo en ms de esas consultas contra Postgres no se midió.
- **Sin problema (5, 6, 7).** Crecimiento lineal o despreciable. El #6 está bien resuelto (`Set`); el #5 solo se cuenta en el peor caso sin superposición y con T real ≤ 5 no tiene riesgo.
- **#8 no confirmado.** Esperaba lineal y el segundo salto dio x21. Con una sola corrida y 6,5 ms absolutos no distingue ruido de JIT/GC de un efecto real, y no gasté más presupuesto en investigarlo. Queda como "investigar si se quiere, bajo impacto".
- **Sobre el 5:** por inscripción activa del alumno se vuelven a pedir las franjas de la otra sección (`findProgramSectionSlots`) dentro del bucle (líneas 59-64 y 73-79): son consultas dentro de un bucle, de la Épica 33.

## Inventario estático (no medido: marcado como estimado)

Revisado en `backend/src/main`. Lo medido está arriba; esto es lectura de código y no tiene benchmark.

| Zona | Archivo:línea | Variable | Complejidad estimada | Observación |
|---|---|---|---|---|
| Importación: claves naturales y de alias | `dataimport/specialist/ImportBatchKeys.java:17-34` | filas del lote | O(log n) por búsqueda (`NavigableSet`) y O(1) (`Set`) | Bien resuelto. Estimado. |
| Importación: pares ya existentes | `enrollment/api/HistoricalEnrollmentRecorder.java:105-140` | filas del lote | O(n) con `HashSet` y particiones `InClauseChunks` | Bien resuelto. Estimado. |
| Importación: validación fila a fila | `dataimport/specialist/ImportBatchValidator.java:66-127,161` | filas | O(n) con claves ya commiteadas en `HashSet` | Estimado; no leí el cuerpo completo ni el de los 7 `*ImportDefinition`. |
| Importación: ciclos de correlativas | `dataimport/importer/PrerequisiteImportDefinition.java` | materias del plan | No lo leí | **No medido ni leído**: detección de ciclos pendiente. |
| Regularidad / correlativas | `enrollment/api/ProgramPrerequisiteBatchEvaluator.java:63-103` | consultas del lote | O(q) con cachés por materia | Estimado; hay caché de materias y exentos por lote. |
| Riesgo de inasistencia | `attendance/specialist/AttendanceRiskCalculator.java` (29 líneas) | clases del alumno | O(clases) | Estimado; función corta. |
| Lista de espera | `enrollment/service/CourseWaitlistExpiration.java:31-33` | alumnos en espera | O(w) con un `save` por alumno | Un `save` por iteración: Épica 33. |
| Promoción de lista de espera | `enrollment/service/ProgramEnrollmentSectionWithdrawer.java:50-62` | secciones liberadas | O(s log s) por `distinct().sorted()` | Estimado; chico. |
| Cierre de comisión / destinatarios | `evaluation/api/ClosureNotificationTargetsFinder.java:40-62` | alumnos de la sección | O(n) con `Map` | Estimado. |
| Comunicación masiva | `notification/broadcast/audience/specialist/SegmentResolver.java:36-51` | personas × criterios | O(c·n) con `HashSet` | Medido en B7. |
| Mora | `billing/reporting/service/FinancialDashboardReader.java:40-50` | alumnos deudores/activos | O(n) con `HashSet` | Medido en B7. |

## Lo que no se pudo medir

- Contra la base real (seed de `perf/`): no se usó; todos los benchmarks son en memoria con mocks, así que el costo de las consultas y de JPA en los hallazgos 1, 2 y 5 no se midió.
- Importación (ciclos de correlativas, validación entre filas), mora y cierres de comisión completos, y lista de espera: se revisaron estáticamente, pero no entran entre los 8 medidos.
- El benchmark #8 queda sin explicar (ver arriba).
- Los benchmarks 3 a 8 usan réplicas del idioma del código real (misma estructura de bucle y colección), no la clase real, salvo B1 (método default real de `BusinessDayCalendar`), B2/B3 (`ProgramProgressReader` real con mocks) y B6 (`PendingTransferApprovalOutcome` real).
- Una sola corrida en una sola máquina, sin repetición entre ejecuciones.
