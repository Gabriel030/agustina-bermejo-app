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

export function cambiosQueMerecenAviso(original, actual) {
  const cambios = [];
  if (original.title.trim() !== actual.title.trim()) cambios.push('el título');
  if (original.urgent !== actual.urgent) cambios.push('la urgencia');
  if ((caducidadAFecha(original) ?? null) !== (caducidadAFecha(actual) ?? null)) cambios.push('la caducidad');
  const envioOriginal = original.cuando === 'programar' ? original.scheduledFor : null;
  const envioActual = actual.cuando === 'programar' ? actual.scheduledFor : null;
  if ((envioOriginal ?? null) !== (envioActual ?? null)) cambios.push('el envío programado');
  return cambios;
}

export function unirCambios(cambios) {
  if (cambios.length <= 1) return cambios.join('');
  return `${cambios.slice(0, -1).join(', ')} y ${cambios[cambios.length - 1]}`;
}

const MENSAJES_DE_ERROR = {
  EMPTY_AUDIENCE: 'Ese grupo no tiene ningún alumno hoy. Elegí otro grupo de destinatarios.',
  SCHEDULED_DATE_IN_THE_PAST: 'La fecha de envío ya pasó. Elegí una fecha y hora futuras.',
  ANNOUNCEMENT_ALREADY_SENT: 'Este aviso ya se envió, así que no se puede reprogramar.',
};

export function mensajeDeErrorDeAviso(error, fallback) {
  return MENSAJES_DE_ERROR[error?.code] ?? fallback;
}

function pad(value) {
  return String(value).padStart(2, '0');
}

export function fechaHoraLocalMinima(ahora = new Date()) {
  return `${ahora.getFullYear()}-${pad(ahora.getMonth() + 1)}-${pad(ahora.getDate())}T${pad(ahora.getHours())}:${pad(ahora.getMinutes())}`;
}

export function fechaLocalMinima(ahora = new Date()) {
  return fechaHoraLocalMinima(ahora).slice(0, 10);
}
