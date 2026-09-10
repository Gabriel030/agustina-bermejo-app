# Frontend — convenciones y patrones (React)

Leé esto antes de tocar `frontend/`. Es la propuesta de patrones para el proyecto —
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
   a mano. Ejemplo: `Button` tiene `default/outline/secondary/ghost/destructive/
   gradient`; `TabsList` tiene `variant="line"` obligatorio en este proyecto.
4. **Patrón de filas clickeables**: `rounded-2xl px-4 py-3 transition-colors
   hover:bg-neutral-bg/60` + `ChevronRight` al final, dentro de un contenedor
   `flex flex-col gap-1`. No usar `divide-y divide-border` — fue descartado a favor
   de este patrón (ver DocenteMisComisiones, PadronAlumnos).
5. **Campos de solo lectura**: usar `<Input value={...} readOnly className="cursor-default" />`
   en vez de `<span>`. Mantiene consistencia visual con formularios editables.
6. **Botón de acción primaria**: `variant="gradient"`. Acción secundaria/cancelar:
   `variant="outline"`. Destructivo: `variant="destructive"`.

Este protocolo aplica tanto para implementación como para mockups.

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

- **Componentes de shadcn/ui (`components/ui/`) nunca se editan a mano** salvo para
  aplicar los tokens de Vidrio Líquido — son generados, si hace falta una variante
  nueva se agrega vía `npx shadcn add` o se envuelve (`GlassCard` envuelve el
  patrón `.card`, no lo reimplementa).
- **Composición sobre herencia / props gigantes**: para piezas con varios estados
  visuales relacionados (ej. un modal de confirmación con variantes), preferir el
  patrón de compound components de Radix (`<Dialog><DialogTrigger/><DialogContent/>`)
  antes que un único componente con 10 props booleanas.
- **Los layouts de rol son el ejemplo del patrón Strategy aplicado al frontend**:
  `RUTAS_POR_ROL` en `AppRouter.jsx` mapea rol → `{Layout, paginas}` en vez de un
  único componente con un `switch` gigante por rol. Cuando agregues una pantalla
  nueva de un rol, se agrega a `paginas` de ese rol, no se toca el router en sí.
- **`apiFetch` es una Facade** sobre `fetch` — cualquier lógica nueva de red
  (headers, reintentos, manejo de error) va ahí adentro, nunca duplicada
  componente por componente.

## Formularios

- **Formularios chicos (1-3 campos, sin pasos): `useState` a mano**, como
  `Login.jsx`. No hace falta una librería para dos inputs.
- **Formularios con validación no trivial o wizards multi-paso (ej. Alta de
  ciclo, Épica 4): React Hook Form + Zod.** El schema de Zod es la fuente de
  verdad de validación en el cliente y debería reflejar las mismas reglas que el
  DTO + Bean Validation del backend (sin duplicar texto de error a mano en cada
  campo). Todavía no está instalado — se agrega en la fase que lo necesite
  (primer candidato: Épica 4 Fase 4, wizard de alta de ciclo).

## Manejo de errores

- **`ErrorBoundary` de React en la raíz de `App.jsx`** (pendiente de agregar —
  hoy no existe, es deuda de la Fase 1 detectada al escribir este doc). Cubre
  errores de render que ninguna captura de `try/catch` en un handler puede
  atajar. No confundir con el manejo de errores de red, que ya vive en
  `apiFetch` (`AuthError`, `RateLimitedError`, mensaje genérico por status).
- Los mensajes de error al usuario se arman en el frontend a partir del status
  HTTP (ver `docs/context/backend.md` — el backend no manda body de error), nunca
  se intenta parsear un mensaje que el backend no envía.

## Naming

- **Nombres de dominio (páginas, hooks de negocio, componentes de feature) en
  español**, igual que el backend (`verPerfilPropio`, `AltaCicloPaso1`). Nombres
  técnicos genéricos de infraestructura en inglés (`apiFetch`, `useAuth`,
  `ProtectedRoute`) — mismo criterio que ya usa el backend
  (`resolveAuthenticatedPerson` en inglés, `actualizarDatosPropios` en español).
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
- **Tablas densas**: patrón "card por fila" en mobile en vez de scroll
  horizontal — se resuelve puntual por pantalla al implementarla, no hay una
  solución genérica forzada para todas.

Mockup de referencia del patrón "card centrada": ver `Login (mobile)` en
[[Diseño UI - Vidrio Líquido]] (Obsidian) antes de implementar cualquier
pantalla de ese tipo.

## Tabs / navegación secundaria por pestañas

**Siempre el componente `Tabs`/`TabsList`/`TabsTrigger` de shadcn
(`components/ui/tabs.jsx`), con `<TabsList variant="line">`** (subrayado bajo la
opción activa) — nunca un grupo de `<button>` hecho a mano, aunque sea "solo
para filtrar una lista" y no para partir contenido en paneles separados. Antes
de esta regla había tres implementaciones distintas conviviendo (botones
píldora con gradiente en Catálogo/Mis carreras, botones píldora blancos en
Director/Aprobaciones, y el `Tabs` de shadcn en Docente/Asistencia) — se
probó también la variante píldora por defecto (`bg-muted`, sin `variant`) como
estándar único, pero el estado activo no se distinguía lo suficiente del
inactivo, así que se descartó a favor de `variant="line"`.

Aplica tanto si las pestañas separan paneles de contenido distintos (ej.
Asistencia/Historial/Reporte en Docente) como si solo cambian el filtro de una
misma lista (ej. Agendadas/Cerradas en Preceptor/Mesa de examen,
Pendientes/Historial en Director/Aprobaciones) — visualmente son la misma
interacción para el usuario y tienen que verse igual.

Nota para tests: `TabsTrigger` renderiza `role="tab"` (semántica ARIA real de
Radix), no `role="button"` — los tests que interactúan con una tab usan
`screen.getByRole('tab', { name: '...' })`, no `'button'`.

## Motion / animación

Solo las transiciones ya definidas en el sistema de diseño (spring
`cubic-bezier(0.22,1,0.36,1)`, hover `translateY(-2px) scale(1.01)` en botones
primarios, `translateX(3px)` en filas de nav). No agregar animación nueva "porque
se puede" — cada pantalla del mockup ya especifica qué se anima.

## Ver también

- [docs/context/testing.md](testing.md) — cómo testear todo lo de acá.
- Skill `design-patterns` — para elegir un patrón GoF/táctico en un caso puntual
  que no esté ya cubierto por las reglas de arriba.
