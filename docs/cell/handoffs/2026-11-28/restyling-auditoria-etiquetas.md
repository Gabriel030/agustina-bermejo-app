# Auditoría de color en etiquetas, pastillas y estados (E36-A, parte 1)

Base: `origin/develop` (eca42f2f), solo lectura. Alcance: `frontend/src` sin tests, billing incluido (se cuenta, pero está fuera de alcance del restyling). Paleta decidida: A «Petróleo y coral». Método: `git archive` de `frontend/src` a un directorio temporal y búsquedas con `rg` y scripts de conteo. Los números son de líneas o usos en código fuente, no de pantallas renderizadas.

## 0. Hallazgo principal (cambia el planteo)

El problema NO es color crudo: es **uso inconsistente de un conjunto de tokens que ya existe**. No hay un solo `bg-red-*`/`text-green-*`, ni hex, ni `oklch(`/`rgb(` fuera de `theme/` y `components/ui/` (las guardas `literal-values.test.js` ya lo impiden). `StatusPill` ya es un conjunto cerrado de 5 tonos (neutral, acento, éxito, aviso, peligro) con ícono por defecto en aviso y peligro, y texto siempre. El desorden está en: (a) qué tono se asigna a cada estado (mismo estado, distinto tono según la pantalla), (b) cajas de aviso y pastillas hechas a mano que no pasan por `Notice`/`StatusPill`, (c) varios nombres y variantes de token para el mismo color, (d) unos pocos usos rotos.

## 1. Números globales

| Medida | Valor |
|---|---|
| Archivos con algún color semántico (token de estado/acento o tono) | 509 de 1130 archivos de `frontend/src` |
| Usos de `<StatusPill>` | 330 en 203 archivos |
| Entradas `tone: '…'` en mapeos estado→tono | 770 en 217 archivos (neutral 236, aviso 176, éxito 142, peligro 133, acento 77) |
| Tonos válidos | 5 (los de `StatusPill`/`Notice`) |
| Vocabulario de tono ajeno a los 5 (no rompe, pero es otro idioma) | 3 usos: `tone="info"` y `tone="ok"` en un `Notice` LOCAL de `ListaDeEsperaDetalle` (reimplementa el compartido), y `tone="info"` en `EmailChangeResultCard` (su propio mapa `success/danger/info/warning`); más `ChoiceChip` usa `soft/solid/text` para otra cosa |
| Clases de color con token distintas (sin escala de tinta `ink*`) | 58 nombres base; 104 contando variantes de opacidad (185 usos con `/NN`, 48 variantes distintas) |
| Variables CSS de color distintas detrás | 14 (acento, acento suave, éxito x3, aviso x3, peligro x3, neutro x2) + 4 alias `pill-*` + `on-accent` |
| Clases Tailwind de paleta cruda (`red-500`…) | **0** |
| Hex / `oklch()` / `rgb()` fuera de `theme/` y `components/ui/` | **0** |
| `text-white` / `bg-black` en código de producción | **0** (solo en tests de guarda) |
| `style={{…}}` con color | 3 archivos, los 3 con `var(--token)` (`CartesianChart`, `DocenteTomarAsistencia`, `SeccionAsistencia`) |
| Fuera de tokens de verdad (rotos) | 2 líneas con `bg-success-soft` (no existe el token; no pinta) en `PreceptorNuevoCiclo:532,539`; 1 `bg-warning/15` y `bg-brand/10`/`bg-brand/20` con opacidad ad hoc; 3 `text-(--accent)` por variable (sí es token, pero salteando el alias) |
| **Crudos totales (fuera de tokens)** | **0 de color real + 2 líneas rotas = 2** |

### Alias y variantes que hacen que un mismo color se escriba de muchas formas

- El acento tiene 3 nombres: `brand`, `primary`, `accent` (este último apunta a `--accent-soft`, no a `--accent`: `bg-accent` x12, `border-accent` x8, `text-accent` x2 pintan el tono suave; `ChoiceChip tone="solid"` usa `bg-accent` con `text-on-accent` blanco sobre fondo pálido, riesgo de contraste).
- El peligro tiene 2 nombres (`danger`, `destructive`) y 4 tintas de texto distintas: `text-danger` (296 usos), `text-danger-ink` (26), `text-destructive` (5), `text-pill-danger` (5). Igual en aviso (`text-warning` 70, `text-warning-ink` 34, `text-pill-warning` 6) y éxito (`text-success` 60, `text-success-ink` 17, `text-pill-success` 5).
- El neutro tiene 3 nombres (`neutral-bg`, `muted`, `secondary`) y una familia de opacidades (`neutral-bg/30…/70`: 8 variantes).
- Bordes de aviso/peligro con 4 opacidades distintas (`/30`, `/40`, `/45`, `/50`) y sin borde en otros lugares.
- Cajas de aviso: 71 `<Notice>` en 43 archivos contra **123 líneas de cajas hechas a mano** (`bg-success|warning|danger-bg` + `rounded-*`) en 104 archivos, con radios `lg`/`xl`/`2xl`, con y sin borde, y texto `text-ink`, `text-danger` o `text-danger-ink` (en cajas de peligro: 43 `text-danger`, 12 `text-ink`, 11 `text-danger-ink`).
- Pastillas hechas a mano (`rounded-full bg-… px-… text-…`) fuera de `StatusPill`: unas 25 (ver tabla 2.4). `Badge` de shadcn: 0 usos reales (solo `DesignCatalog`). `ChoiceChip` y `KpiFilterChip` tienen su propia lógica de color.
- Mapas locales estado→clase de color fuera de los componentes base: 14 archivos (`EmailChangeResultCard`, `HomeTaskCard`, `CertificateVerdict`, `RefundBlockAlert`, `AlumnoPagarTransferencia` `TONE_CLASS`, `RunCounterCards`, `FirstDirectorStateCard`, `ListaDeEsperaDetalle` con su propio `Notice`, etc.).
- Gráficos: `SERIES_COLORS` repite `var(--accent)` dos veces (series 1 y 2 iguales) y `financialPanels` repite acento en dos tramos de antigüedad de deuda distintos (1-30 y más de 90 días).

## 2.1 Tabla de componentes base de estado (qué definen)

| archivo | componente | qué representa | tono/color | ¿token? |
|---|---|---|---|---|
| components/StatusPill.jsx | StatusPill | cualquier estado | 5 tonos: neutral, acento, éxito, aviso, peligro; ícono automático solo en aviso (AlertTriangle) y peligro (XCircle); `dot` opcional | token (`bg-*-bg` + `text-pill-*`) |
| components/Notice.jsx | Notice | avisos en línea | 5 tonos, texto siempre `text-ink`, sin ícono | token |
| components/ProgressBar.jsx | ProgressBar | avance | acento, éxito, aviso, peligro (relleno liso, sin texto del estado) | token |
| components/KpiFilterChip.jsx | KpiFilterChip | contador filtro | solo `danger` pinta el número | token |
| components/ChoiceChip.jsx | ChoiceChip | selección | soft/solid/text sobre acento suave | token (con el problema de `bg-accent`) |
| components/TabCount.jsx | TabCount | contador de pestaña | acento suave | token, hecho a mano |
| components/ui/badge.jsx | Badge | sin uso real | default, secondary, destructive, outline | token, huérfano |
| features/enrollment/components/EstadoSolicitudBadge.jsx | EstadoSolicitudBadge | estado de solicitud | 13 estados con los 5 tonos | token |
| features/academic-structure/lib/holidays.js + HolidayTypePill.jsx | tipo de feriado | nacional, provincial, municipal, no laborable, puente, otro | acento suave x3 (con bordes distintos), neutro x2, trazo punteado; se distingue por letra, no por color | token, mapeo propio de clases |
| features/workspace/components/calendar/calendarEventMeta.js | evento de calendario | clase, feriado, mesa, cancelada | texto `brand`/`warning-ink`/`success-ink`/`danger-ink` más figura (●◆■) | token, mapeo propio |
| features/billing/discounts/StatusPills.jsx, WaveStatusPill, ExceptionStatusPill, EquivalenceStatusPill, PlanStatusPill, WaitlistVerdictPill | pastillas de dominio | estados de cada dominio | sobre `StatusPill` | token |
| components/offline/StaleStamp.jsx | datos desactualizados | aviso y neutro | pastillas a mano | token |

