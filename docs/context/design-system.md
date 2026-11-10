# Sistema de diseño: cómo agregar un diseño nuevo

Objetivo: restilizar la app cambiando un solo lugar (tokens). Todo componente reutilizable lee variables; ningún componente fuera de `src/theme/` y `src/components/ui/` declara colores, sombras ni radios literales.

Diseños registrados hoy: `b` (default desde 26-A P6) y `vl` (Vidrio Líquido, respaldo hasta 26-D).

## Dónde se registra un diseño

- Valor de `data-design` en `<html>`: lista `DESIGNS` y `DEFAULT_DESIGN` en `frontend/src/theme/design.js`.
- Script anti-flash de `frontend/index.html`: el arreglo `validos` y el último fallback deben coincidir con `design.js` (lo verifica `design.test.js`).
- Selección en runtime: `?design=x` en la URL (se guarda en `localStorage.design`), luego lo guardado, luego `VITE_DEFAULT_DESIGN`, luego `DEFAULT_DESIGN`.

## Archivo de tokens

- `vl` es la base: `frontend/src/theme/tokens.css` (`:root` y `:root[data-theme='dark']`). Es el contrato: define TODAS las variables.
- Un diseño nuevo `x` va en `frontend/src/theme/tokens-x.css`, importado en `frontend/src/index.css` junto a `tokens-b.css`, con tres bloques: `:root[data-design='x'][data-theme='light']`, `...[data-theme='dark']` y `:root[data-design='x']` (valores iguales en claro y oscuro: radios, sombras, tamaños).
- Regla: toda variable de la base debe redefinirse en el diseño (salvo `--focus-ring`, compartida a propósito), y toda variable nueva se agrega en AMBOS archivos. Los bloques claro y oscuro deben declarar las mismas variables.
- Mapeo a utilidades Tailwind: `frontend/src/index.css` (`@theme inline`): `text-on-accent`, `bg-surface`, `shadow-popover`, `rounded-card`, etc.

## Variables obligatorias (resumen)

- Color: `--bg1..3`, `--surface`, `--border`, `--ink*`, `--accent*`, `--success|warning|danger` (+ `-bg`, `-ink`), `--neutral-*`, `--dialog-bg`, `--on-accent` (texto sobre acento y sobre rellenos de estado), `--on-overlay` (texto sobre scrim), `--overlay-bg`, `--overlay-bg-sheet`, `--overlay-bg-menu`.
- Forma: `--r-*` (radios), `--surface-blur`, `--surface-saturate`, `--overlay-blur`, `--row-h`, `--shell-pad`, `--shell-gap`.
- Sombras: `--sh-card`, `--sh-overlay`, `--sh-dialog`, `--sh-popover`, `--sh-sticky-left`, `--sh-sticky-right`, `--sh-highlight-row`, `--btn-primary-shadow*`, `--seg-indicator-shadow`.
- Botón primario, segmentado, navegación, tabla y pills: `--btn-*`, `--seg-*`, `--nav-*`, `--table-head*`, `--pill-*-fg`, `--row-*`.
- Cada par texto/fondo nuevo debe sumarse a `TEXT_PAIRS` en `frontend/src/theme/tokens.test.js` (contraste AA).

## Guardas automáticas

- `tokens.test.js`: paridad de variables entre base y diseños, contraste AA, colores dentro de sRGB.
- `design.test.js`: registro de diseños, tokens por diseño y script de `index.html`.
- `literal-values.test.js`: falla ante hex, `oklch(`, `rgb(`, `hsl(`, `text|bg|border-white|black` y `shadow-[...]` fuera de `src/theme/` y `src/components/ui/`. Las excepciones van en `ALLOWED` del test, por archivo y regla (hoy: ninguna).

## Baselines visuales

