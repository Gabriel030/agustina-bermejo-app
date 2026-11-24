import { Link } from 'react-router-dom';
import { Button } from '@/components/ui/button';
import { formatMoney } from '@/lib/utils';
import {
  formatFullDate,
  organismLabelOf,
  overdueLabel,
} from '@/features/graduation/certificate-exceptions/lib/certificateExceptions';

export const DIRECTOR_EXCEPTIONS_PATH = '/director/aprobaciones/excepciones-por-reclamo';

export function PendingExceptionRow({ exception, onApprove, onReject }) {
  return (
    <div className="flex flex-col gap-3 rounded-2xl px-4 py-3 transition-colors hover:bg-neutral-bg/60 md:flex-row md:items-center md:justify-between">
      <div className="flex min-w-0 flex-col gap-0.5">
        <Link
          to={`${DIRECTOR_EXCEPTIONS_PATH}/${exception.id}`}
          className="font-heading text-size-14 font-semibold text-ink hover:underline"
        >
          {exception.studentName}
        </Link>
        <span className="text-size-12-5 text-ink-soft">{exception.programName}</span>
        <span className="text-size-12-5 text-ink">
          {organismLabelOf(exception.organismKind)} · {exception.organismReference}
        </span>
        <span className="text-size-12-5 tabular-nums text-ink">
          <b className="font-heading font-bold">{formatMoney(exception.debtTotalAtRequest)}</b>
          {exception.overdueInstallmentsAtRequest != null && <> · {overdueLabel(exception.overdueInstallmentsAtRequest)}</>}
        </span>
        <span className="text-size-12 text-ink-faint">
          Pidió {exception.requesterName} · {formatFullDate(exception.requestedAt)}
        </span>
      </div>
      <div className="flex shrink-0 gap-2">
        <Button
          type="button"
          variant="primary"
          size="sm"
          className="min-h-11 md:min-h-0"
          aria-label={`Aprobar excepción de ${exception.studentName}`}
          onClick={() => onApprove(exception)}
        >
          Aprobar
        </Button>
        <Button
          type="button"
          variant="outline"
          size="sm"
          className="min-h-11 md:min-h-0"
          aria-label={`Rechazar excepción de ${exception.studentName}`}
          onClick={() => onReject(exception)}
        >
          Rechazar
        </Button>
      </div>
    </div>
  );
}