## 2.2 Distribución de tonos en los 770 mapeos (resumen por significado)

(La lista completa por archivo está en el anexo A al final.)

## 3. Agrupación por significado y mapeo propuesto a los 5 tonos

Regla propuesta (a validar por Gabriel): **neutro** = sin acción, sin urgencia, estado de reposo o terminal sin juicio; **acento** = informativo, en marcha, disponible para actuar, seleccionado; **éxito** = resultado positivo cerrado; **aviso** = requiere atención o decisión pronto, no es error; **peligro** = error, rechazo, bloqueo, vencimiento con consecuencia, pérdida.

| Significado | Estados típicos hoy | Tono propuesto | Cuántos mapeos hoy lo contradicen |
|---|---|---|---|
| Pendiente de acción de alguien | pendiente, en revisión, en lista de espera, comprobante en revisión, justificación pendiente | aviso | pendiente: 11 aviso, 5 neutro, 1 acento; en revisión: 8 aviso, 1 neutro; lista de espera: 6 aviso, 1 neutro; justificación pendiente: 2 acento, 1 aviso |
| En curso / en marcha | en curso, cursando, inscripto, cursada regular, programada, en cola, enviado (en proceso) | acento | en curso: 6 acento, 1 éxito, 1 aviso; cursando: 2 acento, 1 éxito (3 usos más de éxito en `courseEnrollmentStatus`); inscripto: 3 acento, 1 éxito; cursada regular: 2 acento, 2 éxito |
| Positivo terminado | activo, aprobado, pagado, cerrado bien, publicada, enviado (entregado), vigente, presente | éxito | activo: 6 éxito, 1 neutro; enviado: 3 éxito, 1 acento; cerrado/cerrada: 2 éxito, 6 neutro |
| Borrador / no iniciado / archivado | borrador, programado (futuro), cerrado administrativo, cancelado por el usuario, dado de baja, vencido por plazo | neutro | borrador: 5 neutro, 1 aviso; dado de baja: 3 neutro, 1 peligro |
| Rechazo / error / bloqueo con consecuencia | rechazado, ausente, cancelada (por institución), suspendido, fallido, libre, desaprobado, vencida (deuda o certificado) | peligro | rechazado: 8 peligro, 1 neutro; ausente: 3 peligro, 2 neutro; vencida: 7 neutro, 4 peligro; suspendido: 1 peligro, 1 aviso |
| Calendario | feriado, clase, mesa de examen | feriado: aviso (hoy 1 aviso y 1 neutro); clase: acento; mesa: acento/éxito si inscripto | feriado: dos pantallas, dos tonos |

### 3.1 Conflictos: mismo estado, tonos distintos (fuente: análisis por etiqueta, 33 etiquetas en conflicto)

| Estado (etiqueta) | Tonos usados | Dónde |
|---|---|---|
| pendiente | aviso (11), neutro (5), acento (1) | aviso: `attendanceRequests`, `AdministrativoSubjects`, `importedReview`; neutro: `closureSheet`, `invitationWaveLabels`, `AdministrativoCargarComprobante`; acento: `examBoardGrades` |
| vencida | neutro (7), peligro (4) | neutro: `staffRegularCertificates`, `regularCertificateState`, `certificateExceptions`; peligro: `EstadoSolicitudBadge`, `pricing`, `AdministrativoCargarComprobante` |
| en revisión | aviso (8), neutro (1) | neutro en `lib/billing.js` |
| rechazado | peligro (8), neutro (1) | neutro en `refundRequests` |
| en curso | acento (6), éxito (1), aviso (1) | éxito en `CourseSubjectPanel`; aviso en `refundRequests` |
| cursando | acento (2), éxito (3 en `courseEnrollmentStatus` + 1) | `academicRecord`/`gradeGrid` acento; `courseEnrollmentStatus` éxito |
| cursada regular | acento (2), éxito (2) | acento: `academicRecord`, `closureSheet`; éxito: `gradeGrid`, `AlumnoMateriaDetalle` |
| inscripto | acento (3), éxito (1) | éxito en `EstadoSolicitudBadge` |
| activo | éxito (6), neutro (1) | neutro en `certificateSignatories` |
| borrador | neutro (5), aviso (1) | aviso en `closureSheet` |
| cerrado/cerrada | éxito (cierre de comisión) vs neutro (oferta, períodos, descuentos) | mismo término, dos significados: «cierre terminado bien» y «cerrado administrativo» |
| ausente | peligro (3), neutro (2) | neutro en `examCertificates`, `examBoardGrades` (aquí es «no se presentó», no falta con consecuencia) |
| dado de baja | neutro (3), peligro (1) | peligro en `LegajoAlumno` |
| suspendido | peligro (`AdministrativoFacturacion`), aviso (`AlumnoPagos`) | misma entidad (pagos), dos tonos en pantallas hermanas |
| justificada | acento, éxito, neutro | `attendanceStatuses`, `absenceRows`, `courseSubjectDetail`: tres pantallas de asistencia, tres tonos |
| justificación pendiente | acento (2), aviso (1) | |
| enviado | éxito (3), acento (1) | acento en `invitationWaveLabels` |
| programada/programado | neutro, acento, aviso | `calendarEventMeta` neutro; `VersionHistory`/`PriceDetailPage` acento; `avisos.js` aviso |
| feriado | aviso (`calendarEventMeta`), neutro (`UpcomingEventsCard`) | |
| publicada | éxito y aviso en la misma pantalla | `GradeCorrectionSheet:16` (publicada vs no publicada) |
| vencido | neutro (2), éxito (1) | `avisos.js:35` usa éxito y neutro para «vencido» según el caso |
| cancelada/cancelado | peligro (4), neutro (2) | |
| clase cancelada | peligro y neutro en el mismo mapa (`calendarEventMeta:41`) | |
| disponible | acento (2), neutro (1) | |
| en lista de espera | aviso (6), neutro (1) | |
| comprobante en revisión | acento (`FinancieroTab`), aviso (`AlumnoPagarTransferencia`) | |
| en cola | neutro, acento | |

### 3.2 Mismo color, significados distintos

- **Aviso (ámbar)** significa «acción pendiente» (pendiente, en revisión), «espera» (lista de espera), «calendario» (feriado), «borrador» (`closureSheet`), «programado» (`avisos`), «publicada sin ...» y «suspendido» (`AlumnoPagos`). Propuesta: reservar aviso para «requiere acción o atención pronto»; feriado, borrador y programado salen a neutro/acento.
- **Acento (petróleo)** significa «en curso» (cursando, inscripto), «disponible», «programada», «enviado», «justificada», y también el estado «seleccionado» (`ChoiceChip`, filas, nav, contadores). Propuesta: acento = en marcha o seleccionado; «justificada» y «enviado» pasan a éxito.
- **Neutro** hace de «sin dato», «terminal», «vencido» y «ausente»: es el cajón de sastre (236 mapeos, el tono más usado). Propuesta: neutro solo para reposo o terminal sin juicio.
- **Peligro** se usa para error, rechazo y también para acciones destructivas (`text-danger` en botones: baja, cancelar), contadores que pasan el límite (9 casos), énfasis numérico (`FinancialDashboard`, `StaffSectionRoster`): mezcla «estado malo» con «acción peligrosa» con «número alto».
- **Éxito** significa «cursando» en un lado y «cierre terminado» en otro.

