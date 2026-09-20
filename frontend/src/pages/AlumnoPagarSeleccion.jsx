import { useNavigate, useParams } from 'react-router-dom';
import { Banknote, CreditCard, ChevronLeft } from 'lucide-react';

const MP_BLUE = '#009ee3';
import { GlassCard } from '@/components/GlassCard';
import { PageContainer } from '@/components/PageContainer';
import { usePaymentConfig } from '@/hooks/useBilling';

export function AlumnoPagarSeleccion() {
  const { invoiceId } = useParams();
  const navigate = useNavigate();
  const { data: config } = usePaymentConfig();

  const tieneTransferencia = config?.bankCbu || config?.bankAlias;
  const tieneMp = config?.mpEnabled;

  return (
    <PageContainer className="max-w-lg">
      <button
        type="button"
        onClick={() => navigate('/alumno/pagos')}
        className="flex items-center gap-1.5 text-sm text-ink-faint hover:text-ink"
      >
        <ChevronLeft size={16} />
        Volver a Mis pagos
      </button>

      <h1 className="text-xl font-bold">¿Cómo querés pagar?</h1>

      <div className="flex flex-col gap-4">
        {tieneTransferencia && (
          <button
            type="button"
            onClick={() => navigate(`/alumno/pagos/${invoiceId}/transferencia`)}
            className="text-left"
          >
            <GlassCard className="flex items-start gap-4 p-5 transition-shadow hover:shadow-md">
              <div className="mt-0.5 flex h-10 w-10 shrink-0 items-center justify-center rounded-xl bg-gradient-to-br from-brand to-accent-2 text-white">
                <Banknote size={20} />
              </div>
              <div>
                <p className="font-semibold">Transferencia bancaria</p>
                <p className="mt-0.5 text-sm text-ink-faint">
                  Transferí el importe a nuestra cuenta y subí tu comprobante para confirmación.
                </p>
              </div>
            </GlassCard>
          </button>
        )}

        {tieneMp && (
          <button
            type="button"
            onClick={() => navigate(`/alumno/pagos/${invoiceId}/online`)}
            className="text-left"
          >
            <GlassCard className="flex items-start gap-4 p-5 transition-shadow hover:shadow-md">
              <div className="mt-0.5 flex h-10 w-10 shrink-0 items-center justify-center rounded-xl text-white" style={{ backgroundColor: MP_BLUE }}>
                <CreditCard size={20} />
              </div>
              <div>
                <p className="font-semibold">Pagar con tarjeta</p>
                <p className="mt-0.5 text-sm text-ink-faint">
                  Pagá con tarjeta de crédito o débito a través de Mercado Pago. Podés elegir cuotas.
                </p>
              </div>
            </GlassCard>
          </button>
        )}

        {!tieneTransferencia && !tieneMp && (
          <GlassCard className="p-6 text-center text-sm text-ink-faint">
            No hay métodos de pago disponibles por el momento. Contactá a administración.
          </GlassCard>
        )}
      </div>
    </PageContainer>
  );
}
