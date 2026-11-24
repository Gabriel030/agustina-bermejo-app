# Catálogo de componentes del frontend

> Verificado: 2026-10-31, commit `cd1ede80`.

Inventario por categoría de lo que existe hoy en `frontend/src/`, relevado del código real. Se usa para elegir componentes al diseñar, al escribir criterios de aceptación de una spec ("Componentes a usar") y al implementar. Reglas de estilo, tokens y motion: [frontend.md](frontend.md).

**Mantenimiento:** todo PR que cree, elimine o cambie la API (props/variantes) de un componente compartido actualiza este archivo. Si un componente que necesitás no figura acá, verificá con `ls src/components src/components/ui` antes de crearlo.

Leyenda: **[ui]** = `components/ui/` (shadcn, agregar con `npx shadcn add`) · **[shared]** = `components/` (cross-feature) · **[domain]** = específico de un dominio, no reusar afuera.

## 1. Layout y estructura de página
| Componente | Uso | API relevante |
|---|---|---|
| `PageContainer` [shared] | Envoltorio de toda pantalla de contenido (ancho máximo, gap); con `title`/`action` dibuja la cabecera (h1 + acción principal a la derecha) | `className`, `title`, `action` |
| `GlassCard` [shared] | Superficie base (fondo `surface`, borde y sombra de card); base de casi todo bloque de contenido | `interactive` (clickeable: elevación + borde de marca), `className` |
| `StickyActionBar` [shared] | Barra de acciones pegada abajo. En móvil (<768px) va a ancho completo, sin sombra y respeta la safe-area; es una región con `aria-label` (por defecto "Acciones de la página") y anuncia "Guardando…" | `summary`, `hint`, `children` (modo `row` por defecto: botones en fila y el estado en una línea; para pasos de flujo como Volver o Cancelar), `compact="menu"` con `primary` (props de `Button`, `loading`, `loadingLabel`) y `secondary` (`[{ key, label, icon, onSelect, disabled, destructive }]`): en móvil las secundarias van al menú «Más acciones» (para Descartar); en escritorio quedan como botones. `compactSummary`/`compactHint` (textos cortos visuales; el lector de pantalla sigue leyendo los completos), `busy`, `aria-label` |
| `InstitutionBrand` [shared] | Marca de la institución en pantallas públicas | — |
| `layouts/*Layout` + `RoleShell` | Un layout por rol + el público (`PublicLayout`); se agregan a `ROLE_CONFIG` (`routes/roleConfig.js`) | — |

## 2. Navegación
| Componente | Uso | API relevante |
|---|---|---|
| `Sidebar` [shared] | Nav lateral desktop, indicador deslizante | por rol |
| `MobileMenu` [shared] | Drawer mobile con iconos | — |
| `TopHeader` [shared] | Cabecera con título, ítems, campana, tema | `titulo`, `items` |
| `Breadcrumb` [shared] (+ hook `useBreadcrumb`) | Ruta jerárquica en pantallas de detalle | `items` |
| `Tabs` `TabsList` `TabsTrigger` `TabsContent` [ui] | Pestañas de una pantalla | `TabsList variant="segmented"` (nunca `line`) |
| `Stepper` [shared] | Wizards; completados en verde con tilde, pasos visitados clickeables | `pasos`, `pasoActual`, `pasosVisitados`, `onStepClick` |
| `Paginador` [shared] | Paginación de listados (← Anterior / Página N de M / Siguiente →); botones de 44 px en móvil; no dibuja nada con una sola página | `page` (desde 1), `totalPages`, `onChange` |

