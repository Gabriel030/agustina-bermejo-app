export function attendancePercentToPoints(value) {
  if (typeof value === 'number') return value;
  if (value && typeof value.fraction === 'number') return value.fraction * 100;
  return null;
}
