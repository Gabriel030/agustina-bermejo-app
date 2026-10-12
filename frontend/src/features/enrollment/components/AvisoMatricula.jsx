import { AlertTriangle, Clock } from 'lucide-react';
import { Button } from '@/components/ui/button';
import { formatMoney } from '@/lib/utils';

function vencimientoLegible(obligacion) {
  if (!obligacion?.dueDate) return '';
  const [anio, mes, dia] = String(obligacion.dueDate).split('-');
  return ` (vence el ${dia}/${mes}/${anio})`;
}

function periodoLegible(obligacion) {
  if (!obligacion?.slotCount || obligacion.slotCount < 2) return 'del año lectivo';
  return obligacion.slot === 1 ? 'del 1.er cuatrimestre' : 'del 2.º cuatrimestre';
}

function montoLegible(obligacion) {
  return obligacion?.remainingBalance ? ` de ${formatMoney(obligacion.remainingBalance)}` : '';
}

export function AvisoMatricula({ obligacion, onPagar }) {
  if (obligacion?.status === 'IN_REVIEW') {
    return (
      <div role="status" className="flex items-start gap-2 rounded-lg bg-warning-bg px-3 py-2 text-xs font-medium text-warning">
        <Clock className="mt-0.5 h-4 w-4 shrink-0" />
        <span>Tu comprobante de matrícula está en revisión. Cuando se acredite el pago vas a poder inscribirte.</span>
      </div>
    );
  }
  if (obligacion?.status === 'CANCELLED') {
    return (
      <div role="alert" className="flex items-start gap-2 rounded-lg bg-warning-bg px-3 py-2 text-xs font-medium text-warning">
        <AlertTriangle className="mt-0.5 h-4 w-4 shrink-0" />
        <span>La matrícula {periodoLegible(obligacion)} fue anulada. Consultá con la administración para poder inscribirte.</span>
      </div>
    );
  }
  return (
    <div role="alert" className="flex flex-col gap-2 rounded-lg bg-warning-bg px-3 py-2 text-xs font-medium text-warning">
      <div className="flex items-start gap-2">
        <AlertTriangle className="mt-0.5 h-4 w-4 shrink-0" />
        <span>
          {`Para inscribirte tenés que pagar la matrícula ${periodoLegible(obligacion)}${montoLegible(obligacion)}${vencimientoLegible(obligacion)}. Pagala y volvé a esta pantalla.`}
        </span>
      </div>
      {obligacion?.invoiceId && (
        <Button type="button" variant="gradient" size="sm" className="self-start" onClick={() => onPagar(obligacion.invoiceId)}>
          Pagar matrícula
        </Button>
      )}
    </div>
  );
}
