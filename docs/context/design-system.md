# Sistema de diseño: tokens y guardas

Objetivo: restilizar la app cambiando un solo lugar (tokens). Todo componente reutilizable lee variables; ningún componente fuera de `src/theme/` y `src/components/ui/` declara colores, sombras ni radios literales.

Hay un único diseño: la dirección B (plana). No hay registro de diseños ni selección en runtime: no existen `data-design`, `?design=`, `VITE_DEFAULT_DESIGN` ni `DEFAULT_DESIGN`. Vidrio Líquido se retiró en 26-D y no se diseña nada nuevo sobre él.

## Archivo de tokens

- Único archivo: `frontend/src/theme/tokens.css`. El bloque `:root` define el modo claro y `:root[data-theme='dark']` el oscuro.
- Toda variable se declara en los dos bloques. Un test comprueba que el oscuro redefine las mismas variables de color que el claro.
- Modo oscuro: `data-theme` en `<html>`, que maneja `context/ThemeContext.jsx` (`useTheme()`) y el script anti-flash de `frontend/index.html`.
- Mapeo a utilidades Tailwind: `frontend/src/index.css` (`@theme inline`): `bg-surface`, `text-ink`, `text-on-accent`, `text-brand`, `shadow-popover`, `rounded-card`, etc.
- Cada par texto/fondo nuevo se suma a `TEXT_PAIRS` en `frontend/src/theme/tokens.test.js` (contraste AA); cada par que no es texto, a `NON_TEXT_PAIRS` (3:1).

## Cómo restilizar

- Cambiar el valor de una variable en `tokens.css` (claro y oscuro) cambia todos los componentes que la usan.
- Un valor nuevo de color, sombra o radio se agrega como variable, no como clase literal en un componente.
- Estados deshabilitados: `--disabled-fg` y `--disabled-bg`, sin `opacity`. Borde de popovers y menús: `--popover-edge`.
- Foco: `--focus-ring` se declara en la raíz compartida, así claro y oscuro lo heredan.
- Fondos: sin desenfoques de fondo ni degradés. Los botones, segmentados e indicadores de navegación usan color liso (`--btn-primary-bg`, `--seg-indicator-bg`, `--nav-indicator-bg`).

## Variables (resumen)

- Color: `--page-bg`, `--bg1..3`, `--surface`, `--control-bg`, `--border`, `--ink`, `--ink-soft`, `--ink-faint`, `--accent`, `--accent-soft`, `--success|warning|danger` (+ `-bg`, `-ink`), `--neutral-bg`, `--neutral-ink`, `--dialog-bg`, `--on-accent` (texto sobre acento y rellenos de estado), `--on-overlay` (texto sobre scrim), `--overlay-bg`, `--overlay-bg-sheet`, `--overlay-bg-menu`, `--disabled-fg`, `--disabled-bg`.
- Forma: `--r-*` (radios), `--row-h`, `--shell-pad`, `--shell-gap`, `--card-lift-y`, `--btn-lift-y`.
- Sombras: `--sh-card`, `--sh-overlay`, `--sh-dialog`, `--sh-popover`, `--sh-sticky-left`, `--sh-sticky-right`, `--sh-highlight-row`, `--btn-primary-shadow*`, `--seg-indicator-shadow`.
- Botón primario, segmentado, navegación, tabla y pills: `--btn-*`, `--seg-*`, `--nav-*`, `--table-head*`, `--pill-*-fg`, `--row-*`.
- Foco y superficies flotantes: `--focus-ring`, `--ring-surface`, `--popover-edge`.

## Guardas automáticas

