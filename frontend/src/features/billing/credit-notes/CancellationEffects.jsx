import { Skeleton } from '@/components/Skeleton';
import { formatMoney } from '@/lib/billing';

function effectsOf(preview, lateFee) {
  const effects = [`Se emite una nota de crédito por ${formatMoney(preview.creditNoteAmount)}.`];
  if (lateFee) {
    effects.push('El recargo deja de contar en la deuda del alumno.', 'La cuota que lo originó no cambia.');
    return effects;
  }
  effects.push('La cuota queda sin efecto. Sus renglones no se modifican.');
  effects.push(preview.releasesChargeKey ? 'La cuota se puede volver a emitir.' : 'La cuota no se vuelve a emitir.');
  (preview.lateFeesToAnnul ?? []).forEach((lateFee) => {
    effects.push(`Se anula el recargo ${lateFee.invoiceNumber} por ${formatMoney(lateFee.amountToAnnul)}.`);
  });
  (preview.discountConsumptionReleased ?? []).forEach((consumption) => {
    effects.push(
      consumption.reactivatesDiscount
        ? 'Se devuelve el consumo del descuento y vuelve a estar disponible.'
        : 'Se devuelve el consumo del descuento.',
    );
  });
  return effects;
}

export function CancellationEffects({ preview, isLoading, lateFee }) {
  return (
    <section aria-labelledby="cancellation-effects-heading" className="flex flex-col gap-2">
      <h3 id="cancellation-effects-heading" className="text-sm font-semibold text-ink">
        Qué va a pasar
      </h3>
      {!preview && isLoading && (
        <div className="flex flex-col gap-2" aria-busy="true">
          <Skeleton className="h-4 w-3/4" />
          <Skeleton className="h-4 w-2/3" />
          <Skeleton className="h-4 w-1/2" />
        </div>
      )}
      {preview && (
        <ul className="flex flex-col gap-1.5 text-sm text-ink-soft">
          {effectsOf(preview, lateFee).map((effect) => (
            <li key={effect} className="flex items-start gap-2">
              <span className="mt-2 size-1.5 shrink-0 rounded-full bg-ink-faint" aria-hidden="true" />
              <span>{effect}</span>
            </li>
          ))}
        </ul>
      )}
    </section>
  );
}
