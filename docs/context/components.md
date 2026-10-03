# Catálogo de componentes del frontend

Inventario por categoría de lo que existe hoy en `frontend/src/`, relevado del código real. Se usa para elegir componentes al diseñar, al escribir criterios de aceptación de una spec ("Componentes a usar") y al implementar. Reglas de estilo, tokens y motion: [frontend.md](frontend.md).

**Mantenimiento:** todo PR que cree, elimine o cambie la API (props/variantes) de un componente compartido actualiza este archivo. Si un componente que necesitás no figura acá, verificá con `ls src/components src/components/ui` antes de crearlo.

Leyenda: **[ui]** = `components/ui/` (shadcn, agregar con `npx shadcn add`) · **[shared]** = `components/` (cross-feature) · **[domain]** = específico de un dominio, no reusar afuera.

## 1. Layout y estructura de página
| Componente | Uso | API relevante |
|---|---|---|
| `PageContainer` [shared] | Envoltorio de toda pantalla de contenido (ancho máximo, gap); con `title`/`action` dibuja la cabecera (h1 + acción principal a la derecha) | `className`, `title`, `action` |
| `GlassCard` [shared] | Superficie de vidrio; base de casi todo bloque de contenido | `interactive` (clickeable: elevación + borde de marca), `className` |
| `MeshBackground` [shared] | Fondo de pantallas públicas | — |
| `InstitutionBrand` [shared] | Marca de la institución en pantallas públicas | — |
| `layouts/*Layout` + `RoleShell` | Un layout por rol + el público (`PublicLayout`); se agregan a `RUTAS_POR_ROL` | — |

## 2. Navegación
| Componente | Uso | API relevante |
|---|---|---|
| `Sidebar` [shared] | Nav lateral desktop, indicador deslizante | por rol |
| `MobileMenu` [shared] | Drawer mobile con iconos | — |
| `TopHeader` [shared] | Cabecera con título, ítems, campana, tema | `titulo`, `items` |
| `Breadcrumb` [shared] (+ hook `useBreadcrumb`) | Ruta jerárquica en pantallas de detalle | `items` |
| `Tabs` `TabsList` `TabsTrigger` `TabsContent` [ui] | Pestañas de una pantalla | `TabsList variant="segmented"` (nunca `line`) |
| `Stepper` [shared] | Wizards; completados en verde con tilde, pasos visitados clickeables | `pasos`, `pasoActual`, `pasosVisitados`, `onStepClick` |
| `Paginador` [domain: catalogo] | Paginación de listados | `page`, `totalPages`, `onChange` |

## 3. Presentación de datos
| Componente | Uso | API relevante |
|---|---|---|
| `Table` `TableHeader` `TableBody` `TableRow` `TableHead` `TableCell` [ui] | Toda tabla, dentro de un `GlassCard` (sin borde propio). Excepción: grillas con rowSpan/colSpan (asistencia) | `TableRow` admite `onClick` |
| `StatCard` [shared] | KPI/métrica (dentro de `Stagger` en grillas) | `label`, `children` |
| `StatusPill` [shared] | Estado con color semántico | `tone`: `success` `warning` `danger` `accent` `neutral`; `dot` |
| `EstadoSolicitudBadge` [shared] | `StatusPill` ya mapeado para estados de solicitud | `status` |
| `Badge` [ui] | Sin uso hoy; preferir `StatusPill` | variantes `default` `secondary` `destructive` `outline` `ghost` `link` |
| `SortableColumnHeader` [domain: alumno] | Encabezado ordenable (hook `useOrdenamiento`) | — |
| `AnioColumnFilter` `EstadoColumnFilter` [domain: alumno] | Filtro tipo Excel en el encabezado de columna (checkboxes + seleccionar todo) | — |
| `AnioGroupHeaderRow` [domain: alumno] | Fila de agrupación por año | — |
| `AmountBreakdown` [domain: alumno] | Desglose de una cuota: importe base + recargo, beca, promoción o descuento con signo | `baseAmount`, `lines` (`{ label, amount }` con signo) |
| `InvoiceSummary` [domain: alumno] | Resumen de la cuota que se está pagando (concepto, desglose y total) arriba de los pasos de pago | `breakdown` (respuesta de `/me/invoices/{id}/price-breakdown`) |
| `BankAccountDetails` [domain: catalogo] | Titular, CBU y alias de la institución (de `/payment-config`) en el paso de pago del wizard | — |
| `Card` [ui] | Sin uso hoy; usar `GlassCard` | — |

## 4. Formularios y entrada
| Componente | Uso | API relevante |
|---|---|---|
| `Input` [ui] | Texto; también campos de solo lectura | `readOnly className="cursor-default"` |
| `PasswordInput` [ui] | Contraseñas con mostrar/ocultar | — |
| `Textarea` [ui] | Texto largo | — |
| `Select` [ui] | Lista cerrada de opciones | compound (`SelectTrigger`, `SelectContent`, `SelectItem`) |
| `Command` + `Popover` [ui] | Combobox con búsqueda | compound |
| `Buscador` [domain: catalogo] | Campo de búsqueda de listados | `value`, `onChange`, `placeholder` |
| Formularios con validación / wizards | React Hook Form + Zod (patrón: `NuevoAlumno.jsx`) | — |

