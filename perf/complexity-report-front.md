# Inventario de listas del front (Épica 32 / E32-C)

Solo informe: no se tocó `frontend/src`, backend, contrato ni `perf/budget.json`. Las mediciones son de Vitest + `performance.now()` en jsdom (VM de la nube, sin stack local ni navegador): sirven para comparar tamaños entre sí y detectar crecimiento, no como tiempos de usuario. Cada número está marcado **MEDIDO** o **ESTIMADO**.

## 1. Inventario reproducible

Dos pasos. El primero cruza cada `apiFetch` de `frontend/src/features/**` con `contracts/openapi.json` y clasifica los GET que devuelven colección (array o envoltorio con `content`/`items`/array) en paginados (`page`/`size`) o completos. El segundo lista los archivos con tablas.

```
python3 inv.py        # script al pie de este informe; se ejecuta desde la raíz del repo
grep -rlE "useReactTable|<TableBody|<Table\b" --include=*.jsx frontend/src/features | grep -v test
```

Resultado: 150 endpoints de colección distintos consumidos desde los hooks; **16 paginados, 134 completos** (los completos son en su mayoría catálogos chicos o sub-recursos de un solo alumno/comisión). 29 archivos `.jsx` renderizan `Table`. Limitaciones: el script no ve rutas armadas con variables (p. ej. `/audit-log`, que sí está paginado: `audit/hooks/useAuditLog.js:10`, `page`/`size` 20); los paginados detectados son: `/students` (`identity/hooks/useAlumnos.js:16`, 25), `/waitlists` (`enrollment/hooks/useWaitlists.js:14`, 20) y su historial, `/financial-dashboard/debtors/page` (`billing/hooks/useFinancialDashboard.js:16`, 25), `/me/notifications` (`notifications/hooks/useNotifications.js:13`, 10), `/attendance/justifications` (100), `/collection-reminders/contact-list` (100), `/student-discounts`, `/broadcasts`, `/invitation-waves`, `/commission-closures`, `/permissions/discrepancies`, `/program-enrollment-applications/history`, errores de importación.

Completas con volumen potencialmente grande: `/prospects` (`identity/hooks/useProspectos.js:7`), `/{program|course}-sections/{id}/roster` (`attendance/hooks/useDocenteAsistencia.js:15`), `/course-sections/{id}/attendance/history`, `/announcements/{id}/recipients` (`announcements/hooks/useAvisos.js:20`), `/monthly-invoice-runs/{id}/draft-invoices` (`billing/hooks/useDraftInvoices.js`), `/students/{id}/billing-overview`, `/me/teaching-sections`.

Volumen del seed grande (`perf/seed-large.sql`): 5000 alumnos, 300 comisiones con ~133 inscriptos cada una (40.000 inscripciones), 24 clases por comisión, 30.000 facturas, 50.000 notificaciones, 100.000 filas de auditoría.

## 2. Las pantallas de mayor volumen (10 + prospectos)

"Tamaño 1x" = lo que el seed grande entrega hoy a esa pantalla. Nodos DOM: MEDIDO si se renderizó el componente real, ESTIMADO si se calculó filas × nodos por fila.

| # | Pantalla (archivo) | Origen | Filas 1x | Nodos DOM 1x | Ordenar/filtrar en cliente |
|---|---|---|---|---|---|
| 1 | Padrón de alumnos (`identity/pages/PadronAlumnos.jsx`) | paginada 25 (total 5000) | 25 | ESTIMADO ~25 × 15 | en servidor |
| 2 | Prospectos (`PadronAlumnos.jsx:109`) | **completa** | seed: 0; real hasta miles | ESTIMADO ~15/fila | filtro NFD: 1,6 ms a 5000 (MEDIDO) |
| 3 | Listas de espera (`enrollment/hooks/useWaitlists.js`) | paginada 20 | 20 | ESTIMADO | en servidor |
| 4 | Deudores (`billing/dashboard/FinancialDashboardPage.jsx`) | paginada 25 | 25 | ESTIMADO | en servidor |
| 5 | Auditoría (`audit/hooks/useAuditLog.js`) | paginada 20 (100.000) | 20 | ESTIMADO | en servidor |
| 6 | Notificaciones (`notifications/hooks/useNotifications.js`) | paginada 10 (50.000) | 10 | ESTIMADO | en servidor |
| 7 | Grilla de notas (`evaluation/components/GradeGridTable.jsx`) | **completa** | 133 | 3278 (MEDIDO, 6 columnas, solo lectura) | TanStack sort/filter; ver 2.1 |
| 8 | Historial de asistencia (`attendance/pages/DocenteTomarAsistencia.jsx:456-470`) | **completa** | 133 × 24 fechas | ESTIMADO ~3200 celdas + 133 filas | sin sort; hover re-renderiza todo |
| 9 | Tomar asistencia / Alumnos (`attendance/hooks/useTakeAttendanceForm.js:102`, `attendance/components/TabAlumnos.jsx:101`) | **completa** | 133 | ESTIMADO ~20/fila ≈ 2700 | filtro 0,02 ms (MEDIDO) |
| 10 | Borradores de facturación mensual (`billing/monthly-run/DraftInvoicesPage.jsx:68`) | **completa**, agrupada | ~20 grupos / 5000 ids | ESTIMADO ~20 grupos | resumen: 1,0 ms (MEDIDO) |
| 11 | Destinatarios de aviso (`announcements/components/RosterSegmento.jsx`) | **completa** | 133 | 821 (MEDIDO) | `columnasDeNotas` O(R·k) |

