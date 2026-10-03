export function formatCourseDuration(months) {
  if (!months || months < 1) return 'Duración a definir';
  return `${months} ${months === 1 ? 'mes' : 'meses'}`;
}
