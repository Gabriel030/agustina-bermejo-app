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

// Épica 7 Fase 3: montos de matrícula/cuota en el Detalle de carrera/curso y el
// wizard de pago. null (Director no configuró el precio todavía) se maneja en
// el componente que lo llama, no acá.
export function formatMonto(amount) {
  if (amount == null) return '';
  return new Intl.NumberFormat('es-AR', { style: 'currency', currency: 'ARS', maximumFractionDigits: 0 }).format(
    amount,
  );
}
