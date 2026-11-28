# Auditoría de reglas de diseño y movimiento (E36-A, parte 2)

Base: `origin/develop` (825a6f587), solo lectura. Complementa `restyling-auditoria-etiquetas.md` (color de etiquetas, pastillas y estados), que NO se repite acá. Alcance: `frontend/src` sin tests, `test/` ni `mocks/`; billing se cuenta aparte porque está fuera del restyling pantalla por pantalla (recibe el arreglo automático de tokens y componentes).

Método: `grep -rE` por patrón sobre `frontend/src` (sin `npm`, sin build, sin capturas). Los números son **líneas** que matchean, no instancias visuales; cada fila trae el comando de verificación en la sección 8. Lo que NO cubre un grep (jerarquía visual real, densidad, contraste dinámico, comportamiento en pantalla) queda marcado como «a verificar con `ux-advisor` y capturas» en la sección 7.

Decisiones ya tomadas por Gabriel (no se reabren): paleta A «Petróleo y coral», movimiento sobrio con `prefers-reduced-motion` y sin interruptor propio, tonos de etiqueta cerrados (neutro, acento, éxito, aviso, peligro), un solo `StatusPill`, un solo `Notice`, un solo sistema de movimiento.

## 0. Hallazgos principales

1. **Colores literales: cero.** No hay hex, `oklch(`, `rgb(` ni escala cruda de Tailwind fuera de `theme/` y `components/ui/` (las guardas lo impiden). Lo mismo con blur y degradés decorativos: `backdrop-*` 0 usos, `variant="gradient"` 0 usos. Lo ya hecho en E26-D se sostiene.
2. **No existe un sistema de movimiento: existen cinco, sin tokens.** Conviven keyframes propios (`index.css`), `tw-animate-css` en overlays, la librería `motion` (`m.div`) en layouts y pantallas públicas, 116 líneas de `transition*` de Tailwind en 89 archivos y la animación de Recharts. No hay una sola variable de duración ni de easing en `tokens.css` (`--dur-*` y `--ease-*`: 0 definiciones). Duraciones en uso: 100, 150, 200, 250, 280, 300, 400, 500, 700 ms y bucles de 1,6 y 1,8 s; easings: `cubic-bezier(0.22,1,0.36,1)` copiado como literal en 11 lugares, `easeOut`, `ease-out`, `ease-in-out` y un rebote `cubic-bezier(0.34,1.56,0.64,1)`.
3. **Dos escalas tipográficas paralelas**: 15 tokens `text-size-*` (1.531 líneas, 358 archivos) y la escala por defecto de Tailwind (`text-xs/sm/base/lg/xl/2xl`: 1.194 líneas, 332 archivos), con medios píxeles (10,5 / 11,5 / 12,5 / 13,5 / 14,5: 274 usos) y 181 usos de 10 a 11 px.
4. **La escala de radios está invertida**: `--r-2xl` vale 0,5 rem y `--r-xl` 0,75 rem (`theme/tokens.css:42-47`), así que `rounded-2xl` (115 usos) se ve MÁS CHICO que `rounded-xl` (220 usos). Hay 7 nombres para 4 valores reales.
5. **Los títulos de página no pasan por un componente**: 83 `<h1>` contra 70 `PageContainer title=`; los h1 escritos a mano usan 4 tamaños distintos.

## 1. Familias de pantallas (para ordenar E36-E a E36-I)

Las familias salen de la carpeta `features/*` y de los componentes compartidos. «Compartido» = `components/` (excluye `components/ui/`, que son los de shadcn y se tocan en E36-C).

| Familia | Peso en hallazgos | Qué domina |
|---|---|---|
| Compartido (`components/`, `layouts/`) | Muy alto: define el movimiento de todo | `Stepper`, `Sidebar`, `MobileMenu`, `ThemeToggle`, `GlassCard`, `Stagger`, `RoleShell` |
| `features/identity` | Alto | `m.div` con hover escala, 21 h1 a mano, spinners propios, entradas animadas distintas |
| `features/academic-structure` | Alto | 56 usos de 10 a 11 px, 58 radios xl/2xl, 13 h1 a mano, `rounded-[10px]` |
| `features/evaluation` | Alto | 123 tamaños por defecto de Tailwind, 38 de 10 a 11 px, `text-[7.5px]`, grilla de notas |
| `features/enrollment` | Medio-alto | 125 tamaños por defecto, 21 radios xl/2xl, chevrons de acordeón |
| `features/workspace`, `announcements`, `attendance`, `graduation` | Medio | `transition-all` propios, h1 a mano, barras y tortas propias |
| `features/billing` | Alto en números, fuera de alcance | 473 tamaños por defecto, 123 radios xl/2xl/3xl, 13 transiciones, 10 desplazamientos de hover |
| `features/permissions`, `backups`, `notifications`, `audit`, `data-import`, `platform`, `broadcasts`, `schedule`, `institution` | Bajo | Casos puntuales (secciones 2 y 3) |