Las filas 1 a 6 son las de mayor volumen total en el seed pero llegan paginadas; las filas 7 a 11 son las que llegan completas y por eso concentran el costo de cliente (se listan 11 para no dejar afuera ninguna de las dos categorías).

### 2.1 Mediciones (MEDIDO, jsdom, mediana de 5 corridas en los filtros/orden; un render por tamaño)

Render completo del componente real, con 1x = 133 filas (una comisión), 10x = 1330, 100x = 13.300:

| Componente | 1x | 10x | 100x | Nodos DOM 1x / 10x / 100x |
|---|---|---|---|---|
| `GradeGridTable` (6 col., solo lectura) | 447 ms | 2207 ms | 25.900 ms | 3278 / 32.405 / 323.675 |
| `RosterSegmento` (3 col. de notas) | 227 ms | 523 ms | 4580 ms | 821 / 8004 / 79.825 |

Crecimiento lineal (≈2-3 ms por fila de grilla en jsdom; en un navegador real será menor pero con la misma pendiente). El 1x (una comisión) es aceptable; el 10x ya sería inusable sin paginación o ventana.

Filtro y orden en cliente (misma expresión que el código, copiada; n = filas):

| Operación | 133 | 1330 | 5000 | 13.300 | 50.000 | 500.000 |
|---|---|---|---|---|---|---|
| `filter` + `toLowerCase().includes` (`TabAlumnos.jsx:33`) | 0,02 | 0,21 | 0,62 | 1,97 | 23,1 | 111 |
| `filter` con normalización NFD (`PadronAlumnos.jsx:109`) | 0,05 | 0,41 | 1,57 | 7,46 | 29,7 | 248 |
| `sort` con `localeCompare` (`SeccionAsistencia.jsx:45`) | 0,07 | 0,22 | 1,47 | 6,30 | 20,7 | 161 |

`summarizeDraftSelection` (`billing/monthly-run/draftSelection.js:15`, 20 grupos): 1x (5000 ids) 1,0 ms; 10x 11,9 ms; 100x 181 ms (MEDIDO, lineal).

No medidos (ESTIMADO): costo de re-render al pasar el mouse sobre el historial de asistencia, tiempos en navegador con red 4G / CPU x4, y las pantallas paginadas, que por construcción renderizan 10-25 filas.

## 3. Hallazgos

| # | Archivo:línea | Complejidad actual -> posible | Esfuerzo | Riesgo |
|---|---|---|---|---|
| H1 | `attendance/pages/DocenteTomarAsistencia.jsx:365-368` y `:456-470`: `alumnoHover`/`fechaHover` son estado del mismo componente que dibuja toda la matriz; cada `onMouseEnter` re-renderiza R×D celdas con closures nuevas | O(R·D) por movimiento de mouse -> O(1) con `React.memo` por fila/celda y hover por CSS o por clase en el `<table>` | M | bajo (solo presentación) |
| H2 | `evaluation/components/GradeGridTable.jsx:118` + `evaluation/lib/gradeGrid.js:102-103`: `student.grades.find(...)` por cada celda | O(R·C·G) -> O(R·C) con mapa `type+secuencia` por alumno | S | bajo |
| H3 | `GradeGridTable.jsx` y `RosterSegmento.jsx:56`: renderizan todas las filas sin ventana (323.675 nodos a 100x; 3278 a 1x) | render O(R·C) -> O(ventana) con virtualización | M-L | medio (foco de teclado, sticky columns, inputs con borrador) |
| H4 | `billing/monthly-run/DraftInvoicesPage.jsx:68`: `summarizeDraftSelection(groups, selected)` en cada render, sin `useMemo` | O(G+I) por render -> O(G+I) solo cuando cambian `groups` o `selected` | S | bajo |
| H5 | `evaluation/components/TabNotas.jsx:65`: `useQueries` con una petición `grade-items` por fila de la página | N peticiones por pantalla -> 1 endpoint agregado (cambio de contrato, no del front solo) | L | medio |
| H6 | `identity/hooks/useProspectos.js:7` + `PadronAlumnos.jsx:109`: `/prospects` sin `page`/`size`; el filtro corre en cada render | payload y DOM O(P) -> paginar en servidor con búsqueda en servidor | M (contrato + front) | medio |
| H7 | `announcements/hooks/useAvisos.js:20`: destinatarios completos de un aviso (hasta todos los alumnos del segmento) | O(R) en red y DOM -> paginar o virtualizar | M | bajo |
| H8 | `attendance/hooks/useTakeAttendanceForm.js:153,183-186`: cinco pasadas O(n) por render sobre `statuses`/`students`; `TabAlumnos.jsx:152-153` reduce sin `useMemo` | O(5n) -> O(n) en una pasada | S | bajo; ganancia despreciable a 133 filas (0,02 ms) |
| H9 | `billing/pages/AdministrativoFacturacion.jsx:246-260`: `payableInvoicesOf` recalcula `flatMap`+`filter` en cada llamada (se llama más de una vez por render) | O(k·I) -> O(I) con `useMemo` | S | bajo; I son facturas de un solo alumno |

