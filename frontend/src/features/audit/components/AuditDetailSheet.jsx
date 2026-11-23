import { Sheet, SheetContent, SheetDescription, SheetHeader, SheetTitle } from '@/components/ui/sheet';
import { StatusPill } from '@/components/StatusPill';
import { auditActionLabel, auditActionTone, auditEntityLabel, auditRoleLabel, auditRoleTone } from '@/features/audit/lib/auditLabels';
import { formatDateTime } from '@/lib/billing';
import { auditActorName, diffAuditValues, formatAuditValue, MASK } from '@/features/audit/lib/auditPayload';
import { AuditChangeSummary } from '@/features/audit/components/AuditChangeSummary';

function ValueBlock({ title, value }) {
  return (
    <div className="flex min-w-0 flex-col gap-1.5">
      <h3 className="text-size-12 font-semibold uppercase tracking-wide text-ink-soft">{title}</h3>
      <pre className="min-h-16 overflow-auto whitespace-pre-wrap break-words rounded-control border border-border bg-neutral-bg p-2.5 text-size-12 tabular-nums">
        {value.text ?? <span className="text-ink-faint">Sin datos</span>}
      </pre>
    </div>
  );
}

export function AuditDetailSheet({ event, onClose }) {
  const before = formatAuditValue(event?.beforeValue);
  const after = formatAuditValue(event?.afterValue);
  const changes = event ? diffAuditValues(event.beforeValue, event.afterValue) : null;

  return (
    <Sheet open={Boolean(event)} onOpenChange={(open) => !open && onClose()}>
      <SheetContent side="right" className="w-full overflow-y-auto sm:max-w-xl">
        {event && (
          <>
            <SheetHeader>
              <StatusPill tone={auditActionTone(event.action)}>{auditActionLabel(event.action)}</StatusPill>
              <SheetTitle>
                {auditEntityLabel(event.entityType)} #{event.entityId}
              </SheetTitle>
              <SheetDescription className="sr-only">Detalle del evento de auditoría</SheetDescription>
            </SheetHeader>
            <div className="flex flex-col gap-4 px-4 pb-6">
              <dl className="grid grid-cols-[7rem_1fr] gap-x-3 gap-y-2 text-sm">
                <dt className="text-ink-faint">Fecha y hora</dt>
                <dd className="tabular-nums">{formatDateTime(event.occurredAt)} h</dd>
                <dt className="text-ink-faint">Actor</dt>
                <dd>{auditActorName(event)}</dd>
                <dt className="text-ink-faint">Rol</dt>
                <dd>
                  <StatusPill tone={auditRoleTone(event.actorRole)}>{auditRoleLabel(event.actorRole)}</StatusPill>
                </dd>
                <dt className="text-ink-faint">Motivo</dt>
                <dd>{event.reason || <span className="text-ink-faint">Sin motivo registrado</span>}</dd>
              </dl>
              {changes && <AuditChangeSummary changes={changes} />}
              <div className="grid gap-3 md:grid-cols-2">
                <ValueBlock title="Antes" value={before} />
                <ValueBlock title="Después" value={after} />
              </div>
              {(before.plain || after.plain) && <p className="text-size-12 text-ink-faint">Un valor no es JSON válido: se muestra como texto.</p>}
              {(before.masked || after.masked) && (
                <p className="text-size-12 text-ink-faint">{MASK} Contraseñas, tokens y hashes se ocultan antes de mostrarse.</p>
              )}
            </div>
          </>
        )}
      </SheetContent>
    </Sheet>
  );
}
