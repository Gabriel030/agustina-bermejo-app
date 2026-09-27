---
proyecto: agustina-bermejo-app
tipo: spec técnica + UI
épica: 10
fase: 10.3
estado: listo para implementar
---

# Fase 10.3 — Frontend: Gestión de carreras

## Depende de

Fase 10.1 — necesita `ProgramSubjectCategory`, `weeklyHours` en Subject, `totalHoursRequired` en Program.

Al terminar: visibilidad en catálogo → Fase 10.4. Cohortes → Fase 10.6.

---

## Pantalla 1: Listado de carreras

**Ruta:** `/admin/gestion-academica` → tab "Carreras"
Parte de `AdministrativoGestionAcademica.jsx`

### Contenido del tab "Carreras"

```
[Barra de acciones]
  Izquierda: Input búsqueda (placeholder: "Buscar carrera...")
             + Selector de estado: [Todas | Activas | Inactivas]
  Derecha:   Botón "Nueva carrera" (variant="gradient")

[Tabla de carreras]
  Columnas:
    - Nombre            (ej: "Lic. en Administración")
    - Duración          (ej: "4 años")
    - Precio mensual    (ej: "$15.000")
    - Créditos requer.  (ej: "240 cr.")
    - Estado            (StatusPill: "Activa" / "Inactiva")
    - En catálogo       (StatusPill: "Publicada" verde / "No publicada" gris)
    - Acciones          (ícono ✏️ → va al detalle/edición)

  Cada fila completa es clicable → navega a CarreraDetalle

[Estado vacío]
  "Todavía no hay carreras cargadas."
  Botón: "Crear la primera carrera"
```

---

## Pantalla 2: Wizard de alta de carrera

**Ruta:** `/admin/gestion-academica/carreras/nueva`
**Componente:** `CarreraWizard.jsx`
Dos pasos, barra de progreso en el encabezado.

---

### Paso 1 de 2 — Datos básicos

```
[PageContainer]
  [Breadcrumb] ← Carreras
  [Header]
    Título: "Nueva carrera — Paso 1 de 2: Datos básicos"
  [GlassCard]
    Formulario en 2 columnas:

    Columna izquierda:
      Nombre *            | Input text
                          | Placeholder: "Ej: Licenciatura en Administración"
                          | Error: "Requerido" / "Ya existe una carrera con ese nombre"

      Duración (años) *   | Input number, min=1, max=10
                          | Placeholder: "Ej: 4"

      Créditos totales    | Input number, min=0
      requeridos          | Placeholder: "Ej: 240"
                          | Ayuda: "Total de créditos que el alumno debe completar para recibirse"

    Columna derecha:
      Precio mensual      | Input number (moneda)
                          | Placeholder: "Ej: 15000"
                          | Ayuda: "Precio de la cuota mensual base"

      Monto de matrícula  | Input number (moneda)
                          | Placeholder: "Ej: 5000"

      Horas totales       | Input number, min=0
      requeridas          | Placeholder: "Ej: 2400"
                          | Ayuda: "Total de horas semanales acumuladas del plan"

    [Botones — derecha]
      "Cancelar" (ghost) → vuelve al listado
      "Siguiente" (gradient) → guarda el Step 1 en estado local y avanza al Paso 2
```

El Paso 1 NO hace ningún request al backend todavía. Los datos se guardan en el estado del wizard.

---

### Paso 2 de 2 — Plan de estudios

```
[Header]
  Título: "Nueva carrera — Paso 2 de 2: Plan de estudios"
  Subtítulo: "[Nombre de la carrera ingresada en el Paso 1]"

[Counters — fila de 2 cards pequeñas]
  Card 1: "Créditos en el plan"
    Número grande: "0 / 240" (créditos acumulados / créditos requeridos)
    Barra de progreso verde
  Card 2: "Horas semanales"
    Número grande: "0 / 2400"
    Barra de progreso azul

[Nav horizontal (anchor nav)] — aparece apenas se definen los años en el Paso 1
  Pills scrolleables: "Año 1" | "Año 2" | ... | "Adicionales" | (grupos, cuando se agreguen)

[Indicador lateral izquierdo] — aparece junto al contenido del plan
  Misma lista de secciones, el activo resaltado en accent.

[Plan de estudios — todo visible, sin acordeón]

  Para cada año de 1 a N (según duración de la carrera):
    [Sección "Año X" con badge y anchor]
      Para cada cuatrimestre: 1er cuatrimestre / 2do cuatrimestre / Anual:
        Sub-encabezado del cuatrimestre
        Tabla: COD | Nombre | Horas/sem | Créditos | [x]
      Link "+ Agregar materia obligatoria" → PanelAgregarMateria

  [Sección "Obligatorias adicionales" con badge y anchor]
    Tabla: COD | Nombre | Horas/sem | Créditos | [x]
    Link "+ Agregar obligatoria adicional"

  [Para cada grupo optativo — sección con badge "Optativas" y anchor]
    Sub-header: "Aprobar N de M materias · X créditos"
    Tabla: COD | Nombre | Horas/sem | [x]
    Link "+ Agregar materia al grupo"
    Botón "Eliminar grupo" — bloqueado si tiene materias

  Link "+ Agregar grupo optativo" → PanelAgregarGrupoOptativo

[Botones — derecha]
  "← Atrás" (ghost) → vuelve al Paso 1 (datos guardados)
  "Guardar carrera" (gradient) → hace POST /programs con los datos del Paso 1,
                                  luego POST /programs/{id}/subjects para cada materia
                                  luego POST /programs/{id}/elective-groups para cada grupo
                                  Redirige al detalle de la carrera creada.

[Estado vacío — sin ninguna materia]
  Banner info: "El plan de estudios está vacío. Podés guardar la carrera y agregar materias después desde el detalle."
```

