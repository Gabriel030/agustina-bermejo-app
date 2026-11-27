import { Download, Pause, Play, RefreshCw, X } from 'lucide-react';

export const SEND_REQUIRED_REASON = 'Necesitás el permiso de enviar invitaciones. Pedíselo al Director.';

const LIFECYCLE = {
  start: { endpoint: 'start', icon: Play, label: 'Iniciar oleada', success: 'Oleada iniciada', sensitive: true, confirm: true },
  pause: { endpoint: 'pause', icon: Pause, label: 'Pausar', success: 'Oleada pausada', sensitive: false, confirm: false },
  resume: { endpoint: 'resume', icon: Play, label: 'Reanudar', success: 'Oleada reanudada', sensitive: true, confirm: true },
  retry: { endpoint: 'retry-failed', icon: RefreshCw, label: 'Reintentar fallidos', success: 'Reintento programado', sensitive: true, confirm: true },
  cancel: { endpoint: 'cancel', icon: X, label: 'Cancelar oleada', success: 'Oleada cancelada', sensitive: true, confirm: true, destructive: true },
};

export const ACTION_DEFINITIONS = LIFECYCLE;

const CAN_RETRY = ['RUNNING'];
const CAN_CANCEL = ['DRAFT', 'RUNNING', 'PAUSED'];

function item(key, access, overrides = {}) {
  const definition = LIFECYCLE[key];
  const blocked = definition.sensitive && !access.send;
  return {
    key,
    ...definition,
    ...overrides,
    disabled: blocked,
    disabledReason: blocked ? SEND_REQUIRED_REASON : null,
  };
}

export function waveActions(wave, access) {
  const csv = access.read ? { key: 'csv', icon: Download, label: 'Exportar reporte CSV', sensitive: false, confirm: false, disabled: false } : null;
  if (!access.manage) return { primary: null, secondary: [], csv };

  let primary = null;
  if (wave.status === 'DRAFT') primary = item('start', access);
  if (wave.status === 'RUNNING') primary = item('pause', access);
  if (wave.status === 'PAUSED') primary = item('resume', access);

  const secondary = [];
  if (wave.failedTotal > 0 && CAN_RETRY.includes(wave.status)) {
    secondary.push(item('retry', access, { label: `Reintentar fallidos (${wave.failedTotal})` }));
  }
  if (CAN_CANCEL.includes(wave.status)) secondary.push(item('cancel', access));
  return { primary, secondary, csv };
}

export function orderedSecondary({ secondary, csv }) {
  const retry = secondary.filter((entry) => entry.key === 'retry');
  const rest = secondary.filter((entry) => entry.key !== 'retry');
  return [...retry, ...(csv ? [csv] : []), ...rest];
}

export const SEND_REASON_ID = 'invitation-wave-send-reason';

export function actionButtonProps(item, controller) {
  const blocked = item.disabled;
  return {
    type: 'button',
    'aria-disabled': blocked ? true : undefined,
    'aria-describedby': blocked ? SEND_REASON_ID : undefined,
    title: blocked ? item.disabledReason : undefined,
    loading: controller.runningKey === item.key || (item.key === 'csv' && controller.downloading),
    onClick: () => controller.request(item),
  };
}
