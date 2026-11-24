# Frontend — convenciones y patrones (React)

> Verificado: 2026-10-31, commit `cd1ede80`.

Leé esto antes de tocar `frontend/`. Los ejemplos de rutas/carpetas de abajo son
ilustrativos (no todos existen aún); el inventario real de componentes vive en
[components.md](components.md). Es la propuesta de patrones para el proyecto —
lo ya construido en la Fase 1 (Fundación técnica y login) la sigue; lo que falta
(Épica 2 en adelante) la toma como punto de partida obligatorio.

## Protocolo obligatorio antes de tocar cualquier componente o pantalla

**Siempre antes de crear o modificar cualquier componente, pantalla o pieza de UI:**

1. **Listar el catálogo existente** — `ls src/components/` y `ls src/components/ui/`
   para saber qué componentes compartidos hay. No inventar uno nuevo sin primero
   verificar que no existe ya.
2. **Leer 1-2 pantallas similares ya implementadas** — buscar en
   `src/features/*/pages/` o `src/pages/` una pantalla del mismo tipo (lista,
   detalle, formulario, wizard) y copiar sus patrones de layout, filas, botones
   y cards exactamente, sin reinventarlos.
3. **Verificar variantes de componentes existentes** antes de extender — si el
   componente admite `variant=`, revisar cuáles existen antes de agregar estilos
   a mano. Ejemplo: `Button` tiene `primary/default/outline/secondary/ghost/destructive/
   link` (+ prop `loading`); `TabsList` usa `variant="segmented"` (pill deslizante)
   como estándar en este proyecto.
4. **Patrón de filas clickeables**: `rounded-2xl px-4 py-3 transition-colors
   hover:bg-neutral-bg/60` + `ChevronRight` al final, dentro de un contenedor
   `flex flex-col gap-1`. No usar `divide-y divide-border` — fue descartado a favor
   de este patrón (ver DocenteMisComisiones, PadronAlumnos).
5. **Campos de solo lectura**: usar `<Input value={...} readOnly className="cursor-default" />`
   en vez de `<span>`. Mantiene consistencia visual con formularios editables.
6. **Botón de acción primaria**: `variant="primary"`. Acción secundaria/cancelar:
   `variant="outline"`. Destructivo: `variant="destructive"`.
7. **Componentes compartidos clave**: `StatusPill` (tono semántico, nunca spans
   con colores hardcodeados), `EmptyState` (estados vacíos consistentes),
   `GlassCard` (superficie base; variante interactiva con hover/shimmer),
   `StatCard` (KPI tiles en dashboards). Instalar nuevos via `npx shadcn add
   <componente>`, no a mano.

7. **Colores solo por tokens** (ver "Sistema visual (dirección B)" abajo). Nunca
   literales de Tailwind (`text-red-600`, `bg-green-50`) ni `oklch(...)`/hex a mano:
   rompen el modo oscuro.

Este protocolo aplica tanto para implementación como para mockups.

## Flujo de diseño (mockups, pantallas nuevas, cambios visuales)

1. Correr el protocolo de arriba (catálogo, pantallas similares, variantes, tokens).
2. Armar el diseño **siempre como Artifact interactivo** (claro/oscuro, responsive) con la
   skill `mockup-direccion-b`, para verlo dentro de Claude; nunca un `.html` suelto.
3. **Pasarlo por el agente `ux-advisor`** (`.claude/agents/ux-advisor.md`) antes de
   mostrarlo, y de nuevo tras cada iteración importante. Revisa: componentes
   existentes, coherencia con pantallas hermanas y con los tokens, usabilidad y
   experiencia, redundancias, sobrecarga de la pantalla, responsive e intuitividad.
   Sus hallazgos se incorporan o se descartan explicando por qué.
4. Presentar el diseño al usuario y esperar su aprobación explícita. Sin ella, no se
   escribe código.
5. Implementar reusando el catálogo; verificar en el navegador en modo claro, oscuro y
   ancho móvil; `npm run lint`, `npm run build` y `npm run test` en verde antes del PR
   (ver [workflow.md](workflow.md)).