- `tokens.test.js`: paridad de variables entre claro y oscuro, contraste AA de `TEXT_PAIRS`, 3:1 de `NON_TEXT_PAIRS`, botón primario deshabilitado distinguible del habilitado, colores dentro de sRGB.
- `literal-values.test.js`: falla ante hex, `oklch(`, `rgb(`, `hsl(`, `text|bg|border|ring|from|to|via|fill|stroke` con blanco o negro, `shadow-[...]` y tamaños de texto en píxeles arbitrarios fuera de `src/theme/` y `src/components/ui/`. Las excepciones van en `ALLOWED` del test, por archivo y regla.
- `restyle-guards.test.js`: falla ante radios arbitrarios, `font-mono` fuera de los archivos de identificadores, `MeshBackground` y la variante de diseño retirada.
- `noLiteralBlur.test.js` (en `components/ui/`, escanea todo `src/`): prohíbe cualquier desenfoque o saturación de fondo (utilidades `backdrop-*`) y los tokens de blur.
- `noRetiredDesignTokens.test.js`: impide reintroducir el alias de acento retirado, el atributo de diseño y los nombres del registro de diseños.
- `themeInit.test.js`: script anti-flash de `frontend/index.html` (tema guardado, preferencia del sistema y fallback a claro).
- `focusVisibleTokens.test.js`: toda variable usada por un `:focus-visible` está definida; ningún anillo de foco usa alfa translúcido.
- `reducedMotion.test.js`: las animaciones y transiciones se neutralizan con "reducir movimiento".

## Baselines visuales

- Catálogo: `/dev/catalog` (disponible en `npm run dev` o con `VITE_ENABLE_CATALOG=true`). Specs: `e2e/specs/visual/catalog.spec.js` y `shell.spec.js`.
- Baselines en `e2e/specs/visual/__screenshots__/chromium/<spec>/b-<tema>-<viewport>/` (claro y oscuro, escritorio y móvil).
- Actualizar desde `e2e/`: `npm run test:visual:update -- -g "b-"`. Verificar: `npm run test:visual -- -g "b-"`.
- Un cambio visual aprobado actualiza sus baselines en el mismo PR y el PR enumera cuáles cambiaron.

## Qué NO se centraliza con tokens

Estructura y layout (orden y agrupación de ítems del menú, qué se muestra o se oculta por pantalla, grillas, anchos máximos), tipografía por pantalla (`text-size-13` y similares, tokens de tamaño en `index.css`: son tamaños, no color) y los textos. Cambiar eso es trabajo de componente, no de tokens.

## Cuántos usos tiene cada componente

Objetivo: saber que un componente se usa en N lugares y que cambiarlo aplica a todos.

- `npm run component-usage` (en `frontend/`) recorre `src` y, por cada componente exportado de `src/components/` (incluye `ui/`), imprime en cuántos archivos se importa y cuántas veces se usa en JSX, ordenado de más a menos usado. Excluye tests. Con `--json` devuelve el mismo informe como JSON (incluye el detalle por archivo de los elementos crudos). Acepta una carpeta como argumento (los tests lo corren contra `frontend/scripts/__fixtures__/component-usage/src`).
- Al final cuenta los elementos HTML crudos (`<button>`, `<table>`, `<input>`, `<select>`, `<textarea>`) fuera de `components/ui/` y el componente que los reemplaza, y lista los componentes compartidos que nadie importa. Los componentes que se importan a través de un barrel (`@/components/charts`) no se atribuyen al archivo que los define.

## Elementos crudos de formulario y tabla

- Regla de lint `local/no-raw-elements` (`frontend/eslint-rules/raw-elements.js`, error): prohíbe `<table>`, `<select>`, `<textarea>` e `<input>` fuera de `src/components/ui/` y de tests. Usar `Table*`, `Select` (shadcn, para listas con estilos propios) o `NativeSelect` (select nativo con el estilo compartido; mismo contrato `onChange`/`value`), `Textarea` e `Input`.
- `<input>` con `type` `checkbox`, `radio`, `file` o `hidden` queda permitido por la regla: son controles nativos sin aspecto de campo (los `file` van ocultos detrás de un botón). Para checkbox preferir `Checkbox` de `ui/`.
- Excepciones por archivo: `RAW_ELEMENT_EXCEPTIONS` en `frontend/eslint-rules/raw-elements.js`. Hoy: `DocenteTomarAsistencia.jsx` (grilla con `rowSpan`, columnas pegajosas y bordes propios que `Table` no soporta). `raw-elements.test.js` falla si un archivo de la lista ya no tiene el elemento crudo: la lista solo puede achicarse.
- `<button>` queda fuera de esta regla (lo cubre otro paquete).

## Nota sobre `components/ui/`

Vienen del CLI de shadcn con modificaciones locales para leer variables. Un `npx shadcn add` sobre un archivo existente las pisa: revisar el diff antes de aceptar.
