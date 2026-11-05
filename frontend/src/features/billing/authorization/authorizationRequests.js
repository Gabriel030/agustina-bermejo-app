export const REQUEST_STATUS = {
  PENDING: { label: 'Pendiente', tone: 'warning' },
  APPROVED: { label: 'Aprobada', tone: 'success' },
  REJECTED: { label: 'Rechazada', tone: 'danger' },
  WITHDRAWN: { label: 'Retirada', tone: 'neutral' },
  EXPIRED: { label: 'Expirada', tone: 'neutral' },
  APPROVED_NOT_APPLIED: { label: 'No se pudo ejecutar', tone: 'danger' },
};

export const FAMILY_LABELS = {
  REFUNDS: 'Reembolsos',
  INVOICES_AND_NOTES: 'Facturas y notas',
  PAYMENTS_AND_CHARGEBACKS: 'Pagos y contracargos',
  DISCOUNTS_AND_PRICES: 'Descuentos y precios',
  CONFIGURATION: 'Configuración',
};

export const REQUESTER_ROLE_LABELS = {
  ADMINISTRATIVO: 'Administrativo',
  DIRECTOR: 'Director',
};

export const HISTORY_STATUSES = ['APPROVED', 'REJECTED', 'WITHDRAWN', 'EXPIRED'];
export const PENDING_STATUSES = ['PENDING'];
export const INBOX_PAGE_SIZE = 50;
export const DECISION_REASON_MAX_LENGTH = 500;
export const EXPIRING_SOON_DAYS = 7;
export const REFUND_OPERATION_TYPE = 'ISSUE_REFUND';
export const RESUME_REJECTED = 'REJECTED_ON_RESUME';

const MS_PER_DAY = 86400000;

export function displayStatusKey(request) {
  return request.status === 'APPROVED' && request.resumeOutcome === RESUME_REJECTED ? 'APPROVED_NOT_APPLIED' : request.status;
}

export function displayStatus(request) {
  return REQUEST_STATUS[displayStatusKey(request)] ?? { label: request.status, tone: 'neutral' };
}

export function familyLabel(family) {
  return FAMILY_LABELS[family] ?? family;
}

export function requesterRoleLabel(roles) {
  return (roles ?? []).map((role) => REQUESTER_ROLE_LABELS[role] ?? role).join(' · ');
}

export function roleBaseOf(user) {
  return user?.roles?.[0]?.toLowerCase();
}

export function inboxPath(roleBase) {
  return roleBase === 'director' ? '/director/aprobaciones/facturacion' : `/${roleBase}/aprobaciones`;
}

export function detailPath(roleBase, id) {
  return `${inboxPath(roleBase)}/${id}`;
}

export function daysUntil(isoDate, today = new Date()) {
  if (!isoDate) return null;
  const [year, month, day] = isoDate.split('-').map(Number);
  const start = new Date(today.getFullYear(), today.getMonth(), today.getDate());
  return Math.round((new Date(year, month - 1, day) - start) / MS_PER_DAY);
}

export function dueInfo(expiresOn, today) {
  const days = daysUntil(expiresOn, today);
  if (days == null) return { key: 'none', label: 'No vence', tone: 'neutral' };
  if (days <= 0) return { key: 'today', label: 'Vence hoy', tone: 'warning' };
  const label = days === 1 ? 'En 1 día' : `En ${days} días`;
  return { key: 'soon', label, tone: days <= 3 ? 'warning' : 'neutral' };
}

export function isExpiringSoon(request, today) {
  const days = daysUntil(request.expiresOn, today);
  return days != null && days <= EXPIRING_SOON_DAYS;
}

export function sumRefundAmount(requests) {
  const refunds = requests.filter((request) => request.operationType === REFUND_OPERATION_TYPE && request.amount);
  if (refunds.length === 0) return null;
  const cents = refunds.reduce((total, request) => total + Math.round(Number(request.amount.amount) * 100), 0);
  return { amount: (cents / 100).toFixed(2), currency: refunds[0].amount.currency };
}

export function isRequester(request, user) {
  return request.requestedBy?.id != null && request.requestedBy.id === user?.id;
}

export function canDecideRequest(request, user) {
  return request.status === 'PENDING' && roleBaseOf(user) === 'director' && !isRequester(request, user);
}

export function canWithdrawRequest(request, user) {
  return request.status === 'PENDING' && isRequester(request, user);
}

export const STATUS_CHANGE_EVENT_LABELS = {
  OPEN: { label: 'Pedido por', tone: 'neutral' },
  APPROVE: { label: 'Aprobada por', tone: 'success' },
  REJECT: { label: 'Rechazada por', tone: 'danger' },
  WITHDRAW: { label: 'Retirada por', tone: 'neutral' },
  EXPIRE: { label: 'Expiró sin decisión', tone: 'neutral' },
};

export function describeStatusChange(change) {
  const event = STATUS_CHANGE_EVENT_LABELS[change.event] ?? { label: change.event, tone: 'neutral' };
  const hasActor = change.event !== 'EXPIRE' && change.actorName;
  return { label: hasActor ? `${event.label} ${change.actorName}` : event.label, tone: event.tone };
}

export const ALREADY_DECIDED_CODE = 'ILLEGAL_AUTHORIZATION_REQUEST_TRANSITION';

const ACTION_VERBS = { approve: 'aprobar', reject: 'rechazar', withdraw: 'retirar' };

export function requestActionErrorMessage(error, { action, own = false } = {}) {
  const verb = ACTION_VERBS[action] ?? 'resolver';
  if (error?.status === 403) {
    if (own && action !== 'withdraw') return 'No podés decidir un pedido que hiciste vos. Tiene que decidirlo otra persona de dirección.';
    if (action === 'withdraw') return 'Solo quien hizo el pedido puede retirarlo.';
    return `Solo la dirección puede ${verb} pedidos.`;
  }
  if (error?.status === 404) return 'El pedido ya no existe. Actualizamos la lista.';
  if (error?.code === ALREADY_DECIDED_CODE) return 'Este pedido ya no está pendiente: alguien lo resolvió antes. Actualizamos los datos.';
  if (error?.status === 409) return `No se pudo ${verb} el pedido porque cambió su estado. Actualizamos los datos.`;
  return error?.message ?? 'No pudimos completar la operación. Probá de nuevo.';
}

export function loadErrorMessage(error) {
  if (error?.status === 404) return 'No encontramos este pedido. Puede que ya no exista.';
  if (error?.status === 403) return 'No tenés acceso a este pedido.';
  return 'No pudimos cargar el pedido.';
}

export function formatInstant(isoInstant) {
  if (!isoInstant) return '';
  return new Intl.DateTimeFormat('es-AR', { day: '2-digit', month: '2-digit', year: 'numeric', hour: '2-digit', minute: '2-digit' }).format(new Date(isoInstant));
}
