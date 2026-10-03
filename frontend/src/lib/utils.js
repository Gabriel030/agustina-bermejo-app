import { clsx } from "clsx";
import { twMerge } from "tailwind-merge"

export function cn(...inputs) {
  return twMerge(clsx(inputs));
}

// Formato "2 mar 2026" usado en las bandejas con fecha (Mis solicitudes,
// Detalle de solicitud) — sin librería de fechas nueva, Intl alcanza.
export function formatFecha(isoString) {
  if (!isoString) return '';
  return new Intl.DateTimeFormat('es-AR', { day: 'numeric', month: 'short', year: 'numeric' }).format(
    new Date(isoString),
  );
}

export function formatMoney(money) {
  if (money == null) return '';
  return new Intl.NumberFormat('es-AR', {
    style: 'currency',
    currency: money.currency,
    minimumFractionDigits: 2,
    maximumFractionDigits: 2,
  }).format(Number(money.amount));
}

export function moneyValue(money) {
  return money == null ? 0 : Number(money.amount);
}
