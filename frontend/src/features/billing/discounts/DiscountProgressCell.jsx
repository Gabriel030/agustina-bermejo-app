import { discountProgress, isMonthlyDiscount, lostPeriodsOf } from './studentDiscountView';

export function DiscountProgressCell({ discount }) {
  const { applied, total } = discountProgress(discount);
  const lost = lostPeriodsOf(discount).length;
  let text = `${applied} de ${total}`;
  if (!applied && discount.status === 'ACTIVE' && !isMonthlyDiscount(discount)) text = 'Sin emitir';
  else if (!applied && discount.status !== 'ACTIVE') text = '—';
  return (
    <div className="flex flex-col">
      <span>{text}</span>
      {lost > 0 && <span className="text-xs font-semibold text-warning">{lost} {lost === 1 ? 'mes perdido' : 'meses perdidos'}</span>}
    </div>
  );
}