---

## Panel lateral: Agregar materia al plan

Se abre como un sheet deslizable por la derecha (no modal) cuando el usuario hace clic en cualquier "+ Agregar materia".

```
[PanelAgregarMateria]
  Título: "Agregar materia al plan"

  [Tipo de materia]
    Segmented pills o Select:
      "Obligatoria (con año y cuatrimestre)"  → category=MANDATORY
      "Obligatoria adicional"                 → category=ADDITIONAL_MANDATORY
      "Optativa (grupo)"                      → category=ELECTIVE
    Default: el tipo del contexto donde se hizo clic

  [Campos condicionales según tipo]

    Si MANDATORY:
      Año curricular *      | Select: "Año 1" / "Año 2" / ... / "Año N"
                            | Pre-seleccionado si se hizo clic desde una sección de año específica
      Cuatrimestre *        | Select: "1er cuatrimestre" / "2do cuatrimestre" / "Anual"
                            | Pre-seleccionado si aplica

    Si ADDITIONAL_MANDATORY:
      (sin campos adicionales de ubicación)

    Si ELECTIVE:
      Grupo optativo *      | Select de grupos ya asignados a esta carrera
                            | Pre-seleccionado si se hizo clic desde un grupo específico

  [Buscar materia del banco]
    Input buscador *        | "Buscar por nombre o código..."
                            | Muestra resultados en dropdown en tiempo real
                            | Solo muestra materias ACTIVE del banco
                            | Al seleccionar: muestra nombre, código y horas/sem de la materia elegida

  [Datos del vínculo]
    Créditos *              | Input number, min=0
                            | Placeholder: "Ej: 6"
                            | Ayuda: "Créditos de esta materia en esta carrera"
    Recurrencia *           | Select: "Cuatrimestral" / "Anual"
    Orden de aparición      | Input number
                            | Placeholder: "Opcional — se usa para ordenar el plan"

  [Botones]
    "Cancelar" (ghost) → cierra el panel
    "Agregar materia" (gradient) → hace POST /programs/{id}/subjects
                                    El contador de créditos y horas se actualiza
                                    El panel se cierra
                                    La materia aparece en la sección correspondiente

  [Error]
    Si la materia ya está en el plan: "Esta materia ya está en el plan de estudios"
```

---

## Panel lateral: Agregar grupo optativo

```
[PanelAgregarGrupoOptativo]
  Título: "Agregar grupo optativo"

  [Seleccionar grupo del banco]
    Select buscable:
      Opción 1: grupos existentes del banco global
      Opción 2: "+ Crear nuevo grupo" → muestra inline:
        Nombre del grupo *   | Input text
        Descripción          | Input text

  [Reglas del grupo en esta carrera]
    Mínimo a aprobar *   | Input number, min=1
                         | Placeholder: "Ej: 2"
                         | Ayuda: "El alumno debe aprobar al menos N materias de este grupo"
    Máximo permitido *   | Input number, min=1
                         | Validación: máximo ≥ mínimo

  [Botones]
    "Cancelar" + "Agregar grupo" (gradient)
```

---

## Pantalla 3: Detalle de carrera

**Ruta:** `/admin/gestion-academica/carreras/:id`
**Componente:** `CarreraDetalle.jsx`

