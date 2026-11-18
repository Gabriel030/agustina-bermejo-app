# 26-C.0 — Inventario para las pantallas piloto (Épica 26, dirección B)

Solo lectura. Fecha de corte: 2026-11-17, sobre `develop` (HEAD `2276ce6`). Los conteos excluyen `*.test.*`.

## 1. Conteos globales sobre `frontend/src`

| Patrón | Total | Fuera de `components/ui` | Lectura |
|---|---|---|---|
| `text-[Npx]` | 2 | 2 | `CertificateVerdict` (`text-[23px]`) y `CalendarioConfiguracion` (`text-[7.5px]`), ambos en la lista `ALLOWED` de `literal-values.test.js`. Prácticamente resuelto |
| `rounded-[…]` | 19 | 10 | 9 en `ui/`; fuera: `InstitutionBrand` (1), `MonthPicker` (2), `PreceptorMesaExamen` (2), `DirectorAprobaciones` (5, son `rounded-[inherit]`, válidos) |
| `<button>` crudo | 63 | 63 | Contra 683 `<Button>`. Sin guarda de lint |
| `<Button>` | 683 | — | Adopción ~92 % |
| `rounded-{sm..full}` nombrados | ~520 | ~490 | Mapean a `--r-*` vía `@theme` en `index.css`; cambian con el diseño. No son deuda por sí solos |
| `font-mono` | 9 | 9 | DNI en `PadronAlumnos` y `GestionPersonal`, un valor en `AlumnoPagarTransferencia` (CBU/alias), 4 en `components/`, 1 en `evaluation`. Ningún monto detectado |
| Color fijo (hex/oklch/rgb/hsl/white/black) | 0 | 0 | Garantizado por `literal-values.test.js` |
| `<DataTable>` / `<Table>` directos | 39 / 24 | — | Las `<Table>` directas están en pantallas de detalle, comprobantes y facturación |
| Variante `design-b:` | 56 | 56 | Solo en 7 archivos: `button.jsx`, `ThemeToggle`, `MobileMenu`, `TopHeader`, `Sidebar`, `RoleShell`, `NotificationBell` |

### Por carpeta

| Carpeta | `text-[Npx]` | `rounded-[` | `<button>` crudo | `rounded-*` nombrado | `font-mono` | `<Table>` directo | `<DataTable>` |
|---|---|---|---|---|---|---|---|
| components (sin ui) | 0 | 3 | 24 | 57 | 4 | 2 | 0 |
| components/ui | 0 | 9 | 1 | 28 | 0 | 0 | 0 |
| layouts | 0 | 0 | 0 | 0 | 0 | 0 | 0 |
| pages | 0 | 0 | 5 | 27 | 1 | 4 | 0 |
| academic-structure | 0 | 0 | 7 | 66 | 0 | 0 | 4 |
| announcements | 0 | 0 | 4 | 13 | 0 | 2 | 2 |
| attendance | 0 | 0 | 2 | 35 | 0 | 1 | 2 |
| billing | 0 | 0 | 7 | 104 | 0 | 6 | 18 |
| data-import | 0 | 0 | 0 | 0 | 0 | 0 | 2 |
| enrollment | 0 | 0 | 3 | 48 | 0 | 3 | 4 |
| evaluation | 1 | 2 | 5 | 32 | 1 | 1 | 2 |
| graduation | 1 | 0 | 0 | 11 | 0 | 1 | 2 |
| identity | 0 | 0 | 3 | 67 | 2 | 3 | 2 |
| institution | 0 | 0 | 0 | 1 | 0 | 0 | 0 |
| notifications | 0 | 0 | 2 | 5 | 0 | 0 | 0 |
| platform | 0 | 0 | 0 | 9 | 0 | 3 | 0 |
| schedule | 0 | 0 | 0 | 2 | 0 | 0 | 0 |
| workspace | 0 | 5 | 1 | 21 | 0 | 2 | 1 |

(Los conteos de `components` incluyen `ui` en la medición original; la fila "sin ui" es la resta. Los `<button>` crudos están sobre todo en `MobileMenu` (4), `MonthPicker` (3), `BannerCarousel` (3), `RoleSwitcher` (2), `NotificationBell` (2), `MiPerfil` (2), `InscripcionMaterias` (2), `AdministrativoFacturacion` (2) y `AlumnoPagarSeleccion` (2).)

## 2. Inventario por feature y pantalla

