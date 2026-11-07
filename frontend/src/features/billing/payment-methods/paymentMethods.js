import { Banknote, BellRing, CreditCard, Landmark } from 'lucide-react';
import { formatDateTime } from '@/lib/billing';

export const REASON_MIN = 5;
export const REASON_MAX = 500;
export const NOTIFICATIONS_KEY = 'BILLING_NOTIFICATIONS';

export const SETTING_CATALOG = {
  CASH: { label: 'Efectivo', description: 'Cobro en caja', icon: Banknote },
  BANK_TRANSFER: { label: 'Transferencia', description: 'Con comprobante', icon: Landmark },
  GATEWAY: { label: 'Pasarela online', description: 'Mercado Pago', icon: CreditCard },
  [NOTIFICATIONS_KEY]: { label: 'Avisos de facturación', description: 'Interruptor general de cobranza', icon: BellRing },
};

export const AUDIT_LABELS = {
  PAYMENT_METHOD_CASH: SETTING_CATALOG.CASH.label,
  PAYMENT_METHOD_BANK_TRANSFER: SETTING_CATALOG.BANK_TRANSFER.label,
  PAYMENT_METHOD_GATEWAY: SETTING_CATALOG.GATEWAY.label,
  [NOTIFICATIONS_KEY]: SETTING_CATALOG[NOTIFICATIONS_KEY].label,
};

export const PAYMENT_METHOD_CODES = ['CASH', 'BANK_TRANSFER', 'GATEWAY'];

export function toSettingEntries(settings) {
  const methods = PAYMENT_METHOD_CODES.map((code) => settings.paymentMethods.find((method) => method.code === code)).filter(Boolean);
  return {
    methods: methods.map((method) => ({ key: method.code, ...method })),
    notifications: { key: NOTIFICATIONS_KEY, ...settings.billingNotifications },
  };
}

export function stateLabel(enabled) {
  return enabled ? 'Activo' : 'Inactivo';
}

export function buildChangeRows(entry) {
  const next = !entry.enabled;
  return [
    {
      key: entry.key,
      label: SETTING_CATALOG[entry.key].label,
      today: stateLabel(entry.enabled),
      next: stateLabel(next),
      difference: next ? 'Se activa' : 'Se desactiva',
      unchanged: false,
    },
  ];
}

export function dialogTitle(entry) {
  return `${entry.enabled ? 'Desactivar' : 'Activar'} ${SETTING_CATALOG[entry.key].label}`;
}

export function isStaleSetting(error) {
  return error?.status === 409 && error.code === 'STALE_INSTITUTE_SETTING_VERSION';
}

export function isLastDigitalMethod(error) {
  return error?.status === 409 && error.code === 'LAST_DIGITAL_PAYMENT_METHOD_REQUIRED';
}

function staleMessage(details) {
  const who = details?.changedByName;
  const when = details?.changedAt ? formatDateTime(details.changedAt) : '';
  if (who && when) return `Lo cambió ${who} el ${when}.`;
  if (who) return `Lo cambió ${who}.`;
  if (when) return `Se cambió el ${when}.`;
  return 'Alguien lo cambió mientras lo editabas.';
}

function lastDigitalMessage(details) {
  const open = Number(details?.openOnlineInvoices);
  if (!Number.isFinite(open) || open <= 0) return 'No se puede desactivar el último medio digital.';
  const invoices = open === 1 ? 'hay 1 cobro online abierto' : `hay ${open} cobros online abiertos`;
  return `No se puede desactivar el último medio digital mientras ${invoices}.`;
}

export function settingErrorMessage(error) {
  if (isStaleSetting(error)) return staleMessage(error.details);
  if (isLastDigitalMethod(error)) return lastDigitalMessage(error.details);
  if (error?.status === 403) return 'Solo el director puede hacer este cambio.';
  return error?.message || 'No pudimos guardar el cambio. Probá de nuevo.';
}

export function toChangeRequest(entry, reason) {
  return { enabled: !entry.enabled, reason: reason.trim() };
}