## 2. Movimiento

Prioridad: Alta = rompe la coherencia del sistema que Gabriel pidió o puede ocultar contenido; Media = inconsistencia visible; Baja = detalle.

| ID | Familia / pantalla | Regla violada | Ocurrencias (archivo:línea) | Propuesta de token o componente | Prioridad |
|---|---|---|---|---|---|
| M1 | Todas (cuenta: 116 líneas `transition*` en 89 archivos, 25 `duration-*` en 13, 13 `ease-*` en 8) | Un solo sistema de movimiento: duraciones y easings sin tokens | `Stepper.jsx:60,69,70,88`, `Sidebar.jsx:17,66`, `GlassCard.jsx:9`, `MobileMenu.jsx:25,34`, `ThemeToggle.jsx:22`, `Stagger.jsx:16`, `index.css:148`, `NotificationBell.jsx:131` | Tokens en `tokens.css`: `--dur-fast` 120 ms, `--dur-base` 200 ms, `--dur-slow` 300 ms; `--ease-out` `cubic-bezier(0.22,1,0.36,1)` (ya es el mayoritario) y `--ease-standard`. Mapearlos en `@theme` de `index.css` (`duration-fast`, `ease-out`) para que las pantallas dejen de escribir literales | Alta |
| M2 | Compartido: `ThemeToggle`, `Stepper` | Movimiento sobrio: rebote prohibido | `ThemeToggle.jsx:22` (easing con sobreimpulso `0.34,1.56`), `index.css:111-115` (`pop-in` escala hasta 1,15) usado en `Stepper.jsx:69,70` | Retirar `pop-in` y el rebote; el check/X entra con la entrada estándar (M5). `ThemeToggle` usa `--ease-out` | Media |
| M3 | Compartido + billing + data-import | Movimiento sobrio: bucles infinitos decorativos | `Stepper.jsx:62,66` (`pulse-ring` 1,8 s infinito), `ImportProgressPanel.jsx:12` y `RunProgressCard.jsx:9,16` (`animate-pulse`), `index.css:189` (shimmer 1,6 s: aceptable, es estado de carga) | Dejar sólo shimmer (carga) y spinner (trabajo en curso). `pulse-ring` y `animate-pulse` se reemplazan por el estado estático con ícono; el paso activo del `Stepper` se distingue por tono, no por latido | Media |
| M4 | `features/identity` (Login, Registrarse, ForgotPassword) | Movimiento sobrio y tokens: hover con desplazamiento y escala; contradice `--btn-lift-y: 0px` y `--card-lift-y: 0px` (`tokens.css:59-60`) | `Login.jsx:149`, `Registrarse.jsx:269`, `ForgotPassword.jsx:119,158,265` (`whileHover={{ y: -2, scale: 1.01 }}`, `whileTap` 0,98) | Quitar `whileHover`/`whileTap` de esos botones (el `Button` ya define su hover); si se quiere feedback de presión, un `active:` único en `Button` | Media |
| M5 | Compartido + identity + `Stagger` | Un solo patrón de entrada | Pantalla: `RoleShell.jsx:131-137` y `PublicLayout.jsx:21-27` (opacidad 150 ms). Pantallas públicas: `ReviewEmailChangePage.jsx:126`, `BajaComunicaciones.jsx:22-25`, `VerificacionEmail.jsx:26-29` (y 12 px, 250 ms). Pestañas: `index.css:147-149` (`tab-in`, 8 px, 280 ms). Listas: `Stagger.jsx:16` (`stagger-in`, 10 px, 400 ms). Suelto: `AlumnoInicio.jsx:83` (`animate-[stagger-in_0.3s_ease-out_both]`) | Una sola animación `enter` (opacidad + 8 px, `--dur-base`, `--ease-out`) con utilidad `animate-enter` en `@theme`; `tab-in` y `stagger-in` pasan a ser esa. La transición de ruta de `RoleShell` queda en fade (ya se probó que el desplazamiento vertical se leía como flash). Las pantallas públicas usan `animate-enter` y dejan de importar `m` | Alta |
| M6 | `Stagger` en roster y listas largas | Sobrio: demora sin tope | `Stagger.jsx:12-17` (`index * 60 ms`, sin límite). Usos: `TabAlumnos.jsx:159`, `StaffSectionRoster.jsx:210`, `AlumnoMisCarreras.jsx:170`, `KpiBlocks.jsx:94,146` (billing) | Tope de 6 elementos (`min(index, 5) * 50 ms`) o sólo para grillas de tarjetas de resumen; en listados con filas no se usa | Alta |
| M7 | Compartido y `ui/` | Overlays con tiempos distintos | `dialog.jsx:42,61` y `alert-dialog.jsx:32,49` 100 ms; `popover.jsx:31`, `select.jsx:67`, `dropdown-menu.jsx:45` 100 ms; `sheet.jsx:40` 100 ms y `sheet.jsx:61` 200 ms `ease-in-out`; `MobileMenu.jsx:105` 150 ms y `:113` 200 ms | Menús y popovers `--dur-fast`; diálogos, hojas y menú móvil `--dur-base`; mismo easing | Media |
| M8 | `ui/` + 4 pantallas | `transition-all` anima propiedades no previstas (ancho, alto, sombra) | `button.jsx:9`, `toggle.jsx:7`, `tabs.jsx:39`, `Stepper.jsx:60,88`, `EnvioYCaducidad.jsx:23`, `ParametrosDeSegmento.jsx:14`, `SeccionMensaje.jsx:44`, `NotificationBell.jsx:131` (10 líneas, 9 archivos) | `transition-colors` o `transition-[...]` con la lista de propiedades; los tres botones de `announcements` pasan a una variante de `ChoiceChip`/`Button` | Media |
| M9 | Global (`index.css`, `App.jsx`, `chartTheme.js`) | Un solo mecanismo de reducción de movimiento | Bloque global `index.css:121-127`; `MotionConfig reducedMotion="user"` en `App.jsx:35`; `useChartAnimation` con `matchMedia` propio en `chartTheme.js:25-36`; 23 variantes `motion-reduce:`/`motion-safe:` en 18 archivos (p. ej. `MobileMenu.jsx:16`, `ProgressBar.jsx:33`) | Dejar los tres mecanismos de infraestructura (CSS global, `MotionConfig`, gráficos) y retirar las variantes por clase, que son redundantes con el bloque global. **A verificar en navegador**: el bloque global fija `animation-duration: 0.01ms` pero no toca `animation-delay`; con `Stagger` (`animation ... both` y demora `index*60ms`) los hijos podrían quedar en opacidad 0 durante la demora aunque el usuario pida reducir movimiento. Si se confirma, la propuesta es `animation-delay: 0s !important` en el mismo bloque y ampliar `reducedMotion.test.js` | Alta (por el posible contenido oculto) |
| M10 | Compartido: filtro segmentado e indicadores | «Indicador del filtro segmentado» de la decisión de movimiento no tiene componente | Los tokens `--seg-indicator-*` y `--r-seg*` no tienen ningún consumidor fuera de `tokens.css`; el único indicador animado es `Sidebar.jsx:66` (importado en 1 archivo) | En E36-C: `SegmentedFilter` sobre `ToggleGroup` con indicador que se desliza con `--dur-base`/`--ease-out` y los tokens ya existentes; usarlo donde hoy hay `ChoiceChip` o botones a mano como filtro | Media |
| M11 | Identity, billing, compartido | Un solo indicador de «trabajando» | `Loader2` con 6 tamaños: `FirstLoginConsentGate.jsx:28` (24 px), `BajaComunicaciones.jsx:32`, `VerificacionEmail.jsx:36`, `ConfirmarResponsable.jsx:31` (40 px), `AlumnoPagarOnline.jsx:88` (36 px), `GradeEntryCell.jsx:68` (12 px); más `<div>` con borde propio en `CargaInicial.jsx:6` y `ModuleGate.jsx:14`; `button.jsx:82` y `sonner.jsx:28` | Componente `Spinner` con `size` (sm, md, lg) y tono; los dos `<div>` de arranque lo usan | Baja |
| M12 | Gráficos, `ProgressBar`, `Stepper` | Duraciones fuera de la escala | `chartTheme.js:35` (`animationDuration: 700`), `ProgressBar.jsx:33` y `Stepper.jsx:88` (500 ms) | Gráficos 300 ms (`--dur-slow`) y barras `--dur-slow`; 700 ms es largo para movimiento sobrio | Baja |
| M13 | Acordeones y desplegables | Un solo componente de «flecha que gira» | 13 sitios con `transition-transform` + `rotate-*`: `SignatoryAccordionItem.jsx:17`, `CourseSubjectPanel.jsx:178`, `CurriculumYearAccordion.jsx:33`, `GraduationProjectionCard.jsx:97,152`, `FilaAviso.jsx:76`, `PermissionModeLegend.jsx:13`, `PermissionModuleSection.jsx:51`, `BackupHowToRestore.jsx:61`, `StudentCreditNotesSection.jsx:125`, `ReminderSettingsPage.jsx:112`, `TopNavMenu.jsx:132`; unos con `motion-reduce:` y otros sin; algunos giran 90° y otros 180° | Componente `DisclosureChevron` (o clase `.disclosure-chevron`) con duración y giro únicos | Baja |
| M14 | 6 pantallas | Hover con `opacity-*` o `brightness-*` ad hoc | `SeccionAsistencia.jsx:24`, `MiPerfil.jsx:116,125`, `FilaAviso.jsx:96`, `MonthGrid.jsx:105,136`, `TopHeader.jsx:45` (7 líneas) | Un solo estado hover por token (fondo `--row-hover` o similar); sin opacidad | Baja |