Criterio de estado: **B** = usa `Button`/`DataTable`/`Input`/`GlassCard`/`PageContainer` sin crudos ni valores sueltos; **B-parcial** = tiene crudos o `<Table>` directo o `rounded-[`; **B-shell** = depende de `design-b:` o MeshBackground.

| Feature | Pantallas | Estado | Pendiente concreto |
|---|---|---|---|
| academic-structure | 23 | Mayormente B | Crudos: `PanelAgregarMateria`, `PanelCorrelativas`, `PreceptorNuevoCiclo` (728 loc, 11 `rounded-*`). Wizards largos (`AdministrativoSubjectWizard` 603 loc) |
| announcements | 5 | B | Sin crudos en pantallas; `DataTable` ya usado |
| attendance | 2 | B-parcial | `DocenteTomarAsistencia` (1116 loc, 14 `rounded-*`, 1 crudo, exenta de la guarda de inputs/tablas) |
| billing | ~35 | B-parcial | 104 `rounded-*`, 18 `DataTable`, 6 `<Table>`; `AdministrativoFacturacion` (752 loc, 2 crudos, `<Table>`), `NightlyRunsTab`, `SimulatedMailsPage` con crudos |
| pages/ (alumno: pagos) | 8 | B-parcial | `AlumnoPagos`, `AlumnoPagoDetalle` con `<Table>` directo; `AlumnoPagarSeleccion` 2 crudos |
| data-import | 4 | B | Limpio |
| enrollment | 17 | B-parcial | `InscripcionMaterias` y `InscripcionWizard` con crudos; `DetalleSolicitud` / `SolicitudDeIngresoDetalle` densos (9 y 6 `GlassCard`) |
| evaluation | 12 | B-parcial | `GradeGridTable` (exenta), `PreceptorMesaExamen` (`rounded-[10px]` ×2 + crudo), `CalendarioConfiguracion` (`text-[7.5px]`), `GradeReadCell` crudo |
| graduation | 5 | B | `CertificateVerdict` con `text-[23px]` permitido; `VerifyCertificate` usa MeshBackground |
| identity | 10 | B-shell | Login, Registrarse, ForgotPassword, VerificacionEmail, BajaComunicaciones, ConfirmarResponsable: MeshBackground. `MiPerfil` 659 loc, 2 crudos. `font-mono` en DNI |
| institution | 1 | B | Limpio |
| notifications | — | B-parcial | `NotificationBell` 2 crudos y `design-b:` |
| platform | 3 | B | `<Table>` directo en 3 sitios |
| schedule | — | B | Limpio |
| workspace | 6 | B-parcial | `AlumnoInicio` (8 `rounded-*`, 1 crudo), `DirectorAprobaciones` (`rounded-[inherit]`), `AlumnoMateriaDetalle` `<Table>` |
| components/ compartidos | 60+ | B-parcial | 24 crudos en `MobileMenu`, `MonthPicker`, `BannerCarousel`, `RoleSwitcher`; `InstitutionBrand` con `rounded-[17px]` |

## 3. MeshBackground y modo oscuro duplicado

- `MeshBackground.jsx` **sigue renderizándose**. Se importa en 13 archivos: `layouts/RoleShell.jsx` (shell de todos los roles) y 12 pantallas previas a la sesión o públicas: `Login`, `Registrarse`, `ForgotPassword`, `VerificacionEmail`, `BajaComunicaciones`, `ConfirmarResponsable`, `GuardianWaitingScreen`, `LegalDocumentPage`, `VerifyCertificate`, `CargaInicial`, `RolePicker`, `NoAutorizado`.
- En B solo se **oculta** con CSS (`index.css:272`, `:root[data-design='b'] .mesh-blob { display: none }`): el contenedor `fixed inset-0` con `var(--page-bg)` sigue en el DOM. El retiro real es 26-D.
- El modo oscuro **sigue duplicado**: `tokens.css:113-138` (`:root[data-theme='dark']`, diseño A) y `tokens-b.css:37` (`:root[data-design='b'][data-theme='dark']`). `tokens.test.js` exige paridad entre bloques, así que el retiro del bloque A requiere ajustar ese test.
- `DEFAULT_DESIGN = 'b'` en `theme/design.js`; la variante `@custom-variant design-b` (`index.css:108`) tiene 56 usos, todos en shell y `button.jsx`.

## 4. Guardas existentes y faltantes