- Catálogo: `/dev/catalog` (requiere `VITE_ENABLE_CATALOG=true`). Spec: `e2e/specs/visual/catalog.spec.js` y `shell.spec.js`; la lista `DESIGNS` de cada spec incluye el diseño nuevo.
- Generar o actualizar desde `e2e/`: `npm run test:visual:update -- -g "x-"` (solo el diseño nuevo). Verificar: `npm run test:visual -- -g "x-"`.
- Los baselines están en `e2e/specs/visual/__screenshots__/chromium/<spec>/<diseño>-<tema>-<viewport>/`. Un diseño nuevo se aprueba con capturas claro y oscuro, escritorio y móvil.

## Qué tiene ramas por diseño (no se resuelve con tokens)

- Shell y menú: `layouts/RoleShell.jsx`, `components/Sidebar.jsx`, `components/TopHeader.jsx`, `components/MobileMenu.jsx` (variante Tailwind `design-b:` y `isDesignB()`): estructura, agrupación del menú, 44px táctil, foco al h1.
- `components/NotificationBell.jsx`, `components/ThemeToggle.jsx` y `components/ui/button.jsx` tienen ajustes puntuales por `design-b:`.
- Un diseño nuevo que cambie estructura agrega su variante en `index.css` (`@custom-variant design-x`) y ramas en esos archivos.

## Qué NO se centraliza con tokens

Estructura y layout (orden y agrupación de ítems del menú, qué se muestra o se oculta por pantalla, grillas, anchos máximos), tipografía por pantalla (`text-[13px]` y similares: son tamaños, no color), y los textos. Cambiar eso es trabajo de componente, no de tokens.

## Cuántos usos tiene cada componente

Objetivo: saber que un componente se usa en N lugares y que cambiarlo aplica a todos.

- `npm run component-usage` (en `frontend/`) recorre `src` y, por cada componente exportado de `src/components/` (incluye `ui/`), imprime en cuántos archivos se importa y cuántas veces se usa en JSX, ordenado de más a menos usado. Excluye tests. Con `--json` devuelve el mismo informe como JSON (incluye el detalle por archivo de los elementos crudos). Acepta una carpeta como argumento (los tests lo corren contra `frontend/scripts/__fixtures__/component-usage/src`).
- Al final cuenta los elementos HTML crudos (`<button>`, `<table>`, `<input>`, `<select>`, `<textarea>`) fuera de `components/ui/` y el componente que los reemplaza, y lista los componentes compartidos que nadie importa. Los componentes que se importan a través de un barrel (`@/components/charts`) no se atribuyen al archivo que los define.

## Elementos crudos de formulario y tabla

- Regla de lint `local/no-raw-elements` (`frontend/eslint-rules/raw-elements.js`, error): prohíbe `<table>`, `<select>`, `<textarea>` e `<input>` fuera de `src/components/ui/` y de tests. Usar `Table*`, `Select` (shadcn, para listas con estilos propios) o `NativeSelect` (select nativo con el estilo compartido; mismo contrato `onChange`/`value`), `Textarea` e `Input`.
- `<input>` con `type` `checkbox`, `radio`, `file` o `hidden` queda permitido por la regla: son controles nativos sin aspecto de campo (los `file` van ocultos detrás de un botón). Para checkbox preferir `Checkbox` de `ui/`.
- Excepciones por archivo: `RAW_ELEMENT_EXCEPTIONS` en el mismo archivo. Hoy: `DocenteTomarAsistencia.jsx` y `GradeGridTable.jsx` (grillas con `rowSpan`, columnas pegajosas y bordes propios que `Table` no soporta). `raw-elements.test.js` falla si un archivo de la lista ya no tiene el elemento crudo: la lista solo puede achicarse.
- `<button>` queda fuera de esta regla (lo cubre otro paquete).

## Nota sobre `components/ui/`

Vienen del CLI de shadcn con modificaciones locales para leer variables. Un `npx shadcn add` sobre un archivo existente las pisa: revisar el diff antes de aceptar.
