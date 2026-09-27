---
proyecto: agustina-bermejo-app
tipo: spec técnica + UI
épica: 10
fase: 10.5
estado: listo para implementar
---

# Fase 10.5 — Frontend: Gestión de cursos

## Depende de

Fase 10.1 — `weeklyHours` en Subject, `visibleInCatalog` en Course.

Puede implementarse en paralelo con Fase 10.3. Al terminar: visibilidad en catálogo → Fase 10.4.

---

## Diferencias respecto a carreras

Los cursos son análogos a las carreras con estas diferencias:
- **Sin grupos optativos**: el temario solo tiene MANDATORY y ADDITIONAL_MANDATORY.
- **Sin cohortes de ingreso** (Épica 8 gestiona la facturación de cursos de otra forma).
- **Sin agrupamiento por año/cuatrimestre obligatorio**: las materias del temario pueden tener un año/cuatrimestre opcional o simplemente un orden.
- **Con correlativas**: `CourseSubjectPrerequisite` existe con los mismos tipos.

---

## Pantalla 1: Listado de cursos

**Ruta:** `/admin/gestion-academica` → tab "Cursos"

```
[Barra de acciones]
  Izquierda: Input búsqueda + Selector de estado
  Derecha:   Botón "Nuevo curso" (gradient)

[Tabla de cursos]
  Columnas:
    - Nombre             (ej: "Introducción a la Programación")
    - Precio             (ej: "$8.000")
    - Créditos requer.
    - Estado             (StatusPill)
    - En catálogo        (StatusPill)
    - Acciones           (✏️ → detalle)

  Cada fila clicable → CursoDetalle

[Estado vacío]
  "Todavía no hay cursos cargados."
  Botón: "Crear el primer curso"
```

---

## Pantalla 2: Wizard de alta de curso

**Ruta:** `/admin/gestion-academica/cursos/nuevo`
**Componente:** `CursoWizard.jsx`

### Paso 1 de 2 — Datos básicos

```
[Header] "Nuevo curso — Paso 1 de 2: Datos básicos"

Formulario en 2 columnas:
  Nombre *               | Input text
                         | Placeholder: "Ej: Introducción a la Programación"
  Precio mensual         | Input number (moneda)
  Monto de matrícula     | Input number (moneda)
  Créditos totales       | Input number
  Horas totales          | Input number

[Botones]
  "Cancelar" (ghost) + "Siguiente" (gradient)
```

### Paso 2 de 2 — Temario

```
[Header] "Nuevo curso — Paso 2 de 2: Temario"
Subtítulo: "[Nombre del curso]"

[Counters — 2 cards]
  "Créditos en el temario" N / X
  "Horas semanales"        N / X

[Temario]
  [Sección "Materias del curso"]
    Lista de materias MANDATORY (con orden numérico opcional)
    Cada fila: Número | Código | Nombre | Horas/sem | Créditos | [x]
    Link "+ Agregar materia"

  [Sección "Obligatorias adicionales"]
    Materias ADDITIONAL_MANDATORY
    Cada fila: Código | Nombre | Horas/sem | Créditos | [x]
    Link "+ Agregar obligatoria adicional"

[Botones]
  "← Atrás" + "Guardar curso" (gradient)
```

---

## Panel lateral: Agregar materia al temario

Mismo diseño que en la carrera pero sin la opción ELECTIVE y sin selección de grupo.

```
[PanelAgregarMateriaCurso]
  Tipo: Segmented pills
    "Materia del temario" (MANDATORY)
    "Obligatoria adicional" (ADDITIONAL_MANDATORY)

  Si MANDATORY:
    Número de orden     | Input number (opcional)
                        | Ayuda: "Para mostrar el temario en orden"

  Buscar materia *      | Autocomplete del banco global
  Créditos *            | Input number
  Recurrencia *         | Select: Cuatrimestral / Anual

  Botones: "Cancelar" + "Agregar materia" (gradient)
```

---

## Pantalla 3: Detalle del curso

**Ruta:** `/admin/gestion-academica/cursos/:id`
**Componente:** `CursoDetalle.jsx`

```
[Header del curso]
  Título: "Introducción a la Programación"
  Subtítulo: "$8.000/mes · 60 créditos"
  Badges: StatusPill estado + StatusPill catálogo
  Botones:
    "Editar datos" (outline)
    Toggle catálogo: "Publicar" / "Retirar" (mismo patrón que en carrera — Fase 10.4)
    "Dar de baja" / "Reactivar" (outline / outline-danger)

[Counters — 2 cards]
  Créditos en el temario N / X
  Horas semanales        N / X

[Temario — mismas secciones que el wizard Paso 2]
  Con botones de agregar, eliminar y ver correlativas en cada materia

[Correlativas — panel lateral]
  Mismo PanelCorrelativas que en la carrera
  Prereq seleccionable: solo otras materias del curso (no de otras carreras)
```

---

## Pantalla 4: Editar datos básicos del curso

**Ruta:** `/admin/gestion-academica/cursos/:id/editar`
Formulario de 1 paso, campos pre-rellenados. Mismo layout que el Paso 1 del wizard.

---

## Criterios de aceptación

- Crear curso con temario vacío → se puede guardar. Counters en 0.
- Agregar materia → contador de créditos y horas sube.
- Correlativa circular → banner rojo en el panel.
- Toggle catálogo → funciona igual que en carrera (Fase 10.4).

---

## Archivos a crear / modificar

```
frontend/src/features/gestion-academica/
  hooks/
    useCourses.js
  pages/
    CursoWizard.jsx
    CursoDetalle.jsx
    CursoEditarDatos.jsx
  components/
    PanelAgregarMateriaCurso.jsx  (variante sin grupos optativos)
```

Rutas en `AppRouter.jsx`:
```
/admin/gestion-academica/cursos/nuevo       → CursoWizard
/admin/gestion-academica/cursos/:id         → CursoDetalle
/admin/gestion-academica/cursos/:id/editar  → CursoEditarDatos
```
