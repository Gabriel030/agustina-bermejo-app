export const REGULAR_VIEW = {
  AVAILABLE: 'AVAILABLE',
  PENDING: 'PENDING',
  VALID: 'VALID',
  EXPIRED: 'EXPIRED',
  UNAVAILABLE_ATTENDANCE: 'UNAVAILABLE_ATTENDANCE',
  UNAVAILABLE_PAYMENTS: 'UNAVAILABLE_PAYMENTS',
  UNAVAILABLE_ENROLLMENT: 'UNAVAILABLE_ENROLLMENT',
  UNAVAILABLE_REJECTED: 'UNAVAILABLE_REJECTED',
};

export const REGULAR_VIEW_PILL = {
  AVAILABLE: { tone: 'accent', label: 'Disponible' },
  PENDING: { tone: 'warning', label: 'En revisión' },
  VALID: { tone: 'success', label: 'Vigente' },
  EXPIRED: { tone: 'neutral', label: 'Vencida' },
  UNAVAILABLE_ATTENDANCE: { tone: 'neutral', label: 'No disponible' },
  UNAVAILABLE_PAYMENTS: { tone: 'neutral', label: 'No disponible' },
  UNAVAILABLE_ENROLLMENT: { tone: 'neutral', label: 'No disponible' },
  UNAVAILABLE_REJECTED: { tone: 'neutral', label: 'No disponible' },
};

const UNAVAILABLE_VIEW = {
  ATTENDANCE: REGULAR_VIEW.UNAVAILABLE_ATTENDANCE,
  PAYMENTS: REGULAR_VIEW.UNAVAILABLE_PAYMENTS,
  ENROLLMENT: REGULAR_VIEW.UNAVAILABLE_ENROLLMENT,
  REJECTED: REGULAR_VIEW.UNAVAILABLE_REJECTED,
};

export function pastCertificates(state) {
  return (state?.history ?? [])
    .filter((certificate) => certificate.status === 'EXPIRED' || certificate.status === 'REVOKED')
    .sort((a, b) => new Date(b.issuedAt) - new Date(a.issuedAt));
}

export function deriveRegularView(state) {
  switch (state.state) {
    case 'VALID':
      return REGULAR_VIEW.VALID;
    case 'PENDING':
      return REGULAR_VIEW.PENDING;
    case 'UNAVAILABLE':
      return UNAVAILABLE_VIEW[state.unavailableType] ?? REGULAR_VIEW.UNAVAILABLE_ENROLLMENT;
    default:
      return pastCertificates(state)[0]?.status === 'EXPIRED' ? REGULAR_VIEW.EXPIRED : REGULAR_VIEW.AVAILABLE;
  }
}

export function canRequestRegular(view) {
  return view === REGULAR_VIEW.AVAILABLE || view === REGULAR_VIEW.EXPIRED;
}

export function regularRequestErrorMessage(error) {
  if (error?.code === 'NOT_REGULAR') return 'Ahora no figurás como alumno regular. Revisá tu asistencia y tus pagos.';
  if (error?.code === 'REGULAR_CERTIFICATE_ALREADY_ISSUED') return 'Ya tenés una constancia vigente. Actualizá la pantalla.';
  return error?.message ?? 'No pudimos enviar tu pedido. Probá de nuevo.';
}

export function regularDownloadErrorMessage(error) {
  if (error?.code === 'CERTIFICATE_EXPIRED') return 'La constancia venció. Pedí una nueva.';
  return error?.message ?? 'No pudimos descargar la constancia. Probá de nuevo.';
}
