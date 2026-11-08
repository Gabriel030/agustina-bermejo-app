import { GlassCard } from '@/components/GlassCard';
import { Skeleton } from '@/components/Skeleton';
import { formatDate, formatMoney, formatSignedMoney } from '@/lib/billing';

function Metric({ label, children }) {
  return (
    <div className="flex flex-col gap-0.5">
      <span className="text-[11px] font-bold uppercase tracking-wide text-ink-faint">{label}</span>
      <span className="text-[15px] font-semibold tabular-nums text-ink">{children}</span>
    </div>
  );
}

export function PriceImpactPreview({ impact, effectiveFrom }) {
  return (
    <GlassCard className="flex flex-col gap-3 p-5">
      <h2 className="font-heading text-lg font-semibold text-ink">Impacto del cambio</h2>
      {impact.isPending && !impact.data ? (
        <Skeleton className="h-20 w-full" aria-label="Calculando impacto" />
      ) : impact.isError ? (
        <p className="text-sm text-ink-soft">No pudimos calcular el impacto. Podés enviar el cambio igual.</p>
      ) : impact.data ? (
        impact.data.affectedStudents === 0 ? (
          <p className="text-sm text-ink-soft">Hoy no hay alumnos alcanzados por este cambio.</p>
        ) : (
          <div className="grid gap-3 sm:grid-cols-2">
            <Metric label="Alumnos alcanzados">{impact.data.affectedStudents}</Metric>
            <Metric label="Ingreso mensual estimado">{formatMoney(impact.data.proposedMonthlyIncome)}</Metric>
            <Metric label="Diferencia contra hoy">{formatSignedMoney(impact.data.monthlyIncomeDifference)}</Metric>
            <Metric label="Se resigna por descuento">{formatMoney(impact.data.monthlyMarketingDiscountCost)}</Metric>
          </div>
        )
      ) : (
        <p className="text-sm text-ink-soft">Completá el precio y la vigencia para ver el impacto.</p>
      )}
      {effectiveFrom && <p className="text-[12px] text-ink-faint">Aplica desde el {formatDate(effectiveFrom)}.</p>}
    </GlassCard>
  );
}
