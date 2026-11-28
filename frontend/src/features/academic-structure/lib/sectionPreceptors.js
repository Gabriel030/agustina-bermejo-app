export function sameIds(a, b) {
  if (a.length !== b.length) return false;
  const sorted = [...b].sort((x, y) => x - y);
  return [...a].sort((x, y) => x - y).every((id, index) => id === sorted[index]);
}

export function describeImpact(current, next, pendingRequestCount) {
  const removed = current.filter((id) => !next.includes(id));
  const withoutPreceptor = next.length === 0;
  const pendingPass = removed.length > 0 && pendingRequestCount > 0;
  const lines = [];
  if (withoutPreceptor) lines.push({ bold: 'Queda sin preceptor.', text: 'Las solicitudes nuevas llegan a Dirección.' });
  if (pendingPass) {
    const noun = pendingRequestCount === 1 ? 'solicitud pendiente' : 'solicitudes pendientes';
    lines.push({ bold: `La comisión tiene ${pendingRequestCount} ${noun};`, text: 'pasan a Dirección.' });
  }
  return lines;
}