Existen:
- ESLint `local/no-raw-elements` (error): `table`, `select`, `textarea`, `input` (excepto checkbox/radio/file/hidden); exentos `ui/`, tests y 2 archivos (`DocenteTomarAsistencia`, `GradeGridTable`).
- ESLint: recharts solo vía `@/components/charts`; límites entre features (**warn**, y la lista `FEATURES` omite `institution`, `platform`, `schedule`, `data-import`); `components/lib/layouts` no importan de features (**warn**).
- Vitest `literal-values.test.js`: hex, `oklch()`, `rgb()`, `hsl()`, `*-white/black`, `shadow-[`, `text-[Npx]` fuera de `theme/` y `ui/`.
- Vitest `tokens.test.js` (paridad light/dark, contraste) y `design.test.js`.
- `vitest-axe` y `axe-core` instalados (sin `eslint-plugin-jsx-a11y`).

Faltan:
1. Regla para `<button>` crudo (63 hoy) — mismo patrón que `no-raw-elements`, con lista de excepciones acotada.
2. Regla/test para `rounded-[` arbitrario (salvo `inherit`) — hoy no está en `literal-values`.
3. Test de `font-mono` en montos (hoy no hay forma de distinguir monto de DNI; sugerir un componente `Amount`/`Money` y prohibir `font-mono` fuera de él y de identificadores).
4. Prohibir importar `MeshBackground` y usar `design-b:` fuera del shell (preparar el retiro en 26-D; luego borrar ambos).
5. `eslint-plugin-jsx-a11y` (para 26-E) y tests `axe` por pantalla piloto.
6. Subir los límites entre features de `warn` a `error` y completar `FEATURES`.

## 5. Propuesta de 5 pantallas piloto

| Rol | Pantalla | Patrón | Por qué | Esfuerzo |
|---|---|---|---|---|
| Alumno | `AlumnoPagos` (+ `AlumnoPagoDetalle` como vista anidada) | Lista con filtros | Alta frecuencia; tabla con filtros por columna, estados, montos; `<Table>` directo hoy | M (1–1,5 d) |
| Docente | `DocenteTomarAsistencia` | Formulario / grilla de captura | La pantalla más usada del docente; la más grande (1116 loc), exenta de lint, crudos | L (2–3 d) — alternativa más barata: `DocenteMisComisiones` (S, 0,5 d) + `StaffSectionRoster` |
| Administrativo | `SolicitudDeIngresoDetalle` (con `SolicitudesDeIngreso` como entrada) | Detalle con acciones y estados | Flujo diario de trámites; 470 loc, 6 `GlassCard`, `rounded-*` | M (1–1,5 d) |
| Director | `DirectorAprobaciones` (dashboard de bandeja) | Dashboard con tarjetas y contadores | Primera pantalla del Director; ya tiene shell B; `rounded-[inherit]` ×5 | S (0,5 d) |
| Platform admin | `PrimerDirector` (+ `ModulosPlataforma`) | Formulario | Único formulario del rol, 334 loc, 4 `Input` | S–M (0,5–1 d) |

Total estimado: ~5,5 a 8 días-persona. Orden sugerido: Director → Platform → Alumno → Administrativo → Docente, para validar primero lo barato y dejar la pantalla más riesgosa con los componentes ya probados. Cobertura de patrones: lista (Alumno), formulario (Docente, Platform), detalle (Administrativo), dashboard (Director).

Supuestos: la frecuencia de uso es inferida del menú y del tamaño de la pantalla, no de métricas reales; confirmar con el coordinador si hay analítica.

## 6. Pantallas que necesitarían mockup nuevo

Los mockups vigentes de B están en Obsidian y no son visibles desde acá. Sin verificar, probablemente falten:
- `DocenteTomarAsistencia` (grilla de captura densa, estados por fila, móvil).
- `AlumnoPagos` / `AlumnoPagoDetalle` (tabla con filtros por columna y desglose de montos).
- `SolicitudDeIngresoDetalle` (detalle con acciones y timeline).
- `PrimerDirector` (formulario de plataforma, si no se mockeó antes).
- Pantallas previas a la sesión sin `MeshBackground` (Login, Registrarse, ForgotPassword, RolePicker, errores): cuando se retire el fondo hace falta definir su fondo en B.

`DirectorAprobaciones` se asume cubierto por el mockup del shell del Director; confirmar.
