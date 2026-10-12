import { StatusPill } from '@/components/StatusPill';
import { admissionPaymentPhase } from '@/lib/admissionInvoice';

// Pill de estado de una ProgramEnrollmentApplication (PENDING/APPROVED/REJECTED)
// — reusado por MisSolicitudes.jsx y DetalleSolicitud.jsx. Envuelve StatusPill
// (superficie base compartida) con el vocabulario propio de esta entidad.
// PENDING_REVIEW/APPROVED/REJECTED (Épica 7 Fase 3, PaymentReceiptStatus) se
// suman acá para las solicitudes de curso — mismo componente, mismo criterio
// visual (pendiente/aprobado/rechazado), solo cambia el vocabulario del status
// que manda cada tipo de solicitud. Para las solicitudes de carrera aprobadas,
// el estado de la matrícula de ingreso (D-227/D-229) refina el badge.
const ESTADOS = {
  PENDING: { label: 'En revisión', tone: 'warning' },
  PENDING_REVIEW: { label: 'En revisión', tone: 'warning' },
  APPROVED: { label: 'Aprobada', tone: 'success' },
  APPLIED_TO_DEBT: { label: 'Aplicado a deuda anterior', tone: 'warning' },
  REJECTED: { label: 'Rechazada', tone: 'danger' },
  WAITLISTED: { label: 'En lista de espera', tone: 'neutral' },
};

const ESTADOS_DE_MATRICULA = {
  PENDING: { label: 'Pagá la matrícula', tone: 'warning' },
  UNDER_REVIEW: { label: 'Pago en revisión', tone: 'warning' },
  OVERDUE: { label: 'Matrícula vencida', tone: 'danger' },
  CONFIRMING_ENROLLMENT: { label: 'Confirmando inscripción', tone: 'warning' },
  ENROLLED: { label: 'Inscripto', tone: 'success' },
};

export function EstadoSolicitudBadge({ solicitud }) {
  const { status } = solicitud;
  const fase = admissionPaymentPhase(solicitud);
  const estado = (fase && ESTADOS_DE_MATRICULA[fase]) ?? ESTADOS[status] ?? { label: status, tone: 'neutral' };
  return <StatusPill tone={estado.tone}>{estado.label}</StatusPill>;
}
