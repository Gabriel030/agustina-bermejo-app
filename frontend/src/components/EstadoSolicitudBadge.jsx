// Pill de estado de una ProgramEnrollmentApplication (PENDING/APPROVED/REJECTED)
// — reusado por MisSolicitudes.jsx y DetalleSolicitud.jsx, mismo criterio de
// "no reimplementar la superficie base" que GlassCard.
// PENDING_REVIEW/APPROVED/REJECTED (Épica 7 Fase 3, PaymentReceiptStatus) se
// suman acá para las solicitudes de curso — mismo componente, mismo criterio
// visual (pendiente/aprobado/rechazado), solo cambia el vocabulario del status
// que manda cada tipo de solicitud.
const ESTADOS = {
  PENDING: { label: 'Pendiente', bg: 'bg-warning-bg', text: 'text-warning' },
  PENDING_REVIEW: { label: 'En revisión', bg: 'bg-warning-bg', text: 'text-warning' },
  APPROVED: { label: 'Aprobada', bg: 'bg-success-bg', text: 'text-success' },
  REJECTED: { label: 'Rechazada', bg: 'bg-danger-bg', text: 'text-danger' },
};

export function EstadoSolicitudBadge({ status }) {
  const estado = ESTADOS[status] ?? { label: status, bg: 'bg-surface', text: 'text-ink-faint' };
  return (
    <span
      className={`inline-flex items-center gap-1.5 rounded-full px-3 py-1 text-[11.5px] font-bold ${estado.bg} ${estado.text}`}
    >
      {estado.label}
    </span>
  );
}
