---
proyecto: agustina-bermejo-app
tipo: spec técnica + UI
épica: 10
fase: 10.6
estado: listo para implementar
---

# Fase 10.6 — Frontend: Cohortes de ingreso

## Depende de

Fase 10.3 — las cohortes son una sección dentro del detalle de la carrera.

El backend (`ProgramCohort`, `ProgramYearBillingTemplate`, endpoints en `billing/`) ya existe. Esta fase es solo frontend.

---

## Contexto de negocio

Una **cohorte de ingreso** es el "contrato" que asocia a un grupo de nuevos alumnos con un período de ingreso y un template de facturación. El ciclo de vida es:

1. El ADMINISTRATIVO / DIRECTOR crea una cohorte para una carrera (ej: "Ingreso 1er semestre 2027").
2. La cohorte tiene una ventana de admisión (fechas de inscripción).
3. Cuando un alumno se inscribe, queda vinculado a esa cohorte → se activa la recurrencia de cobro según el template.
4. La recurrencia de cobro continúa mientras el alumno tenga materias pendientes y siga reinscribiéndose.

**No confundir cohorte con ciclo académico (Épica 4):** la cohorte define el ingreso y la facturación; el ciclo define qué comisiones se dictan en qué período.

---

## Pantalla: Sección "Cohortes" dentro del detalle de la carrera

Esta sección reemplaza el placeholder vacío del tab "Cohortes" en `CarreraDetalle.jsx`.

```
[Tab "Cohortes"]

  [Barra de acciones]
    Botón "Nueva cohorte" (gradient) — alineado a la derecha

  [Lista de cohortes — en orden cronológico inverso]
    Para cada cohorte:
    [GlassCard]
      Header:
        Título: "Ingreso [1er semestre | 2do semestre | Anual] [año]"
          Ej: "Ingreso 1er semestre 2027"
        StatusPill:
          "Admisión abierta" (verde) — si admissionEnd >= hoy
          "Admisión cerrada" (gris)  — si admissionEnd < hoy

      Cuerpo en 3 columnas:
        Col 1: "Período de admisión"
          [fecha inicio] → [fecha fin]
          Ej: "01/03/2027 → 31/03/2027"
        Col 2: "Inicio de cursada"
          Ej: "01/04/2027"
        Col 3: "Alumnos inscriptos"
          Número (si el backend lo devuelve, sino "—")

      Footer:
        Texto: "Template: [nombre del template]"

  [Estado vacío — sin cohortes]
    EmptyState: "Esta carrera no tiene cohortes de ingreso todavía."
    Subtexto: "Creá la primera cohorte para que los alumnos puedan inscribirse."
    Botón: "Crear cohorte" (gradient)
```

---

## Formulario de alta de cohorte

Se abre como un Dialog (modal) de tamaño mediano.

```
[Dialog]
  Título: "Nueva cohorte de ingreso"
  Subtítulo: "[Nombre de la carrera]"

  [Formulario]

    Período de ingreso *      | Select (radio buttons o segmented)
                              | Opciones:
                              |   "1er semestre"  → FIRST_SEMESTER
                              |   "2do semestre"  → SECOND_SEMESTER
                              |   "Anual"         → ANNUAL

    Período de admisión:
      Fecha de apertura *     | DatePicker
                              | Placeholder: "Desde"
      Fecha de cierre *       | DatePicker
                              | Placeholder: "Hasta"
                              | Validación: debe ser >= fecha de apertura
                              | Error: "El cierre debe ser posterior a la apertura"

    Inicio de cursada *       | DatePicker
                              | Placeholder: "Inicio de clases"
                              | Advertencia (no bloqueante) si es anterior a la fecha de cierre:
                              |   "La cursada empieza antes de que cierre la admisión"

    Template de facturación * | Select buscable
                              | Opciones: templates disponibles para esta carrera
                              | Si no hay templates: campo bloqueado + mensaje
                              |   "No hay templates de facturación para esta carrera.
                              |    Configurá uno en el módulo de facturación antes de
                              |    crear la cohorte."
                              |   → Botón "Ir a facturación" (link)

  [Botones]
    "Cancelar" (ghost) + "Crear cohorte" (gradient)
    "Crear cohorte" → deshabilitado si no hay template disponible

  [Estado del submit]
    Mientras carga: spinner en el botón, campos bloqueados
    Error: banner rojo dentro del Dialog con el mensaje del backend
    Éxito: Dialog se cierra, cohorte aparece en la lista, toast "Cohorte creada"
```

---

## Notas de implementación

### Endpoints a verificar antes de implementar

Antes de escribir los hooks, verificar en los controllers de `billing/` cuáles son los endpoints exactos:

```
GET  /billing/program-cohorts?programId={id}           — listar cohortes de una carrera
POST /billing/program-cohorts                          — crear cohorte
GET  /billing/program-year-billing-templates?programId={id} — listar templates disponibles
```

Si los endpoints tienen una ruta diferente, ajustar los hooks según lo que existe.

---

## Criterios de aceptación

- `admissionEnd` < `admissionStart` → error en el campo fecha de cierre, no se puede enviar.
- `cursadaStart` < `admissionEnd` → advertencia visible pero no bloqueante.
- No hay templates de facturación → botón "Crear cohorte" deshabilitado + mensaje explicativo.
- Creada exitosamente → la cohorte aparece al tope de la lista con StatusPill "Admisión abierta" (si las fechas son futuras).
- Una carrera puede tener múltiples cohortes del mismo ingreso en distintos años.

---

## Archivos a crear / modificar

```
frontend/src/features/gestion-academica/
  hooks/
    useCohorts.js
  components/
    CohortesSection.jsx    (sección del tab "Cohortes" en CarreraDetalle)
    NuevaCohortDialog.jsx  (Dialog de alta)
```

Modificar `CarreraDetalle.jsx` para que el tab "Cohortes" renderice `CohortesSection`.

---

## Verificación

1. Ir al detalle de "Lic. en Administración" → tab "Cohortes" → EmptyState.
2. Clic "Nueva cohorte" → Dialog abre.
3. Seleccionar "1er semestre", apertura 01/03/2027, cierre 31/03/2027, inicio 01/04/2027, template X.
4. "Crear cohorte" → Dialog cierra, la cohorte aparece en la lista con "Admisión abierta".
5. Crear segunda cohorte con cierre en el pasado → StatusPill "Admisión cerrada".
6. Intentar crear cohorte sin template disponible → botón deshabilitado + mensaje.