## 3. Presentación de datos
| Componente | Uso | API relevante |
|---|---|---|
| `Table` `TableHeader` `TableBody` `TableRow` `TableHead` `TableCell` [ui] | Toda tabla, dentro de un `GlassCard` (sin borde propio). Excepción: grillas con rowSpan/colSpan (asistencia) | `TableRow` admite `onClick` |
| `StatCard` [shared] | KPI/métrica (dentro de `Stagger` en grillas) | `label`, `children` |
| `KpiFilterChip` [shared] | Tarjeta KPI clicable que es el único control de estado de una lista: número grande + etiqueta, `aria-pressed`, varias en un `role="group"` con `aria-label`. Los contadores salen del backend (`counts`, calculados con los demás filtros pero sin el estado); volver a tocar la activa la quita. El estado vive en la URL (`?status=`). No combinar con tabs ni con filtro Excel de la misma columna. Ejemplo: `ClosureListPage` | `label`, `count`, `pressed`, `onClick`, `variant` (`card` por defecto; `inline` es la fila compacta sin borde de `ClosureSummaryCards`), `ariaLabel`, `tone` (`danger` pinta el número con el token de peligro, para el contador de fallidos) |
| `ProgressBar` [shared] | barra de un solo valor (avance, porcentaje), `role="progressbar"`; nunca la librería de gráficos | `value` (0-100), `label`, `tone`, `showValue` |
| `components/charts/*` [shared] | gráficos con Recharts 3.8.0 encapsulado: `TrendBarChart`, `StackedBarChart`, `TrendLineChart`, `StackedAreaChart`, `ComposedTrendChart`, `GoalLine`, `ChartFrame` (título, `info` en tooltip, `badge`, carga, error con `onRetry`, no disponible con `unavailableText`, vacío, "Ver como tabla"), `InfoTip`; `recharts` solo se importa dentro de ese directorio (ESLint). **Contrato de `data`:** filas `{label, [key]: valor}` donde `valor` es un número, `null` o un Money `{amount, currency}` (el gráfico lo convierte a número y toma la moneda del propio Money; la tabla lo formatea con su moneda). Porcentajes en puntos (45 = 45 %) | `title`, `subtitle`, `info`, `badge`, `ariaLabel`, `data`, `series` (`{key, label, color?, unit?, type? ('bar'/'line'/'area' en el compuesto), axis? ('right' = eje Y secundario), stack?}`), `tableSeries` (columnas extra solo en la tabla), `unit` (`money`/`percent`/`days`/`count`), `currency`, `goal` (`{value, label, axis?}`), `loading`, `error`, `onRetry`, `unavailableText`, `onCategoryClick(row)` (clic en el gráfico y botón `openLabel` por fila de la tabla, accesible con teclado; sin el prop no hay affordance) |
| `StatusPill` [shared] | Estado con color semántico | `tone`: `success` `warning` `danger` `accent` `neutral`; `dot`; `icon` (componente de lucide, decorativo con `aria-hidden`, antes del texto: el estado no depende solo del color) |
| `EquivalenceChip` [dominio workspace] | Marca «Equivalencia» (ícono + texto, sobre `StatusPill` tono `accent`) para una materia aprobada por equivalencia (`approvalSource === 'EQUIVALENCE'`); hoy en `MapSubjectCard` del mapa curricular del alumno, la reusan el resumen y el detalle (#1335 A2/A3) | `className` |
| `SubjectDetailSheet` [dominio workspace] | Detalle de una materia del mapa curricular en `Sheet` modal (lateral en escritorio, inferior en móvil con `useMediaQuery`, 384 px): «Qué te falta», «Qué desbloquea», «Disponible», equivalencia (institución, origen, fecha, resolución) y «Ver mi cursada» solo con `currentEnrollmentId`. La X de cierre mide 44 px (`SheetClose` propio, `showCloseButton={false}`). Lo monta `CurriculumMap`, que también maneja selección (`?materia=`), resaltado sin `opacity`, franja `role="status"` y Escape en dos pasos (#1335 A3) | `subject`, `relationIndex`, `creditsDefined`, `open`, `onOpenChange`, `onCloseAutoFocus` |
| `EstadoSolicitudBadge` [shared] | `StatusPill` ya mapeado para estados de solicitud | `status` |
| `Badge` [ui] | Sin uso hoy; preferir `StatusPill` | variantes `default` `secondary` `destructive` `outline` `ghost` `link` |
| `DataTable` [shared: `components/table/`] | Tabla con `@tanstack/react-table` v8: orden por encabezado, filtro estilo Excel, filas expandibles, estados cargando/vacío/error y cards bajo 768px. Las columnas son definiciones de TanStack; `meta`: `filterable`, `filterTitle`, `filterLabel(valor)`, `align: 'right'`, `hideInCard`, `cardTitle` (encabezado de la tarjeta, sin etiqueta), `cardBlock` (etiqueta arriba y valor a la izquierda) | `columns`, `data`, `getRowId`, `isLoading`, `isError`, `onRetry`, `errorMessage`, `emptyState`, `renderExpanded(row)`, `expandLabel`, `onRowClick(item)` (clic en la fila fuera de enlaces/botones; el acceso por teclado va con un enlace en la celda), `initialSorting`, `minWidth`, `mobileLayout` (`'cards'` por defecto; `'scroll'` mantiene la tabla con scroll horizontal bajo 768px ; en cards hay barra "Ordenar por" y filtros, y las columnas con header vacío van al pie de la tarjeta) |
| `ColumnFilter` [shared: `components/table/`] | Filtro multi-selección (Popover + `Checkbox`) con los valores presentes en los datos; lo usa `DataTable` cuando la columna tiene `meta.filterable` | `label`, `options` (`{ value, label, count }`), `selected`, `onChange` |
| `RefundsSection` [domain: billing] | Reembolsos de un alumno (pedidos agrupados, partes expandibles, "Marcar como hecho" / "No se pudo"); vive en el Financiero de la ficha del alumno | `studentId` |
| `SortableColumnHeader` [domain: evaluation] | Encabezado ordenable (hook `useOrdenamiento`) | — |
| `AnioColumnFilter` `EstadoColumnFilter` [domain: evaluation] | Filtro tipo Excel en el encabezado de columna (checkboxes + seleccionar todo) | — |
| `ExamCertificatesSection` `ExamCertificatesTable` [domain: evaluation] | Lote de constancias de examen en la mesa (candidatos con checkbox, confirmación, resultado en `Notice`, revocación con `ModalMotivo` solo para el Director) | `mesaId`, `role` |
| `AnioGroupHeaderRow` [domain: alumno] | Fila de agrupación por año | — |
| `AmountBreakdown` [domain: billing] (`features/billing/components/`) | Desglose de una cuota: importe base + recargo, beca, promoción o descuento con signo | `baseAmount`, `lines` (`{ label, amount }` con signo) |
| `InvoiceSummary` [domain: billing] (`features/billing/components/`) | Resumen de la cuota que se está pagando (concepto, desglose y total) arriba de los pasos de pago | `breakdown` (respuesta de `/me/invoices/{id}/price-breakdown`) |
| `BankAccountDetails` [domain: enrollment] | Titular, CBU y alias de la institución (de `/payment-config`) en el paso de pago del wizard | — |
| `Card` [ui] | Sin uso hoy; usar `GlassCard` | — |
| `DataTable` [shared: table] | Tabla con orden, filtro Excel por columna (`meta.filterable`; no se dibuja con una sola opción), filas expandibles y modo tarjeta bajo 768px | `columns`, `data`, `renderExpanded`, `onRowClick`, `emptyState` |
| `ProgressBar` [shared] | Barra de avance con `aria-label`; en Mis descuentos con `showValue={false}` y el texto "N de M" en `label` | `value`, `label`, `showValue`, `tone` |
| `MonthGrid` [academic-structure, `features/academic-structure/components/MonthGrid.jsx`] | Grilla mensual interactiva (`role="grid"`, un solo tab stop, flechas, Home/End, PageUp/PageDown cambian de mes); la celda es un botón con feriado/estado, hoy y selección | `year`, `month`, `entries` (`{ [iso]: { label, typeLabel, chip, cellClass } }`), `todayIso`, `selectedIso`, `onSelect(iso)`, `onNavigate(year, month)` |
| `MonthPicker` [billing, `features/billing/components/MonthPicker.jsx`] | Selector de mes (`YYYY-MM`) con mínimo/máximo; en el otorgamiento de descuentos el mínimo es la próxima cuota | `value`, `onChange`, `minMonth`, `maxMonth` |

## 4. Formularios y entrada
| Componente | Uso | API relevante |
|---|---|---|
| `Input` [ui] | Texto; también campos de solo lectura | `readOnly className="cursor-default"` |
| `PasswordInput` [ui] | Contraseñas con mostrar/ocultar | — |
| `Textarea` [ui] | Texto largo | — |
| `Select` [ui] | Lista cerrada de opciones | compound (`SelectTrigger`, `SelectContent`, `SelectItem`) |
| `RadioGroup` [ui] | Elección exclusiva corta (Porcentaje / Monto fijo); en billing se usa envuelto por `RadioChoice` | compound |
| `Command` + `Popover` [ui] | Combobox con búsqueda | compound |
| `Switch` [ui] | Interruptor (CLI de shadcn, con tokens: color de marca al activarse, `neutral-bg` apagado); lo usa la matriz de notificaciones de Mi perfil y los filtros de recordatorios | `checked`, `onCheckedChange`, `disabled` |
| `Checkbox` [ui] | Casilla (la usa `ColumnFilter`); en el archivo generado por la CLI se corrigió el import de `cn` y el estado `data-[state=checked]` | `checked`, `onCheckedChange` |
| `Buscador` | Campo de búsqueda de listados (catálogo, grilla de permisos) | `value`, `onChange`, `placeholder`, `ariaLabel`, `className` |
| Formularios con validación / wizards | React Hook Form + Zod (patrón: `NuevoAlumno.jsx`) | — |
| `ChangeSectionSheet` + `SectionTargetPicker` [shared] | Sheet de cambio de comisión (personal desde Académico del legajo; alumno desde Trámites). Con `rows` (y sin `row`) arranca eligiendo la materia; `own` consulta `/me/...`; `showReason={false}` oculta el motivo y no lo envía; `confirmNotice` agrega un aviso en la confirmación; `canChooseAnother={false}` oculta «Elegir otra comisión» en errores definitivos. Hook de candidatas: `useChangeSectionCandidates(id, enabled, { own })`; textos y reglas en `lib/enrollmentChanges` | `row`, `rows`, `own`, `showReason`, `confirmNotice`, `onClose`, `onSubmit`, `pending`, `serverError`, `canChooseAnother`, `onChooseAnother` |

## 4b. Acciones
| Componente | Uso | API relevante |
|---|---|---|
| `ChoiceChip` [shared] | Botón de selección tipo chip/segmento (`aria-pressed`): Actuales/Anteriores, Carrera/Curso, días, turnos. Antes se reescribía a mano en cada pantalla | `selected`, `onClick`, `shape` (`pill`/`rounded`), `size` (`xs`/`sm`/`md`/`lg`), `tone` (`soft`/`solid`/`text`), `strong` (peso medio también sin seleccionar), `className` |
| `Button` [ui] | Toda acción | `variant`: `default` `outline` `secondary` `ghost` `destructive` `link` (subrayado al hover) `text` (texto de acento sin subrayado) `subtle` (volver / acción discreta) `ghost-destructive` (ícono que se vuelve rojo) `row` / `row-card` (fila clicable, con `size="row"`) `primary` (CTA primario, 1 por vista); `size`: `default` `xs` `sm` `lg` `icon` `icon-xs` `icon-sm` `inline` (texto en línea, sin alto ni padding); `loading`; `asChild` (sin `loading`) |
| `ThemeToggle` [shared] | Switch claro/oscuro | — |

## 5. Feedback y estados
| Componente | Uso | API relevante |
|---|---|---|
| `toast` / `Toaster` [ui: sonner] | Resultado de acciones (`toast.success/error`); nunca errores de mutación como párrafo inline | montado en `App.jsx` |
| `Skeleton` [shared] | Carga (shimmer) | `className` |
| `LoadErrorState` [shared] | Error de carga con Reintentar y 404 diferenciado; `bare` para usarlo dentro de un bloque | `error`, `onRetry`, `resource`, `backTo`, `backLabel`, `bare` |
| `ModuleDisabled` [domain: pages/] | Pantalla "Este módulo no está habilitado en tu instituto" con enlace a Inicio; la muestra `ModuleGate` en una URL directa a una pantalla de un módulo apagado | `role` |
| `CatalogDetailLayout` / `CatalogEnrollAction` / `CatalogViewerAction` / `CatalogBlock` / `CatalogItemList` [domain: enrollment] | Detalle de carrera y curso del catálogo: cabecera con `<h1>`, CTA con motivo asociado (`aria-describedby`), bloque con carga y error propios, filas de plan/temario (con rótulo opcional); `CatalogViewerAction` reemplaza el CTA por "Vista previa" (roles de staff) o por el estado del alumno (solicitud pendiente, ya inscripto) | ver `features/enrollment/components/` |
| `Notice` [shared] | Aviso en línea (`role="note"` por defecto; `status` o `alert` según urgencia) | `tone` (`neutral` `accent` `warning` `success` `danger`), `title`, `role`, `className` |
| `EmptyState` [shared] | Listas/tablas vacías | `icon`, `title`, `description`, `action` |
| `NotificationBell` [shared] | Campana con badge de no leídas | — |
| `Dialog` [ui] | Confirmaciones y acciones breves; no para flujos de varios pasos | compound |
| `ConfirmActionDialog` [shared] | Confirmación con `alertdialog` (foco en Cancelar, Esc cierra salvo en curso); el cuerpo (resumen, lista) va como `children` | `open`, `onOpenChange`, `title`, `description`, `confirmLabel`, `confirmVariant` (`destructive` por defecto; `primary`), `cancelLabel` (por defecto «Cancelar»; «Volver» cuando el botón de confirmar también dice «Cancelar…»), `pending`, `confirmDisabled`, `buttonClassName`, `onConfirm` |
| `DropdownMenu` `DropdownMenuTrigger` `DropdownMenuContent` `DropdownMenuItem` `DropdownMenuGroup` `DropdownMenuLabel` `DropdownMenuSeparator` [ui] | Menú de acciones por fila (flechas, foco, `disabled` real). Fijar el ancho del contenido con `className="w-72"`: por defecto toma el ancho del trigger | `DropdownMenuItem variant="destructive"` |
| `ModalMotivo` [domain: director] | Pide un motivo al aprobar/rechazar | `open`, `onClose`, `onConfirmar`, `confirmando`, `requerido`, `titulo`, `descripcion`, `placeholder`, `confirmarLabel`, `confirmarVariant` |
| `AuthorizationInboxPage` / `AuthorizationRequestDetailPage` [domain: billing] | Bandeja de autorizaciones (director: `aprobaciones/facturacion`; administrativo: `aprobaciones`) y detalle con barra fija Aprobar/Rechazar/Retirar | — |
| `*Modal` [domain: gestion-personas] | `AltaPersonalModal`, `EditarPersonalModal`, `DarDeBajaModal`, `DarDeBajaPersonalModal`, `ReactivarPersonalModal`, `ResetPasswordModal` | — |

## 6. Motion
| Pieza | Uso |
|---|---|
| `Stagger` [shared] | Entrada escalonada de hijos de listas/grillas de cards (`delayStep`) |
| `useSlidingIndicator` (hook) | Fondo deslizante sobre el ítem activo (Sidebar, MobileMenu, Tabs) — reusar, no reimplementar |
| Clases de `index.css` | `skeleton-shimmer`, `scroll-fade-y`, keyframes `stagger-in` `tab-in` `pop-in` `pulse-ring` `badge-pulse` |
| Clases de `index.css` (no-motion) | `card-avisos`, `tabla-avisos`, `nota-fila` — composición opaca de la columna fija de la tabla de avisos (`features/avisos`). No se puede expresar con utilidades: apila `--surface` sobre `--bg1` y mete el tinte de fila como una capa aparte vía `--capa-fila`, porque el shorthand `background` destruiría esa composición |

## 7. Componentes de dominio (no reusar fuera de su dominio)
- **workspace (mapa curricular del alumno, `features/workspace/components/`, #1335 A1):** `CurriculumMap` (columnas por año con scroll horizontal interno desde 4 años, electivas al final, chips de estado con conteo; prop `audience` = `student` por defecto), `MapSubjectCard` (`Button` con `aria-pressed`, nombre accesible «Materia, estado, nota»), `PlanViewSwitch` (conmutador Mapa/Lista sobre `ToggleGroup`, `?vista=mapa|lista`), `EquivalenceChip`, `CurriculumSummaryBar` (A2: «Tu avance en el plan»; conteos de `summary.counts` de solo lectura (sin botones ni `aria-pressed`; el filtro por estado vive en los chips de `CurriculumMap`), barra de créditos solo con `requiredCredits > 0`, «N por equivalencia»), `CurriculumYearAccordion` (A2: `<h3><button aria-expanded aria-controls>` solo bajo 768 px, `<h3>` plano en escritorio; abierto por defecto `getDefaultOpenYear`). `GraduationProjectionCard` acepta `collapsible` (cerrada por defecto) en la vista Mapa. La vista Lista es la `TabPlanDeEstudio` de siempre; ambos modos comparten `?estado=`. En `AlumnoMisCarreras` la `StatCard` «Avance del plan» se oculta solo en la pestaña Plan de estudio con `?vista=mapa` (el resumen ya muestra el avance) y `?materia=` se limpia al cambiar de pestaña o de carrera. Tests a11y de la pestaña: `TabPlanDeEstudio.a11y.test.jsx` (#1335 A4).
- **alumno:** `TabAsistencia`, `TabMaterias`, `TabMesasDeExamen`, `TabNotas`, `TabPlanDeEstudio`, `SeccionAsistencia`, `InscripcionMaterias`, `MateriaContenidoMock`.
- **catalogo:** `TabCarreras`, `TabCursos`, `TabMisSolicitudes`, `InscripcionWizard`, `CatalogDetailError` (detalle inexistente/no disponible con vuelta al catálogo).
- **enrollment:** `BankAccountDetails` (`features/enrollment/components/`, paso de pago del wizard de inscripción).
- **director:** `BandejaTramites`.
- **billing (descuentos):** `features/billing/discounts/` — `DiscountsPage` (catálogo, promociones, otorgados), `DiscountDefinitionFormPage`, `DiscountDefinitionDetailPage`, `GrantDiscountPage`, `StudentDiscountsSection` (Financiero del legajo), `MyDiscountsTab` (Pagos del alumno), `ViolationList`, `DiscountStatusPill` / `PromotionStatusPill` (constantes sobre `StatusPill`).
- **docente:** `CalendarioConfiguracion`.
- **academic-structure (feriados, `/administrativo/feriados`):** `HolidayCalendar` (mes con ‹ ›, sobre `MonthGrid`), `HolidayList` (lista del año por mes con filas clicables), `HolidayPanelContent` (alta/edición; panel lateral en desktop, `Sheet` inferior bajo 1024 px), `DeleteHolidayDialog` (único Dialog), `HolidayTypePill` (letra + tono + trazo por tipo).
- **billing/payment-reversals:** `StudentPaymentsCard` (card "Pagos del alumno" de la ficha de Facturación del staff), `PaymentActionsMenu`, `ReversalDialog` (contracargo, reversión y revocación con vista previa del impacto), `ReversalImpact`, `PendingRefundsNotice`.
- **billing (`features/billing/credit-notes/`):** `ReasonCards` (dos o más tarjetas seleccionables como `radiogroup`, con flechas), `AmountDiffTable` (tabla Hoy / Nuevo / Diferencia con filas sin cambios; en móvil se apila en pares etiqueta/valor), `CancelInvoiceDialog` (cancelar cuota / anular recargo con vista previa de efectos), `InvoiceActions` (acciones de la cuota; entra a `InvoiceDetail` por su prop `actions`, que no conoce roles), `CreditNoteLinesForm`, `CreateCreditNotePage` (2 pasos con `Stepper`), `StudentCreditNotesSection`, `PendingAuthorizationBanner`, `CreditBalanceCard` (alumno) y `MyCreditNotesPage`.
- **billing (pago por transferencia del alumno, `features/billing/components/` y `components/billing/`):** `BankTransferReceiptForm` (paso 2 de `AlumnoPagarTransferencia`: cuotas en orden, dropzone, número de operación, monto, CBU o alias, titular precargado, banco opcional, "Qué va a pasar" y barra fija con el único CTA; envía a `POST /me/bank-transfer-receipts` con `Idempotency-Key`), `InvoiceCoverageList` (cuotas por prefijo con cobertura por fila y filas informativas en revisión) y `ReceiptDropzone` (valida tipo y 5 MB, con "Quitar"). La lógica pura vive en `lib/bankTransferReceipt.js`. El paso Pago del wizard de curso todavía no lo usa (V2-04b1c-2).
- **billing (`features/billing/reminders/`):** `ReminderSettingsPage` (Recordatorios de pago, 2 pasos con `Stepper`), `ReminderTimeline` (avisos por hito) con `NoticeEditor` inline, `SettingsFields` (ajustes generales y `SendingOverrides` con herencia), `ExceptionsTable`, `VersionHistory`, `ReminderConfirmStep` (tabla Hoy / Nuevo / Diferencia, vigencia, motivo, impacto), `ContactListTab` (pestaña Para contactar de Facturación, con `mobileLayout="scroll"`) y `SimulatedMailsPage` (solo local y demo). En identity: `NotificationMatrix` (Mi perfil › Notificaciones, candados según `mandatoryChannels`).
- **graduation (`features/graduation/debt-exceptions/`):** `EmitDebtException` (Administración › Emitir por excepción, `/administrativo|director/constancias-por-excepcion`; Subdirector excluido con `excludeSubdirector` en la ruta), `StudentPicker` (buscador por apellido o DNI con resultados como filas de 44 px), `IssuedDebtException` (estado Emitida con Descargar para entregar / Emitir otra).
- **notifications (invitaciones por oleadas, `features/notifications/invitation-waves/`, `/director|administrativo/invitaciones`; sin Subdirector):** `InvitationWaveListPage`, `InvitationWaveCreatePage` (paso 1, formulario con React Hook Form + Zod y topes de `GET /invitation-waves/settings`), `InvitationWavePreviewPage` (paso 2, `/:waveId/vista-previa`, Elegibles / Excluidos con paginación del servidor), `InvitationWaveDetailPage` (6 totales como filtro `?status=`, avance, destinatarios, acciones). Piezas: `WaveStatusPill` / `RecipientStatusPill` (sobre `StatusPill` con ícono), `WaveProgress` (sobre `ProgressBar`), `RecipientsTable` (sobre `DataTable`; muestra «Persona N° …» y un aviso cuando el servidor no manda nombre ni mail), `WaveActionButtons` (escritorio) y `WaveMobileBar` (móvil, sobre `StickyActionBar`), `WaveActionDialog` (confirmaciones con resumen), `WaveForm`, `NoAccessState`. Los permisos `read/manage/send` salen de `useInvitationWaveAccess()`: arranca con todos y un 403 apaga el que corresponde (el contrato no expone los permisos efectivos del usuario).
- **graduation (`features/graduation/certificates/`, Plantillas de certificados, `/director|administrativo/plantilla-de-certificados`):** `CertificateTemplateEditor` (6 pestañas: egreso carrera y curso sobre `/certificate-templates/{kind}`; alumno regular, inscripción a examen, examen rendido y materias aprobadas sobre `/certificate-templates/documents/{documentType}`; bajo `md` un select agrupado reemplaza las pestañas), `TemplatePanel` (una pestaña: textos, logo, firmantes, `StickyActionBar` con Descartar / Guardar borrador / Publicar y el motivo por el que Publicar está deshabilitado), `TemplatePreviewPanel` (xl) / `TemplatePreviewDialog` (menor a xl) con el PDF de `/preview` y el switch «Ver borrador», `TemplateHistorySheet` (versiones inmutables, «Ver» en solo lectura y «Usar como base» con confirmación si hay borrador). Edita solo el Director; el resto lee. `ConfirmActionDialog` acepta `onCloseAutoFocus` para devolver el foco al botón que lo abrió.
- **oferta-academica:** `ResumeCycleButton` (retomar un ciclo en construcción o rechazado en el wizard, `?cicloId=`), `RejectionReasonNotice` (motivo de rechazo de un ciclo).
Si un segundo dominio necesita uno de estos, se sube a `components/` (regla de `frontend.md`).

## 8. Contextos y hooks transversales
- `AuthContext` (`useAuth`) y `ThemeContext` (`useTheme` → `{ tema, alternarTema }`).
- Hooks de UI: `useSlidingIndicator`, `useOrdenamiento`, `useBreadcrumb`, `useMediaQuery(query)`.
- Hooks de datos (React Query), uno por recurso: en `hooks/` (`useAlumno`, `useBilling`, `useCatalogo`, `useDirector`, `useDocente`, `useDocenteAsistencia`, `useMateriasDelPlan`, `useMesasDeExamen`, `useNotifications`, `useSolicitudesAsistencia`) y por feature en `features/*/hooks/`.

## Cómo usar este catálogo en una spec (criterios de aceptación)
La spec técnica de una pantalla incluye una sección **"Componentes a usar"** con, por cada bloque de la pantalla, el componente de este catálogo (o la marca "componente nuevo: X, porque ninguno cubre Y"). Cada criterio de aceptación visual referencia esos componentes ("la lista de pagos es una `Table` dentro de `GlassCard`, con `EmptyState` si no hay filas"). `ux-advisor` valida esa lista antes de aprobar el diseño.

## Tamaños de fuente (tokens)
Los tamaños tipográficos puntuales son tokens de `index.css` (`--text-size-13` → `text-size-13`, `text-size-12-5`, etc.: 10, 10.5, 11, 11.5, 12, 12.5, 13, 13.5, 14, 14.5, 15, 16, 17, 22, 26). No se escribe `text-[13px]`: lo impide `theme/literal-values.test.js` (regla "arbitrary pixel font size"). Un token nuevo se declara en `index.css` y en `FONT_SIZE_TOKENS` de `lib/utils.js` (así `cn` no lo confunde con un color; lo verifica `lib/fontSizeTokens.test.js`).

## Reporte de usos (`npm run component-usage`, 12-nov-2026)
Antes → después de REUSE-C: `ChoiceChip` 0 → 5 usos en 3 archivos (`PreceptorDetalleCarrera`, `PreceptorNuevoCiclo`, `PreceptorCalendario`), `KpiFilterChip` 1 → 2 (`ClosureListPage`, `ClosureSummaryCards`), clases `text-[Npx]` 922 → 2 (excepciones declaradas), `<button>` crudos 66 → 61 (el resto son tarjetas seleccionables y filas clicables que `Button` no cubre).
Pendiente de componente (3+ usos con variaciones visuales que no se pueden unificar sin cambiar aspecto): tarjeta seleccionable (`InscripcionMaterias`, `InscripcionWizard` cohortes, `BillingModePanel`, `OptionCard`, `HolidayPanelContent`) y pill informativo `rounded-full bg-accent-soft` (`DocenteComisionDetalle`, `DirectorRevisarConfiguracion`, `PreceptorDetalleCurso`, `MiPerfil`).