## 2.3 Mapeo de los 5 tonos cerrados a tokens (propuesta, sin tocar paleta)

| Tono | Token de fondo | Token de texto de pastilla | Ícono obligatorio |
|---|---|---|---|
| neutro | `--neutral-bg` | `--pill-neutral-fg` | ninguno (el texto alcanza) |
| acento | `--accent-soft` | `--accent` | ninguno o `Info`/`Clock` |
| éxito | `--success-bg` | `--pill-success-fg` | `CheckCircle2` sugerido |
| aviso | `--warning-bg` | `--pill-warning-fg` | `AlertTriangle` (ya automático) |
| peligro | `--danger-bg` | `--pill-danger-fg` | `XCircle` (ya automático) |

Consolidación de alias a decidir en E36-B/C: dejar `brand`/`primary` como un único nombre, que `accent` deje de significar «suave», unificar `danger`/`destructive`, y reducir las 3 tintas de texto por tono a una (`*-ink` para texto sobre fondo claro, `pill-*-fg` solo en `StatusPill`).

## 2.4 Pastillas, etiquetas y cajas hechas a mano (fuera de `StatusPill`/`Notice`)

| archivo | componente | qué representa | tono/color | ¿token? |
|---|---|---|---|---|
| features/announcements/pages/DocenteComisionDetalle.jsx:102 | pastilla de docente/inst. | etiqueta informativa | acento suave + `text-primary` | token, a mano |
| features/evaluation/pages/DirectorRevisarConfiguracion.jsx:121 | ídem | etiqueta | acento suave + `text-primary` | token, a mano |
| features/academic-structure/pages/PreceptorDetalleCarrera.jsx:70 | ídem | etiqueta | acento suave + `text-brand` | token, a mano |
| features/academic-structure/pages/PreceptorDetalleCurso.jsx:114 | ídem | etiqueta | acento suave + `text-brand` | token, a mano |
| features/identity/pages/MiPerfil.jsx:337 | rol/condición | etiqueta | acento suave + `text-brand` | token, a mano |
| features/academic-structure/pages/PreceptorNuevoCiclo.jsx:532 | cantidad de secciones | éxito | `bg-success-soft` (no existe) | **roto** |
| components/offline/StaleStamp.jsx:15,22 | «desactualizado» / neutro | aviso y neutro | `warning-bg`/`neutral-bg` | token, a mano |
| features/billing/components/BankTransferReceiptForm.jsx:183 | estado | neutro | `neutral-bg` | token, a mano |
| features/enrollment/components/InscripcionWizard.jsx:615 | estado | neutro | `neutral-bg` | token, a mano |
| features/identity/pages/ReviewEmailChangePage.jsx:189 | estado | neutro | `neutral-bg` | token, a mano |
| components/TabCount.jsx | contador | acento suave | `accent-soft`+`text-brand` | token |
| components/TopNavGridPanel.jsx:72 y features/enrollment/components/TopNavCatalogPanel.jsx:192 | contador/etiqueta | acento suave | `text-(--accent)` por variable | token por variable |
| features/enrollment/components/CatalogItemList.jsx:11 | etiqueta de oferta | `bg-brand/10` | opacidad ad hoc | token + opacidad |
| components/PreceptorMultiSelect.jsx:181 | chip de preceptor | acento suave | token |
| features/workspace/components/calendar/CalendarFilters.jsx:28,31 | chips «Después» | neutro con opacidad | token |
| features/billing/dashboard/FinancialDashboardPage.jsx:267, features/audit/components/AuditFilters.jsx:30 | contador de filtros activos | `bg-primary` | token |
| features/academic-structure/lib/holidays.js + HolidayTypePill | tipo de feriado | 6 variantes con bordes y trazos | token, mapa propio |
| calendarEventMeta.js | glifo de evento | texto de 4 tonos + figura | token |
| ~10 círculos con ícono (`rounded-full bg-success-bg|danger-bg`) en `VerificacionEmail`, `ConfirmarResponsable`, `BajaComunicaciones`, `StaffHome`, `InscripcionWizard:337`, `AdministrativoSubjectWizard` | iconos de resultado | éxito o peligro | token |
| ~123 líneas de cajas de aviso a mano en 104 archivos | aviso en línea | éxito, aviso, peligro | token; radios y bordes inconsistentes | token |

## 4. Las 15 pantallas/componentes más cargados de color

Puntaje = usos de clases de color semántico + entradas de tono + pastillas/avisos en el archivo (billing incluido; los `lib/` se listan aparte porque alimentan a muchas pantallas).

| # | Archivo | Puntaje | Clases color | Tonos en mapeo | Pastillas/avisos | Nota |
|---|---|---|---|---|---|---|
| 1 | features/academic-structure/pages/AdministrativoSubjects.jsx | 32 | 13 | 14 | 5 | 9 neutro, 3 éxito; borrador/pendiente chocan con otras pantallas |
| 2 | features/attendance/pages/DocenteTomarAsistencia.jsx | 30 | 17 | 8 | 5 | dona y barra solo con color; 6 familias de color; piloto del restyling |
| 3 | features/billing/pages/AdministrativoFacturacion.jsx | 27 | 14 | 8 | 5 | «suspendido» peligro aquí y aviso en `AlumnoPagos` |
| 4 | features/attendance/lib/attendanceStatuses.js | 27 | 21 | 6 | 0 | «justificada» acento; en otros mapas éxito o neutro |
| 5 | features/enrollment/pages/SolicitudDeIngresoDetalle.jsx | 25 | 11 | 6 | 8 | piloto del restyling |
| 6 | features/enrollment/components/InscripcionWizard.jsx | 24 | 24 | 0 | 0 | 7 familias; pastillas neutras a mano |
| 7 | features/enrollment/pages/DetalleSolicitud.jsx | 24 | 21 | 1 | 2 | 8 cajas con fondo de estado |
| 8 | features/attendance/components/SeccionAsistencia.jsx | 23 | 23 | 0 | 0 | torta verde/roja solo con color |
| 9 | features/identity/pages/MiPerfil.jsx | 22 | 20 | 1 | 1 | 8 familias; pastilla de acento a mano |
| 10 | features/academic-structure/lib/holidays.js | 20 | 20 | 0 | 0 | mapa propio de clases por tipo de feriado |
| 11 | features/billing/annual-upfront/AnnualUpfrontCard.jsx | 20 | 7 | 6 | 7 | |
| 12 | features/billing/pages/AlumnoPagarTransferencia.jsx | 20 | 9 | 10 | 1 | `TONE_CLASS` local |
| 13 | features/billing/pages/AdministrativoCargarComprobante.jsx | 19 | 9 | 6 | 4 | «pendiente» neutro, «vencida» peligro |
| 14 | features/evaluation/pages/DocenteConfiguracionEvaluacion.jsx | 19 | 19 | 0 | 0 | 6 familias; celdas con `bg-danger-bg/30`/`bg-success-bg/30` |
| 15 | features/academic-structure/pages/CarreraDetalle.jsx | 18 | 9 | 6 | 3 | acciones destructivas con `text-danger` |

Siguen (puntaje 18 a 15): `ListaDeEsperaDetalle`, `CursoDetalle`, `UpcomingEventsCard`, `PreceptorDetalleCarrera`, `PermissionModeDialog`, `HomeTaskCard`, `InvoiceCoverageList`, `ManualIssuanceSimulationPanel`, `ForgotPassword`, `ActivateAccount`. Mapas más cargados: `authorizationRequests.js` (15), `backupFormat.js` (14), `closureSheet.js` (14), `discountLabels.js` (14), `EstadoSolicitudBadge.jsx` (13), `pricing.js` (13), `broadcastLabels.js` (13).

