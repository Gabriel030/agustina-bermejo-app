import { useParams } from 'react-router-dom';
import { Banknote, CreditCard, ChevronLeft } from 'lucide-react';
import { GlassCard } from '@/components/GlassCard';
import { PageContainer } from '@/components/PageContainer';
import { Skeleton } from '@/components/Skeleton';
import { EmptyState } from '@/components/EmptyState';
import { StatusPill } from '@/components/StatusPill';
import { cn } from '@/lib/utils';
import { usePaymentReturn } from '@/hooks/usePaymentReturn';
import { InvoiceSummary } from '@/components/alumno/InvoiceSummary';
import { usePaymentConfig, usePriceBreakdown } from '@/hooks/useBilling';

const PAYABLE_STATUSES = ['PENDING', 'OVERDUE'];

function CardPaymentOption({ enabled, onSelect }) {
  const content = (
    <GlassCard interactive={enabled} className={cn('flex items-start gap-4 p-5', enabled ? 'hover:shadow-md' : 'cursor-default')}>
      <div
        className={cn(
          'mt-0.5 flex h-10 w-10 shrink-0 items-center justify-center rounded-xl',
          enabled ? 'bg-gradient-to-br from-brand to-accent-2 text-white' : 'bg-neutral-bg text-ink-faint',
        )}
      >
        <CreditCard size={20} />
      </div>
      <div className="flex flex-col gap-1">
        <div className="flex flex-wrap items-center gap-2">
          <p className={cn('font-semibold', !enabled && 'text-ink-soft')}>Pagar con tarjeta</p>
          {!enabled && <StatusPill tone="neutral">Próximamente</StatusPill>}
        </div>
        <p className="text-sm text-ink-faint">
          {enabled
            ? 'Pagá con tarjeta de crédito o débito a través de Mercado Pago. Podés elegir cuotas.'
            : 'El pago con tarjeta de crédito o débito todavía no está disponible. Por ahora podés pagar por transferencia bancaria y subir tu comprobante.'}
        </p>
      </div>
    </GlassCard>
  );

  if (!enabled) {
    return <div aria-disabled="true">{content}</div>;
  }
  return (
    <button type="button" onClick={onSelect} className="text-left">
      {content}
    </button>
  );
}

export function AlumnoPagarSeleccion() {
  const { invoiceId } = useParams();
  const { originLabel, goToOrigin, goTo } = usePaymentReturn();
  const { data: config, isLoading: loadingConfig } = usePaymentConfig();
  const { data: breakdown, isLoading: loadingBreakdown } = usePriceBreakdown(invoiceId);
  const payable = !breakdown || PAYABLE_STATUSES.includes(breakdown.status);

  const tieneTransferencia = Boolean(config?.bankCbu || config?.bankAlias);
  const tieneMp = config?.mpEnabled === true;

  return (
    <PageContainer className="max-w-lg">
      <button
        type="button"
        onClick={goToOrigin}
        className="flex items-center gap-1.5 text-sm text-ink-faint hover:text-ink"
      >
        <ChevronLeft size={16} />
        {originLabel}
      </button>

      <h1 className="text-xl font-bold">¿Cómo querés pagar?</h1>

      {loadingBreakdown && <Skeleton className="h-32" />}
      {breakdown && <InvoiceSummary breakdown={breakdown} />}

      {!payable && (
        <GlassCard className="p-6 text-center text-sm text-ink-soft">
          Esta cuota ya está saldada. No tenés que pagar nada más.
        </GlassCard>
      )}

      {payable && loadingConfig && <Skeleton className="h-24" />}

      {payable && !loadingConfig && (
        <div className="flex flex-col gap-4">
          {tieneTransferencia && (
            <button
              type="button"
              onClick={() => goTo(`/alumno/pagos/${invoiceId}/transferencia`)}
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

          {!tieneTransferencia && (
            <EmptyState
              title="No hay medios de pago disponibles"
              description="Todavía no están configurados los datos para pagar esta cuota. Contactate con administración para coordinar el pago."
            />
          )}

          <CardPaymentOption
            enabled={tieneMp}
            onSelect={() => goTo(`/alumno/pagos/${invoiceId}/online`)}
          />
        </div>
      )}
    </PageContainer>
  );
}
