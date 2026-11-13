import { Button } from '@/components/ui/button';
import { usePaymentConfig } from '@/hooks/useBilling';

export function BankAccountDetails() {
  const { data: config, isLoading, isError, refetch } = usePaymentConfig();
  if (isLoading) return null;

  if (isError) {
    return (
      <div role="alert" className="flex flex-wrap items-center justify-between gap-2 rounded-xl bg-neutral-bg px-4 py-3 text-xs text-ink-soft">
        <span>No pudimos cargar los datos de la cuenta.</span>
        <Button variant="outline" size="sm" onClick={() => refetch()}>Reintentar</Button>
      </div>
    );
  }

  const fields = [
    { label: 'Titular', value: config?.accountHolder },
    { label: 'CBU', value: config?.bankCbu },
    { label: 'Alias', value: config?.bankAlias },
  ].filter((field) => field.value);

  if (fields.length === 0) {
    return (
      <div className="rounded-xl bg-neutral-bg px-4 py-3 text-xs text-ink-soft">
        Pedí los datos de la cuenta para transferir en la administración del instituto.
      </div>
    );
  }

  return (
    <div className="flex flex-col gap-2 rounded-xl bg-neutral-bg px-4 py-3">
      <div className="text-xs font-semibold text-ink-soft">Datos para la transferencia</div>
      {fields.map((field) => (
        <div key={field.label} className="flex flex-wrap items-baseline justify-between gap-x-4">
          <span className="text-xs text-ink-faint">{field.label}</span>
          <span className="break-all font-mono text-size-13 font-semibold text-ink">{field.value}</span>
        </div>
      ))}
    </div>
  );
}
