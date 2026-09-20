---
proyecto: agustina-bermejo-app
tipo: spec técnica + UI
épica: 10
fase: 10.4
estado: listo para implementar
---

# Fase 10.4 — Frontend: Oferta académica (publicación)

## Depende de

- Fase 10.1 — necesita `visibleInCatalog` en Program y Course + endpoints `PUT /programs/{id}/catalog-visibility` y `PUT /courses/{id}/catalog-visibility`.
- Fase 10.3 — el toggle de catálogo vive también en el detalle de carrera.
- Fase 10.5 — ídem para cursos.

---

## Conceptos clave

**Catálogo maestro vs. oferta académica:**
- Una carrera o curso puede existir en el sistema (ACTIVE) pero no estar visible en el catálogo para nuevos inscriptos.
- `visibleInCatalog = true` → aparece en el autoservicio de alta (Épica 7) y los alumnos pueden solicitar inscripción.
- `visibleInCatalog = false` → existe pero no acepta nuevos inscriptos. Los alumnos actuales no se ven afectados.
- El estado ACTIVE/INACTIVE es independiente de la visibilidad en catálogo.

**Roles:** DIRECTOR y ADMINISTRATIVO pueden publicar y retirar del catálogo.

---

## Pantalla 1: Vista "Oferta académica" (tab)

**Ruta:** `/admin/gestion-academica` → tab "Oferta académica"

```
[Contenido del tab]
  [Sub-tabs] ← TabsList variant="line"
    "Carreras publicadas" | "Cursos publicados"

  [Sub-tab "Carreras publicadas"]
    [Tabla de carreras con visibleInCatalog=true]
      Columnas:
        - Nombre
        - Duración
        - Precio mensual
        - Estado (StatusPill ACTIVE/INACTIVE)
        - En catálogo  (siempre "Publicada" en esta vista)
        - Acciones:
            "Retirar del catálogo" (button size=sm, variant=outline-danger)
              → PUT /programs/{id}/catalog-visibility { "visible": false }
              → Sin Dialog de confirmación (es reversible)
              → La fila desaparece de este listado

    [Estado vacío]
      "No hay carreras publicadas en el catálogo."
      Botón: "Ir al listado de carreras para publicar" → va al tab "Carreras"

  [Sub-tab "Cursos publicados"]
    Estructura idéntica para cursos.
```

---

## Control inline en CarreraDetalle / CursoDetalle

El toggle de visibilidad también vive en el header del detalle de cada carrera/curso (implementado en las Fases 10.3 y 10.5):

```
[Header del detalle]
  Si visibleInCatalog = false:
    StatusPill gris: "No publicada"
    Botón "Publicar en catálogo" (variant=outline, size=sm)
      → PUT /programs/{id}/catalog-visibility { "visible": true }
      → StatusPill cambia a verde "En catálogo"
      → Botón cambia a "Retirar del catálogo"
      → Toast: "La carrera ahora aparece en el catálogo de inscripciones"

  Si visibleInCatalog = true:
    StatusPill verde: "En catálogo"
    Botón "Retirar del catálogo" (variant=outline-danger, size=sm)
      → PUT /programs/{id}/catalog-visibility { "visible": false }
      → StatusPill cambia a gris
      → Toast: "La carrera fue retirada del catálogo. Los alumnos actuales no se ven afectados."
```

---

## Criterios de aceptación

- `visibleInCatalog = false` → la carrera NO aparece en el autoservicio del alumno.
- Carrera INACTIVE puede publicarse igual (son dimensiones independientes).
- Al retirar del catálogo: el toggle cambia en tiempo real sin reload. Toast informativo.
- Alumno inscripto no pierde su cursada cuando la carrera se retira del catálogo.

---

## Archivos a crear / modificar

```
frontend/src/features/gestion-academica/
  hooks/
    usePrograms.js   (agregar useSetProgramCatalogVisibility)
    useCourses.js    (agregar useSetCourseCatalogVisibility)
  pages/
    AdministrativoGestionAcademica.jsx   (agregar sub-tab "Oferta académica")
```

El control de visibilidad en el header del detalle se implementa directamente en `CarreraDetalle.jsx` y `CursoDetalle.jsx`.

---

## Verificación

1. Publicar la carrera "Lic. en Administración" desde su detalle → badge "En catálogo".
2. Ir al tab "Oferta académica" → la carrera aparece en "Carreras publicadas".
3. "Retirar del catálogo" desde la vista de oferta → la carrera desaparece del listado.
4. Verificar en el autoservicio del alumno (Épica 7) que la carrera ya no aparece en el catálogo.
5. Volver al detalle de la carrera → badge "No publicada", botón "Publicar en catálogo" disponible.
