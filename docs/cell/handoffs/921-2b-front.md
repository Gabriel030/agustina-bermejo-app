# Handoff 921#2b — front pendiente

Verde (backend): casos de uso promote/prioritize/remove, WaitlistEntryActions (transacción), eventos y notificación, authorization-matrix, contrato OpenAPI, error-catalog, domainErrorMessages.

Falta (front, features/enrollment):
- Menú por fila, diálogo Promover (cupo antes → después), WaitlistActionDialog (motivo 10–500 con contador) para Mover/Quitar; Promover deshabilitado con tooltip si verdict.eligible=false; numeración entre prioritarios.
- Mover la entrada "Listas de espera" DENTRO de Inscripciones (ruta + sidebar).
- Tests: `requires reason before prioritizing`, `promote disabled when not eligible`, `menu entry lives under Enrollments`; vitest --maxWorkers=2 + build.