```
[PageContainer]
  [Breadcrumb] ← Carreras

  [Header de la carrera]
    Título: "Lic. en Administración"
    Subtítulo: "4 años · $15.000/mes · 240 créditos"
    Fila de badges: StatusPill estado (Activa/Inactiva) + StatusPill catálogo (En catálogo/No publicada)
    Botones alineados a la derecha:
      "Editar datos" (button outline) → /admin/gestion-academica/carreras/:id/editar
      "Dar de baja" (button outline danger) — si está ACTIVE
      "Reactivar" (button outline) — si está INACTIVE

  [Counters — fila de 2 GlassCard]
    Card 1: "Créditos en el plan"
      N / 240 cr. (progreso)
    Card 2: "Horas semanales"
      N / 2400 hs.

  [Tabs internos del detalle]  ← TabsList variant="line"
    "Plan de estudios" | "Grupos optativos" | "Cohortes" (vacío hasta Fase 10.6)

  [Tab "Plan de estudios"]

    [Nav horizontal (anchor nav) — sticky bajo el header de la carrera]
      Pills / chips scrolleables que llevan a las secciones del plan
      El chip activo se resalta (accent-soft bg). Al hacer clic, la página hace scroll suave.

    [Indicador lateral izquierdo — sticky mientras se scrollea el plan]
      Lista vertical de labels correspondientes a las secciones del nav superior.
      El label de la sección actualmente visible se resalta con accent.
      Al hacer clic → scroll suave a esa sección.
      En mobile: el indicador lateral se oculta, solo queda el nav horizontal.

    [Contenido del plan]
      Para cada año:
        [Sección "Año X" — anchor]
          Badge: "Año X"
          Por cuatrimestre: tablas de materias MANDATORY
            COD | Nombre | Horas/sem | Créd. | Correlativas | Acciones (⬜ correlativas, ✕ quitar)

      [Sección "Obligatorias adicionales"]
        Tabla de materias ADDITIONAL_MANDATORY

      Para cada grupo optativo:
        [Sección "Nombre del grupo"]
          Sub-header: "Aprobar N de M materias · X créditos"
          Tabla de materias ELECTIVE del grupo
          Link "+ Agregar materia al grupo"

      En cada sección: Link "+ Agregar materia"

    Clic en ⬜ (correlativas) → abre PanelCorrelativas

  [Tab "Grupos optativos"]
    Lista de grupos asignados con: nombre, minRequired/maxAllowed, cantidad de materias
    Botón "Agregar grupo optativo" → mismo PanelAgregarGrupoOptativo que el wizard
```

---

## Panel lateral: Gestión de correlativas

```
[PanelCorrelativas]
  Título: "Correlativas de '[Nombre de la materia]'"

  [Lista de correlativas existentes]
    Para cada correlativa:
      "[Nombre del prerrequisito] ([Código])"
      Tipo: "Para cursar" / "Para rendir examen final"
      Nota mínima: "6.0" (si aplica) / "—"
      [x] eliminar

  [Agregar nueva correlativa]
    Materia prerrequisito *  | Select buscable de OTRAS materias del plan de esta carrera
    Tipo de correlativa *    | Select:
                             |   "Para cursar la materia" (PASSED_COURSEWORK)
                             |   "Para rendir el examen final" (APPROVED)
    Nota mínima              | Input number, min=0, max=10
                             | Placeholder: "Opcional"
    Botón "Agregar" (gradient small)

  [Error de correlativa circular]
    Banner rojo: "No se puede agregar esta correlativa porque crearía un ciclo (A→B→A)."
```

---

## Pantalla 4: Editar datos básicos de la carrera

**Ruta:** `/admin/gestion-academica/carreras/:id/editar`
Formulario de 1 paso, campos idénticos al Paso 1 del wizard, pre-rellenados.

---

## Criterios de aceptación

- Carrera creada con plan de estudios vacío → se puede guardar, detalle muestra counters en 0.
- Al agregar materia con `category=ELECTIVE` sin seleccionar grupo → botón "Agregar materia" deshabilitado con mensaje de validación.
- Al agregar materia ya existente en el plan → banner error dentro del panel.
- Correlativa circular → banner rojo dentro del panel, no se agrega.
- Eliminar grupo con materias → acción bloqueada con tooltip.
- Counter de créditos se actualiza en tiempo real al agregar/eliminar materias.

---

## Archivos a crear / modificar

```
frontend/src/features/gestion-academica/
  hooks/
    usePrograms.js
  pages/
    CarreraWizard.jsx
    CarreraDetalle.jsx
    CarreraEditarDatos.jsx
  components/
    PlanEstudiosPanel.jsx    (bloque de secciones del plan, reutilizable en wizard y detalle)
    PanelAgregarMateria.jsx  (sheet lateral)
    PanelAgregarGrupo.jsx    (sheet lateral)
    PanelCorrelativas.jsx    (sheet lateral)
    PlanCounters.jsx         (cards de créditos y horas)
```

Rutas en `AppRouter.jsx`:
```
/admin/gestion-academica/carreras/nueva      → CarreraWizard
/admin/gestion-academica/carreras/:id        → CarreraDetalle
/admin/gestion-academica/carreras/:id/editar → CarreraEditarDatos
```
