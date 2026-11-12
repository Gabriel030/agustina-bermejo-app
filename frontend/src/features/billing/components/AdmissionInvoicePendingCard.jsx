import { useLocation, useNavigate } from 'react-router-dom';
import { paymentOriginState } from '@/hooks/usePaymentReturn';
import { GlassCard } from '@/components/GlassCard';
import { Skeleton } from '@/components/Skeleton';
import { StatusPill } from '@/components/StatusPill';
import { Button } from '@/components/ui/button';
import { formatFecha, formatMoney } from '@/lib/utils';
import { admissionPaymentPhase, daysUntilDate, isAdmissionInvoiceActionable } from '@/lib/admissionInvoice';
import { useAdmissionInvoicesPending } from '../hooks/useAdmissionInvoicesPending';

function textoDeVencimiento(solicitud) {
  const { admissionInvoice, programName } = solicitud;
  const dias = daysUntilDate(admissionInvoice.dueDate);
  const monto = formatMoney(admissionInvoice.total);
  const fecha = formatFecha(admissionInvoice.dueDate);
  if (admissionPaymentPhase(solicitud) === 'OVERDUE') {
    return `${programName} · ${monto}. La matrícula venció el ${fecha}. Todavía podés pagarla para confirmar tu inscripción mientras la admisión siga abierta.`;
  }
  const quedan = dias == null ? '' : ` (te quedan ${Math.max(dias, 0)} día${dias === 1 ? '' : 's'})`;
  return `${programName} · ${monto}. Vencimiento: ${fecha}${quedan}.`;
}

function SolicitudPendiente({ solicitud, principal }) {
  const navigate = useNavigate();
  const { pathname } = useLocation();
  const originState = paymentOriginState(pathname, pathname === '/alumno/pagos' ? 'Volver a Mis pagos' : 'Volver al inicio');
  const fase = admissionPaymentPhase(solicitud);
  const { admissionInvoice } = solicitud;

  if (fase === 'UNDER_REVIEW') {
    return (
      <div className="flex flex-col gap-2 rounded-xl bg-warning-bg px-4 py-3">
        <StatusPill tone="warning">Pago en revisión</StatusPill>
        <div className="text-size-13 text-ink-soft">
          <span className="font-semibold text-ink">{solicitud.programName}.</span> Subiste el comprobante el{' '}
          {formatFecha(admissionInvoice.receiptUploadedAt)}; cuando lo aprobemos tu inscripción queda confirmada.
        </div>
        <Button
          variant="text"
          size="inline"
          type="button"
          onClick={() => navigate(`/alumno/pagos/${admissionInvoice.id}/transferencia`, { state: originState })}
          className="w-fit text-size-12-5 font-semibold"
          >
          Ver estado del pago
        </Button>
      </div>
    );
  }

  return (
    <div className={`flex flex-col gap-3 rounded-xl px-4 py-3 sm:flex-row sm:items-center sm:justify-between ${fase === 'OVERDUE' ? 'bg-warning-bg' : 'bg-neutral-bg'}`}>
      <div className="text-size-13 text-ink-soft">{textoDeVencimiento(solicitud)}</div>
      <Button
        variant={principal ? 'gradient' : 'outline'}
        onClick={() => navigate(`/alumno/pagos/${admissionInvoice.id}/seleccion`, { state: originState })}
      >
        Pagar matrícula
      </Button>
    </div>
  );
}

export function AdmissionInvoicePendingCard() {
  const { pendientes, isLoading, isError } = useAdmissionInvoicesPending();

  if (isLoading) {
    return <Skeleton className="h-28 w-full" />;
  }
  if (isError) {
    return (
      <GlassCard className="p-5 text-sm text-danger">
        No pudimos cargar tus matrículas de ingreso. Probá recargar la página.
      </GlassCard>
    );
  }
  if (pendientes.length === 0) {
    return null;
  }

  const primeraAccionable = pendientes.find(isAdmissionInvoiceActionable);
  const titulo = primeraAccionable
    ? 'Pagá la matrícula de ingreso para confirmar tu inscripción'
    : 'Matrícula de ingreso en revisión';

  return (
    <GlassCard className="flex flex-col gap-3 p-5">
      <div className="font-heading text-size-15 font-semibold text-ink">{titulo}</div>
      {pendientes.map((solicitud) => (
        <SolicitudPendiente key={solicitud.id} solicitud={solicitud} principal={solicitud.id === primeraAccionable?.id} />
      ))}
    </GlassCard>
  );
}
