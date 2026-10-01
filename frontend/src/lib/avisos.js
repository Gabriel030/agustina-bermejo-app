export function instanciaLabel(segmentKey, index) {
  if (segmentKey.startsWith('EXAM')) return `${index}° parcial`;
  if (segmentKey.startsWith('TP')) return `TP ${index}`;
  if (segmentKey.startsWith('FINAL')) {
    const ordinals = ['1er', '2do', '3er', '4to'];
    return `${ordinals[index - 1] ?? index + '°'} llamado`;
  }
  return `Instancia ${index}`;
}

export const CUANTIFICADOR_LABEL = {
  ANY: 'alguno de los seleccionados',
  ALL: 'todos los seleccionados',
};

export const CATEGORIA_AVISO_LABEL = {
  EVALUATION: 'Evaluación',
  REMINDER: 'Recordatorio',
  MATERIAL: 'Material',
  SCHEDULING: 'Cronograma',
  ADMIN: 'Administrativo',
};

export const ESTADO_AVISO_LABEL = {
  SCHEDULED: { label: 'Programado', tone: 'warning' },
  SENT: { label: 'Enviado', tone: 'success' },
};

export function estadoDeVigencia(aviso, hoy = new Date()) {
  if (aviso.status === 'SCHEDULED') return ESTADO_AVISO_LABEL.SCHEDULED;
  if (!aviso.expiresAt) return { label: 'Vigente', tone: 'success' };
  const vence = new Date(`${aviso.expiresAt}T23:59:59`);
  return vence < hoy ? { label: 'Vencido', tone: 'neutral' } : { label: 'Vigente', tone: 'success' };
}

export function esAudienciaCompleta(aviso) {
  return aviso.audienceLabel === 'Toda la comisión';
}

export function formatearFechaHora(valorIso) {
  if (!valorIso) return null;
  return new Date(valorIso).toLocaleString('es-AR', {
    day: '2-digit',
    month: '2-digit',
    year: 'numeric',
    hour: '2-digit',
    minute: '2-digit',
  });
}

export function formatearFechaCorta(valorIso) {
  if (!valorIso) return null;
  return new Date(valorIso).toLocaleDateString('es-AR', { day: '2-digit', month: '2-digit', year: 'numeric' });
}
