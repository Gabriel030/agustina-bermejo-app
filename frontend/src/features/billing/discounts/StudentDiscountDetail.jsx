import { formatDate } from '@/lib/billing';
import { formatMonth } from './discountLabels';
import { lostPeriodsOf } from './studentDiscountView';

function Block({ title, children }) {
  return (
    <div className="flex min-w-0 flex-col gap-1.5">
      <h4 className="text-[11px] font-bold uppercase tracking-wide text-ink-faint">{title}</h4>
      {children}
    </div>
  );
}

function Hint({ children }) {
  return <span className="text-xs text-ink-faint">{children}</span>;
}

export function StudentDiscountDetail({ discount }) {
  const applied = discount.appliedInstallments ?? [];
  const lost = lostPeriodsOf(discount);
  return (
    <div className="grid gap-5 px-5 py-4 text-sm sm:grid-cols-3">
      <Block title="Cuotas con descuento">
        {applied.length ? (
          <ul className="flex flex-col gap-1 text-ink">
            {applied.map((installment) => (
              <li key={installment.period}>
                {formatMonth(installment.period)}
                {installment.installmentNumber != null &&
                  ` · cuota ${installment.installmentNumber}${installment.installmentsTotal != null ? ` de ${installment.installmentsTotal}` : ''}`}
              </li>
            ))}
          </ul>
        ) : (
          <Hint>Todavía no se aplicó a ninguna factura.</Hint>
        )}
      </Block>
      <Block title="Meses perdidos">
        {lost.length ? (
          <>
            <ul className="flex flex-col gap-1 text-ink">
              {lost.map((period) => (
                <li key={period}>{formatMonth(period)}</li>
              ))}
            </ul>
            <Hint>Ese mes ganó otro descuento y no se corre.</Hint>
          </>
        ) : (
          <Hint>Ninguno.</Hint>
        )}
      </Block>
      <Block title="Otorgado">
        <span className="text-ink">{formatDate(discount.grantedAt)}</span>
        {discount.reason && <span className="text-ink-soft">Motivo: {discount.reason}</span>}
        {discount.revokedAt && (
          <Hint>
            Revocado el {formatDate(discount.revokedAt)}
            {discount.revokedFromPeriod && `. Dejó de aplicarse desde ${formatMonth(discount.revokedFromPeriod)}`}
            {discount.revocationReason && `. Motivo: ${discount.revocationReason}`}
          </Hint>
        )}
      </Block>
    </div>
  );
}
