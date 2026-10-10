export const TIPO_NOTIFICACION_LABEL = {
  SECTION_EVAL_CONFIG_PROPOSED: 'Nueva propuesta pendiente',
  SECTION_EVAL_CONFIG_APPROVED: 'Configuración aprobada',
  SECTION_EVAL_CONFIG_REJECTED: 'Configuración rechazada',
  SECTION_EVAL_CONFIG_REMINDER: 'Recordatorio',
  ATTENDANCE_CHANGE_REQUEST_APPROVED: 'Cambio de asistencia aprobado',
  ATTENDANCE_CHANGE_REQUEST_REJECTED: 'Cambio de asistencia rechazado',
  GRADUATION_REQUEST_APPROVED: 'Egreso aprobado',
  GRADUATION_REQUEST_REJECTED: 'Egreso rechazado',
  DIRECTOR_APPROVAL_REQUEST_APPROVED: 'Excepción aprobada',
  DIRECTOR_APPROVAL_REQUEST_REJECTED: 'Excepción rechazada',
  COURSE_WAITLIST_EXPIRED: 'Lista de espera vencida',
  SUSPENSION_LIFTED: 'Suspensión levantada',
  PAYMENT_RECEIPT_APPROVED: 'Pago acreditado',
  PAYMENT_RECEIPT_REJECTED: 'Comprobante rechazado',
  ANNOUNCEMENT_PUBLISHED: 'Nuevo aviso',
  ANNOUNCEMENT_UPDATED: 'Aviso actualizado',
  PROGRAM_APPLICATION_APPROVED: 'Solicitud aprobada',
  PROGRAM_ENROLLMENT_STALLED: 'Inscripción pendiente de revisión',
  PAYMENT_LEFT_AS_ACCOUNT_CREDIT: 'Pago a saldo a favor',
  PAYMENT_RECEIPT_APPLIED_TO_OLDER_DEBT: 'Pago aplicado a deuda anterior',
  CREDIT_NOTE_ISSUED: 'Nota de crédito emitida',
  LATE_FEE_ISSUED: 'Recargo por mora emitido',
  LATE_FEE_ISSUED_AFTER_FISCAL_WINDOW: 'Recargo emitido fuera de plazo',
};

export const TONO_NOTIFICACION = {
  SECTION_EVAL_CONFIG_PROPOSED: 'warning',
  SECTION_EVAL_CONFIG_APPROVED: 'success',
  SECTION_EVAL_CONFIG_REJECTED: 'danger',
  SECTION_EVAL_CONFIG_REMINDER: 'accent2',
  ATTENDANCE_CHANGE_REQUEST_APPROVED: 'success',
  ATTENDANCE_CHANGE_REQUEST_REJECTED: 'danger',
  GRADUATION_REQUEST_APPROVED: 'success',
  GRADUATION_REQUEST_REJECTED: 'danger',
  DIRECTOR_APPROVAL_REQUEST_APPROVED: 'success',
  DIRECTOR_APPROVAL_REQUEST_REJECTED: 'danger',
  COURSE_WAITLIST_EXPIRED: 'warning',
  SUSPENSION_LIFTED: 'success',
  PAYMENT_RECEIPT_APPROVED: 'success',
  PAYMENT_RECEIPT_REJECTED: 'danger',
  ANNOUNCEMENT_PUBLISHED: 'accent2',
  ANNOUNCEMENT_UPDATED: 'accent2',
  PROGRAM_APPLICATION_APPROVED: 'success',
  PROGRAM_ENROLLMENT_STALLED: 'warning',
  PAYMENT_LEFT_AS_ACCOUNT_CREDIT: 'accent2',
  PAYMENT_RECEIPT_APPLIED_TO_OLDER_DEBT: 'warning',
  CREDIT_NOTE_ISSUED: 'accent2',
  LATE_FEE_ISSUED: 'warning',
  LATE_FEE_ISSUED_AFTER_FISCAL_WINDOW: 'danger',
};

export function tiempoRelativo(fechaIso) {
  const diffMs = Date.now() - new Date(fechaIso).getTime();
  const minutos = Math.floor(diffMs / 60000);
  if (minutos < 1) return 'recién';
  if (minutos < 60) return `hace ${minutos} min`;
  const horas = Math.floor(minutos / 60);
  if (horas < 24) return `hace ${horas} h`;
  const dias = Math.floor(horas / 24);
  return `hace ${dias} d`;
}