Por zona: de los 217 archivos con mapeo de tono, 44 son de billing (fuera de alcance salvo arreglo automático por tokens), 19 de evaluación, 8 de asistencia y 146 del resto.

## 5. Accesibilidad: estados que dependen solo del color

Criterio: el estado se transmite por color y no hay ícono, forma ni texto propio del estado. Las 330 `StatusPill` NO caen aquí: siempre llevan texto, y aviso y peligro llevan además ícono por defecto (35 pastillas pasan `icon` explícito; 15 usan `dot`). Tampoco caen los `Notice` (siempre texto). Éxito, acento y neutro van solo con texto (cumple «ícono o texto»).

| # | Dónde | Qué se transmite solo con color |
|---|---|---|
| 1 | features/notifications/components/NotificationBell.jsx:71-77 | punto ámbar de «falló la carga», `aria-hidden`, sin texto |
| 2 | features/attendance/pages/DocenteTomarAsistencia.jsx:664-676 | barra de asistencia: verde presente / rojo ausente, sin texto del estado |
| 3 | features/attendance/components/SeccionAsistencia.jsx:60-70 | torta verde o roja según si se excedió el máximo de ausentismo (hay % pero no el estado «en riesgo» en la torta; `riesgo.l…` en línea 169 lo repite como texto coloreado) |
| 4 | components/ProgressBar.jsx | tono (éxito/aviso/peligro) solo en el relleno; usado con `tone` en `CohortesSection`, `TabPlanDeEstudio`, `PlanSubjectCard` |
| 5 | components/KpiFilterChip.jsx | número en peligro (`tone="danger"`) para fallidos; la etiqueta ayuda pero el énfasis es solo color |
| 6 | features/billing/dashboard/FinancialDashboardPage.jsx:148 | deuda total en rojo, sin ícono |
| 7 | features/billing/runs/NightlyRunsTab.jsx:83 | conteo de fallidos en rojo/negrita |
| 8 | features/workspace/pages/StaffSectionRoster.jsx:215 | «en riesgo» como número rojo |
| 9 | features/billing/components/AmountBreakdown.jsx:14 | importe rojo (débito) o verde (crédito); el signo ayuda pero no está garantizado |
| 10 | Contadores de caracteres que pasan a rojo al exceder el máximo (9 casos): `RequestRefundPage:38`, `CancelInvoiceDialog:149`, `CreateCreditNotePage:252`, `ExamBoardCorrectionSheet:85`, `GradeCorrectionSteps:104`, `JustificarFaltaDialog:84`, `AnnualTermsAdminPage:62`, `DirectorInstitutionSettings:134`, `SeccionMensaje:12` (ámbar) | límite superado solo por color (hay texto n/máx pero no «excedido») |
| 11 | features/evaluation/components/GradeReadCell.jsx:20-21 | puntos de «corregida» (relleno) y «sin publicar» (aro ámbar); tienen `aria-label` para lector de pantalla, pero visualmente solo forma/color |
| 12 | features/evaluation/pages/DocenteConfiguracionEvaluacion.jsx:62-63 | celdas con fondo rojo o verde al 30 % según error/completo |
| 13 | Leyenda de `CalendarioConfiguracion.jsx:58-67` | puntos verde/ámbar/acento (con texto al lado, pero dos eventos distintos comparten acento: «Parcial» y «Llamado a final») |
| 14 | Gráficos (`chartTheme.SERIES_COLORS`, `financialPanels`) | series 1 y 2 con el mismo `var(--accent)`; hay «Ver como tabla», pero la leyenda no las distingue |

**Cuenta:** 14 puntos concretos (de ellos, 4 son componentes compartidos con múltiples usos: `ProgressBar`, `KpiFilterChip`, contadores, gráficos) donde el estado depende del color; sumando los 9 contadores y los usos de `ProgressBar` con tono (3), el total de ocurrencias en código es unas 26. Uso de `StatusPill` solo con texto y sin ícono (éxito/acento/neutro): la mayoría de los 330; no es violación de la regla «ícono o texto».

## 6. Resumen de decisiones que quedan para Gabriel/E36-B

1. Confirmar que el significado de cada tono sigue la regla de la sección 3 (sobre todo «pendiente = aviso», «en curso = acento», «cerrado/vencido/ausente administrativo = neutro»).
2. Consolidar alias: `brand`/`primary`/`accent`, `danger`/`destructive`, `muted`/`secondary`/`neutral-bg`, y las tres tintas de texto por tono.
3. Reparar lo roto: `bg-success-soft` (2 líneas), `ChoiceChip tone="solid"` (`bg-accent` suave + texto claro), series de gráfico duplicadas.
4. Migrar a `StatusPill`/`Notice` las ~25 pastillas y ~123 cajas a mano (trabajo de E36-C/F-I, no de tokens).
5. Dejar un solo mapa estado→tono por dominio en `lib/` (hoy hay 217 archivos con tonos y 14 mapas locales de clases).
6. Guarda sugerida para E36-D: prohibir `tone:` fuera del vocabulario de 5 y `rounded-full bg-*-bg` fuera de `StatusPill`.

## Anexo A. Mapeo estado→tono por archivo (770 entradas, 215 archivos; etiqueta o clave tal como figura en el código; «sin etiqueta» = no se pudo leer del contexto cercano)

