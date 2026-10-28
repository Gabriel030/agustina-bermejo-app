import { CheckCircle2, XCircle } from 'lucide-react';
import { GlassCard } from '@/components/GlassCard';
import { Skeleton } from '@/components/Skeleton';
import { InfoTip } from '@/components/charts/InfoTip';
import { formatMoney } from '@/lib/billing';
import { CURRENCY, hasCalculation, sampleOutcome } from './discountDefinitionForm';

function money(amount) {
  return formatMoney({ amount: amount.toFixed(2), currency: CURRENCY });
}

function PanelHeader({ title, tip }) {
  return (
    <div className="flex items-center gap-2">
      <h2 className="font-heading text-base font-semibold text-ink">{title}</h2>
      <InfoTip>{tip}</InfoTip>
    </div>
  );
}

export function ValidationPanel({ state }) {
  let body;
  if (state.kind === 'idle') {
    body = <p className="rounded-xl bg-neutral-bg px-3 py-2.5 text-sm text-ink-soft">Completá los datos para revisar las reglas del catálogo.</p>;
  } else if (state.kind === 'loading') {
    body = <Skeleton className="h-9 w-full rounded-xl" />;
  } else if (state.kind === 'error') {
    body = <p className="rounded-xl bg-warning-bg px-3 py-2.5 text-sm text-ink">No pudimos revisar las reglas ahora. Se revisan de nuevo al enviar.</p>;
  } else if (state.messages.length === 0) {
    body = (
      <p className="flex items-start gap-2 rounded-xl bg-success-bg px-3 py-2.5 text-sm text-ink">
        <CheckCircle2 className="mt-0.5 size-4 shrink-0 text-success" aria-hidden="true" />
        <span><b>Sin problemas.</b> Las reglas del catálogo se cumplen.</span>
      </p>
    );
  } else {
    body = (
      <div className="flex items-start gap-2 rounded-xl bg-danger-bg/70 px-3 py-2.5 text-sm text-ink">
        <XCircle className="mt-0.5 size-4 shrink-0 text-danger" aria-hidden="true" />
        <div>
          <b>{state.messages.length} {state.messages.length === 1 ? 'problema' : 'problemas'}</b>
          <ul className="mt-1 flex list-disc flex-col gap-1 pl-5">
            {state.messages.map((message) => (
              <li key={message}>{message}</li>
            ))}
          </ul>
        </div>
      </div>
    );
  }
  return (
    <GlassCard className="flex flex-col gap-3 p-5">
      <PanelHeader title="Validación" tip="Se revisa sola mientras completás, con las mismas reglas que al crear." />
      <div aria-live="polite">{body}</div>
    </GlassCard>
  );
}

export function ExamplePanel({ values }) {
  const outcome = sampleOutcome(values);
  let body;
  if (!hasCalculation(values)) {
    body = <p className="text-sm text-ink-faint">El valor se fija en cada otorgamiento.</p>;
  } else if (!outcome) {
    body = <p className="text-sm text-ink-faint">Cargá un valor válido para ver el ejemplo.</p>;
  } else {
    body = (
      <div className="flex flex-col gap-2 text-sm">
        <div className="flex justify-between gap-4"><span className="text-ink-soft">Precio de ejemplo</span><b className="tabular-nums text-ink">{money(outcome.price)}</b></div>
        <div className="flex justify-between gap-4">
          <span className="text-ink-soft">Descuento{outcome.capped ? ' (con tope)' : ''}</span>
          <b className="tabular-nums text-success">−{money(outcome.discount)}</b>
        </div>
        <div className="flex justify-between gap-4 border-t border-border pt-2 font-bold text-ink"><span>Paga</span><span className="tabular-nums">{money(outcome.total)}</span></div>
      </div>
    );
  }
  return (
    <GlassCard className="flex flex-col gap-3 p-5">
      <PanelHeader title="Ejemplo" tip="Es una cuenta de referencia con un precio de muestra. El precio real depende de la oferta." />
      {body}
    </GlassCard>
  );
}
