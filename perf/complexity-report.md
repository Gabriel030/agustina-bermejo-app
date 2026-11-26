# Inventario de complejidad algorítmica del backend (32.1, Épica 32)

Solo mide e informa: no se tocó `backend/src/main`. Consultas, N+1 e índices son de la Épica 33 (`perf/index-report.md`): acá solo se marca el solapamiento.

## Cómo reproducir

```bash
cd backend && ./mvnw -q test -Dtest=AlgorithmicComplexityBenchmarkTest -Dperf.benchmark=true -Djacoco.skip=true | grep BENCH
```

Sin `-Dperf.benchmark=true` el test queda deshabilitado (`@EnabledIfSystemProperty`), así que no corre en el CI normal. Archivo: `backend/src/test/java/com/red_escuela/backend/perf/AlgorithmicComplexityBenchmarkTest.java`.

Método: 3 corridas de calentamiento y mediana de 9 (32.1 usó 5), `System.nanoTime`, datos sintéticos en memoria, repositorios mockeados con Mockito `stubOnly`. Máquina: Mac local, Java 23, una sola corrida (el ruido de JIT en tamaños chicos es alto; leer el factor entre 10x y 100x, no el primero). Tamaños 1x = realista hoy (700 alumnos), 10x y 100x.

## Hallazgos medidos

Factor = tiempo(n×10) / tiempo(n). Lineal ≈ 10, cuadrático ≈ 100.

