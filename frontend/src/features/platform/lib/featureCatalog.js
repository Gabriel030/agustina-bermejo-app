import { BarChart3, Banknote, Bell, Building2, CreditCard, FileText, Landmark, Receipt } from 'lucide-react';

export const BILLING_CODE = 'BILLING';

export const FEATURE_CATALOG = {
  BILLING: { label: 'Facturación', description: 'Cómo cobra el instituto y cómo avisa de las cuotas.', icon: Receipt },
  FISCAL_INVOICING: { label: 'Factura electrónica', description: 'Emite la factura electrónica de cada pago.', icon: FileText, dependsOnBilling: true },
  PAYMENT_METHOD_CASH: { label: 'Efectivo', description: 'Se paga en secretaría y se registra a mano.', icon: Banknote, dependsOnBilling: true, paymentMethod: true },
  PAYMENT_METHOD_BANK_TRANSFER: { label: 'Transferencia bancaria', description: 'El alumno transfiere y sube el comprobante.', icon: Landmark, dependsOnBilling: true, paymentMethod: true },
  PAYMENT_METHOD_GATEWAY: { label: 'Pasarela de pago (Mercado Pago)', description: 'Pago online con tarjeta o dinero en cuenta.', icon: CreditCard, dependsOnBilling: true, paymentMethod: true },
  BILLING_NOTIFICATIONS: { label: 'Avisos de cuotas', description: 'Avisa por mail cuándo vence una cuota.', icon: Bell, dependsOnBilling: true },
  EXTERNAL_BILLING: { label: 'Facturación externa', description: 'Importa facturas desde otro sistema.', icon: Building2, dependsOnBilling: true, blocked: true },
  ANALYTICS: { label: 'Estadísticas', description: 'Gráficos de matrícula y cobros.', icon: BarChart3 },
};

export const FEATURE_GROUPS = [
  {
    id: 'payments',
    title: 'Cobros y pagos',
    codes: ['BILLING', 'FISCAL_INVOICING', 'PAYMENT_METHOD_CASH', 'PAYMENT_METHOD_BANK_TRANSFER', 'PAYMENT_METHOD_GATEWAY', 'BILLING_NOTIFICATIONS', 'EXTERNAL_BILLING'],
  },
  { id: 'reports', title: 'Reportes y estadísticas', codes: ['ANALYTICS'] },
];

export const BILLING_MODES = ['OFF', 'MIRROR', 'FULL'];

export const BILLING_MODE_INFO = {
  OFF: { label: 'Apagada', description: 'La app no cobra ni muestra nada de facturación.', tone: 'neutral', effects: [false, false, false] },
  MIRROR: { label: 'Calcula sin cobrar', description: 'Calcula y emite las cuotas, pero no cobra. Los alumnos no ven precios.', tone: 'accent', effects: [true, false, false] },
  FULL: { label: 'Completa', description: 'Cobra de verdad. Los alumnos ven precios y reciben cuotas.', tone: 'success', effects: [true, true, true] },
};

export const BILLING_EFFECT_QUESTIONS = ['Calcula y emite las cuotas', 'Cobra a los alumnos', 'Los alumnos ven precios y cuotas'];

export function modeAvailability(currentMode, targetMode) {
  if (currentMode === targetMode) return { available: true };
  if (currentMode === 'FULL') return { available: false, reason: 'Requiere la verificación previa' };
  if (targetMode === 'FULL') return { available: false, reason: 'Requiere el proceso de corte' };
  return { available: true };
}

export function isFeatureAvailable(code, features) {
  if (!FEATURE_CATALOG[code]?.dependsOnBilling) return true;
  return features.find((feature) => feature.code === BILLING_CODE)?.mode === 'FULL';
}