No hay `filter` dentro de `map` ni búsquedas lineales anidadas relevantes en las demás tablas revisadas: los `find`/`includes` restantes operan sobre listas de decenas de elementos (roles, tabs, enums).

## 4. Conclusión

- **Paginar (en servidor):** `/prospects` (H6) y los destinatarios de aviso (H7). Son las únicas colecciones potencialmente no acotadas que hoy llegan completas; el resto de listas con volumen real del seed (alumnos, listas de espera, deudores, auditoría, notificaciones) ya está paginado en 10-25 filas y no necesita nada.
- **Memoizar:** H1 (hover del historial de asistencia, el hallazgo con efecto más visible para un docente), H4 y H2. Son cambios chicos, de bajo riesgo y sin tocar contrato.
- **Virtualizar:** solo `GradeGridTable` si alguna vez una comisión supera ~1000 inscriptos; con 133 filas (1x del seed) el render lineal es aceptable. Mientras el tamaño real de comisión se mantenga en cientos, no vale la pena el riesgo de H3 (foco, sticky, inputs).
- **No vale la pena:** optimizar filtros y orden en cliente (a 13.300 filas cuestan <8 ms; hasta 500.000, <250 ms), H8 y H9, ni virtualizar las listas paginadas.
- Propuesta de corrección (se spec-ea aparte, D2): primero H1+H2+H4 (S/M, un PR frontend), después H6/H7 (contrato + front).

## 5. Cómo reproducir

1. Inventario: el script de abajo y el `grep`, desde la raíz del repo.
2. Mediciones de render y filtro: `cd frontend && npm ci`, crear un test temporal (no commitear) que renderice `GradeGridTable` y `RosterSegmento` con 133/1330/13.300 filas y mida con `performance.now()`, y correr `npx vitest run <ruta> --maxWorkers=2`. Las mediciones de este informe se hicieron así y el archivo temporal se borró.
3. Mediciones en navegador (4G + CPU x4, LCP, nodos con datos reales del seed grande): seguir `perf/e31/README.md` (levantar el stack `perf` con `perf/seed-large.sql`, `npm run measure` en `perf/e31`). **No se hicieron: no hay stack local en la nube**; por eso los nodos de las pantallas paginadas y del historial son ESTIMADOS.

### Script del inventario (`inv.py`)

```python
import json,re,glob,sys
d=json.load(open('contracts/openapi.json'))
def res(x):
    while '$ref' in x:
        x=d['components']['schemas'][x['$ref'].split('/')[-1]]
    return x
def is_list(g):
    try: sc=res(g['responses']['200']['content']['application/json']['schema'])
    except KeyError: return False
    if sc.get('type')=='array': return True
    pr=sc.get('properties',{})
    return 'content' in pr or 'items' in pr or any(res(v).get('type')=='array' for v in pr.values())
tpl={}
for p,ops in d['paths'].items():
    g=ops.get('get')
    if g and is_list(g):
        n={x.get('name') for x in g.get('parameters',[]) if 'name' in x}
        tpl[re.sub(r'\{[^}]+\}','{}',p)]=bool({'page','size'}&n)
rx=re.compile(r"apiFetch\(\s*[`'\"]([^`'\"]+)[`'\"]")
rows=[]
for f in sorted(glob.glob('frontend/src/features/**/*.js*',recursive=True)):
    if re.search(r'\.test\.|/mocks/',f): continue
    s=open(f).read()
    for m in rx.finditer(s):
        p=m.group(1).split('?')[0]
        p=re.sub(r'\$\{[^}]*\}','{}',p)
        if p in tpl:
            ln=s[:m.start()].count('\n')+1
            rows.append((f.replace('frontend/src/features/',''),ln,p,'paginada' if tpl[p] else 'completa'))
for r in rows: print('|'.join(map(str,r)))
```
