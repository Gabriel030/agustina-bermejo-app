export const SHIFT_LABELS = {
  MORNING: 'Mañana',
  AFTERNOON: 'Tarde',
  EVENING: 'Noche',
};

export function shiftLabel(shift) {
  return SHIFT_LABELS[shift] ?? 'Sin turno';
}
