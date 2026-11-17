# Handoff 921#2b — front pendiente

Verde (backend): casos de uso promote/prioritize/remove, WaitlistEntryActions (transacción), eventos y notificación, authorization-matrix, contrato OpenAPI, error-catalog, domainErrorMessages.

Falta (front, features/enrollment):
- Menú por fila, diálogo Promover (cupo antes → después), WaitlistActionDialog (motivo 10–500 con contador) para Mover/Quitar; Promover deshabilitado con tooltip si verdict.eligible=false; numeración entre prioritarios.
- Mocks MSW de las 3 acciones (POST /waitlists/{kind}/entries/{enrollmentId}/promote|prioritize|remove).
- Mover la entrada "Listas de espera" DENTRO de Inscripciones (ruta + sidebar).
- Tests: `requires reason before prioritizing`, `promote disabled when not eligible`, `menu entry lives under Enrollments`; vitest --maxWorkers=2 + build.
Backend pendiente: test `concurrentPromotionsNeverExceedCapacity` (WaitlistActionsIT es @Transactional; hace falta uno con commits y hilos) y la variante IT de removalBlockedWhilePendingReceipt con comprobante real (hoy es unitario en WaitlistEntryActionsTest).
