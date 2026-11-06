const DRAFT_ERROR_MESSAGES = {
  DRAFT_NOT_PENDING: 'Algunos borradores ya no estaban pendientes. Actualizamos la lista.',
  MONTHLY_INVOICE_RUN_NOT_AWAITING_REVIEW: 'La corrida ya no está en revisión.',
  IDEMPOTENCY_KEY_CONFLICT: 'No pudimos confirmar el pedido anterior. Volvé a intentar la aprobación.',
};

export function groupKey(group) {
  return `${group.enrollmentType}|${group.feeType}|${group.name}`;
}

function toCents(money) {
  return Math.round(Number(money?.amount ?? 0) * 100);
}

export function summarizeDraftSelection(groups, selectedKeys) {
  const selected = groups.filter((group) => selectedKeys.has(groupKey(group)));
  const invoiceIds = [...new Set(selected.flatMap((group) => group.invoiceIds))];
  const idSet = new Set(invoiceIds);
  const groupsPerInvoice = new Map();
  for (const group of groups) {
    for (const id of group.invoiceIds) {
      if (idSet.has(id)) groupsPerInvoice.set(id, (groupsPerInvoice.get(id) ?? 0) + 1);
    }
  }
  const sharedCount = [...groupsPerInvoice.values()].filter((count) => count > 1).length;
  const outsideGroups = groups.filter((group) => !selectedKeys.has(groupKey(group)) && group.invoiceIds.some((id) => idSet.has(id)));
  return {
    invoiceIds,
    count: invoiceIds.length,
    groupCount: selected.length,
    sharedCount,
    outsideGroupNames: [...new Set(outsideGroups.map((group) => group.name))],
    hasOutsideGroups: outsideGroups.length > 0,
    total: {
      amount: (selected.reduce((sum, group) => sum + toCents(group.draftTotal), 0) / 100).toFixed(2),
      currency: selected[0]?.draftTotal?.currency ?? 'ARS',
    },
  };
}

export function pruneSelection(groups, selectedKeys) {
  const present = new Set(groups.map(groupKey));
  return new Set([...selectedKeys].filter((key) => present.has(key)));
}

export function sharedInvoicesWarning(summary) {
  if (summary.sharedCount === 0) return null;
  const subject = summary.sharedCount === 1 ? 'Una factura abarca más de un grupo' : `${summary.sharedCount} facturas abarcan más de un grupo`;
  const tail = summary.hasOutsideGroups ? ` Incluye líneas de: ${summary.outsideGroupNames.join(', ')}.` : '';
  return `${subject}: se procesa completa, con todas sus líneas.${tail}`;
}

export function draftErrorMessage(error, fallback) {
  return DRAFT_ERROR_MESSAGES[error?.code] ?? fallback;
}

export function differenceLabel(group) {
  if (toCents(group.previousPeriodIssuedTotal) === 0) return { text: 'Nuevo este mes', tone: 'neutral' };
  const points = Number(group.differencePercentage);
  if (!points) return { text: 'Igual', tone: 'neutral' };
  const text = `${points > 0 ? '+' : '−'}${Math.abs(points).toFixed(1).replace('.', ',')}%`;
  return { text, tone: points > 0 ? 'warning' : 'success' };
}
