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
    const inicio = comision.termStartDate ? new Date(comision.termStartDate) : null;
    const fin = comision.termEndDate ? new Date(comision.termEndDate) : null;
    const yaCerro = fin && ahora > fin;
    const yaArranco = inicio && ahora >= inicio;

    if (yaCerro) {
      cerradas.push(comision);
    } else if (comision.status === 'APPROVED' && yaArranco) {
      enCurso.push(comision);
    } else {
      bandejaDeAprobacion.push(comision);
    }
  }

  return { bandejaDeAprobacion, enCurso, cerradas };
}

export function comisionCerrada(termEndDate) {
  return !!termEndDate && hoy() > new Date(termEndDate);
}

export function diasHastaVencimientoMasProximo(comisiones) {
  const ahora = hoy();
  const pendientes = comisiones.filter((c) => c.status === 'DRAFT' || c.status === 'REJECTED');
  const dias = pendientes
    .map((c) => (c.termStartDate ? Math.ceil((new Date(c.termStartDate) - ahora) / (1000 * 60 * 60 * 24)) : null))
    .filter((d) => d !== null && d >= 0);
  return dias.length ? Math.min(...dias) : null;
}

export function formatearFecha(fechaIso) {
  if (!fechaIso) return null;
  return new Date(fechaIso).toLocaleDateString('es-AR', { day: 'numeric', month: 'short', year: 'numeric' });
}
