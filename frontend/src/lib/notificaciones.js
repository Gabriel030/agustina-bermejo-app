export const TIPO_NOTIFICACION_LABEL = {
  SECTION_EVAL_CONFIG_PROPOSED: 'Nueva propuesta pendiente',
  SECTION_EVAL_CONFIG_APPROVED: 'Configuración aprobada',
  SECTION_EVAL_CONFIG_REJECTED: 'Configuración rechazada',
  SECTION_EVAL_CONFIG_REMINDER: 'Recordatorio',
};

export const TONO_NOTIFICACION = {
  SECTION_EVAL_CONFIG_PROPOSED: 'warning',
  SECTION_EVAL_CONFIG_APPROVED: 'success',
  SECTION_EVAL_CONFIG_REJECTED: 'danger',
  SECTION_EVAL_CONFIG_REMINDER: 'accent2',
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
