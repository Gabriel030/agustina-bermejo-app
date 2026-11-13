import { useEffect, useRef } from 'react';
import { Navigate, useParams } from 'react-router-dom';
import { usePaymentReturn } from '@/hooks/usePaymentReturn';
import { toast } from 'sonner';
import { ChevronLeft, CreditCard, Loader2 } from 'lucide-react';
import { GlassCard } from '@/components/GlassCard';
import { PageContainer } from '@/components/PageContainer';
import { Button } from '@/components/ui/button';
import { Skeleton } from '@/components/Skeleton';
import { LoadErrorState } from '@/components/LoadErrorState';
import { useCrearPreferenciaMp, usePaymentConfig } from '@/hooks/useBilling';

export function AlumnoPagarOnline() {
  const { invoiceId } = useParams();
  const { data: config, isLoading, isError, error, refetch } = usePaymentConfig();
  const notified = useRef(false);
  const gatewayDisabled = !isLoading && !isError && config?.mpEnabled !== true;

  useEffect(() => {
    if (gatewayDisabled && !notified.current) {
      notified.current = true;
      toast.info('El pago con tarjeta todavía no está disponible. Elegí otro medio de pago.');
    }
  }, [gatewayDisabled]);

  if (isLoading) {
    return (
      <PageContainer className="max-w-lg">
        <Skeleton className="h-40" />
      </PageContainer>
    );
  }
  if (isError) {
    return (
      <PageContainer className="max-w-lg">
        <LoadErrorState
          error={error}
          resource="los medios de pago"
          onRetry={refetch}
          backTo={`/alumno/pagos/${invoiceId}/seleccion`}
          backLabel="la selección de pago"
        />
      </PageContainer>
    );
  }
  if (gatewayDisabled) {
    return <Navigate to={`/alumno/pagos/${invoiceId}/seleccion`} replace />;
  }
  return <OnlinePaymentRedirect invoiceId={invoiceId} />;
}

function OnlinePaymentRedirect({ invoiceId }) {
  const { goTo } = usePaymentReturn();
  const { mutate, isPending, error, data } = useCrearPreferenciaMp();
  const initiatedRef = useRef(false);

  useEffect(() => {
    if (!initiatedRef.current) {
      initiatedRef.current = true;
      mutate(invoiceId);
    }
  }, [invoiceId]);

  useEffect(() => {
    if (data?.initPoint) {
      window.location.href = data.initPoint;
    }
  }, [data]);

  return (
    <PageContainer className="max-w-lg">
      <Button
        variant="subtle"
        size="inline"
        type="button"
        onClick={() => goTo(`/alumno/pagos/${invoiceId}/seleccion`)}
        className="flex items-center gap-1.5 text-sm"
        >
        <ChevronLeft size={16} />
        Volver
      </Button>

      <h1 className="text-xl font-bold">Pagar con tarjeta</h1>

      <GlassCard className="flex flex-col items-center gap-6 p-10 text-center">
        {isPending && (
          <>
            <Loader2 size={36} className="animate-spin text-brand" />
            <div>
              <p className="font-semibold">Preparando tu pago…</p>
              <p className="mt-1 text-sm text-ink-faint">Serás redirigido a Mercado Pago en un momento.</p>
            </div>
          </>
        )}

        {!isPending && data?.initPoint && (
          <>
            <CreditCard size={36} className="text-brand" />
            <div>
              <p className="font-semibold">Tu sesión de pago está lista</p>
              <p className="mt-1 text-sm text-ink-faint">Si no fuiste redirigido automáticamente, hacé clic abajo.</p>
            </div>
            <a
              href={data.initPoint}
              className="rounded-xl bg-gradient-to-br from-brand to-accent-2 px-6 py-3 font-semibold text-on-accent shadow-sm transition-opacity hover:opacity-90"
            >
              Ir a Mercado Pago
            </a>
          </>
        )}

        {error && (
          <>
            <p className="font-semibold text-danger">No pudimos iniciar el pago</p>
            <p className="text-sm text-ink-faint">{error.message}</p>
            <Button variant="gradient" onClick={() => mutate(invoiceId)}>
              Reintentar
            </Button>
          </>
        )}
      </GlassCard>
    </PageContainer>
  );
}
