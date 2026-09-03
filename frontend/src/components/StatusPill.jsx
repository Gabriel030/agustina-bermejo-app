import { cn } from '@/lib/utils';

// Pill de estado con color semántico — antes reescrito a mano en cada pantalla
// (EstadoSolicitudBadge, el estado de pago de Inicio, el badge de materias en
// curso y la condición de "alumno regular" en Mis carreras). `tone` decide el
// color; `dot` agrega el puntito antes del texto (usado en Inicio para el
// estado de pago, no en el resto).
const TONOS = {
  success: 'bg-success-bg text-success',
  warning: 'bg-warning-bg text-warning',
  danger: 'bg-danger-bg text-danger',
  accent: 'bg-accent-soft text-primary',
  neutral: 'bg-neutral-bg text-ink-faint',
};

const COLOR_DOT = {
  success: 'bg-success',
  warning: 'bg-warning',
  danger: 'bg-danger',
  accent: 'bg-brand',
  neutral: 'bg-neutral-ink',
};

export function StatusPill({ tone = 'neutral', dot = false, className, children }) {
  return (
    <span
      className={cn(
        'inline-flex w-fit items-center gap-1.5 rounded-full px-3 py-1 text-[11px] font-bold',
        TONOS[tone],
        className,
      )}
    >
      {dot && <span className={cn('h-2 w-2 rounded-full', COLOR_DOT[tone])} />}
      {children}
    </span>
  );
}
