export const TIPO_NOTIFICACION_LABEL = {
  SECTION_EVAL_CONFIG_PROPOSED: 'Nueva propuesta pendiente',
  SECTION_EVAL_CONFIG_APPROVED: 'Configuración aprobada',
  SECTION_EVAL_CONFIG_REJECTED: 'Configuración rechazada',
  SECTION_EVAL_CONFIG_REMINDER: 'Recordatorio',
  GRADUATION_REQUEST_APPROVED: 'Egreso aprobado',
  GRADUATION_REQUEST_REJECTED: 'Egreso rechazado',
  DIRECTOR_APPROVAL_REQUEST_APPROVED: 'Excepción aprobada',
  DIRECTOR_APPROVAL_REQUEST_REJECTED: 'Excepción rechazada',
  ANNOUNCEMENT_PUBLISHED: 'Nuevo aviso',
  ANNOUNCEMENT_UPDATED: 'Aviso actualizado',
};

export const TONO_NOTIFICACION = {
  SECTION_EVAL_CONFIG_PROPOSED: 'warning',
  SECTION_EVAL_CONFIG_APPROVED: 'success',
  SECTION_EVAL_CONFIG_REJECTED: 'danger',
  SECTION_EVAL_CONFIG_REMINDER: 'accent2',
  GRADUATION_REQUEST_APPROVED: 'success',
  GRADUATION_REQUEST_REJECTED: 'danger',
  DIRECTOR_APPROVAL_REQUEST_APPROVED: 'success',
  DIRECTOR_APPROVAL_REQUEST_REJECTED: 'danger',
  ANNOUNCEMENT_PUBLISHED: 'accent2',
  ANNOUNCEMENT_UPDATED: 'accent2',
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
