import { monthsBetween } from './grantForm';
import { APPLIED_TYPE_LABELS, appliesToLabel, formatCalculation, formatMonth, formatMonthRange } from './discountLabels';

export function discountKind(discount) {
  return APPLIED_TYPE_LABELS[discount.type] ?? discount.type;
}

export function discountValue(discount) {
  return formatCalculation(discount.calculation);
}

export function discountAppliesTo(discount) {
  return appliesToLabel(discount.applicableInvoiceType);
}

export function isMonthlyDiscount(discount) {
  return discount.applicableInvoiceType === 'MONTHLY_INSTALLMENT';
}

export function discountPeriod(discount) {
  return isMonthlyDiscount(discount) ? formatMonthRange(discount.fromPeriod, discount.toPeriod) : formatMonth(discount.fromPeriod);
}

export function discountProgress(discount) {
  const applied = (discount.appliedInstallments ?? []).length;
  const total = isMonthlyDiscount(discount) ? Math.max(monthsBetween(discount.fromPeriod, discount.toPeriod), applied) : 1;
  return { applied, total };
}

export function lostPeriodsOf(discount) {
  return discount.lostPeriods ?? [];
}

export function discountDefinitionSubtitle(discount) {
  return discount.definitionName && discount.definitionName !== discountKind(discount) ? discount.definitionName : null;
}
