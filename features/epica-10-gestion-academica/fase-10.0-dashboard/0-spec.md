---
proyecto: agustina-bermejo-app
tipo: spec técnica + UI
épica: 10
fase: 10.0
estado: listo para implementar
---

# Fase 10.0 — Frontend: Dashboard de gestión académica

## Depende de

Puede implementarse antes o en paralelo con las demás fases. No bloquea nada, pero es más útil cuando hay datos reales (Fases 10.1–10.6).

---

## Concepto

La sección "Gestión académica" tiene una **página de inicio propia** antes de los 4 tabs funcionales. Es el dashboard que ve el DIRECTOR o ADMINISTRATIVO cuando entra a la sección. Responde la pregunta: *¿qué está pasando hoy con la oferta académica?*

---

## Pantalla: Landing de gestión académica

**Ruta:** `/admin/gestion-academica` (ruta base, antes de los tabs)

```
[PageContainer]
  [Header]
    Título: "Gestión académica"

  [Strip de KPIs — fila de 4 StatCard]
    Card 1: "Carreras activas"      | Número grande | ícono BookOpen
    Card 2: "Cursos activos"        | Número grande | ícono GraduationCap
    Card 3: "Materias en el banco"  | Número grande | ícono Layers
    Card 4: "Cohortes abiertas"     | Número grande | ícono Calendar
                                    | (admissionEnd >= hoy)
                                    | Si > 0: número en accent color, ícono de urgencia

  [Dos columnas en desktop, apiladas en mobile]

  [Columna izquierda — 55%: Próximos vencimientos y eventos]
    Título: "Próximos eventos"
    Lista cronológica de eventos importantes (próximas 8 semanas):
      Cada ítem:
        [Fecha]  [Ícono tipo]  [Descripción]
        Tipos:
          📅 Cierre de admisión  → "Cierre admisión Cohorte 1er sem. 2027 — Lic. en Administración"
          🎓 Inicio de cursada   → "Inicio de cursada Cohorte 1er sem. 2027 — Lic. en Administración"
          📢 Cohorte nueva        → aparece cuando se crea una nueva cohorte con admisionStart próxima
        Ordenado por fecha ASC
        Máximo 8 ítems; link "Ver todo" si hay más
        Ítem de cohorte clicable → va al detalle de la carrera, tab Cohortes

    [Si no hay eventos en las próximas 8 semanas]
      "No hay eventos programados en los próximos 2 meses."

  [Columna derecha — 45%: Accesos rápidos]
    Título: "Acciones frecuentes"
    Lista de botones de acción rápida:
      "Nueva materia"      (outline) → /admin/gestion-academica/materias/nueva
      "Nueva carrera"      (outline) → /admin/gestion-academica/carreras/nueva
      "Nuevo curso"        (outline) → /admin/gestion-academica/cursos/nuevo
      "Nueva cohorte"      (gradient) → abre selector de carrera y luego el Dialog de cohorte

    Título: "Carreras publicadas recientemente"
    Lista de las últimas 3 carreras con visibleInCatalog=true (más recientes primero):
      Cada fila: Nombre de la carrera → link al detalle
    Link "Ver todas las carreras publicadas" → tab Oferta académica

  [Banner de alertas — solo si hay situaciones que requieren atención]
    (aparece entre el strip de KPIs y las columnas si hay alertas)
    Ejemplos de alertas:
      - Carrera publicada sin ninguna cohorte activa: "Lic. en Administración está publicada pero no tiene cohortes de ingreso abiertas."
        → Botón "Agregar cohorte"
      - Carrera con plan de estudios vacío y publicada: aviso similar
```

---

## Tabs funcionales (debajo del dashboard)

Los 4 tabs ("Banco de materias", "Carreras", "Cursos", "Oferta académica") siguen disponibles debajo. El dashboard es la vista default cuando se entra a `/admin/gestion-academica`; los tabs son el acceso a la gestión detallada.

---

## Criterios de aceptación

- Los KPIs se calculan desde el backend (o desde cache de React Query).
- Los eventos próximos se generan a partir de las fechas de `admissionEnd` y `cursadaStart` de las cohortes activas.
- La alerta de "carrera publicada sin cohortes" aparece si `visibleInCatalog=true` y no hay cohortes con `admissionEnd >= hoy`.
- En mobile los 4 KPIs se apilan en 2×2.

---

## Archivos a crear / modificar

```
frontend/src/features/gestion-academica/
  pages/
    GestionAcademicaDashboard.jsx
  hooks/
    useGestionAcademicaSummary.js  (agrega calls a los endpoints ya existentes y los combina)
```

`AdministrativoGestionAcademica.jsx` muestra el dashboard encima de los tabs, o el dashboard reemplaza el contenido del tab default y los 4 tabs son el menú.
