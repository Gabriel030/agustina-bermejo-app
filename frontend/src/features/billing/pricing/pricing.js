export const PRICE_STATUS = {
  NONE: { tone: 'danger', label: 'Sin precio vigente' },
  PROPOSAL: { tone: 'warning', label: 'Propuesta pendiente' },
  SCHEDULED: { tone: 'accent', label: 'Aumento programado' },
  FREE: { tone: 'neutral', label: 'Gratuito' },
  EFFECTIVE: { tone: 'success', label: 'Vigente' },
};

export const VERSION_STATUS = {
  PROPOSED: { tone: 'warning', label: 'Propuesta' },
  SCHEDULED: { tone: 'accent', label: 'Aprobada · programada' },
  EFFECTIVE: { tone: 'success', label: 'Vigente' },
  SUPERSEDED: { tone: 'neutral', label: 'Reemplazada' },
  REJECTED: { tone: 'danger', label: 'Rechazada' },
  WITHDRAWN: { tone: 'neutral', label: 'Retirada' },
  EXPIRED: { tone: 'danger', label: 'Vencida' },
  CANCELLED: { tone: 'neutral', label: 'Cancelada antes de regir' },
};

export const DISCOUNT_KIND = { NONE: 'NONE', PERCENTAGE: 'PERCENTAGE', FIXED: 'FIXED' };

export const REASON_MAX_LENGTH = 500;

export function amountOf(money) {
  return money == null ? 0 : Number(money.amount);
}

export function priceStatusKey(price) {
  if (!price.currentVersion) return 'NONE';
  if (price.openProposal) return 'PROPOSAL';
  if (price.nextScheduledVersion) return 'SCHEDULED';
  if (amountOf(price.currentVersion.finalAmount) === 0) return 'FREE';
  return 'EFFECTIVE';
}

export function priceStatusKeys(price) {
  const keys = [];
  if (!price.currentVersion) keys.push('NONE');
  if (price.openProposal) keys.push('PROPOSAL');
  if (price.nextScheduledVersion) keys.push('SCHEDULED');
  return keys;
}

export function countByPresence(prices) {
  return {
    PROPOSAL: prices.filter((price) => price.openProposal).length,
    SCHEDULED: prices.filter((price) => price.nextScheduledVersion).length,
    NONE: prices.filter((price) => !price.currentVersion).length,
  };
}

export function advanceVersionOf(price) {
  return [price.nextScheduledVersion, price.openProposal, price.currentVersion].find((version) => version?.appliesToLectiveYear != null) ?? null;
}

export function offerKind(price) {
  return price.programId != null ? 'Carrera' : 'Curso';
}

export function offerName(price, names) {
  return price.programId != null ? names.programName(price.programId) : names.courseName(price.courseId);
}

function toCents(value) {
  return Math.round(Number(value) * 100);
}

export function computeFinalAmount({ amount, discountKind, discountValue }) {
  const base = toCents(amount);
  if (Number.isNaN(base)) return null;
  const value = Number(discountValue);
  let discount = 0;
  if (discountKind === DISCOUNT_KIND.PERCENTAGE && !Number.isNaN(value)) discount = Math.round((base * value) / 100);
  if (discountKind === DISCOUNT_KIND.FIXED && !Number.isNaN(value)) discount = toCents(value);
  return { base: base / 100, discount: discount / 100, final: Math.max(base - discount, 0) / 100 };
}

export function buildPriceChangePayload({ amount, currency, discountKind, discountValue, effectiveFrom, reason, appliesToLectiveYear }) {
  const payload = {
    amount: { amount: Number(amount).toFixed(2), currency },
    effectiveFrom,
    changeReason: reason.trim(),
  };
  if (discountKind === DISCOUNT_KIND.PERCENTAGE) payload.marketingDiscount = { type: 'PERCENTAGE', percent: Number(discountValue) };
  if (discountKind === DISCOUNT_KIND.FIXED) {
    payload.marketingDiscount = { type: 'FIXED', fixedAmount: { amount: Number(discountValue).toFixed(2), currency } };
  }
  if (appliesToLectiveYear) payload.appliesToLectiveYear = Number(appliesToLectiveYear);
  return payload;
}

export function isFormComplete({ amount, discountKind, discountValue, effectiveFrom, reason }) {
  if (amount === '' || Number.isNaN(Number(amount)) || Number(amount) < 0) return false;
  if (discountKind !== DISCOUNT_KIND.NONE && (discountValue === '' || Number(discountValue) < 0)) return false;
  return Boolean(effectiveFrom) && reason.trim() !== '';
}

export function lectiveYearOptions(today, termYears) {
  const currentYear = Number(today.slice(0, 4));
  return [currentYear + 1, currentYear + 2].map((year) => ({ year, enabled: termYears.includes(year) }));
}

export function whatHappens({ finalAmount, currentFinal, effectiveFrom, appliesToLectiveYear }) {
  if (finalAmount == null) return [];
  const lines = [];
  if (appliesToLectiveYear) {
    lines.push({ key: 'advance', kind: 'advance', year: Number(appliesToLectiveYear), finalAmount, effectiveFrom });
  } else {
    lines.push({ key: 'regular', kind: 'regular', finalAmount, effectiveFrom });
  }
  if (currentFinal != null) lines.push({ key: 'difference', kind: 'difference', difference: finalAmount - currentFinal });
  lines.push({ key: 'issued', kind: 'issued' });
  return lines;
}
