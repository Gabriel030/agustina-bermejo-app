import { StatusPill } from '@/components/StatusPill';
import { Button } from '@/components/ui/button';
import { formatMoney } from '@/lib/utils';
import { formatFullDate, overdueLabel } from '@/features/graduation/certificate-exceptions/lib/certificateExceptions';

export function RequestDebtCell({ row }) {
  if (!row.hasDebt) return <span className="text-size-13 text-ink-faint">—</span>;
  return (
    <div className="flex flex-col gap-1">
      <StatusPill tone="danger">Con deuda</StatusPill>
      <span className="font-heading text-size-14 font-bold tabular-nums text-ink">{formatMoney(row.totalDue)}</span>
      {row.overdueInstallments != null && <span className="text-size-12 tabular-nums text-ink-soft">{overdueLabel(row.overdueInstallments)}</span>}
    </div>
  );
}

export function RequestExceptionCell({ row, canRequest, onRequest, onEmit, onShowReason }) {
  if (!row.hasDebt) return null;
  const name = row.studentName ?? 'el alumno';
  if (row.exceptionStatus === 'PENDING') return <StatusPill tone="warning">Excepción pendiente</StatusPill>;
  if (row.exceptionStatus === 'APPROVED') {
    return (
      <div className="flex flex-col items-start gap-1.5">
        <StatusPill tone="success">{`Vence ${formatFullDate(row.exceptionValidUntil)}`}</StatusPill>
        <Button size="sm" variant="primary" className="min-h-11 md:min-h-0" aria-label={`Emitir con excepción la constancia de ${name}`} onClick={() => onEmit(row)}>
          Emitir con excepción
        </Button>
      </div>
    );
  }
  if (row.exceptionStatus === 'REJECTED') {
    return (
      <div className="flex flex-col items-start gap-1">
        <StatusPill tone="danger">Excepción rechazada</StatusPill>
        <Button size="sm" variant="ghost" className="min-h-11 md:min-h-0" aria-label={`Ver motivo del rechazo de ${name}`} onClick={() => onShowReason(row)}>
          Ver motivo
        </Button>
      </div>
    );
  }
  if (!canRequest) return null;
  return (
    <Button size="sm" variant="outline" className="min-h-11 md:min-h-0" aria-label={`Pedir excepción para ${name}`} onClick={() => onRequest(row)}>
      Pedir excepción
    </Button>
  );
}
