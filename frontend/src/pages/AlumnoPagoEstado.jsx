import { useNavigate, useSearchParams } from 'react-router-dom';
import { CheckCircle, XCircle, Clock, ArrowLeft } from 'lucide-react';
import { GlassCard } from '@/components/GlassCard';
import { PageContainer } from '@/components/PageContainer';
import { Button } from '@/components/ui/button';

const STATUS_CONFIG = {
  success: {
    icon: CheckCircle,
    title: 'Pago realizado',
    message: 'Tu pago fue procesado correctamente por Mercado Pago. El estado de tu factura se actualizará en breve.',
    colorClass: 'text-success',
  },
  failure: {
    icon: XCircle,
    title: 'Pago fallido',
    message: 'El pago no pudo completarse. Podés intentarlo nuevamente o usar otro método de pago.',
    colorClass: 'text-danger',
  },
  pending: {
    icon: Clock,
    title: 'Pago pendiente',
    message: 'Tu pago está siendo procesado por Mercado Pago. Te notificaremos cuando se confirme.',
    colorClass: 'text-warning',
  },
};

export function AlumnoPagoEstado() {
  const navigate = useNavigate();
  const [params] = useSearchParams();
  const status = params.get('status') ?? 'pending';
  const invoiceId = params.get('invoiceId');

  const config = STATUS_CONFIG[status] ?? STATUS_CONFIG.pending;
  const Icon = config.icon;

  return (
    <PageContainer className="max-w-lg">
      <GlassCard className="flex flex-col items-center gap-6 p-10 text-center">
        <Icon size={56} className={config.colorClass} />
        <div>
          <p className="text-xl font-bold">{config.title}</p>
          <p className="mt-2 text-sm text-ink-faint">{config.message}</p>
        </div>

        <div className="flex flex-col gap-3 w-full">
          <Button variant="gradient" onClick={() => navigate('/alumno/pagos')} className="flex items-center gap-2">
            <ArrowLeft size={16} />
            Volver a Mis pagos
          </Button>

          {status === 'failure' && invoiceId && (
            <button
              type="button"
              onClick={() => navigate(`/alumno/pagos/${invoiceId}/seleccion`)}
              className="rounded-xl border border-border px-6 py-3 text-sm font-semibold text-ink transition-colors hover:border-brand hover:text-brand"
            >
              Intentar con otro método de pago
            </button>
          )}
        </div>
      </GlassCard>
    </PageContainer>
  );
}
