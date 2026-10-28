import { CheckCircle2, Clock } from 'lucide-react';
import { Skeleton } from '@/components/Skeleton';
import { formatMoney } from '@/lib/billing';
import { refundMethodLabel } from './refundRequests';

function PreviewPart({ part }) {
  const destination = [part.destinationAccount, part.destinationHolder].filter(Boolean).join(' · ');
  return (
    <li className="flex items-start justify-between gap-4 rounded-2xl px-4 py-3 transition-colors hover:bg-neutral-bg/60">
      <div className="flex min-w-0 flex-col">
        <span className="text-sm font-medium text-ink">
          Pago #{part.paymentId} · {refundMethodLabel(part.methodType)}
        </span>
        <span className="break-words text-xs text-ink-faint">
          {part.destinationComplete ? destination : 'Falta completar el destino'}
        </span>
      </div>
      <span className="shrink-0 text-sm font-semibold tabular-nums text-ink">{formatMoney(part.amount)}</span>
    </li>
  );
}

function Approver({ isDirector }) {
  const Icon = isDirector ? CheckCircle2 : Clock;
  return (
    <div className="flex items-start gap-3 rounded-2xl bg-neutral-bg px-4 py-3">
      <Icon className={isDirector ? 'mt-0.5 size-4 text-success' : 'mt-0.5 size-4 text-warning'} aria-hidden="true" />
      <div className="flex flex-col text-sm">
        <span className="font-semibold text-ink">{isDirector ? 'Se aprueba ahora' : 'Lo aprueba la dirección'}</span>
        <span className="text-xs text-ink-soft">
          {isDirector
            ? 'Lo pedís como director, no necesita otra aprobación.'
            : 'Queda pendiente de autorización del director y la plata se reserva desde ahora.'}
        </span>
      </div>
    </div>
  );
}

export function RefundSplitPreview({ hasValidAmount, preview, isLoading, isError, isDirector, amountMessage }) {
  const parts = hasValidAmount ? (preview?.parts ?? []) : [];

  let body;
  if (!hasValidAmount) {
    body = <p className="px-1 text-sm text-ink-faint">Ingresá un monto válido para ver cómo se reparte.</p>;
  } else if (isError) {
    body = <p role="alert" className="px-1 text-sm text-danger">No pudimos calcular el reparto. Probá de nuevo.</p>;
  } else if (isLoading && !preview) {
    body = <Skeleton className="h-14 w-full" />;
  } else if (amountMessage) {
    body = <p className="px-1 text-sm text-ink-faint">Ajustá el monto para ver cómo se reparte.</p>;
  } else if (parts.length === 0) {
    body = <p className="px-1 text-sm text-ink-faint">No hay pagos para repartir este monto.</p>;
  } else {
    body = (
      <ul className="flex flex-col gap-1" aria-label="Reparto por pago">
        {parts.map((part) => (
          <PreviewPart key={part.paymentId} part={part} />
        ))}
      </ul>
    );
  }

  return (
    <div className="flex flex-col gap-3">
      <h2 className="font-heading text-base font-semibold text-ink">Cómo se reparte</h2>
      <p className="px-1 text-xs text-ink-faint">Se devuelve primero lo pagado más recientemente, siempre por el mismo método del pago.</p>
      {body}
      <Approver isDirector={isDirector} />
    </div>
  );
}