| # | Archivo:línea | Variable de tamaño | Actual | Posible | 1x | 10x | 100x | Crecimiento medido | Esfuerzo | Riesgo | Afecta | Benchmark |
|---|---|---|---|---|---|---|---|---|---|---|---|---|
| 1 | `enrollment/api/ProgramProgressReader.java` (`approved.contains(entry)` sobre `List`) | materias del plan E | O(E·A) | O(E) con `Set` | — | — | — | **Corregido por #973** (`Set<Long>` y una consulta por lote). B2 reescrito contra el camino actual: x7,2 y x10,5, lineal; 1 llamada al repositorio (antes E) | Bajo | Bajo | Solo tiempo | B2 |
| 2 | `enrollment/api/ProgramProgressReader.java` (`coursed.contains(entry)`) | materias del plan E | O(E·C) | O(E) con `Set` | — | — | — | **Corregido por #973.** B3 reescrito contra el camino actual: x7,1 y x9,1, lineal; 1 llamada al repositorio | Bajo | Bajo | Solo tiempo | B3 |
| 3 | `enrollment/service/CourseEnrollmentService.java:325-330` (módulos × inscripciones) | módulos M | O(M·I) | O(M+I) con `Set` | — | — | — | **Corregido por #973** (`approvedModuleIds` es un `Set`). B4 retirado: medía una réplica de la implementación anterior | Bajo | Bajo | Solo tiempo | — (retirado) |
| 4 | `billing/operation/PendingTransferApprovalOutcome.java:14` (`List.contains` dentro de `anyMatch`) | facturas liquidadas por un comprobante S | O(S·R) | O(S+R) con `Set` (resuelto en E32-A, #965; después: 0,07 ms / 0,14 ms / 1,0 ms, x2,0 y x7,2: lineal) | 0,09 ms (S=100) | 3,8 ms (S=1000) | 393 ms (S=10000) | x44 y luego x104: cuadrático | Bajo | Bajo (es plata: cambiar la estructura, no la regla) | Solo tiempo, plata cerca | B6 |
| 5 | `enrollment/service/StudentScheduleOverlapGuard.java:87-91` (franjas × franjas) | franjas por sección T | O(T·O) por par de secciones, repetido por inscripción | OK: T real ≤ ~5 (la consulta dentro del bucle se corrigió en #990, E33) | 0,019 ms (T=3) | 0,27 ms (T=30) | 2,1 ms (T=300) | x14,6 y luego x7,9 | Bajo | Bajo | Solo tiempo | B5 |
| 6 | `notification/broadcast/audience/specialist/SegmentResolver.java:46` y `billing/reporting/service/FinancialDashboardReader.java:50` (`retainAll`/`removeAll` sobre `HashSet`) | personas por segmento | O(n) | O(n) (ya óptimo) | 0,55 ms (n=700) | 4,4 ms (n=7000) | 20,2 ms (n=70000) | x8,1 y x4,5: lineal | Ninguno | Ninguno | Solo tiempo | B7 |
| 7 | `attendance/specialist/AttendanceHistoryBuilder.java:49-61,98` (agrupa por inscripción, mapa por fecha, ordena por nombre) | alumnos de la sección S (×40 clases) | O(S·D + S log S) | O(S·D + S log S) (óptimo) | 1,75 ms (S=100) | 10,8 ms (S=1000) | 110,6 ms (S=10000) | x6,2 y x10,2: lineal | Ninguno | Ninguno | Solo tiempo | B8 |
| 8 | `attendance/api/JustificationDeadlinePolicy.java:33-36` + `academicstructure/api/BusinessDayCalendar.java:8-21` | fechas de clase distintas D | O(D·k) (k = días hábiles del plazo, constante) | idem | 0,04 ms (D=40) | 0,21 ms (D=400) | 1,6 ms (D=4000) | **Descartado como ruido de JIT**: con 9 corridas por tamaño y la serie B1 (x4,9 y x7,9) y B1b (D=20/200/2000: x1,8 y x8,6) el crecimiento es lineal; el x21 de 32.1 no se reproduce | — | — | Solo tiempo | B1, B1b |

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
| Importación: validación fila a fila | `dataimport/specialist/ImportBatchValidator.java:93-135` (`validateFile`) | filas | O(n) por archivo: dos pasadas por lotes, `HashSet`/`HashMap` por columna y `preloadBase` por lote | Leído en E32-B; sin bucles anidados sobre filas, salvo la validación entre filas de correlativas (B9). Las consultas por lote (`findCommittedKeys`, `preloadBase`) son de E33. Los otros `*ImportDefinition` no se leyeron uno por uno: solo se confirmó que `validatesAcrossRows()` es `true` únicamente en `PrerequisiteImportDefinition` (ver nota abajo). |
| Importación: ciclos de correlativas | `dataimport/importer/PrerequisiteImportDefinition.java:77-90,122-135` (`validateAcrossRows` + `reaches`) | filas de correlativas R de una carrera | O(R·(V+E)): una búsqueda en profundidad por fila | **Medido (B9)**: cadena de R filas, 0,9 ms (50) → 30 ms (500, x33) → 3253 ms (5000, x108): cuadrático. Peor caso artificial (una sola cadena); una carrera real tiene ~40 materias y pocas correlativas por materia, donde cuesta ms. Mejora posible: una sola pasada con detección de ciclos (Tarjan o coloreo), O(V+E). Es oportunista, fuera de E32 (toca `backend/src/main`) |
| Regularidad / correlativas | `enrollment/api/ProgramPrerequisiteBatchEvaluator.java:63-103` | consultas del lote | O(q) con cachés por materia | Estimado; hay caché de materias y exentos por lote. |
| Riesgo de inasistencia | `attendance/specialist/AttendanceRiskCalculator.java` (29 líneas) | clases del alumno | O(clases) | Estimado; función corta. |
| Lista de espera | `enrollment/service/CourseWaitlistExpiration.java:31-33` | alumnos en espera | O(w) con un `save` por alumno | Un `save` por iteración: Épica 33. |
| Promoción de lista de espera | `enrollment/service/ProgramEnrollmentSectionWithdrawer.java:50-62` | secciones liberadas | O(s log s) por `distinct().sorted()` | Estimado; chico. |
| Cierre de comisión / destinatarios | `evaluation/api/ClosureNotificationTargetsFinder.java:40-62` | alumnos de la sección | O(n) con `Map` | Estimado. |
| Comunicación masiva | `notification/broadcast/audience/specialist/SegmentResolver.java:36-51` | personas × criterios | O(c·n) con `HashSet` | Medido en B7. |
| Mora | `billing/reporting/service/FinancialDashboardReader.java:44-50` | alumnos deudores/activos | O(n) con `HashSet` | **Medido (B10)** con el lector real: 0,4 ms (700) → 1,8 ms (7000, x4,1) → 12,0 ms (70000, x6,7): lineal. |

## Lo que no se pudo medir

- Contra la base real (seed de `perf/`): no se usó (decisión D4=b de #965: ya hay tests de conteo de #973 y #990); todos los benchmarks son en memoria con mocks, así que el costo de las consultas y de JPA en los hallazgos 1, 2 y 5 no se midió.
- Cierres de comisión completos y lista de espera: no se midieron en memoria porque sus costos dominantes son consultas (E33) y requieren base; se mantienen como estimados.
- El benchmark #8 queda descartado como ruido (ver tabla).
- Los benchmarks B5, B7 y B8 usan réplicas del idioma del código real (misma estructura de bucle y colección), no la clase real, salvo B1/B1b (método default real de `BusinessDayCalendar`), B2/B3 (`ProgramProgressReader` y `SubjectApprovalLookup` reales, con `findPrerequisiteStatusRows` mockeado), B6 (`PendingTransferApprovalOutcome` real), B9 (`PrerequisiteImportDefinition.validateAcrossRows` real) y B10 (`FinancialDashboardReader` real con repositorios mockeados).
- Una sola corrida en una sola máquina, sin repetición entre ejecuciones.
