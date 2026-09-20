# Backend Surface — Endpoints existentes

> Archivo acumulativo. El Líder lo actualiza al cerrar cada feature.
> Antes de planificar, grepeá los controllers reales para validar que sigue actualizado.
> **No confíes solo en este archivo** — siempre verificá con `grep -r @GetMapping backend/src/`.

---

## Cómo leer este archivo

Formato por endpoint:
```
MÉTODO /ruta — descripción breve
  Auth: rol requerido
  Response: campos principales
```

---

## `academicstructure` — Estructura académica

### Courses (`/courses`)
```
POST   /courses                     — crear curso
PUT    /courses/{id}                — editar curso
POST   /courses/{id}/activate       — activar
POST   /courses/{id}/deactivate     — desactivar
GET    /courses                     — listar
GET    /courses/{id}                — detalle
```

### CourseSubjects (`/course-subjects`)
```
POST   /courses/{id}/subjects       — agregar materia a curso
PUT    /course-subjects/{id}        — editar
GET    /courses/{id}/subjects       — listar materias de un curso
GET    /course-subjects/{id}        — detalle
POST   /course-subjects/{id}/activate
POST   /course-subjects/{id}/deactivate
```

### CourseSections (`/course-sections`)
```
POST   /course-subjects/{id}/sections    — crear comisión de materia de curso
PUT    /course-sections/{id}
POST   /course-sections/{id}/activate
POST   /course-sections/{id}/deactivate
GET    /course-subjects/{id}/sections
GET    /course-sections/{id}
POST   /course-sections/{id}/instructors — asignar docente
GET    /course-sections/{id}/instructors
```

### Programs (`/programs`)
```
POST   /programs
PUT    /programs/{id}
POST   /programs/{id}/activate
POST   /programs/{id}/deactivate
GET    /programs
GET    /programs/{id}
```

### ProgramSubjects (`/program-subjects`)
```
POST   /programs/{id}/subjects
PUT    /program-subjects/{id}
GET    /programs/{id}/subjects
GET    /program-subjects/{id}
POST   /program-subjects/{id}/activate
POST   /program-subjects/{id}/deactivate
POST   /program-subjects/{id}/prerequisites  — agregar correlativa
GET    /program-subjects/{id}/prerequisites
```

### SubjectSections — comisiones de carrera (`/program-sections`)
```
POST   /subjects/{id}/sections
PUT    /program-sections/{id}
POST   /program-sections/{id}/activate
POST   /program-sections/{id}/deactivate
GET    /subjects/{id}/sections
GET    /program-sections/{id}
POST   /program-sections/{id}/instructors
GET    /program-sections/{id}/instructors
```

### Subjects (`/subjects`)
```
POST   /subjects
PUT    /subjects/{id}
POST   /subjects/{id}/activate
POST   /subjects/{id}/deactivate
GET    /subjects
GET    /subjects/{id}
```

### Terms (`/terms`) — períodos académicos
```
POST   /terms
PUT    /terms/{id}
POST   /terms/{id}/activate
POST   /terms/{id}/deactivate
GET    /terms
GET    /terms/{id}
POST   /terms/{id}/enrollment/open   — abrir inscripción
POST   /terms/{id}/enrollment/close  — (inferido)
POST   /terms/{id}/enrollment/clear  — (inferido)
```

### Misc (read-only, sin service)
```
GET    /holidays
GET    /institutions
GET    /instructors
GET    /elective-groups
GET    /programs/{id}/elective-groups
POST   /elective-groups
PUT    /elective-groups/{id}
GET    /elective-groups/{id}
POST   /programs/{id}/elective-groups
DELETE /programs/{id}/elective-groups/{groupId}
```

---

## `attendance` — Asistencia

```
POST   /program-sections/{id}/attendance     — cargar asistencia (Docente)
POST   /course-sections/{id}/attendance
POST   /attendance/{id}/justify              — justificar
POST   /attendance/{id}/approve-justification
POST   /attendance/{id}/reject-justification
GET    /section-enrollments/{id}/attendance
GET    /course-section-enrollments/{id}/attendance
GET    /section-enrollments/{id}/attendance/risk

POST   /attendance/{id}/change-requests
GET    /attendance/change-requests
GET    /me/attendance-change-requests
POST   /attendance/change-requests/{id}/approve
POST   /attendance/change-requests/{id}/reject
```

---

## `billing` — Facturación

```
POST   /billing-periods
GET    /billing-periods/current
POST   /collections-policy
GET    /collections-policy/current
GET    /financial-dashboard
GET    /financial-dashboard/debtors

GET    /course-enrollments/{id}/invoices
GET    /program-enrollments/{id}/invoices
POST   /invoices/{id}/adjustments
GET    /invoices/{id}/price-breakdown

GET    /me/credit-notes
POST   /me/invoices/{id}/mp-preference
POST   /me/invoices/{id}/payment-receipts
GET    /me/payment-receipts
GET    /me/invoices/{id}/price-breakdown
GET    /me/course-enrollments/{id}/invoices
```

---

## Otros dominios

> Ver controllers correspondientes para lista completa.
> Esta sección se actualiza al cerrar features de identity, enrollment, evaluation, graduation, notification.

---

## Registro de actualizaciones

| Feature | Endpoints agregados | Fecha |
|---|---|---|
| (inicial — extraído de controllers) | ver secciones arriba | 2026-09-20 |
