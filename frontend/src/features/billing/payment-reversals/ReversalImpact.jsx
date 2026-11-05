import { ArrowRight } from 'lucide-react';
import { Skeleton } from '@/components/Skeleton';
import { StatusPill } from '@/components/StatusPill';
import { formatMoney, invoiceStatusInfo } from '@/lib/billing';

function Group({ title, children }) {
  return (
    <div className="flex flex-col gap-1.5">
      <h4 className="text-[11px] font-bold uppercase tracking-widest text-ink-faint">{title}</h4>
      {children}
    </div>
  );
}

function Row({ label, children }) {
  return (
    <div className="flex items-center justify-between gap-3">
      <span className="min-w-0 text-ink">{label}</span>
      <span className="flex shrink-0 items-center gap-2">{children}</span>
    </div>
  );
}

function InvoiceTransition({ impact, invoiceLabel }) {
  const before = invoiceStatusInfo(impact.paymentStatusBefore);
  const after = invoiceStatusInfo(impact.paymentStatusAfter);
  return (
    <Row label={invoiceLabel(impact.invoiceId)}>
      <StatusPill tone={before.tone}>{before.label}</StatusPill>
      <ArrowRight className="size-3.5 text-ink-faint" aria-hidden="true" />
      <StatusPill tone={after.tone}>{after.label}</StatusPill>
    </Row>
  );
}

function CalculatedEffects({ effects, invoiceLabel }) {
  return (
    <>
      {effects.reopenedInvoices.length > 0 && (
        <Group title="Cuotas que se reabren">
          {effects.reopenedInvoices.map((impact) => (
            <InvoiceTransition key={impact.invoiceId} impact={impact} invoiceLabel={invoiceLabel} />
          ))}
        </Group>
      )}
      {effects.settledInvoices.length > 0 && (
        <Group title="Cuotas que se saldan">
          {effects.settledInvoices.map((impact) => (
            <InvoiceTransition key={impact.invoiceId} impact={impact} invoiceLabel={invoiceLabel} />
          ))}
        </Group>
      )}
      {effects.lateFeeReinstatements.length > 0 && (
        <Group title="Recargos por mora que se reponen">
          {effects.lateFeeReinstatements.map((fee) => (
            <Row key={fee.invoiceId} label={`${invoiceLabel(fee.invoiceId)} · ${fee.days} días`}>
              <span className="font-semibold tabular-nums">{formatMoney(fee.amount)}</span>
            </Row>
          ))}
        </Group>
      )}
    </>
  );
}

export function ReversalImpact({ kind, preview, isLoading, invoiceLabel }) {
  return (
    <section aria-label="Vista previa del impacto" className="flex flex-col gap-3 rounded-xl bg-neutral-bg px-4 py-3 text-sm">
      <div>
        <h3 className="font-heading text-sm font-semibold text-ink">Qué va a pasar</h3>
        <p className="text-xs text-ink-faint">Es una simulación: no cambia nada hasta que confirmes.</p>
      </div>
      {!preview && isLoading && (
        <div className="flex flex-col gap-2" aria-busy="true" aria-label="Calculando impacto">
          <Skeleton className="h-4 w-2/3" />
          <Skeleton className="h-4 w-5/6" />
          <Skeleton className="h-4 w-1/2" />
        </div>
      )}
      {preview && kind === 'reversal' && (
        <Group title="Se acredita al alumno">
          <Row label="Crédito del pago">
            <span className="font-semibold tabular-nums">{formatMoney(preview.creditToReverse)}</span>
          </Row>
          <p className="text-xs text-ink-faint">
            El crédito se aplica a las cuotas abiertas más antiguas, que no son necesariamente la cuota original.
          </p>
        </Group>
      )}
      {preview && kind !== 'reversal' && (
        <>
          <Group title="Se descuenta del saldo del alumno">
            <Row label="Crédito que generó el pago">
              <span className="font-semibold tabular-nums">{formatMoney(preview.creditToReverse)}</span>
            </Row>
          </Group>
          <Group title="Cuotas que este pago había saldado">
            {preview.invoiceIdsFundedByPayment.length === 0 ? (
              <p className="text-ink-faint">Ninguna.</p>
            ) : (
              preview.invoiceIdsFundedByPayment.map((invoiceId) => (
                <Row key={invoiceId} label={invoiceLabel(invoiceId)}>
                  <span className="text-xs text-ink-faint">Puede reabrirse</span>
                </Row>
              ))
            )}
          </Group>
        </>
      )}
      {preview?.effects?.calculated && <CalculatedEffects effects={preview.effects} invoiceLabel={invoiceLabel} />}
      {preview && !preview.effects?.calculated && (
        <p className="text-xs text-ink-faint">El detalle de cuotas y recargos se informa al confirmar.</p>
      )}
      {kind === 'chargeback' && (
        <p className="text-xs text-ink-faint">La fecha del banco es informativa: el movimiento rige desde hoy.</p>
      )}
    </section>
  );
}
