# Inventario de estilos inline para una CSP estricta (#919)

Primer paso obligatorio del Issue #919 (espera levantada por Gabriel el 24-nov-2026). Solo informe: no cambia el Caddyfile, el front ni la política. Complementa la sección CSP de [plan-produccion.md](plan-produccion.md), cuyo conteo («25 usos en 16 archivos») está desactualizado: hoy son 28 usos en 20 archivos.

## Política que se sirve hoy

`frontend/Caddyfile`, bloque `security_headers` (línea 17):

```
default-src 'self'; script-src 'self' https://accounts.google.com/gsi/client;
style-src 'self' 'unsafe-inline' https://accounts.google.com/gsi/style; font-src 'self';
img-src 'self' data: blob:; connect-src 'self' https://accounts.google.com/gsi/;
frame-src https://accounts.google.com/gsi/; manifest-src 'self'; worker-src 'self';
object-src 'none'; base-uri 'self'; form-action 'self'; frame-ancestors 'none'
```

- `'unsafe-inline'` aparece una sola vez, en `style-src`; `script-src` ya es estricto. `frontend/src/caddyHeaders.test.js` (líneas 66, 73 y 74) fija esa guarda.
- `frontend/index.html` línea 5 lleva una `<meta>` CSP solo para desarrollo y build sin Docker; esa meta sí tiene `script-src 'unsafe-inline'` (script del tema). El build de Docker usa `VITE_CSP_IN_HEADER=true` (`vite.config.js` línea 13), que quita la meta y saca el script a `theme-init.js`. No es producción.
- `style-src` solo gobierna `<style>`, atributos `style="..."` en el HTML y `setAttribute('style', ...)`. Asignar por CSSOM (`el.style.x = ...`, que es lo que hace React con `style={{}}`) **no** está bloqueado.

## Resultado en una línea

El código propio del front **no** produce ningún `<style>` ni atributo `style="..."` como texto. Lo que puede exigir `'unsafe-inline'` son librerías que inyectan `<style>` en runtime, y eso hay que **confirmar en navegador** (en este informe no hay `node_modules` instalado, así que esas filas están marcadas «a verificar»).

## Tabla de orígenes

| # | Origen | Cantidad | Quién lo genera | ¿Bloqueado por CSP sin `unsafe-inline`? | ¿Se puede quitar? |
|---|---|---|---|---|---|
| 1 | `style={{...}}` en JSX | 28 líneas, 20 archivos (5 en `components/ui`, 9 en `components/` fuera de `ui`, 14 en `features/`) | Código propio; React lo aplica por CSSOM | No | No hace falta. Opcional: pasar a clase/variable CSS |
| 2 | `style` por JS: `field.style.position/opacity` | 2 líneas, 1 archivo (`features/workspace/hooks/useCopyToClipboard.js`) | Código propio, CSSOM | No | No hace falta |
| 3 | Atributos `style="..."` como texto, `dangerouslySetInnerHTML`, `<style>` en JSX, `insertRule`, `cssText`, `adoptedStyleSheets` | 0 | — | — | Nada que quitar |
| 4 | `<style>` en runtime de `sonner` (toasts) | 1 por carga de página, al importar `sonner` (único consumidor: `components/ui/sonner.jsx`, montado en `App.jsx`) | Librería: CSS embebido en el bundle, inyectado con `document.createElement('style')` (ya anotado en plan-produccion.md) | **Sí** | Con `nonce` (si la versión lo admite), hash del CSS (frágil al actualizar) o reemplazar el toast |
| 5 | `<style>` en runtime de `react-style-singleton` | 1 por apertura de modal, contenido variable (compensación de scrollbar) | Dependencia transitiva de `radix-ui` vía `react-remove-scroll` (Dialog, Sheet, Dropdown, Select, AlertDialog) | **Sí** | Solo con `nonce` (el contenido cambia, no admite hash) o desactivando el bloqueo de scroll (`modal={false}`), con costo de UX |
| 6 | Atributos `style` que inyectan los primitivos de Radix (Popper: `position`, `transform`, `--radix-*`; `ScrollArea`) | Uno por popover/select/tooltip/dropdown abierto | Radix, vía React (CSSOM) | No | No hace falta |
| 7 | `motion` (animaciones) | 9 archivos importan `motion`; 8 usan `<m.*>`/`<motion.*>` | Aplica transforms por CSSOM / Web Animations API | No (a verificar) | No hace falta |
| 8 | `recharts` | 1 archivo (`components/charts/CartesianChart.jsx`) | SVG con atributos de presentación y `style` por React | No (a verificar; los gráficos animados son lo sensible) | No hace falta |
| 9 | `cmdk` (`components/ui/command.jsx`) | 1 componente | Atributos `data-*` y CSS en clases | No (a verificar) | No hace falta |
| 10 | Google Identity (`accounts.google.com/gsi/style`) | 1 hoja externa | Google, ya permitida por URL | No | No (se mantiene la URL en `style-src`) |
| 11 | Tailwind v4: CSS en `/assets/*.css` | 1 hoja por build | Vite, servida desde `'self'` | No | — |

