import { Link } from 'react-router-dom';
import { CalendarClock, Download, TriangleAlert } from 'lucide-react';
import { Skeleton } from '@/components/Skeleton';
import { Button } from '@/components/ui/button';
import { useMyModules } from '@/hooks/useMyModules';
import { useProcedureEligibility } from '../hooks/useStudentProcedures';
import { isInformativeReason, reasonActionOf } from '../lib/procedures';

function ReasonLine({ reason, carreraId, billingEnabled, onShowApproved }) {
  const informative = isInformativeReason(reason);
  const Icon = informative ? CalendarClock : TriangleAlert;
  const action = reasonActionOf(reason, { carreraId, billingEnabled });
  return (
    <span className={`flex flex-wrap items-center gap-1.5 text-sm ${informative ? 'text-ink-soft' : 'font-semibold text-warning'}`}>
      <Icon aria-hidden="true" className="size-3.5 shrink-0" />
      <span>{reason.message}</span>
      {action?.to && (
        <Link to={action.to} className="font-semibold text-brand hover:underline">
          {action.label}
        </Link>
      )}
      {action?.section && (
        <Button variant="text" size="inline" className="font-semibold" onClick={() => onShowApproved()}>
          {action.label}
        </Button>
      )}
    </span>
  );
}

export function ProcedureRow({ procedure, carreraId, onOpen, onShowApproved }) {
  const { billingEnabled } = useMyModules();
  const { data, isLoading, isError, refetch } = useProcedureEligibility(procedure.type, { enrollmentId: carreraId });
  const Icon = procedure.icon;
  const eligible = data?.eligible === true;
  const reasons = data?.reasons ?? [];

  if (isLoading) {
    return (
      <li>
        <Skeleton className="h-[68px]" />
      </li>
    );
  }

  return (
    <li className="flex flex-wrap items-center gap-3 px-4 py-3.5">
      <span
        className={`grid size-9 shrink-0 place-items-center rounded-xl ${eligible ? 'bg-accent-soft text-brand' : 'bg-neutral-bg text-ink-faint'}`}
      >
        <Icon aria-hidden="true" className="size-[18px]" />
      </span>
      <div className="flex min-w-[200px] flex-1 flex-col gap-0.5">
        <span className="font-semibold text-ink">{procedure.title}</span>
        <span className="text-sm text-ink-soft">{eligible && data.summary ? data.summary : procedure.description}</span>
        {isError && (
          <span className="flex flex-wrap items-center gap-1.5 text-sm font-semibold text-warning">
            <TriangleAlert aria-hidden="true" className="size-3.5" />
            No pudimos revisar si podés generarla.
            <Button variant="text" size="inline" className="font-semibold" onClick={() => refetch()}>
              Reintentar
            </Button>
          </span>
        )}
        {data &&
          !eligible &&
          reasons.map((reason) => (
            <ReasonLine
              key={reason.code}
              reason={reason}
              carreraId={carreraId}
              billingEnabled={billingEnabled}
              onShowApproved={onShowApproved}
            />
          ))}
      </div>
      {eligible && (
        <Button variant="primary" className="w-full sm:w-auto" onClick={() => onOpen(procedure.type)}>
          <Download aria-hidden="true" />
          Generar
        </Button>
      )}
    </li>
  );
}
