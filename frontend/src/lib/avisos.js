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
    hour12: false,
  });
}

export function formatearFechaCorta(valorIso) {
  if (!valorIso) return null;
  return new Date(valorIso).toLocaleDateString('es-AR', { day: '2-digit', month: '2-digit', year: 'numeric' });
}

export const GRUPOS_DE_SEGMENTO = [
  { titulo: 'Toda la comisión', keys: ['ALL'] },
  { titulo: 'Estado de cursada', keys: ['MUST_TAKE_FINAL', 'PROMOTED', 'PASSED', 'FAILED_COURSE'] },
  { titulo: 'Parciales', keys: ['EXAM_FAILED', 'EXAM_PASSED', 'EXAM_MISSING'] },
  { titulo: 'Trabajos prácticos', keys: ['TP_FAILED', 'TP_MISSING', 'TP_PASSED'] },
  { titulo: 'Final', keys: ['FINAL_PASSED', 'FINAL_FAILED'] },
  { titulo: 'Asistencia', keys: ['ABSENCES'] },
];

export function nombreDeInstancia(key, numero) {
  if (key.startsWith('TP_')) return `TP${numero}`;
  if (key.startsWith('FINAL_')) return `${numero}° llamado`;
  return `${numero}° parcial`;
}

export function agruparSegmentos(segmentos) {
  const porKey = new Map((segmentos ?? []).map((s) => [s.key, s]));
  return GRUPOS_DE_SEGMENTO.map((grupo) => ({
    titulo: grupo.titulo,
    opciones: grupo.keys.map((key) => porKey.get(key)).filter(Boolean),
  })).filter((grupo) => grupo.opciones.length > 0);
}

export function textoDeAlcance(alcanzados, total) {
  if (alcanzados === 1) return `Lo ve 1 de los ${total} alumnos de la comisión.`;
  return `Lo ven ${alcanzados} de los ${total} alumnos de la comisión.`;
}

export function hintDePrivacidad({ alcanzados, programado }) {
  const cuantos = alcanzados === 1 ? '1 alumno' : `${alcanzados} alumnos`;
  if (programado) {
    return `Cuando salga, solo lo van a ver los alumnos que cumplan esa condición — hoy serían ${alcanzados}. No hace falta aclarar a quién le hablás, escribile directo.`;
  }
  return `Solo lo ${alcanzados === 1 ? 've' : 'ven'} ${alcanzados === 1 ? 'el' : 'los'} ${cuantos} del grupo que elegiste. No hace falta aclarar a quién le hablás — escribile directo.`;
}

export function fechaEnPalabras(valorIso) {
  if (!valorIso) return null;
  const fecha = new Date(valorIso);
  const diaSemana = fecha.toLocaleDateString('es-AR', { weekday: 'long' });
  const fechaCorta = fecha.toLocaleDateString('es-AR', { day: 'numeric', month: 'long' });
  const hora = fecha.toLocaleTimeString('es-AR', { hour: '2-digit', minute: '2-digit', hour12: false });
  return `${diaSemana} ${fechaCorta} a las ${hora}, hora de Argentina`;
}