## 4b. Acciones
| Componente | Uso | API relevante |
|---|---|---|
| `Button` [ui] | Toda acción | `variant`: `default` `outline` `secondary` `ghost` `destructive` `link` `gradient` (CTA primario, 1 por vista); `size`: `default` `xs` `sm` `lg` `icon`; `loading`; `asChild` (sin `loading`) |
| `ThemeToggle` [shared] | Switch claro/oscuro | — |

## 5. Feedback y estados
| Componente | Uso | API relevante |
|---|---|---|
| `toast` / `Toaster` [ui: sonner] | Resultado de acciones (`toast.success/error`); nunca errores de mutación como párrafo inline | montado en `App.jsx` |
| `Skeleton` [shared] | Carga (shimmer) | `className` |
| `EmptyState` [shared] | Listas/tablas vacías | `icon`, `title`, `description`, `action` |
| `NotificationBell` [shared] | Campana con badge de no leídas | — |
| `Dialog` [ui] | Confirmaciones y acciones breves; no para flujos de varios pasos | compound |
| `ModalMotivo` [domain: director] | Pide un motivo al aprobar/rechazar | `open`, `onClose`, `onConfirmar`, `confirmando`, `requerido` |
| `*Modal` [domain: gestion-personas] | `AltaPersonalModal`, `EditarPersonalModal`, `DarDeBajaModal`, `DarDeBajaPersonalModal`, `ReactivarPersonalModal`, `ResetPasswordModal` | — |

## 6. Motion
| Pieza | Uso |
|---|---|
| `Stagger` [shared] | Entrada escalonada de hijos de listas/grillas de cards (`delayStep`) |
| `useSlidingIndicator` (hook) | Fondo deslizante sobre el ítem activo (Sidebar, MobileMenu, Tabs) — reusar, no reimplementar |
| `BannerCarousel` [shared] | Carrusel de banners (autoplay con pausa) |
| Clases de `index.css` | `skeleton-shimmer`, `scroll-fade-y`, keyframes `stagger-in` `tab-in` `pop-in` `pulse-ring` `badge-pulse` |
| Clases de `index.css` (no-motion) | `card-avisos`, `tabla-avisos`, `nota-fila` — composición opaca de la columna fija de la tabla de avisos (`features/avisos`). No se puede expresar con utilidades: apila `--surface` sobre `--bg1` y mete el tinte de fila como una capa aparte vía `--capa-fila`, porque el shorthand `background` destruiría esa composición |

## 7. Componentes de dominio (no reusar fuera de su dominio)
- **alumno:** `TabAsistencia`, `TabMaterias`, `TabMesasDeExamen`, `TabNotas`, `TabPlanDeEstudio`, `SeccionAsistencia`, `InscripcionMaterias`, `MateriaContenidoMock`, `AmountBreakdown`, `InvoiceSummary`.
- **catalogo:** `TabCarreras`, `TabCursos`, `TabMisSolicitudes`, `InscripcionWizard`, `CatalogDetailError` (detalle inexistente/no disponible con vuelta al catálogo), `BankAccountDetails`.
- **director:** `BandejaTramites`.
- **docente:** `CalendarioConfiguracion`.
- **oferta-academica:** `ResumeCycleButton` (retomar un ciclo en construcción o rechazado en el wizard, `?cicloId=`), `RejectionReasonNotice` (motivo de rechazo de un ciclo).
Si un segundo dominio necesita uno de estos, se sube a `components/` (regla de `frontend.md`).

## 8. Contextos y hooks transversales
- `AuthContext` (`useAuth`) y `ThemeContext` (`useTheme` → `{ tema, alternarTema }`).
- Hooks de UI: `useSlidingIndicator`, `useOrdenamiento`, `useBreadcrumb`.
- Hooks de datos (React Query), uno por recurso: en `hooks/` (`useAlumno`, `useBilling`, `useCatalogo`, `useDirector`, `useDocente`, `useDocenteAsistencia`, `useGrillaDeMaterias`, `useMateriasDelPlan`, `useMesasDeExamen`, `useNotifications`, `useSolicitudesAsistencia`) y por feature en `features/*/hooks/`.

## Cómo usar este catálogo en una spec (criterios de aceptación)
La spec técnica de una pantalla incluye una sección **"Componentes a usar"** con, por cada bloque de la pantalla, el componente de este catálogo (o la marca "componente nuevo: X, porque ninguno cubre Y"). Cada criterio de aceptación visual referencia esos componentes ("la lista de pagos es una `Table` dentro de `GlassCard`, con `EmptyState` si no hay filas"). `ux-advisor` valida esa lista antes de aprobar el diseño.
