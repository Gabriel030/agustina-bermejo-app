import { describeError } from '@/api/describeError';

const KIND_LABEL = { PROGRAM: 'Carrera', COURSE: 'Curso' };

const STATUS_PILL = {
  VALID: { label: 'Vigente', tone: 'success' },
  REVOKED: { label: 'Revocado', tone: 'danger' },
};

export function ownCertificateKindLabel(kind) {
  return KIND_LABEL[kind] ?? kind;
}

export function ownCertificateStatusPill(status) {
  return STATUS_PILL[status] ?? { label: status, tone: 'neutral' };
}

export function ownCertificateActionError(error) {
  return describeError(error).message;
}

export function ownCertificatePreviewLabel(certificate) {
  return `Vista previa del certificado de ${certificate.titleName}`;
}