### 2.1 Qué del mockup de Gabriel se adopta y qué no (movimiento sobrio)

| Pieza del mockup | Estado en la app | Decisión coherente con «sobrio» |
|---|---|---|
| Aparición escalonada | `Stagger` y `stagger-in` existen | Se conserva, con tope (M6) y unificada en `enter` (M5) |
| Indicador del filtro segmentado | No existe (M10) | Se crea en E36-C |
| Hover suave | `Button` ya lo tiene; lift en 0 | Se mantiene plano (tokens en 0) |
| Barrido de máscara | No existe en la app | No se adopta |
| Líneas que suben | No existe | No se adopta |
| Tarjetas con rebote | Existe un rebote suelto (M2) | Se retira |

## 3. Visual y forma

| ID | Familia / pantalla | Regla violada | Ocurrencias (archivo:línea) | Propuesta de token o componente | Prioridad |
|---|---|---|---|---|---|
| V1 | Todas (cuenta: `text-size-*` 1.531 líneas/358 archivos, escala Tailwind 1.194 líneas/332 archivos) | Tipografía consistente: dos escalas a la vez, medios píxeles y tamaños de 10 a 11 px | Mayores concentraciones de escala por defecto: `features/billing` 473, `enrollment` 125, `evaluation` 123, `identity` 85, compartido 61. De 10 a 11 px (181 usos): `academic-structure` 56, `evaluation` 38, `identity` 31, `workspace` 17, `announcements` 17. Sueltos: `CalendarioConfiguracion.jsx:105` (`text-[7.5px]`), `TabCount.jsx:9` (`text-[0.6875rem]`) | Una sola escala en `index.css`: `caption` 12, `label` 13, `body` 14, `subtitle` 16, `title` 20, `display` 24 (más los 22 y 26 de las cifras de las tarjetas si se confirman), todo como `text-size-*`; retirar los medios píxeles y subir el piso a 12 px. Se migra por familia (E36-F a E36-I) y D agrega la regla que prohíba `text-xs/sm/...` | Alta |
| V2 | Unas 80 pantallas con `<h1>` propio | Un solo componente de título de página | 83 `<h1>` y 70 `PageContainer title=`; clases de los h1: `text-2xl` 41, `text-xl` 28, `text-lg` 12, `text-size-22` 2; `tracking-tight` en 17 y no en el resto; `font-semibold` en `StaffHome.jsx:27` y `DetalleSolicitud.jsx:118`. Por familia: `identity` 21, `academic-structure` 13, `workspace` 9, `announcements` 8, `enrollment` 7, `evaluation` 6, `graduation` 5. Patrón de referencia en `PageContainer.jsx:15` | Todas las pantallas de contenido usan `PageContainer title=`; las de acceso público (login, registro, baja) usan un `AuthCardTitle` con un solo tamaño (`text-lg`). Regla ESLint que prohíba `<h1>` fuera de esos dos componentes | Alta |
| V3 | Todas, sobre todo billing (123), `academic-structure` (58), `identity` (25), `graduation` (21), `enrollment` (21) | Radios solo por tokens y escala coherente | `tokens.css:42-47`: `--r-md`, `--r-lg`, `--r-2xl` y `--r-control` valen 0,5 rem y `--r-xl` 0,75 rem. Usos: `rounded-xl` 220, `rounded-full` 123, `rounded-2xl` 115, `rounded-lg` 80, `rounded-control` 59, `rounded-md` 30, `rounded-card` 26, `rounded-3xl` 5. Literales: `rounded-[10px]` en `PreceptorMesaExamen.jsx:66,73`, `MonthPicker.jsx:71,267`; `rounded-[17px]` en `InstitutionBrand.jsx:11` | Reducir a cuatro nombres: `rounded-control` (0,5 rem), `rounded-card` (0,75 rem), `rounded-dialog` (1 rem) y `rounded-full`. `xl`, `2xl` y `3xl` se migran a `card` o `control` según el elemento (mecánico por familia). `tokens.test.js` agrega la guarda de que `--r-xl` no sea mayor que `--r-2xl` mientras existan | Media |
| V4 | `ui/` y 3 pantallas | Sombras solo por tokens | Escala por defecto de Tailwind: `ThemeToggle.jsx:22` (`shadow-sm`), `AlumnoPagarSeleccion.jsx:19,112` (`hover:shadow-md`, cuando `--sh-card` es `none`), `ui/switch.jsx:20` y `ui/sheet.jsx:61` (`shadow-lg`), `ui/tabs.jsx:39` (`shadow-sm`) | Sólo `shadow-card`, `shadow-popover`, `shadow-overlay`; para la perilla del switch y las pestañas, agregar `--sh-knob` en tokens o quitar la sombra. `literal-values.test.js` agrega `shadow-(sm|md|lg|xl)` | Baja |
| V5 | Todas | Jerarquía: pesos tipográficos sin reglas | `font-semibold` 908, `font-bold` 468, `font-medium` 256, `font-normal` 51, `font-extrabold` 8 (`UpcomingEventsCard.jsx:38` y otros) | Asociar peso a rol en los tokens de V1 (título bold, subtítulo y etiqueta semibold, cuerpo normal); retirar `extrabold` | Baja |
| V6 | Encabezados de tabla y de grupo | Tracking sin escala | `tracking-wide` 116, `tracking-widest` 41, `tracking-wider` 17, `tracking-tight` 19, valores literales `[0.04em]`, `[0.05em]`, `[0.06em]`, `[0.08em]` en 12 líneas: `NavGroups.jsx:4`, `FilaAviso.jsx:141`, `TablaAvisos.jsx:93`, `RosterSegmento.jsx:48`, `AvisoWizard.jsx:142,147`, `CertificateDetails.jsx:12` | Un solo estilo `overline` (12 px, semibold, mayúsculas, un tracking) en la escala de V1 | Baja |
| V7 | `attendance`, `academic-structure` | Un solo componente por función: barras y tortas hechas a mano | Barras con `style={{ width }}`: `DocenteTomarAsistencia.jsx:671,672,676`, `ClassroomAvailabilityPicker.jsx:24` (ya existe `ProgressBar`). Tortas con `conic-gradient`: `SeccionAsistencia.jsx:63`, `DocenteTomarAsistencia.jsx:547` | Barras apiladas y torta pasan a `components/charts` (`StackedBar`, `DonutChart`) con los tonos del conjunto cerrado; sin degradé de decoración | Media |
| V8 | Todas | Foco visible: dos mecanismos | `focus-visible:ring-*` en 45 líneas/41 archivos, `focus-visible:outline-2` en 21/19, `outline-none` en 53/49; la regla base de `index.css` pinta `outline: 2px solid var(--focus-ring)` en botones y enlaces | Un solo mecanismo: el contorno base (`--focus-ring`) para todo control; `ring` sólo dentro de `ui/` donde el componente lo necesita. La guarda `focusIndicatorGuard.test.js` ya existe: extenderla para fallar ante `outline-none` sin foco propio | Media |
| V9 | `GlassCard` (498 usos) | Espaciado: padding de tarjeta sin escala | En `className` de `GlassCard`: `p-4` 166, `p-5` 146, `p-6` 55, `p-3` 33, `p-0` 35, `p-12` 21, `p-8` 10, `p-10` 10 | `GlassCard` con `padding` (`sm`, `md`, `lg`, `none`) y valor por defecto `md`; las pantallas dejan de pasar `p-*` | Media |
| V10 | Todas | Densidad: alturas de control menores al objetivo táctil de 44 px | `h-8` 146 líneas, `h-9` 31, `h-10` 82, contra `h-11`/`min-h-11` 455. **No se cuentan como violación sin confirmar**: muchas son íconos, avatares o celdas no interactivas | A verificar con `ux-advisor` sobre el recorrido por rol; si se confirma, `Button` y `Input` fijan `min-h-11` en móvil y el resto de tamaños se quitan de las pantallas | Media (a verificar) |
| V11 | Billing, evaluation, workspace | Valores literales sueltos que las guardas no atrapan | `border-[1.5px]` en 8 líneas (`PermissionCell.jsx:40`, `HolidayTypePill.jsx:10`, `MonthGrid.jsx:125`, `MonthPicker.jsx:94,267`, `GradeReadCell.jsx:18`, `BackupHowToRestore.jsx:38`, `CertificateVerdict.jsx:18`); `size-[7px]` en `GradeReadCell.jsx:18`; `z-[60]` en `SkipToContent.jsx:5`; `style={{ marginLeft: -8 }}` en `PreceptorMesaExamen.jsx:67` | Token `--border-w-strong` (1,5 px) o pasar a `border-2`; `z` por escala (10/20/30/40/50 ya en uso: `z-50` 14 usos, `z-10` 13) con un token `--z-skip` | Baja |
| V12 | `components/` | Excepción justificada, sin acción: degradés funcionales | `index.css:195-196` (`scroll-fade-y`), `DataTable.jsx:27` y `useScrollEdgeMask.js:36` (máscaras de borde), `index.css:188` (shimmer) | Documentar la excepción en `design-system.md` (máscara y shimmer son de estado, no decoración) | Info |
| V13 | Compartido | Nombre del componente contradice el diseño vigente | `GlassCard.jsx` (498 usos) conserva el nombre de Vidrio Líquido, retirado en 26-D | Renombrar a `Card` en un PR estructural aparte, con codemod | Baja |

