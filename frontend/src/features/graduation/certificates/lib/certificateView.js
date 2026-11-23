const MONTHS = ['enero', 'febrero', 'marzo', 'abril', 'mayo', 'junio', 'julio', 'agosto', 'septiembre', 'octubre', 'noviembre', 'diciembre'];

const DOCUMENT_TITLES = {
  GRADUATION_CERTIFICATE: 'Certificado de egreso',
  REGULAR_STUDENT_CERTIFICATE: 'Constancia de alumno regular',
  APPROVED_SUBJECTS_CERTIFICATE: 'Constancia de materias aprobadas',
  EXAM_REGISTRATION_CERTIFICATE: 'Constancia de inscripción a examen',
  EXAM_CERTIFICATE: 'Constancia de examen',
};

function parseDay(value) {
  const match = /^(\d{4})-(\d{2})-(\d{2})/.exec(value ?? '');
  return match ? { year: match[1], month: Number(match[2]), day: Number(match[3]), rawMonth: match[2], rawDay: match[3] } : null;
}

export function formatDay(value) {
  const parts = parseDay(value);
  return parts ? `${parts.rawDay}/${parts.rawMonth}/${parts.year}` : '';
}

export function formatLongDay(value) {
  const parts = parseDay(value);
  return parts ? `${parts.day} de ${MONTHS[parts.month - 1]} de ${parts.year}` : '';
}

export function formatDayAndTime(date) {
  const pad = (number) => String(number).padStart(2, '0');
  return `${pad(date.getDate())}/${pad(date.getMonth() + 1)}/${date.getFullYear()} ${pad(date.getHours())}:${pad(date.getMinutes())}`;
}

export function documentTitle(certificate) {
  return DOCUMENT_TITLES[certificate.documentType] ?? 'Documento';
}

const withDate = (prefix, value, suffix) => (value ? `${prefix} ${formatLongDay(value)}${suffix}` : '');

export function verdictOf(certificate) {
  if (certificate.status === 'REVOKED') {
    return {
      icon: 'ban',
      tone: 'danger',
      badge: 'Revocada',
      title: 'Este documento fue anulado por el instituto',
      message: certificate.revokedAt ? `No tiene validez desde el ${formatLongDay(certificate.revokedAt)}.` : 'No tiene validez.',
    };
  }
  if (certificate.status === 'REPLACED') {
    return {
      icon: 'alert',
      tone: 'warning',
      badge: 'Reemplazada',
      title: 'Este documento fue reemplazado por una versión nueva',
      message: 'Pedí a la persona interesada la versión vigente.',
    };
  }
  if (certificate.status === 'EXPIRED') {
    return {
      icon: 'alert',
      tone: 'warning',
      badge: 'Auténtica · vencida',
      title: 'Este documento es auténtico, pero su vigencia terminó',
      message: `${withDate('Venció el', certificate.expiresAt, '. ')}Pedí a la persona interesada una constancia nueva.`,
    };
  }
  return {
    icon: 'ok',
    tone: 'success',
    badge: 'Auténtica y vigente',
    title: 'Este documento fue emitido por el instituto',
    message: 'Coincide con el registro oficial y sigue vigente.',
  };
}

function validityOf(certificate) {
  if (certificate.status === 'REVOKED') return withDate('Revocada el', certificate.revokedAt, '');
  if (certificate.status === 'REPLACED') return withDate('Reemplazada el', certificate.replacedAt, '');
  if (certificate.status === 'EXPIRED') return withDate('Venció el', certificate.expiresAt, '');
  return certificate.expiresAt ? `Vigente hasta el ${formatLongDay(certificate.expiresAt)}` : 'Vigente';
}

export function detailRows(certificate) {
  const rows = [
    { label: 'Documento', value: documentTitle(certificate) },
    { label: 'A nombre de', value: certificate.holderFullName, masked: true },
    { label: 'DNI', value: certificate.maskedDocument, masked: true },
  ];
  if (certificate.registryNumber) rows.push({ label: 'Registro', value: certificate.registryNumber });
  rows.push({ label: 'Emitido el', value: formatLongDay(certificate.issuedAt) });
  const validity = validityOf(certificate);
  if (validity) rows.push({ label: 'Vigencia', value: validity });
  return rows;
}
