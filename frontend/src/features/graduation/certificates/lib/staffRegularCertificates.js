export const REASON_MIN_LENGTH = 10;

const CERTIFICATE_STATUS = {
  VALID: { label: 'Vigente', tone: 'success' },
  EXPIRED: { label: 'Vencida', tone: 'neutral' },
  REVOKED: { label: 'Revocada', tone: 'danger' },
};

export function certificateStatusOf(status) {
  return CERTIFICATE_STATUS[status] ?? { label: status, tone: 'neutral' };
}

export function studentSectionPath(role, studentId, view) {
  const suffix = view ? `?view=${view}` : '';
  return `/${role}/alumnos/${studentId}${suffix}`;
}

export function saveBlob(blob, filename) {
  const url = URL.createObjectURL(blob);
  const link = document.createElement('a');
  link.href = url;
  link.download = filename;
  link.click();
  URL.revokeObjectURL(url);
}
