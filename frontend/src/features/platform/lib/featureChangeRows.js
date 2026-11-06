import { BILLING_CODE, BILLING_EFFECT_QUESTIONS, BILLING_MODE_INFO, FEATURE_CATALOG } from '@/features/platform/lib/featureCatalog';

const yesNo = (value) => (value ? 'Sí' : 'No');

export function changeRows(code, current, target) {
  const feature = FEATURE_CATALOG[code];
  if (code === BILLING_CODE) {
    const from = BILLING_MODE_INFO[current.mode];
    const to = BILLING_MODE_INFO[target.mode];
    const rows = [{ label: feature.label, today: from.label, next: to.label, diff: 'Cambia el modo' }];
    BILLING_EFFECT_QUESTIONS.forEach((question, index) => {
      if (from.effects[index] === to.effects[index]) return;
      rows.push({ label: question, today: yesNo(from.effects[index]), next: yesNo(to.effects[index]), diff: to.effects[index] ? 'Se activa' : 'Se desactiva' });
    });
    return rows;
  }
  return [
    {
      label: feature.label,
      today: current.enabled ? 'Habilitado' : 'Deshabilitado',
      next: target.enabled ? 'Habilitado' : 'Deshabilitado',
      diff: target.enabled ? 'Se habilita' : 'Se deshabilita',
    },
  ];
}

export function isSameState(code, current, target) {
  return code === BILLING_CODE ? current.mode === target.mode : current.enabled === target.enabled;
}

export function toChangeRequest(code, target, reason, previewConfirmationToken) {
  if (code === BILLING_CODE) {
    const request = { enabled: target.mode !== 'OFF', mode: target.mode, reason: reason.trim() };
    return previewConfirmationToken ? { ...request, previewConfirmationToken } : request;
  }
  return { enabled: target.enabled, reason: reason.trim() };
}

export function describeState(code, state) {
  if (code === BILLING_CODE) return BILLING_MODE_INFO[state.mode]?.label ?? state.mode;
  return state.enabled ? 'Habilitado' : 'Deshabilitado';
}

export function requiresPreview(code, current, target) {
  return code === BILLING_CODE && current.mode === 'FULL' && target.mode !== 'FULL';
}
