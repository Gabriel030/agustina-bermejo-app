# Frontend Surface — Pantallas y componentes existentes

> Archivo acumulativo. El Líder lo actualiza al cerrar cada feature.
> Antes de planificar, verificá también con `ls frontend/src/features/` y `ls frontend/src/pages/`.

---

## Pantallas existentes

### Por feature (`src/features/`)

#### `gestion-personas/`
| Pantalla | Ruta | Roles |
|---|---|---|
| `GestionPersonal.jsx` | — | Administrativo, Preceptor |
| `LegajoAlumno.jsx` | — | Administrativo, Preceptor |
| `LegajoPersonal.jsx` | — | Administrativo |
| `MiPerfil.jsx` | — | Todos |
| `NuevoAlumno.jsx` | — | Administrativo, Preceptor |
| `PadronAlumnos.jsx` | — | Administrativo, Preceptor |
| `tabs/AcademicoTab.jsx` | — | Legajo: pestaña académica |
| `tabs/DatosPersonalesTab.jsx` | — | Legajo: pestaña datos personales |
| `tabs/EvaluacionTab.jsx` | — | Legajo: pestaña evaluación |
| `tabs/FinancieroTab.jsx` | — | Legajo: pestaña financiero |

### En `src/pages/` (legacy — Fase 1, pre-feature-folders)

| Pantalla | Rol principal |
|---|---|
| `Login.jsx` | Todos |
| `Registrarse.jsx` | Público |
| `ForgotPassword.jsx` | Todos |
| `VerificacionEmail.jsx` | Todos |
| `CargaInicial.jsx` | Nuevo usuario |
| `NoAutorizado.jsx` | Sistema |
| `Placeholder.jsx` | Sistema |
| `Catalogo.jsx` | Público, Alumno |
| `DetalleCarrera.jsx` | Público, Alumno |
| `DetalleCurso.jsx` | Público, Alumno |
| `InscripcionCarrera.jsx` | Alumno |
| `InscripcionCurso.jsx` | Alumno |
| `AlumnoInicio.jsx` | Alumno |
| `AlumnoMisCarreras.jsx` | Alumno |
| `AlumnoMateriaDetalle.jsx` | Alumno |
| `AlumnoPagos.jsx` | Alumno |
| `AlumnoPagarOnline.jsx` | Alumno |
| `AlumnoPagarSeleccion.jsx` | Alumno |
| `AlumnoPagarTransferencia.jsx` | Alumno |
| `AlumnoPagoDetalle.jsx` | Alumno |
| `AlumnoPagoEstado.jsx` | Alumno |
| `AdministrativoFacturacion.jsx` | Administrativo |
| `AdministrativoSolicitudesAsistencia.jsx` | Administrativo |
| `DirectorAprobaciones.jsx` | Director |
| `DirectorAprobacionesConfigEvaluacion.jsx` | Director |
| `DirectorAprobacionesEgresos.jsx` | Director |
| `DirectorAprobacionesExcepciones.jsx` | Director |
| `DirectorEgresoDetalle.jsx` | Director |
| `DirectorEsquemasEvaluacion.jsx` | Director |
| `DirectorExcepcionDetalle.jsx` | Director |
| `DirectorFinanciero.jsx` | Director |
| `DirectorRevisarConfiguracion.jsx` | Director |
| `DetalleSolicitud.jsx` | Director |
| `DocenteConfiguracionEvaluacion.jsx` | Docente |
| `DocenteMisComisiones.jsx` | Docente |
| `DocenteTomarAsistencia.jsx` | Docente |
| `PreceptorMesaExamen.jsx` | Preceptor |

---

## Componentes compartidos (`src/components/`)

> Ejecutá `ls frontend/src/components/` y `ls frontend/src/components/ui/` para la lista actualizada.
> Los componentes de `ui/` son de shadcn — no editar a mano.

Componentes custom conocidos:
- `GlassCard` — card con glassmorphism (Vidrio Líquido 2.0)
- `Sidebar.jsx` — sidebar de navegación por rol
- `MobileMenu.jsx` — drawer mobile con los mismos ítems del sidebar

---

## Registro de actualizaciones

| Feature | Pantallas/componentes agregados | Fecha |
|---|---|---|
| (inicial — extraído de directorios) | ver tablas arriba | 2026-09-20 |