## 4. Partición: se arregla una vez o pantalla por pantalla

Recuento por causa (cada fila de las secciones 2 y 3 cuenta una vez). «Una vez» significa que el cambio vive en tokens (E36-B), en un componente base (E36-C) o en una guarda (E36-D) y el resto es mecánico.

| Dónde se arregla | Hallazgos | Cuáles |
|---|---|---|
| Una vez en tokens (E36-B) | 7 | M1, M7 (valores), M12 (valores), V1 (escala), V3 (radios), V4 (sombra de knob), V11 (`--border-w-strong`, `--z-*`) |
| Una vez en componentes base (E36-C) | 9 | M2, M5, M6, M8, M10, M11, M13, V2 (`PageContainer` y `AuthCardTitle`), V9 (`GlassCard padding`) |
| Una vez en CSS global o guardas (E36-B y E36-D) | 3 | M9, V8, V12 |
| Pantalla por pantalla (E36-F a E36-I) | 8 | M3, M4, M14, V5, V6, V7, V10, V13 (renombre con codemod) |
| **Total** | **27** | 14 de movimiento y 13 de visual y forma |


## 5. Orden propuesto de ataque (se suma al de etiquetas)

1. **E36-B tokens**: movimiento (`--dur-*`, `--ease-*`, `animate-enter`), escala tipográfica única, radios y `--border-w-strong`. Va primero porque todo lo demás referencia estos nombres.
2. **E36-C componentes base**: `Stagger` con tope, `animate-enter`, `SegmentedFilter`, `Spinner`, `DisclosureChevron`, `PageContainer`/`AuthCardTitle`, `GlassCard padding` y los ajustes de `ui/` (M7, M8).
3. **E36-D guardas** (en paralelo): prohibir `text-xs/sm/...`, `<h1>` suelto, `transition-all`, `duration-[`/`ease-[` literales y `whileHover` con desplazamiento; y el chequeo de `animation-delay` bajo `prefers-reduced-motion` (M9).
4. **E36-F a E36-I por familia**, en el orden ya decidido (administrativo y director primero): los barridos mecánicos de V1, V2, V3 por familia (las de más ocurrencias: `academic-structure`, `evaluation`, `enrollment`, `identity`), más los puntuales M3, M4, M14, V7.
5. Billing: sólo recibe tokens y componentes; los 473 tamaños y 123 radios se cuentan acá para que la épica sepa lo que queda cuando se encienda.

