export const CODIGO_MATRICULA_IMPAGA = 'ENROLLMENT_FEE_NOT_PAID_FOR_TERM';

export function obligacionPendiente(obligaciones) {
  return (obligaciones ?? []).find((o) => !o.satisfied && o.status !== 'NOT_ISSUED') ?? null;
}
