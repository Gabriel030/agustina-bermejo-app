import { StatusPill } from '@/components/StatusPill';

// Pill de estado de una ProgramEnrollmentApplication (PENDING/APPROVED/REJECTED)
// — reusado por MisSolicitudes.jsx y DetalleSolicitud.jsx. Envuelve StatusPill
// (superficie base compartida) con el vocabulario propio de esta entidad.
// PENDING_REVIEW/APPROVED/REJECTED (Épica 7 Fase 3, PaymentReceiptStatus) se
// suman acá para las solicitudes de curso — mismo componente, mismo criterio
// visual (pendiente/aprobado/rechazado), solo cambia el vocabulario del status
// que manda cada tipo de solicitud.
const ESTADOS = {
  PENDING: { label: 'Pendiente', tone: 'warning' },
  PENDING_REVIEW: { label: 'En revisión', tone: 'warning' },
  APPROVED: { label: 'Aprobada', tone: 'success' },
  REJECTED: { label: 'Rechazada', tone: 'danger' },
};

export function EstadoSolicitudBadge({ status }) {
  const estado = ESTADOS[status] ?? { label: status, tone: 'neutral' };
  return <StatusPill tone={estado.tone}>{estado.label}</StatusPill>;
}
