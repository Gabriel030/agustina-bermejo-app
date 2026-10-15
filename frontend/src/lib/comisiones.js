export const ESTADO_CONFIG_LABEL = {
  DRAFT: { label: 'Borrador', tone: 'neutral' },
  PENDING_APPROVAL: { label: 'Pendiente', tone: 'warning' },
  APPROVED: { label: 'Aprobado', tone: 'success' },
  REJECTED: { label: 'Rechazado', tone: 'danger' },
};

export const ROL_INSTRUCTOR_LABEL = {
  LEAD: 'Titular',
  ASSISTANT: 'Adjunto',
  PRACTICAL: 'Práctico',
  LAB: 'Laboratorio',
  GUEST: 'Invitado',
};

function hoy() {
  return new Date();
}

export function agruparComisionesPorFase(comisiones) {
  const ahora = hoy();
  const bandejaDeAprobacion = [];
  const enCurso = [];
  const cerradas = [];

  for (const comision of comisiones) {
    const fin = comision.termEndDate ? new Date(comision.termEndDate) : null;
    const yaCerro = fin && ahora > fin;

    if (yaCerro) {
      cerradas.push(comision);
    } else if (ESTADOS_EN_BANDEJA.has(comision.status)) {
      bandejaDeAprobacion.push(comision);
    } else {
      enCurso.push(comision);
    }
  }

  return { bandejaDeAprobacion, enCurso, cerradas };
}

const ESTADOS_EN_BANDEJA = new Set(['DRAFT', 'PENDING_APPROVAL', 'REJECTED']);

export function comisionCerrada(termEndDate) {
  return !!termEndDate && hoy() > new Date(termEndDate);
}

export function diasHastaVencimientoMasProximo(comisiones) {
  const ahora = hoy();
  const dias = comisiones
    .filter((c) => c.status === 'DRAFT' && c.termStartDate)
    .map((c) => Math.ceil((new Date(c.termStartDate) - ahora) / (1000 * 60 * 60 * 24)));
  const futuros = dias.filter((d) => d >= 0);
  if (futuros.length) return Math.min(...futuros);
  return dias.length ? 'vencido' : null;
}

export function parsearFecha(fechaIso) {
  if (!fechaIso) return null;
  const soloDia = /^(\d{4})-(\d{2})-(\d{2})$/.exec(fechaIso);
  if (soloDia) return new Date(Number(soloDia[1]), Number(soloDia[2]) - 1, Number(soloDia[3]));
  return new Date(fechaIso);
}

export function formatearFecha(fechaIso) {
  if (!fechaIso) return null;
  return parsearFecha(fechaIso).toLocaleDateString('es-AR', { day: 'numeric', month: 'short', year: 'numeric' });
}