## 6. Decisiones que hacen falta de Gabriel

1. **Escala tipográfica**: ¿piso de 12 px y seis tamaños (caption, label, body, subtitle, title, display)? Subir lo que hoy es 10 a 11 px cambia densidad en grillas (notas, asistencia, calendario). Recomendación: sí, con excepción explícita para la grilla de notas si no entra.
2. **Duraciones**: ¿120, 200 y 300 ms como escala única? Recomendación: sí; los diálogos y hojas quedan en 200 ms y los menús en 120 ms.
3. **Retirar el hover con escala y el rebote de pantallas públicas (M2, M4)**: es un cambio visible en Login, Registrarse y el interruptor de tema. Recomendación: retirarlo, es coherente con «sobrio».
4. **Radios**: ¿se colapsa a control, card, dialog y full? Cambia el aspecto de 220 elementos con `rounded-xl` (0,75 rem) y 115 con `rounded-2xl` (hoy 0,5 rem). Recomendación: `xl` pasa a `card`, `2xl` a `control`, sin cambio visual donde ya se veían igual.
5. **`GlassCard` a `Card` (V13)**: renombre masivo. Recomendación: hacerlo en un PR aparte, no en la épica.

## 7. Qué NO cubre esta auditoría (a verificar con `ux-advisor` y capturas)