| archivo (frontend/src/) | estado → tono (todos vienen de token vía StatusPill/Notice) |
|---|---|
| components/ChangeSectionSheet.jsx | sin etiqueta→danger |
| components/Notice.jsx | sin etiqueta→neutral |
| components/ProgressBar.jsx | sin etiqueta→accent |
| components/StatusPill.jsx | sin etiqueta→neutral |
| features/academic-structure/components/AcademicManagementHome.jsx | Cierre de admisión→warning; Inicio de cursada→accent; sin etiqueta→warning |
| features/academic-structure/components/AniosLectivosSection.jsx | sin etiqueta→warning; sin etiqueta→danger; sin etiqueta→warning |
| features/academic-structure/components/ClassroomWeekOccupancy.jsx | sin etiqueta→neutral; sin etiqueta→warning |
| features/academic-structure/components/CohortesSection.jsx | sin etiqueta→warning; sin etiqueta→warning; sin etiqueta→danger |
| features/academic-structure/components/SectionPendingScheduleNotice.jsx | sin etiqueta→warning |
| features/academic-structure/components/SectionPreceptorsEditor.jsx | sin etiqueta→danger; sin etiqueta→warning |
| features/academic-structure/components/SectionScheduleApprovalRow.jsx | sin etiqueta→neutral |
| features/academic-structure/components/SectionScheduleRequestSheet.jsx | sin etiqueta→neutral |
| features/academic-structure/lib/classroomAvailability.js | Capacidad menor→danger; Ocupada→warning; Libre en tu período→accent; Libre→success |
| features/academic-structure/lib/cohorts.js | Abierta→success; Incompleta→warning; Lista para abrir→accent |
| features/academic-structure/pages/AdministrativoSubjects.jsx | Activa→success; Inactiva→neutral; Borrador→neutral; Pendiente→warning; Rechazada→danger; Activa→success; Inactiva→neutral; Activo→success; Inactivo→neutral; cell→neutral; sin etiqueta→neutral; sin etiqueta→neutral; sin etiqueta→neutral; sin etiqueta→neutral |
| features/academic-structure/pages/CarreraDetalle.jsx | Activa→success; Inactiva→neutral; Activa→success; Inactiva→neutral; sin etiqueta→neutral; sin etiqueta→neutral |
| features/academic-structure/pages/CursoDetalle.jsx | Activo→success; Inactivo→neutral; Activo→success; Inactivo→neutral; sin etiqueta→neutral; sin etiqueta→neutral |
| features/academic-structure/pages/DirectorOfertaCicloDetalle.jsx | sin etiqueta→danger; Borrador→neutral; Cerrado→neutral; Sin estado→neutral; sin etiqueta→warning |
| features/academic-structure/pages/DirectorSubjectReview.jsx | sin etiqueta→warning |
| features/academic-structure/pages/PreceptorDetalleCarrera.jsx | En construcción→neutral; En revisión→warning; Aprobado→success; Rechazado→danger; Cerrado→neutral; Activa→success; Inactiva→neutral; sin etiqueta→neutral; sin etiqueta→warning; sin etiqueta→neutral |
| features/academic-structure/pages/PreceptorDetalleCurso.jsx | En construcción→neutral; En revisión→warning; Aprobado→success; Rechazado→danger; Cerrado→neutral; sin etiqueta→neutral |
| features/academic-structure/pages/PreceptorOfertaAcademica.jsx | En construcción→neutral; En revisión→warning; Aprobado→success; Rechazado→danger; Cerrado→neutral; sin etiqueta→neutral; sin etiqueta→neutral |
| features/academic-structure/pages/PreceptorPeriodos.jsx | Abierta (manual)→warning; Cerrada→neutral; Según ventana→accent |
| features/announcements/components/EnvioYCaducidad.jsx | sin etiqueta→neutral |
| features/announcements/components/FilaAviso.jsx | sin etiqueta→danger |
| features/announcements/lib/readReceipts.js | Programado→accent; Vencido→neutral; Enviado→success |
| features/announcements/pages/AvisoEditar.jsx | sin etiqueta→warning; sin etiqueta→success |
| features/announcements/pages/AvisoWizard.jsx | sin etiqueta→accent |
| features/announcements/pages/DocenteComisionDetalle.jsx | sin etiqueta→neutral |
| features/announcements/pages/StaffSectionAnnouncements.jsx | sin etiqueta→neutral; cell→danger |
| features/attendance/components/AttendanceSaveBar.jsx | sin etiqueta→accent; sin etiqueta→success |
| features/attendance/components/JustificarFaltaDialog.jsx | sin etiqueta→success |
| features/attendance/components/TabAsistencia.jsx | Cursada regular · falta final→warning; En curso→accent; En lista de espera→warning; Lista de espera vencida→neutral; Inscripto→accent; Aprobada→success; Desaprobada→danger; De baja→neutral; sin etiqueta→neutral |
| features/attendance/lib/absenceRows.js | Justificada→success; Justificación pendiente→accent; Justificación rechazada→danger; Ausente→danger |
| features/attendance/lib/attendanceRequests.js | Pendiente→warning; Aprobada→success; Rechazada→danger |
| features/attendance/lib/attendanceStatuses.js | Presente→success; Tarde→warning; Ausente→danger; Justificada→accent; Justificación pendiente→accent; Sin marcar→neutral |
| features/attendance/pages/DocenteTomarAsistencia.jsx | sin etiqueta→danger; sin etiqueta→success; sin etiqueta→neutral; sin etiqueta→danger; sin etiqueta→neutral; sin etiqueta→neutral; sin etiqueta→danger; sin etiqueta→warning |
| features/attendance/pages/SolicitudesAsistencia.jsx | Aprobada→success; Rechazada→danger; sin etiqueta→warning |
| features/backups/components/BackupKeyFacts.jsx | sin etiqueta→danger |
| features/backups/lib/backupFormat.js | Desconocido→neutral; Verificada→success; Dañada→danger; Sin verificar→warning; Crítica→danger; Aviso→warning; Aprobada→success; Fallida→danger; sin etiqueta→success; sin etiqueta→danger; sin etiqueta→warning; sin etiqueta→neutral; Crítico→danger; Bajo→warning |
| features/billing/advance-enrollment-fee/AdvanceEnrollmentFeePage.jsx | sin etiqueta→warning |
| features/billing/advance-enrollment-fee/AdvanceEnrollmentFeeStaffSection.jsx | sin etiqueta→neutral; sin etiqueta→neutral |
| features/billing/annual-upfront/AnnualUpfrontCard.jsx | sin etiqueta→neutral; sin etiqueta→success; sin etiqueta→warning; sin etiqueta→success; sin etiqueta→neutral; sin etiqueta→warning |
| features/billing/annual-upfront/AnnualUpfrontStaffSection.jsx | sin etiqueta→neutral |
| features/billing/annual-upfront/annualUpfront.js | Esperando el pago→warning; Pago en revisión→warning; Año confirmado→success; Descartado→neutral; Ya no disponible→neutral; Con beca→neutral; Con deuda→danger; Fuera de fecha→neutral; Sin promoción→neutral |
| features/billing/authorization/RequestTimeline.jsx | sin etiqueta→warning |
| features/billing/authorization/authorizationRequests.js | Pendiente→warning; Aprobada→success; Rechazada→danger; Retirada→neutral; Expirada→neutral; No se pudo ejecutar→danger; sin etiqueta→neutral; No vence→neutral; Vence hoy→warning; Pedido por→neutral; Aprobada por→success; Rechazada por→danger; Retirada por→neutral; Expiró sin decisión→neutral; sin etiqueta→neutral |
| features/billing/components/AdmissionInvoicePendingCard.jsx | sin etiqueta→warning |
| features/billing/components/InvoiceCoverageList.jsx | sin etiqueta→success; sin etiqueta→neutral; sin etiqueta→danger; sin etiqueta→neutral; sin etiqueta→warning |
| features/billing/components/InvoiceDetail.jsx | sin etiqueta→neutral |
| features/billing/credit-notes/creditNotes.js | Crédito a favor→accent; Reduce deuda→neutral; A tu favor→accent; Sin saldo a pagar→neutral; Descontado de la cuota→neutral |
| features/billing/dashboard/FinancialDashboardPage.jsx | sin etiqueta→warning |
| features/billing/dashboard/MetricPanel.jsx | sin etiqueta→warning |
| features/billing/discounts/LostBenefitsSection.jsx | cell→warning |
| features/billing/discounts/MyDiscountCard.jsx | Vigente→success; Usado completo→neutral; Finalizado→neutral; Revocado→danger; sin etiqueta→neutral |
| features/billing/discounts/StatusPills.jsx | sin etiqueta→neutral; sin etiqueta→neutral |
| features/billing/discounts/discountLabels.js | Próxima→accent; Abierta→success; Cupo completo→warning; Inscripción cerrada→warning; Finalizada→neutral; Cerrada→neutral; Vigente→success; Agotado→neutral; Finalizado→neutral; Revocado→danger; Vigente→success; Próxima→accent; Vencida→neutral; Cerrada→neutral |
| features/billing/discounts/grantForm.js | sin etiqueta→success; sin etiqueta→warning; sin etiqueta→success; sin etiqueta→warning; sin etiqueta→success; sin etiqueta→warning |
| features/billing/monthly-run/ManualIssuanceSimulationPanel.jsx | sin etiqueta→success |
| features/billing/monthly-run/MonthlyRunHeader.jsx | sin etiqueta→neutral |
| features/billing/monthly-run/draftSelection.js | sin etiqueta→neutral; sin etiqueta→neutral |
| features/billing/pages/AdministrativoCargarComprobante.jsx | En revisión→warning; Vencida→danger; Pendiente→neutral; sin etiqueta→accent; sin etiqueta→success; sin etiqueta→warning |
| features/billing/pages/AdministrativoFacturacion.jsx | Al día→success; En mora→danger; Suspendido→danger; Dado de baja→neutral; sin etiqueta→warning; sin etiqueta→neutral; sin etiqueta→neutral; sin etiqueta→accent |
| features/billing/pages/AlumnoPagarSeleccion.jsx | sin etiqueta→neutral |
| features/billing/pages/AlumnoPagarTransferencia.jsx | Comprobante en revisión→warning; Revisamos parte de tu transferencia→warning; Comprobante aprobado→success; Aprobado, aplicado a deuda anterior→warning; La fecha de la transferencia no coincide→danger; Comprobante rechazado→danger; En revisión→warning; Pagada→success; Pendiente→neutral; sin etiqueta→neutral |
| features/billing/pages/AlumnoPagos.jsx | Al día→success; Moroso→danger; Suspendido→warning; Dado de baja→neutral; sin etiqueta→neutral; sin etiqueta→neutral |
| features/billing/payment-methods/PaymentMethodsTab.jsx | sin etiqueta→neutral |
| features/billing/payment-reversals/PendingRefundsNotice.jsx | sin etiqueta→neutral |
| features/billing/payment-reversals/StudentPaymentsCard.jsx | sin etiqueta→neutral |
| features/billing/payment-reversals/paymentReversals.js | Pendiente→warning; Autorizado→accent; Aprobado→success; Rechazado→danger; Reembolsado→neutral; Contracargado→danger; Cancelado→neutral |
| features/billing/pricing/PriceDetailPage.jsx | Programada→accent; Propuesta→warning |
| features/billing/pricing/pricing.js | Sin precio vigente→danger; Propuesta pendiente→warning; Aumento programado→accent; Gratuito→neutral; Vigente→success; Propuesta→warning; Aprobada · programada→accent; Vigente→success; Reemplazada→neutral; Rechazada→danger; Retirada→neutral; Vencida→danger; Cancelada antes de regir→neutral |
| features/billing/refunds/RefundPartsList.jsx | sin etiqueta→neutral |
| features/billing/refunds/RequestRefundPage.jsx | sin etiqueta→danger |
| features/billing/refunds/refundForm.js | sin etiqueta→danger; sin etiqueta→danger; sin etiqueta→neutral |
| features/billing/refunds/refundRequests.js | Pendiente de aprobación→warning; Listo para transferir→accent; Hecho→success; No se pudo→danger; Rechazado→neutral; En curso→warning; Hecho con fallas→warning; En curso · ${completed} de ${parts.length} hechas→warning |
| features/billing/reminders/ContactListTab.jsx | sin etiqueta→accent |
| features/billing/reminders/ExceptionsTable.jsx | cell→warning; cell→success |
| features/billing/reminders/ReminderSettingsPage.jsx | sin etiqueta→warning |
| features/billing/reminders/SettingsFields.jsx | sin etiqueta→accent |
| features/billing/reminders/VersionHistory.jsx | Vigente→success; Programada→accent; Pendiente de aprobación→warning; Reemplazada→neutral; Rechazada→danger; Retirada→neutral; Vencida→neutral; Cancelada→neutral |
| features/billing/runs/StepItemsTable.jsx | cell→warning |
| features/broadcasts/components/AudienceStep.jsx | sin etiqueta→warning |
| features/broadcasts/lib/broadcastLabels.js | Borrador→neutral; Confirmado→accent; Enviando→warning; Completado→success; Cancelado→danger; Pendiente→neutral; En cola→accent; Enviado→success; Enviado→success; Falló→danger; Número inválido→danger; Excluido→warning; Omitido→warning |
| features/broadcasts/pages/BroadcastWizard.jsx | sin etiqueta→danger |
| features/data-import/components/ImportBlockRow.jsx | sin etiqueta→warning |
| features/data-import/components/ImportReportStep.jsx | sin etiqueta→warning; sin etiqueta→warning; sin etiqueta→warning |
| features/data-import/pages/DataImportRollbackPage.jsx | sin etiqueta→warning; sin etiqueta→danger |
| features/enrollment/components/CourseSubjectPanel.jsx | Aprobada→success; Desaprobada→danger; Baja→neutral; En lista de espera→warning; Lista de espera vencida→neutral; Nota final ${Number(sectionEnrollment.finalScore).toLocaleString(→success; En curso→success |
| features/enrollment/components/EstadoSolicitudBadge.jsx | En revisión→warning; En revisión→warning; Aprobada→success; Aplicado a deuda anterior→warning; Rechazada→danger; Vencida→danger; En lista de espera→neutral; Pagá la matrícula→warning; Pago en revisión→warning; Matrícula vencida→danger; Confirmando inscripción→warning; Inscripto→success; sin etiqueta→neutral |
| features/enrollment/components/InformativeSessionCard.jsx | sin etiqueta→danger; sin etiqueta→success |
| features/enrollment/components/OfferingCard.jsx | sin etiqueta→neutral; sin etiqueta→accent |
| features/enrollment/components/StaffEnrollmentOptions.jsx | sin etiqueta→danger; sin etiqueta→warning |
| features/enrollment/components/TopNavCatalogPanel.jsx | sin etiqueta→neutral; sin etiqueta→accent |
| features/enrollment/components/WaitlistPositionCard.jsx | sin etiqueta→success |
| features/enrollment/lib/applicationReview.js | Pendiente→warning; Aprobada→success; Rechazada→danger; Vencida→neutral |
| features/enrollment/lib/catalogOffering.js | Inscripciones abiertas→success; Consultá fechas→neutral; Cierra ${formatDayMonth(nextEnd)}→warning |
| features/enrollment/lib/courseEnrollmentStatus.js | Cursando→success; Cursando→success; Cursando→success; En lista de espera→warning; Aprobado→success; Desaprobado→danger; Dado de baja→neutral; Lista de espera vencida→neutral; sin etiqueta→neutral |
| features/enrollment/lib/courseSubjectDetail.js | Presente→success; Llegada tarde→success; Ausente→danger; Justificación pendiente→warning; Justificada→neutral; sin etiqueta→neutral |
| features/enrollment/lib/waitlist.js | sin etiqueta→success; sin etiqueta→warning; Baja registrada por el personal→danger; Cambio de comisión por el personal→accent; Movimiento registrado→neutral |
| features/enrollment/pages/DetalleCurso.jsx | sin etiqueta→warning |
| features/enrollment/pages/DetalleSolicitud.jsx | sin etiqueta→warning |
| features/enrollment/pages/DirectorAprobacionesExcepciones.jsx | sin etiqueta→neutral |
| features/enrollment/pages/DirectorExcepcionDetalle.jsx | sin etiqueta→warning |
| features/enrollment/pages/InscribirAlumno.jsx | sin etiqueta→warning |
| features/enrollment/pages/ListaDeEsperaDetalle.jsx | sin etiqueta→accent; sin etiqueta→accent; sin etiqueta→info |
| features/enrollment/pages/ListasDeEspera.jsx | sin etiqueta→success |
| features/enrollment/pages/SolicitudDeIngresoDetalle.jsx | sin etiqueta→success; sin etiqueta→warning; sin etiqueta→danger; sin etiqueta→success; sin etiqueta→neutral; sin etiqueta→neutral |
| features/enrollment/pages/SolicitudesDeIngreso.jsx | cell→neutral |
| features/evaluation/components/ClosureNoAbsenceLimitNotice.jsx | sin etiqueta→warning |
| features/evaluation/components/ClosurePrerequisitesSheet.jsx | sin etiqueta→neutral |
| features/evaluation/components/ExamBoardGradesSection.jsx | sin etiqueta→warning |
| features/evaluation/components/ExamCertificatesSection.jsx | sin etiqueta→danger; sin etiqueta→success; sin etiqueta→danger |
| features/evaluation/components/GradeCorrectionSheet.jsx | Sin nota→neutral; Corregida→accent; Publicada→success; Publicada→warning |
| features/evaluation/components/GradeGridTable.jsx | sin etiqueta→success |
| features/evaluation/components/TabMesasDeExamen.jsx | sin etiqueta→accent; sin etiqueta→danger; sin etiqueta→warning |
| features/evaluation/components/TabNotas.jsx | sin etiqueta→accent; En curso→accent; Cursada regular · falta final→warning; Aprobada→success; Desaprobada→danger; Disponible→accent; Bloqueada→neutral |
| features/evaluation/components/closureColumns.jsx | cell→neutral |
| features/evaluation/lib/closureList.js | Confirmada por el docente→accent; Reabierta→warning; Pendiente del docente→neutral; Cerrada→success |
| features/evaluation/lib/closureSheet.js | Promociona→success; Promoción condicionada→warning; Va a final→accent; Libre por inasistencias→danger; Desaprobado→danger; Pendiente→neutral; Borrador→warning; Entregada por el docente→accent; Cerrado→success; Reabierto→warning; Aprobada→success; Cursada regular→accent; No cursada→danger; Exceptuada→accent |
| features/evaluation/lib/examBoardGrades.js | Aprobado→success; Desaprobado→danger; Ausente→neutral; Pendiente→accent; Sin cursada regular→warning; Libre por inasistencias→danger |
| features/evaluation/lib/examCertificates.js | Para emitir→accent; Emitida→success; Ausente→neutral; Debe regularizar sus pagos→warning; Revocada→danger; No disponible→neutral |
| features/evaluation/lib/gradeGrid.js | Cursando→accent; Cursada regular→success; Libre por inasistencias→danger; Aprobado→success; Desaprobado→danger |
| features/evaluation/pages/DirectorAprobacionesConfigEvaluacion.jsx | sin etiqueta→neutral |
| features/evaluation/pages/DirectorRevisarConfiguracion.jsx | sin etiqueta→neutral |
| features/evaluation/pages/DocenteClosureSheet.jsx | sin etiqueta→neutral; sin etiqueta→warning |
| features/evaluation/pages/PreceptorMesaExamen.jsx | Agendada→accent; Agendada→neutral; Cancelada→danger |
| features/evaluation/pages/StaffClosureSheet.jsx | sin etiqueta→neutral; sin etiqueta→warning |
| features/graduation/certificate-exceptions/components/CreateDirectExceptionDialog.jsx | sin etiqueta→danger |
| features/graduation/certificate-exceptions/components/DecideExceptionDialog.jsx | sin etiqueta→danger |
| features/graduation/certificate-exceptions/components/RejectionReasonDialog.jsx | sin etiqueta→danger |
| features/graduation/certificate-exceptions/components/RequestDebtCell.jsx | sin etiqueta→danger; sin etiqueta→warning; sin etiqueta→success; sin etiqueta→danger |
| features/graduation/certificate-exceptions/components/RequestExceptionDialog.jsx | sin etiqueta→danger |
| features/graduation/certificate-exceptions/lib/certificateExceptions.js | Pendiente→warning; Aprobada→success; Rechazada→danger; Retirada→neutral; Usada→neutral; Vencida→neutral; sin etiqueta→neutral |
| features/graduation/certificate-exceptions/pages/CertificateExceptionDetail.jsx | sin etiqueta→danger; sin etiqueta→success; sin etiqueta→warning |
| features/graduation/certificates/components/TemplatePanel.jsx | sin etiqueta→neutral |
| features/graduation/certificates/components/TemplateSignatoryField.jsx | sin etiqueta→warning |
| features/graduation/certificates/lib/certificateTemplate.js | Cambios sin guardar→accent; Borrador sobre v${base}→warning; Publicada v${template.published.versionNumber}→success; Sin plantilla→neutral |
| features/graduation/certificates/lib/certificateView.js | sin etiqueta→danger; sin etiqueta→warning; sin etiqueta→warning; sin etiqueta→success; sin etiqueta→neutral |
| features/graduation/certificates/lib/importedReview.js | Pendiente→warning; Habilitado→success; Deshabilitado→danger; No requiere revisión→neutral; sin etiqueta→neutral |
| features/graduation/certificates/lib/issuedGraduationCertificates.js | Emitido→success; Revocado→danger; sin etiqueta→neutral |
| features/graduation/certificates/lib/ownCertificates.js | Vigente→success; Revocado→danger; sin etiqueta→neutral |
| features/graduation/certificates/lib/regularCertificateState.js | Disponible→accent; En revisión→warning; Vigente→success; Vencida→neutral; No disponible→neutral; No disponible→neutral; No disponible→neutral |
| features/graduation/certificates/lib/staffGraduationCertificates.js | Pendiente→warning; Bloqueado por deuda→danger; Pendiente de revisión→warning; Certificado deshabilitado→danger; sin etiqueta→neutral |
| features/graduation/certificates/lib/staffRegularCertificates.js | Vigente→success; Vencida→neutral; Revocada→danger; sin etiqueta→neutral |
| features/graduation/certificates/pages/VerifyCertificate.jsx | sin etiqueta→neutral; sin etiqueta→neutral; sin etiqueta→neutral |
| features/graduation/components/GraduationDebtCard.jsx | sin etiqueta→neutral; sin etiqueta→success; sin etiqueta→danger |
| features/graduation/debt-exceptions/components/IssuedDebtException.jsx | sin etiqueta→success |
| features/graduation/debt-exceptions/pages/EmitDebtException.jsx | sin etiqueta→warning; sin etiqueta→neutral |
| features/graduation/lib/graduationState.js | Todavía no elegible→neutral; Elegible→success; Solicitada→warning; En revisión de Dirección→accent; Egreso aprobado→success; Rechazada→danger |
| features/graduation/lib/graduationVerification.js | Por verificar→warning; En el Director→neutral; Aprobado→success; Rechazado→danger; sin etiqueta→neutral |
| features/graduation/pages/DirectorAprobacionesEgresos.jsx | sin etiqueta→neutral |
| features/graduation/pages/DirectorEgresoDetalle.jsx | Al día→success; Moroso→danger; Sin información→neutral; sin etiqueta→warning; sin etiqueta→neutral |
| features/graduation/procedures/components/ChangeMySectionFlow.jsx | sin etiqueta→warning |
| features/graduation/procedures/lib/procedures.js | Vigente→success; Vencida→neutral; Revocada→danger; Reemplazada→neutral |
| features/graduation/signatories/components/SignatoryForm.jsx | sin etiqueta→warning |
| features/graduation/signatories/lib/certificateSignatories.js | Activo→success; Activo→neutral |
| features/identity/legal/GuardianWaitingScreen.jsx | sin etiqueta→success |
| features/identity/legal/LegalAcceptancesTab.jsx | sin etiqueta→warning; sin etiqueta→success |
| features/identity/legal/LegalDocumentPage.jsx | sin etiqueta→neutral; sin etiqueta→neutral |
| features/identity/lib/academicRecord.js | Aprobada→success; Cursada regular→accent; Cursando→accent; Desaprobada→danger; Dada de baja→neutral; Inscripta→neutral; En lista de espera→warning; Lista de espera vencida→neutral; sin etiqueta→neutral |
| features/identity/lib/regularity.js | No es alumno regular→neutral |
| features/identity/pages/ActivateAccount.jsx | sin etiqueta→danger; sin etiqueta→warning; sin etiqueta→danger; sin etiqueta→success; sin etiqueta→danger |
| features/identity/pages/LegajoAlumno.jsx | Activo→success; Dado de baja→danger; sin etiqueta→accent |
| features/identity/pages/LegajoPersonal.jsx | Activo→success; Inactivo→neutral; sin etiqueta→warning |
| features/identity/pages/MiPerfil.jsx | sin etiqueta→warning |
| features/identity/pages/PadronAlumnos.jsx | sin etiqueta→danger; sin etiqueta→warning; sin etiqueta→neutral; sin etiqueta→warning; sin etiqueta→neutral; sin etiqueta→neutral |
| features/identity/pages/ReviewEmailChangePage.jsx | sin etiqueta→warning; sin etiqueta→warning; sin etiqueta→info; sin etiqueta→success; sin etiqueta→danger; sin etiqueta→danger; sin etiqueta→danger; sin etiqueta→danger; sin etiqueta→danger |
| features/identity/pages/tabs/FinancieroTab.jsx | Todavía no emitida→neutral; Pendiente de pago→warning; Comprobante en revisión→accent; Paga→success; Habilitado sin pagar→accent; sin etiqueta→neutral; sin etiqueta→neutral |
| features/institution/pages/DirectorInstitutionSettings.jsx | sin etiqueta→accent; sin etiqueta→accent; sin etiqueta→danger |
| features/notifications/invitation-waves/components/RecipientsTable.jsx | sin etiqueta→warning |
| features/notifications/invitation-waves/components/WaveAccessNotice.jsx | sin etiqueta→warning |
| features/notifications/invitation-waves/components/WaveForm.jsx | sin etiqueta→accent |
| features/notifications/invitation-waves/lib/invitationWaveLabels.js | Borrador→neutral; En curso→accent; Pausada→warning; Completada→success; Cancelada→danger; Pendiente→neutral; En cola→neutral; Enviado→accent; Fallido→danger; Excluido→warning; Activó→success |
| features/notifications/invitation-waves/pages/InvitationWavePreviewPage.jsx | sin etiqueta→warning; sin etiqueta→success; sin etiqueta→warning |
| features/permissions/components/PermissionDiscrepanciesTab.jsx | sin etiqueta→warning |
| features/permissions/components/PermissionModeDialog.jsx | sin etiqueta→accent |
| features/permissions/components/PermissionModuleSection.jsx | sin etiqueta→warning; sin etiqueta→warning |
| features/permissions/lib/permissionGrid.js | Legado→neutral; Sombra→warning; Activo→success; La regla vieja permitía y la grilla negaría→warning; La regla vieja negaba y la grilla permitiría→accent |
| features/platform/components/FeatureRow.jsx | sin etiqueta→warning; sin etiqueta→neutral; sin etiqueta→success; sin etiqueta→neutral |
| features/platform/components/FirstDirectorForm.jsx | sin etiqueta→neutral |
| features/platform/components/FirstDirectorStateCard.jsx | sin etiqueta→danger; sin etiqueta→danger |
| features/platform/components/FirstDirectorSuccess.jsx | sin etiqueta→success; sin etiqueta→success; sin etiqueta→accent; sin etiqueta→neutral |
| features/platform/lib/featureCatalog.js | Apagada→neutral; Calcula sin cobrar→accent; Completa→success |
| features/platform/pages/PrimerDirector.jsx | sin etiqueta→danger; sin etiqueta→danger; sin etiqueta→danger; action→warning |
| features/workspace/components/EquivalenceChip.jsx | sin etiqueta→accent |
| features/workspace/components/EquivalenceDetailSheet.jsx | sin etiqueta→warning |
| features/workspace/components/EquivalenceStatusPill.jsx | sin etiqueta→neutral; sin etiqueta→success |
| features/workspace/components/EquivalencesSection.jsx | sin etiqueta→warning; sin etiqueta→neutral |
| features/workspace/components/GraduationProjectionCard.jsx | sin etiqueta→warning; sin etiqueta→warning |
| features/workspace/components/HomeTaskCard.jsx | sin etiqueta→neutral; sin etiqueta→neutral; sin etiqueta→neutral |
| features/workspace/components/PlanSubjectCard.jsx | sin etiqueta→success |
| features/workspace/components/StudentBalanceNotice.jsx | sin etiqueta→success |
| features/workspace/components/StudentCurrentSubjects.jsx | sin etiqueta→warning |
| features/workspace/components/SubjectDetailSheet.jsx | sin etiqueta→neutral |
| features/workspace/components/TabMaterias.jsx | Cursada regular · falta final→warning; En curso→accent; En lista de espera→warning; Lista de espera vencida→neutral; Inscripto→accent; Aprobada→success; Desaprobada→danger; De baja→neutral; sin etiqueta→neutral |
| features/workspace/components/TabPlanDeEstudio.jsx | sin etiqueta→warning; sin etiqueta→success |
| features/workspace/components/UpcomingEventsCard.jsx | Mesa de examen→accent; Feriado→neutral; Cierre de período→warning; Inscripción→success; Inicio de período→neutral; Clase→accent; Vencimiento→warning; Entrega→neutral; sin etiqueta→neutral; sin etiqueta→neutral; sin etiqueta→neutral |
| features/workspace/components/calendar/CalendarExportPanel.jsx | sin etiqueta→danger |
| features/workspace/components/calendar/CalendarFeedSection.jsx | sin etiqueta→success; sin etiqueta→warning; sin etiqueta→danger; sin etiqueta→success; sin etiqueta→danger; sin etiqueta→danger |
| features/workspace/components/calendar/calendarEventMeta.js | Feriado→warning; Clase cancelada→danger; Clase cancelada→neutral; Mesa cancelada→danger; Inscripto a la mesa→success; Inscripción abierta→accent; Programada→neutral |
| features/workspace/components/planStatusPills.js | En curso→accent; Cursada regular · falta final→warning; Aprobada→success; Desaprobada · a recursar→danger; Disponible→neutral; Bloqueada→neutral |
| features/workspace/hooks/useStaffHomeCards.js | sin etiqueta→warning |
| features/workspace/lib/curriculumPlan.js | Confianza alta→success; Confianza media→accent; Confianza baja→warning |
| features/workspace/pages/AlumnoInicio.jsx | sin etiqueta→danger; sin etiqueta→accent |
| features/workspace/pages/AlumnoMateriaDetalle.jsx | Cursada regular→success; En curso→accent; En lista de espera→warning; Lista de espera vencida→neutral; Inscripto→accent; Aprobada→success; Desaprobada→danger; De baja→neutral; Estado desconocido→neutral |
| features/workspace/pages/AlumnoMisCarreras.jsx | sin etiqueta→success |
| features/workspace/pages/DocenteMisComisiones.jsx | sin etiqueta→neutral; sin etiqueta→neutral |
| features/workspace/pages/StaffSectionRoster.jsx | sin etiqueta→danger |
| lib/aprobaciones.js | Pendiente→warning; Pendiente→warning; Pendiente→warning; Pendiente→warning; Aprobado→success; Rechazado→danger |
| lib/avisos.js | Programado→warning; Enviado→success; Vigente→success; Vencido→neutral; Vencido→success |
| lib/bankTransferReceipt.js | sin etiqueta→warning; sin etiqueta→warning; sin etiqueta→success; sin etiqueta→warning; sin etiqueta→success; sin etiqueta→neutral; sin etiqueta→neutral |
| lib/billing.js | Pendiente→warning; En revisión→neutral; Vencida→danger; Pagada→success; Cuota sin efecto→neutral; sin etiqueta→neutral; En revisión→warning; Aprobado→success; Aprobado, aplicado a deuda anterior→warning; Rechazado→danger |
| lib/comisiones.js | Borrador→neutral; Pendiente→warning; Aprobado→success; Rechazado→danger |
| pages/DesignCatalog.jsx | sin etiqueta→accent; sin etiqueta→neutral |