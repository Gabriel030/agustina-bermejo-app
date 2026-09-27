---
proyecto: agustina-bermejo-app
tipo: spec técnica + UI
épica: 10
fase: 10.2
estado: listo para implementar
---

# Fase 10.2 — Frontend: Banco de materias

## Depende de

Fase 10.1 — necesita `weeklyHours` en `SubjectResponse`.

Al terminar: la asignación de materias a carreras/cursos vive en las Fases 10.3 y 10.5.

---

## Navegación y acceso

La funcionalidad de Gestión académica se accede desde la barra lateral de ADMINISTRATIVO y DIRECTOR. El ítem del sidebar es **"Gestión académica"** y lleva a `/admin/gestion-academica`. Esta pantalla principal tiene 4 pestañas (TabsList `variant="line"`):

1. **Banco de materias** (esta fase)
2. **Carreras** (Fase 10.3)
3. **Cursos** (Fase 10.5)
4. **Oferta académica** (Fase 10.4)

---

## Pantalla 1: Banco de materias (listado)

**Ruta:** `/admin/gestion-academica` → tab activo "Banco de materias"
**Componente:** `AdministrativoGestionAcademica.jsx` (página principal con tabs)

### Layout

```
[PageContainer]
  [Header]
    Título: "Gestión académica"
    Subtítulo: "Banco de materias, carreras, cursos y oferta"
  [TabsList variant="line"]
    Banco de materias | Carreras | Cursos | Oferta académica
  [Contenido del tab activo]
```

### Contenido del tab "Banco de materias"

```
[Barra de acciones]
  Izquierda: Input de búsqueda (placeholder: "Buscar por nombre o código...")
             + Selector de estado: [Todos | Activos | Inactivos] (segmented pills o select)
  Derecha:   Botón "Nueva materia" (variant="gradient")

[Tabla / lista de materias]
  Columnas:
    - Código       (4 chars, ej: "CALC")
    - Nombre       (ej: "Cálculo I")
    - Horas/sem.   (número, ej: "4")
    - Intentos f.  (límite de intentos de examen final; "—" si no tiene)
    - Estado       (StatusPill: "Activo" verde / "Inactivo" gris)
    - Acciones     (iconos: ✏️ Editar, ⊘ Dar de baja / ✓ Reactivar)

  Cada fila es clicable en nombre/código → navega a edición
  Ordenamiento default: alfabético por nombre

[Estado vacío — sin materias]
  EmptyState: "Todavía no hay materias en el banco."
  Botón: "Agregar la primera materia" → abre formulario de creación

[Estado vacío — sin resultados para la búsqueda]
  EmptyState: "No encontramos materias con ese nombre o código."
```

---

## Pantalla 2: Formulario de alta / edición de materia

**Ruta alta:** `/admin/gestion-academica/materias/nueva`
**Ruta edición:** `/admin/gestion-academica/materias/:id`
**Componente:** `MateriaForm.jsx`

### Layout

```
[PageContainer]
  [Breadcrumb]
    ← Banco de materias
  [Header]
    Título: "Nueva materia" (alta) / "Editar materia" (edición)
  [GlassCard]
    [Formulario — 2 columnas en desktop, 1 en mobile]
      Fila 1:
        - Código *         | Input text, maxLength=4, uppercase automático
                           | Placeholder: "CALC"
                           | En edición: Input readOnly (no editable)
                           | Error: "El código ya existe" / "Requerido, máximo 4 caracteres"
        - Nombre *         | Input text
                           | Placeholder: "Ej: Cálculo I"
                           | Error: "El nombre ya existe" / "Requerido"
      Fila 2:
        - Descripción      | Textarea, 3 líneas
                           | Placeholder: "Descripción opcional de la materia"
      Fila 3:
        - Horas semanales  | Input number, min=1, max=40
                           | Placeholder: "Ej: 4"
        - Límite de        | Input number, min=1, max=20
          intentos (final) | Placeholder: "Sin límite si se deja vacío"

    [Botones — alineados a la derecha]
      "Cancelar" (ghost) → vuelve al listado sin guardar
      "Guardar materia" (gradient) → POST /subjects o PUT /subjects/{id}

  [Sección "Peligro" — solo en edición, debajo del formulario]
    Si está activa:
      "Dar de baja" (button outline danger) → abre Dialog de confirmación
        Dialog: "¿Dar de baja 'Nombre de la materia'?
                 Los alumnos que ya la tienen en su historial no se ven afectados.
                 Podés reactivarla en cualquier momento."
        Botones: "Cancelar" + "Dar de baja" (danger)
    Si está inactiva:
      "Reactivar materia" (button outline secondary)
        Sin confirmación — es reversible.
```

### Estados de carga y error

- Mientras carga los datos para edición: Skeleton del formulario
- Submit en progreso: botón "Guardar materia" muestra spinner, deshabilitado
- Error 409 (código o nombre duplicado): se marca el campo específico con mensaje
- Error genérico: toast de error global

---

## Criterios de aceptación

- **Crear** con código "TEST" → aparece en el listado con estado ACTIVE.
- **Crear** con el mismo código → campo Código en rojo, mensaje "El código ya existe".
- **Editar** → el campo Código aparece como `Input readOnly`, no como texto plano.
- **Buscar** por "calc" → solo aparecen materias con "calc" en nombre o código (case-insensitive).
- **Filtrar** por "Inactivos" → solo aparecen las dadas de baja.
- **Dar de baja** → StatusPill cambia a "Inactivo". En el Dialog se confirma la acción.
- **Reactivar** → StatusPill cambia a "Activo". No requiere confirmación.

---

## Archivos a crear / modificar

```
frontend/src/features/gestion-academica/
  hooks/
    useSubjects.js
  pages/
    AdministrativoGestionAcademica.jsx   (página principal con 4 tabs)
    MateriaForm.jsx
```

Agregar rutas en `AppRouter.jsx`:
```
/admin/gestion-academica              → AdministrativoGestionAcademica (tab=materias por default)
/admin/gestion-academica/materias/nueva → MateriaForm modo alta
/admin/gestion-academica/materias/:id   → MateriaForm modo edición
```

Agregar ítem en el sidebar del rol ADMINISTRATIVO/DIRECTOR.

---

## Verificación

1. Navegar a `/admin/gestion-academica` → tab "Banco de materias" activo, tabla vacía con EmptyState.
2. Clic "Nueva materia" → formulario de alta, campos vacíos.
3. Completar: código "CALC", nombre "Cálculo I", horas: 4, intentos: 3. Guardar → aparece en tabla con estado Activo.
4. Intentar crear otra con código "CALC" → error en el campo Código.
5. Clic en "Cálculo I" → formulario de edición, código en read-only.
6. Cambiar nombre a "Cálculo Diferencial". Guardar → tabla actualizada.
7. Filtrar por "Inactivos" → tabla vacía (ninguna dada de baja todavía).
8. Dar de baja "Cálculo Diferencial" → confirmación, luego desaparece del filtro "Activos".
9. Cambiar filtro a "Inactivos" → aparece la materia dada de baja. Reactivar → vuelve a Activos.