- Jerarquía visual y densidad real por pantalla (no se mide con grep); espaciado entre secciones (`gap-*` 2: 746, 3: 619, 4: 372, 5: 54, 6: 28, 8: 4 es sólo un indicio).
- Targets táctiles y contraste dinámico (V10 queda «a verificar»).
- El comportamiento de M9 con `prefers-reduced-motion` activo (hipótesis de contenido oculto): hay que probarlo en un navegador con la preferencia activada.
- Las capturas antes en claro/oscuro, 1280 y 390 px (criterio de la tarea completa, no de esta parte).
- Las pantallas de billing se listan por conteo, sin auditar a fondo.

## 8. Comandos de verificación

Todos desde `frontend/src`, con `--include='*.jsx' --include='*.js' --include='*.css' --exclude='*.test.*' --exclude-dir=test --exclude-dir=mocks`:

| Dato | Patrón (`grep -rEn`) |
|---|---|
| Transiciones (116 líneas/89 archivos) | `transition` |
| `transition-all` (10/9) | `transition-all` |
| Duraciones y easings (25/13 y 13/8) | `duration-` y `ease-` |
| Rebote | `1\.56` |
| Librería de movimiento | `from 'motion\|AnimatePresence\|motion\.` |
| Hover con desplazamiento o escala | `whileHover\|hover:-translate\|hover:scale` |
| Animaciones propias | `animate-` y `stagger-in\|pop-in\|tab-in\|pulse-ring` |
| Variantes de movimiento reducido (23/18) | `motion-reduce:\|motion-safe:` |
| Tamaños (1.531 y 1.194) | `text-size-` y `\btext-(xs\|sm\|base\|lg\|xl\|2xl\|3xl\|4xl)\b` |
| De 10 a 11 px (181) | `text-size-(10\|10-5\|11)\b` |
| Radios | `\brounded-(sm\|md\|lg\|xl\|2xl\|3xl\|full\|card\|control\|dialog)\b` y `rounded-\[` |
| Sombras de Tailwind por defecto | `shadow-(sm\|md\|lg\|xl\|2xl)\b` |
| Títulos | `<h1` y `<PageContainer[^>]*title=` |
| Foco | `focus-visible:ring-[0-9]`, `focus-visible:outline-2`, `outline-none` |
| Colores literales (0) | `#[0-9a-fA-F]{3,8}\b` |
| `backdrop-*` y `variant="gradient"` (0) | `backdrop-` y `variant="gradient"` |