## Detalle de los 28 `style={{}}` (fila 1)

| Patrón | Líneas | Dónde |
|---|---|---|
| Medida dinámica (`width`, `height`, `minWidth`, `minHeight`, `marginLeft`) | 14 | `ProgressBar`, `StablePageHeight`, `DataTable`, `ChartFrame` (x2), `ClassroomAvailabilityPicker`, `FilaAviso`, `DetailBlocks` (x2), `DocenteTomarAsistencia` (x3 barras), `PreceptorMesaExamen` (x2) |
| `transform` de un indicador animado | 2 | `Sidebar`, `ui/tabs` |
| `maskImage` / `WebkitMaskImage` calculado | 2 | `ui/table`, `ui/tabs` |
| `background` con `conic-gradient` o color de leyenda | 4 | `SeccionAsistencia`, `DocenteTomarAsistencia` (x2), `CartesianChart` |
| Variable CSS propia (`--role-count`, `--years`, `--gap`, tokens de sonner) | 4 | `PermissionsPage`, `CurriculumMap`, `ui/toggle-group`, `ui/sonner` |
| `animation` / `animationDelay` | 2 | `Stagger`, `MobileMenu` |

Clasificación hecha leyendo cada línea (suma 28); el conteo por archivo sale del comando 1b de abajo.

## Cómo reproducir los conteos

```bash
cd frontend
# 1. style={...} en JSX, sin tests
grep -rnE "style=\{" src --include=*.jsx --include=*.js | grep -v '\.test\.' | wc -l          # 28
grep -rlE "style=\{" src --include=*.jsx --include=*.js | grep -v '\.test\.' | wc -l          # 20
grep -rnE "style=\{" src/components/ui | wc -l                                                 # 5
grep -rnE "style=\{" src/features | grep -v '\.test\.' | wc -l                                 # 14
# 1b. por archivo
grep -rcE "style=\{" src --include=*.jsx | grep -v ':0' | grep -v test | sort -t: -k2 -nr
# 2. asignación por CSSOM
grep -rnE "\.style\." src | grep -v '\.test\.'                                                 # 2 líneas
# 3. estilos como texto
grep -rnE 'style="' src | wc -l                                                                # 0
grep -rnE "dangerouslySetInnerHTML|<style|insertRule|cssText|adoptedStyleSheets" src | grep -v '\.test\.'   # sin resultados
# 7-9. consumidores de librerías
grep -rlE "from 'motion|motion/react" src | grep -v '\.test\.' | wc -l                         # 9
grep -rlE "recharts" src | grep -v '\.test\.'                                                  # 1 archivo
# política
grep -n "Content-Security-Policy" Caddyfile index.html
```

La confirmación de las filas 4 a 9 requiere `npm ci` y buscar en `node_modules` (`grep -rn "createElement(\"style\")\|createElement('style')" node_modules/{sonner,react-style-singleton,motion,recharts,cmdk,radix-ui}/dist`) y, sobre todo, una pasada real con la política activa.

## Propuesta de etapas (no implementadas)

Coincide con el plan de plan-produccion.md §2 y le agrega la etapa 0.

0. **Medir (Report-Only).** Servir `Content-Security-Policy-Report-Only` con `style-src 'self' https://accounts.google.com/gsi/style` **además** de la política vigente, y pasar la matriz de roles con la consola abierta leyendo `securitypolicyviolation`. Resultado esperado: solo las filas 4 y 5; si aparecen `motion`, `recharts` o `cmdk`, se agregan al inventario. Riesgo bajo, reversible quitando la cabecera.
1. **Nonce por respuesta.** Caddy genera el nonce (`templates` solo sobre `index.html`) y lo escribe en la cabecera y en un `<meta name="csp-nonce">`. Riesgo: el service worker precachea `index.html` (`injectManifest` en `vite.config.js` línea 83) y congelaría el nonce; hay que excluirlo de la precarga o servirlo network-first. Es el riesgo principal.
2. **Propagar el nonce.** `setNonce` de `react-style-singleton` al arrancar; para `sonner`, opción de nonce si la versión la trae, si no hash o toast propio.
3. **Activar** `style-src 'self' 'nonce-<n>' https://accounts.google.com/gsi/style` y cambiar la expectativa de `caddyHeaders.test.js` a «ningún `unsafe-inline`».
4. **Opcional, aparte:** migrar los `style={{}}` de la fila 1 a clases o variables CSS. No es necesario para la CSP; solo reduce la superficie si más adelante se agregara `style-src-attr 'none'`.

Orden recomendado: 0, luego 1 y 2 juntas, luego 3. Cada etapa es un PR chico, con reversa en una línea de Caddy.

## Decisiones que quedan para el coordinador

- Aprobar la etapa 0 (Report-Only) como primer PR de implementación.
- Si `sonner` no admite nonce: ¿hash frágil o toast propio?
