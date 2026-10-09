import { admissionPaymentPhase } from '@/lib/admissionInvoice';

const STEPS = [
  { key: 'submitted', label: 'Enviada' },
  { key: 'review', label: 'En revisión' },
  { key: 'payment', label: 'Matrícula' },
  { key: 'enrolled', label: 'Inscripto' },
];

const REJECTED_STEPS = [
  { key: 'submitted', label: 'Enviada' },
  { key: 'rejected', label: 'Rechazada' },
];

export function applicationProgress(solicitud) {
  if (solicitud.status === 'REJECTED') {
    return {
      pasos: REJECTED_STEPS,
      pasoActual: null,
      pasosVisitados: ['submitted', 'rejected'],
      stepValidity: { submitted: true, rejected: false },
    };
  }
  const phase = admissionPaymentPhase(solicitud);
  if (phase === 'ENROLLED') {
    return {
      pasos: STEPS,
      pasoActual: null,
      pasosVisitados: STEPS.map((step) => step.key),
      stepValidity: { submitted: true, review: true, payment: true, enrolled: true },
    };
  }
  if (solicitud.status !== 'APPROVED') {
    return { pasos: STEPS, pasoActual: 'review' };
  }
  return { pasos: STEPS, pasoActual: phase === 'CONFIRMING_ENROLLMENT' ? 'enrolled' : 'payment' };
}
