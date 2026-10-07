export const SPEC_INICIAL = { key: 'ALL', instances: [], quantifier: 'ANY', threshold: null };

export const VALORES_INICIALES = {
  category: 'REMINDER',
  urgent: false,
  title: '',
  body: '',
  cuando: 'ahora',
  scheduledFor: null,
  caducidad: 'siempre',
  expiresAt: null,
};

export function caducidadAFecha({ caducidad, expiresAt }, hoy = new Date()) {
  if (caducidad === 'siempre') return null;
  if (caducidad === 'otra') return expiresAt || null;
  const fecha = new Date(hoy);
  fecha.setDate(fecha.getDate() + (caducidad === '1s' ? 7 : 30));
  return fecha.toISOString().slice(0, 10);
}

export function aPayload({ valores, spec, esCurso }) {
  return {
    title: valores.title.trim(),
    body: valores.body.trim(),
    category: valores.category,
    urgent: valores.urgent,
    expiresAt: caducidadAFecha(valores),
    scheduledFor: valores.cuando === 'programar' ? valores.scheduledFor : null,
    audience: esCurso ? null : { key: spec.key, instances: spec.instances, quantifier: spec.quantifier, threshold: spec.threshold },
  };
}

export function desdeAviso(aviso) {
  return {
    category: aviso.category,
    urgent: aviso.urgent,
    title: aviso.title,
    body: aviso.body,
    cuando: aviso.status === 'SCHEDULED' ? 'programar' : 'ahora',
    scheduledFor: aviso.scheduledFor ? aviso.scheduledFor.slice(0, 16) : null,
    caducidad: aviso.expiresAt ? 'otra' : 'siempre',
    expiresAt: aviso.expiresAt,
  };
}
