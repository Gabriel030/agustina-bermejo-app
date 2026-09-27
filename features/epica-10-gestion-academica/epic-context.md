# Épica 10 — Gestión académica

> Contexto que el Líder lee antes de planificar cualquier feature de esta épica.
> Actualizá este archivo cuando descubrás dependencias o decisiones de diseño nuevas.

---

## Qué es esta épica

Cubre la gestión académica operativa del sistema: banco global de materias (Subject),
carreras (Program) con plan de estudios completo, cursos (Course) con temario,
cohortes de ingreso (ProgramCohort), y visibilidad en catálogo (visibleInCatalog).

**Roles**: DIRECTOR y ADMINISTRATIVO tienen acceso completo a todas las operaciones.

---

## Diagrama de dominio

```
Subject (banco global)
  ├── id, name, code, description, weeklyHours (nuevo en 10.1), maxFinalExamAttempts, status
  └── Se reutiliza en múltiples programas y cursos

Program (carrera)
  ├── id, name, totalYears, price, enrollmentFeeAmount, totalCreditsRequired
  ├── totalHoursRequired (nuevo en 10.1)
  ├── status: ACTIVE | INACTIVE
  └── visibleInCatalog: boolean (nuevo en 10.1) — oferta pública separada del status

ProgramSubject (vínculo materia ↔ carrera)
  ├── programId, subjectId
  ├── category: MANDATORY | ADDITIONAL_MANDATORY | ELECTIVE (nuevo en 10.1)
  ├── curricularYear (null si ADDITIONAL_MANDATORY o ELECTIVE)
  ├── curricularTerm: FIRST | SECOND | ANNUAL (null si ADDITIONAL_MANDATORY o ELECTIVE)
  ├── credits, sortOrder, status, subjectRecurrence
  └── electiveGroupId (obligatorio si ELECTIVE, null en los otros)

ProgramSubjectPrerequisite (correlativa)
  ├── programSubjectId → ProgramSubject (materia que tiene la correlativa)
  ├── prerequisiteProgramSubjectId → ProgramSubject (materia requerida)
  ├── requirementType: PASSED_COURSEWORK | APPROVED
  └── minGrade, notes

ElectiveGroup (banco global de grupos optativos)
  └── id, name, description

ProgramElectiveGroup (vínculo grupo ↔ carrera)
  ├── programId, electiveGroupId
  ├── minRequired (cuántas hay que aprobar en esta carrera)
  └── maxAllowed

Course (curso)
  ├── id, name, price, enrollmentFeeAmount, totalCreditsRequired, totalHoursRequired
  ├── status: ACTIVE | INACTIVE
  └── visibleInCatalog: boolean (nuevo en 10.1)

CourseSubject (vínculo materia ↔ curso)
  └── Análogo a ProgramSubject (solo MANDATORY y ADDITIONAL_MANDATORY)

ProgramCohort (cohorte de ingreso — entidad en billing/)
  ├── programId, intake: FIRST_SEMESTER | SECOND_SEMESTER | ANNUAL
  ├── admissionStart, admissionEnd (ventana de inscripción)
  ├── cursadaStart (inicio de cursada)
  └── templateId → ProgramYearBillingTemplate
```

---

## Features de la épica

| ID | Título | Estado | Depende de |
|---|---|---|---|
| 10.0 | Dashboard de gestión académica (Frontend) | pending | — (paralelo) |
| 10.1 | Backend: dominio y gaps | pending | — |
| 10.2 | Frontend: Banco de materias | pending | 10.1 |
| 10.3 | Frontend: Gestión de carreras | pending | 10.1 |
| 10.4 | Frontend: Oferta académica | pending | 10.1, 10.3, 10.5 |
| 10.5 | Frontend: Gestión de cursos | pending | 10.1 |
| 10.6 | Frontend: Cohortes de ingreso | pending | 10.3 |

**Orden:** 10.1 → [10.2, 10.3, 10.5] en paralelo → 10.4 → 10.6
(10.0 y 10.6 pueden ir en paralelo con 10.4)

---

## Endpoints base ya existentes que esta épica extiende

```
GET    /subjects, /subjects/{id}
POST   /subjects, PUT /subjects/{id}
POST   /subjects/{id}/activate, /subjects/{id}/deactivate

GET    /programs, /programs/{id}
POST   /programs, PUT /programs/{id}
POST   /programs/{id}/activate, /programs/{id}/deactivate
GET    /programs/{id}/subjects
POST   /programs/{id}/subjects
PUT    /program-subjects/{id}
GET    /program-subjects/{id}/prerequisites
POST   /program-subjects/{id}/prerequisites
GET    /programs/{id}/elective-groups
POST   /programs/{id}/elective-groups
DELETE /programs/{id}/elective-groups/{groupId}
GET    /elective-groups

GET    /courses, /courses/{id}
POST   /courses, PUT /courses/{id}
POST   /courses/{id}/activate, /courses/{id}/deactivate
GET    /courses/{id}/subjects
POST   /courses/{id}/subjects
```

**Nuevos en 10.1:**
```
PUT    /programs/{id}/catalog-visibility   — Auth: DIRECTOR | ADMINISTRATIVO
PUT    /courses/{id}/catalog-visibility    — Auth: DIRECTOR | ADMINISTRATIVO
```

**Billing (ya existen, verificar ruta exacta antes de implementar 10.6):**
```
GET    /billing/program-cohorts?programId={id}
POST   /billing/program-cohorts
GET    /billing/program-year-billing-templates?programId={id}
```

---

## Decisiones de diseño acordadas

1. **ProgramSubjectCategory**: MANDATORY / ADDITIONAL_MANDATORY / ELECTIVE con enum. Invariantes: MANDATORY → curricularYear+Term requeridos, electiveGroupId null; ADDITIONAL_MANDATORY → año/cuatrimestre null; ELECTIVE → electiveGroupId requerido.

2. **weeklyHours en Subject**: propiedad global de la materia, no del vínculo con la carrera.

3. **visibleInCatalog separado de status**: una carrera puede estar ACTIVE y no estar en el catálogo, o estar INACTIVE y seguir publicada (son dimensiones independientes).

4. **minRequired/maxAllowed en ProgramElectiveGroup**: por carrera, no globales. Ya existen en la entidad.

5. **RequirementType ya implementado**: PASSED_COURSEWORK (para cursar) | APPROVED (para rendir final). No requiere cambio de backend.

6. **Versionado del plan de estudios**: fuera de alcance de Épica 10. Cambios aplican hacia adelante.

7. **Wizard de alta en 2 pasos**: Paso 1 no hace request al backend; Paso 2 hace POST /programs seguido de los POST de materias y grupos en secuencia.

---

## Notas del Líder

- [2026-09-20]: Épica iniciada. Specs tomadas de Obsidian, todas marcadas "listo para implementar".
- Primera feature a implementar: 10.1 (backend puro, sin fase UX).
- Después de 10.1, implementar 10.2 o 10.3 en paralelo (cada una puede ejecutarse en una sesión independiente del harness).