Criterio de diseño: intuitivo y funcional, una sola forma de hacer cada cosa,
sin controles duplicados en un mismo nivel de navegación y sin sobrecargar la pantalla.

## Filosofía general

Mismo principio que el backend: sin abstracciones especulativas, sin librerías
nuevas hasta que haya un caso de uso real que las justifique (YAGNI). Cada decisión
de abajo tiene su motivo — si un patrón agrega complejidad sin resolver un
problema real de la pantalla que estás haciendo, no lo apliques ahí solo porque
está en esta lista.

## Estado

- **Estado de sesión / cross-cutting (auth, y lo que surja de similar alcance):
  React Context + hooks** (`AuthContext`, patrón `useX()` que valida que está
  dentro del Provider y tira si no). No hay Redux/Zustand — no hay estado global
  lo bastante grande hoy para justificar una librería de estado dedicada. Si en
  algún momento aparece estado compartido complejo (ej. un wizard de varios pasos
  con estado que sobrevive navegación entre rutas), primero evaluar si alcanza con
  Context + `useReducer` antes de sumar una dependencia nueva.
- **Estado de servidor (datos que vienen del backend): TanStack Query (React
  Query)** a partir de la Épica 2 — la Fase 1 usó `apiFetch` a pelo porque el único
  fetch era login/perfil, sin necesidad de cache/revalidación. En cuanto haya
  pantallas con listados, mutaciones y necesidad de invalidar cache (ej. "aprobar
  ciclo" invalida la bandeja de ciclos), se suma React Query — no antes. Patrón:
  un hook por recurso, colocado en la carpeta de la feature
  (`features/oferta-academica/hooks/useCiclos.js`), nunca fetch directo dentro de
  un componente de pantalla.
- **Estado local de UI (inputs, toggles, pasos de un form chico): `useState`
  dentro del componente**, sin pasarlo por Context ni por una librería. Ver
  `Login.jsx` como referencia — dos campos, sin necesidad de nada más.

## Estructura por módulo (Épica 18, Fase F0)

El front se organiza por módulo de backend, no por rol:

- `src/features/<módulo>/` con `routes.jsx` (y, a medida que F1 mueve cada módulo, `pages/`,
  `components/`, `hooks/` y `api.js`). Módulos: `identity`, `academic-structure`, `enrollment`,
  `attendance`, `evaluation`, `billing`, `graduation`, `announcements`, `notifications`,
  `workspace` (pantallas que combinan varios módulos).
- Cada `routes.jsx` exporta `routes = { public, shared, role }`: `public` (rutas sin sesión),
  `shared` (rutas de cualquier usuario autenticado, bajo el layout del rol o el de autoservicio) y
  `role` (`{ roles, path, element }`, relativas a `/<rol>`).
- `src/routes/featureRoutes.jsx` junta las de todas las features y `src/routes/roleConfig.js`
  define layout y páginas de navegación de cada rol (las que no tienen ruta real caen en
  `Placeholder`). `AppRouter` solo compone: para sumar una pantalla se toca solo el `routes.jsx`
  de su módulo.
- Un componente que usan dos o más módulos va a `src/components/`, no a una feature.
- F1 ya movió identity, academic-structure (incluye lo que era `gestion-academica` y `oferta-academica`),
  enrollment, attendance, evaluation, graduation, announcements, notifications y workspace: tienen
  `pages/`, `components/` y `hooks/` propios. Sigue pendiente billing (V2-14/15/16): sus páginas viven
  todavía en `src/pages/`, y `features/billing/` solo tiene `routes.jsx`, `index.js` y los hooks
  `usePaymentStanding` y `useFinancialDashboard` que salieron de los hooks por rol.
- Los hooks por rol `useAlumno`, `useDocente` y `useDirector` ya no existen: cada hook vive en el
  módulo cuyo endpoint llama y se consume desde el `index.js` de ese módulo.
- `workspace` guarda las pantallas que combinan varios módulos (`AlumnoInicio`, `AlumnoMisCarreras`,
  `AlumnoMateriaDetalle`, `DirectorAprobaciones`, `DocenteMisComisiones`) y compone hooks y componentes
  de cada módulo a través de sus `index.js`.
- Convención "copiar, no borrar": mientras un hook por rol mezcla módulos, cada módulo copia a su
  carpeta solo sus funciones y el archivo original queda hasta que workspace lo borre.
- Un módulo expone a los demás solo lo que sale de su `index.js`.

Regla de límites (ESLint, `no-restricted-imports`, en `warn` hasta M6): una feature no importa
de otra salvo por su `index.js`, y `components/` y `lib/` no importan de `features/`. La lista
`FEATURES` de `eslint.config.js` cubre las 18 carpetas de `src/features/`.

Política layouts/shell → features: `layouts/**` y `components/TopHeader.jsx` (la cabecera, que es
el único archivo de shell en `components/`) pueden importar de `features/<módulo>` solo a través
del `index.js` del módulo (`@/features/<módulo>`), nunca de rutas profundas. Por eso
`features/identity/index.js` exporta `usePerfilPropio`, `useAvatar` y `LegalNoticeBanner`, y
`TopHeader` y `RoleShell` importan de ahí. El resto de `components/` y `lib/` sigue sin poder
importar de `features/`.

Estado de la regla (F0, PR "límites" de E18 F1): antes había 12 warnings en `eslint .` (7 de
límites y 5 de `react-hooks/incompatible-library` y `exhaustive-deps`, fuera de esta regla); ahora
hay 0 de límites y 5 warnings en total, todos de `react-hooks` (`DataTable`, `InscripcionWizard`,
`GradeGridTable`, `AlumnoPagarOnline`, `AlumnoPagos`). `eslint .` termina con 0 errores y el CI no
usa `--max-warnings`, así que la regla sigue en `warn`. Los cruces que quedaban se reescribieron a
los `index.js` de la feature dueña: `billing/dashboard/FinancialDashboardPage`, `broadcasts`,
`institution`, los tests de `workspace` (`AlumnoInicio` y `pilotScreens.a11y`) y `TopHeader` /
`RoleShell`. Se exportaron desde `academic-structure/index.js` (`useSubjects`,
`useUnsavedChangesGuard`) e `identity/index.js`, y se creó `audit/index.js`. Un ciclo que ya
existía (`enrollment/index.js` ↔ `InscripcionMaterias.jsx` ↔ `useMateriasDelPlan.js`) no cambió.
Cada feature tiene `routes.jsx`, `pages/`, `components/` y
`hooks/` (las carpetas vacías llevan `.gitkeep`); `api.js` se crea cuando el módulo tenga llamadas
propias. Mover las páginas que siguen en `src/pages/` (F1 de billing) se coordina con líder-3 por
las listas de paths de `eslint-rules/` y de los guards (`noStickyBottomOutsideActionBar`).

## Estructura de carpetas

Fase 1 usa carpetas **por tipo** (`api/`, `components/`, `context/`, `layouts/`,
`pages/`, `routes/`, `theme/`) porque es infraestructura transversal, no una
feature de negocio. **A partir de la Épica 2, las pantallas de negocio van en
carpetas por feature**, no por tipo:

```
src/
  features/
    oferta-academica/
      components/       # UI específica de esta feature, no reusada afuera
      hooks/             # useCiclos, useAltaDeComision, etc.
      pages/              # las pantallas que cuelgan de las rutas
    autoservicio-alumno/
      ...
  components/            # compartido cross-feature: GlassCard, Sidebar, ui/ (shadcn)
  layouts/                # los 5 *Layout.jsx por rol, siguen acá (son infra de ruteo)
  api/, context/, routes/, theme/  # igual que Fase 1
```

Regla simple para decidir dónde va un componente nuevo: si lo usa una sola
feature, vive en `features/<feature>/components/`; si lo usan dos o más, sube a
`src/components/`. No subir "por las dudas" — esperar a que un segundo lugar lo
necesite de verdad.

## Componentes y composición

- **Componentes de shadcn/ui (`components/ui/`)**: los componentes nuevos se agregan
  con `npx shadcn add` (y se revisa el resultado, ver "Reglas prácticas"). Los ya
  adaptados (`button`, `tabs`, `table`, `sonner`) se editan solo para aplicar tokens
  de la dirección B o sumar una variante/prop del sistema (`primary`, `loading`,
  `segmented`); no se les mete lógica de una pantalla puntual. Para algo específico,
  se envuelve (`GlassCard` envuelve el patrón `.card`, no lo reimplementa).
- **Composición sobre herencia / props gigantes**: para piezas con varios estados
  visuales relacionados (ej. un modal de confirmación con variantes), preferir el
  patrón de compound components de Radix (`<Dialog><DialogTrigger/><DialogContent/>`)
  antes que un único componente con 10 props booleanas.
- **Los layouts de rol son el ejemplo del patrón Strategy aplicado al frontend**:
  `ROLE_CONFIG` (`frontend/src/routes/roleConfig.js`) mapea rol → `{Layout, pages}` y
  `FEATURE_ROUTES` (`routes/featureRoutes.jsx`) declara las rutas de cada funcionalidad
  (públicas, compartidas y por rol) en vez de un único componente con un `switch`
  gigante por rol. Cuando agregues una pantalla nueva de un rol, se agrega a `pages` de
  ese rol y a `FEATURE_ROUTES`, no se toca `AppRouter.jsx` en sí.
- **`apiFetch` es una Facade** sobre `fetch` — cualquier lógica nueva de red
  (headers, reintentos, manejo de error) va ahí adentro, nunca duplicada
  componente por componente.

## Formularios

- **Formularios chicos (1-3 campos, sin pasos): `useState` a mano**, como
  `Login.jsx`. No hace falta una librería para dos inputs.
- **Formularios con validación no trivial o wizards multi-paso: React Hook Form +
  Zod.** El schema de Zod es la fuente de verdad de validación en el cliente y
  debería reflejar las mismas reglas que el DTO + Bean Validation del backend (sin
  duplicar texto de error a mano en cada campo). Ya instalado y en uso desde Épica 4
  (wizard de alta de ciclo, `NuevoAlumno.jsx`, etc.); copiar ese patrón.

## Manejo de errores

- **`ApiError`** (`api/apiError.js`): todo error HTTP de `apiFetch` lo es, con
  `status`, `code`, `retryable`, `requestId` y `violations` (cuerpo estándar del
  catálogo `docs/api/error-catalog.md`).
- **`describeError(error)`** (`api/describeError.js`) devuelve `{ kind, message,
  retryable, requestId }` con el mensaje en castellano por código, transversal o de
  dominio (`api/domainErrorMessages.js` y `api/billingErrorMessages.js`). Un test cruza
  `ERROR_MESSAGES` contra todo el catálogo: sumar un código en el backend obliga a
  sumarlo acá, y borrar uno obliga a quitarlo. Una feature puede seguir teniendo su
  mensaje más específico (prioridad: mensaje de la feature, luego `describeError`).
  `ApiError.message` nunca trae texto del backend: es un mensaje por estado HTTP.
  Números y porcentajes en pantalla pasan por `lib/formatNumber.js` (es-AR). Regla en mutaciones:
  `toast.error(describeError(err).message)`.
- **Timeouts**: 30 s por pedido (`timeoutMs` en las opciones lo cambia), 120 s para
  `FormData` y descargas. Un timeout se trata como falla de red.
- **`ErrorBoundary`** (`components/ErrorBoundary.jsx`) en la raíz de `App.jsx` y por
  pantalla en `RoleShell` (se resetea al cambiar `resetKey`, la ruta). Muestra
  `ErrorFallback`. Los errores de render los captura el boundary; los de red,
  `apiFetch`.
- **`reportClientError`** (`lib/reportClientError.js`) es el puerto de reporte: por
  defecto solo `console.error`; se enchufa con `setClientErrorReporter` cuando
  exista Sentry (Épica 17). También reporta los `unhandledrejection`.

## Naming

- **Todos los identificadores de código en inglés** — clases, funciones, variables,
  hooks, props, nombres de archivo. El backend completó su migración completa
  (PRs #66-#81); el frontend sigue el mismo criterio sin excepción. Lo que ya
  existe en castellano no se renombra de forma retroactiva salvo pedido explícito.
  El texto visible al usuario final sigue siempre en castellano, y los enums se
  muestran traducidos.
- **Cero comentarios ni JSDoc en código nuevo** (misma regla de `CLAUDE.md`).
- Archivos de componentes/páginas/layouts: `PascalCase.jsx`. Hooks, utils, clientes:
  `camelCase.js`. Un componente por archivo, nombre de archivo = nombre exportado.

## Responsive — obligatorio en toda pantalla nueva

El proyecto termina empaquetado en nativo vía Capacitor (ver stack) — nunca
se implementa una pantalla "solo desktop" para adaptarla después. Breakpoints
Tailwind default, mobile-first (clases sin prefijo = mobile, `md:`/`lg:`
agregan desde ahí, nunca al revés):

- **Layouts por rol** (`Sidebar.jsx` + contenido): el sidebar fijo a la
  izquierda es el layout `lg:` (≥1024px). En mobile/tablet colapsa a un
  **menú hamburguesa** (`MobileMenu.jsx`, drawer lateral con los mismos
  ítems) — decisión revisada con el usuario; la versión anterior de esta
  convención pedía una bottom tab bar, descartada a pedido explícito.
- **Pantallas centradas sin sidebar** (Login, Registrarse): `GlassCard`
  pasa de ancho fijo a `w-full` con padding en mobile, ancho fijo recién en
  `md:`+.
- **Grillas de cards**: `grid-cols-1` mobile → `grid-cols-2` `md:` →
  `grid-cols-3` `lg:`. Nunca scroll horizontal de cards.
- **Tablas densas**: envolver en `<div className="overflow-x-auto">` y darle a
  la `<Table>` un `className="min-w-[Xpx]"` adecuado — permite scroll horizontal
  contenido sin romper el layout general. El valor de `min-w` se ajusta al ancho
  mínimo razonable de la tabla en cada pantalla (típico: 480-720px).

Mockup de referencia del patrón "card centrada": ver `Login (mobile)` en
[[Diseño UI - Vidrio Líquido]] (Obsidian) antes de implementar cualquier
pantalla de ese tipo.

## Tabs / navegación secundaria por pestañas

**Siempre el componente `Tabs`/`TabsList`/`TabsTrigger` de shadcn
(`components/ui/tabs.jsx`), con `<TabsList variant="segmented">`** — cinta con un
fondo en gradiente que se desliza detrás de la pestaña activa (hook
`useSlidingIndicator`). Nunca un grupo de `<button>` hecho a mano, aunque sea "solo
para filtrar una lista". `variant="line"` (subrayado) fue reemplazado por
`segmented` (PR #80 + #89); no usarlo en pantallas nuevas.

Aplica tanto si las pestañas separan paneles distintos como si solo cambian el
filtro de una misma lista — visualmente es la misma interacción.

Nota para tests: `TabsTrigger` renderiza `role="tab"`, no `role="button"` — usar
`screen.getByRole('tab', { name: '...' })`.

## Sistema visual (dirección B)

Dirección B (plana), aprobada con mockup antes de codificar. La fuente de verdad de tokens es `src/theme/tokens.css`; las notas de producto y el documento de diseño viven en Obsidian (ubicación en [specs.md](specs.md)). Si difiere de este archivo, gana el código y se corrige la doc.

### Tokens y modo oscuro
- Única fuente de color: `src/theme/tokens.css` (oklch), mapeada a utilidades
  Tailwind en `index.css` (`@theme inline`): `bg-surface`, `text-ink`,
  `text-ink-soft`, `text-ink-faint`, `border-border`, `bg-neutral-bg`, `text-danger`,
  `bg-danger-bg`, `text-success`, `bg-success-bg`, `text-warning`, `bg-accent-soft`,
  `text-brand`, etc. Ver ese archivo para la lista completa.
- Modo oscuro real: atributo `data-theme` en `<html>` (la preferencia del sistema la resuelve el script de `index.html`). Lo maneja `context/ThemeContext.jsx` (`useTheme()` →
  `{ tema, alternarTema }`, persiste en `localStorage`); `index.html` tiene un script
  inline que evita el flash del tema equivocado; el switch es `ThemeToggle.jsx`.
  Los tokens oscuros se definen solo bajo `[data-theme='dark']` (sin media query). Un solo archivo de tokens: ver [design-system.md](design-system.md).
- Los tests que renderizan árboles completos necesitan `<ThemeProvider>`; `matchMedia`
  está polyfilleado en `src/test/setup.js`.

### Catálogo de componentes compartidos (resumen; inventario completo en [components.md](components.md))
| Necesidad | Usar |
|---|---|
| Card / contenedor de superficie | `GlassCard`; con `interactive` si es clickeable (elevación + borde de marca al hover) |
| Entrada escalonada de listas/grillas de cards | `<Stagger>` envolviendo los hijos (`delayStep` en ms) |
| KPI / métrica | `StatCard` (dentro de `Stagger` en grillas) |
| Tablas | `components/ui/table.jsx` (`Table`, `TableHeader`, `TableRow`, `TableHead`, `TableCell`), dentro de un `GlassCard` — la tabla no trae borde propio. Header sticky, mayúsculas, hover `accent-soft`. Excepción: grillas con rowSpan/colSpan/sticky propias (ej. tomar asistencia) |
| Encabezado ordenable | `features/evaluation/components/SortableColumnHeader.jsx` (ya con el estilo del header de tabla) |
| Pestañas | `Tabs` con `variant="segmented"` |
| Stepper / wizard | `components/Stepper.jsx` único (completados en verde con tilde, activo en color de marca con pulso; pasos visitados clickeables). No hay otro Stepper |
| Notificaciones/feedback de acciones | `toast` de `sonner` (`toast.error/success`), con `<Toaster />` ya montado en `App.jsx`. No mostrar errores de mutaciones como párrafos inline |
| Botón con acción en curso | `<Button loading={mutation.isPending}>` (no combinar `loading` con `asChild`) |
| Carga | `Skeleton` (usa `skeleton-shimmer`); `EmptyState` para vacíos |
| Badges de estado | `StatusPill` / `EstadoSolicitudBadge` / `ui/badge` |
| Menú lateral / drawer mobile | `Sidebar`: fondo deslizante sobre el ítem activo (`useSlidingIndicator`, marcador `nav-item-activo`). `MobileMenu` (drawer): el ítem activo usa `--nav-indicator-bg` |

### Reglas prácticas
- **`components/ui/` generado por CLI**: usar `npx shadcn add <x>` y revisar el
  resultado — dos veces generó imports rotos (`next-themes`, un paquete `cn` falso en
  vez de `@/lib/utils`) y variables CSS que no existen (`--popover`, `--radius`; las
  reales son `--surface`, `--radius-lg`).
- Estados de color semánticos siempre por token (`text-danger`, `bg-success-bg`,
  `border-success`), nunca literales.
- Superficies: `bg-surface` (o `GlassCard`); sin desenfoques de fondo ni degradés (los prohíbe `noLiteralBlur.test.js`), ni un `oklch(...)` a mano (no se adapta al modo oscuro).
- Scroll con desvanecido en los bordes: utilidad `.scroll-fade-y`.
- Un `MutationObserver` mide el ítem activo en `useSlidingIndicator(selector)`; para
  una lista nueva con indicador deslizante, reusar el hook, no reimplementarlo.

## Motion / animación

Base: spring `cubic-bezier(0.22,1,0.36,1)`. Las cards interactivas y el botón primario se elevan al hover según `--card-lift-y` y `--btn-lift-y` (en B valen 0px). Las animaciones propias son solo estas: `skeleton-shimmer`, `stagger-in` (vía `<Stagger>`), `tab-in` (pestaña `segmented`), `pop-in` (tilde del Stepper) y `pulse-ring` (paso activo), keyframes en `index.css`; además, la hamburguesa animada
(transforms en `MobileMenu.jsx`) y el stagger de ítems del drawer (`tw-animate-css`).
`index.css` tiene una regla global `prefers-reduced-motion` que las desactiva. No
agregar animación nueva "porque se puede".

## Ver también

- [docs/context/testing.md](testing.md) — cómo testear todo lo de acá.
- Skill `design-patterns` — para elegir un patrón GoF/táctico en un caso puntual
  que no esté ya cubierto por las reglas de arriba.
