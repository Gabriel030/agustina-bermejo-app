# E28-F S2b-P backend (backend-4): alcance del preceptor por comisión

## Criterios
- [x] Migración V494 `preceptor_assignment`: comisión de carrera (`subject_section_id`), comisión de curso (`course_section_id`) o cohorte (`program_cohort_id`), CHECK de un solo destino, únicos parciales por persona, `created_by`, `created_at`
- [x] Permiso nuevo `attendance.read_all_change_requests` (patrón V330) para ver la cola completa; Administrativo por `role_permission`, Subdirector por herencia, Director por `allCodes` y `legacyRoles` en el controller
- [x] `PreceptorScopeFinder` en `identity.api` (clase concreta: la regla ArchUnit exige que una interfaz `*Port` en `api` la implemente otro módulo)
- [x] Cola de pendientes, historial de resueltas y aprobar/rechazar/revertir filtran por asignación (comisión o cohorte del alumno); fuera de alcance responde 404
- [x] Test por rol: preceptor sin asignación ve cola e historial vacíos y no puede decidir ni revertir ajenas
- [x] Sabotaje: anular el filtro del repositorio rompe 4 de 7 tests de alcance
- [ ] Merge: solo después de que entren V486 a V493 (E34); revalidar con `scripts/next-migration.sh --check <n>`

## Notas
- Reusado de #1636 por cherry-pick: filtro `findInScope`, resolver de alcance, tests y matriz. Reescrito: modelo por comisión/cohorte, `findByIdInScope` (sin recorrer entidades de otros módulos).
- Fuera de alcance: administración de asignaciones, obligatoriedad al crear cohorte/curso (R1), checklist «Abrir» (R3).
