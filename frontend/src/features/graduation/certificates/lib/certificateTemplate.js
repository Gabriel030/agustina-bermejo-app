export const TEMPLATE_KINDS = [
  { value: 'PROGRAM', label: 'Carrera', documentName: 'Certificado de egreso de carrera' },
  { value: 'COURSE', label: 'Curso', documentName: 'Certificado de egreso de curso' },
];

export const SIGNATORY_DOCUMENT_TYPE = 'GRADUATION_APPROVAL';
export const LOGO_MAX_BYTES = 2 * 1024 * 1024;

export const ALLOWED_MARKERS = [
  'holder',
  'documentType',
  'documentNumber',
  'titleKind',
  'titleName',
  'hours',
  'graduationDate',
  'issuedOn',
  'registryNumber',
];

export const TEXT_FIELDS = [
  { name: 'instituteName', label: 'Nombre del instituto', max: 120 },
  { name: 'title', label: 'Título del certificado', max: 80 },
  { name: 'introText', label: 'Texto de apertura', max: 160 },
  { name: 'approvalText', label: 'Texto de aprobación', max: 160 },
  { name: 'dictationText', label: 'Texto de dictado y evaluación', max: 160 },
  { name: 'qrLabel', label: 'Rótulo del QR', max: 160 },
  { name: 'footerLegend', label: 'Leyenda al pie', max: 240, multiline: true },
];

const MARKER_PATTERN = /\{([^{}]*)\}/g;

export function emptyForm() {
  return {
    instituteName: '',
    title: '',
    introText: '',
    approvalText: '',
    dictationText: '',
    qrLabel: '',
    footerLegend: '',
    logoImageId: null,
    signatory1Id: null,
    signatory2Id: null,
  };
}

export function formFromContent(content) {
  const form = emptyForm();
  if (!content) return form;
  TEXT_FIELDS.forEach(({ name }) => {
    form[name] = content[name] ?? '';
  });
  form.logoImageId = content.logoImageId ?? null;
  form.signatory1Id = content.signatory1Id ?? null;
  form.signatory2Id = content.signatory2Id ?? null;
  return form;
}

export function contentFromForm(form) {
  const content = {
    logoImageId: form.logoImageId,
    signatory1Id: form.signatory1Id,
    signatory2Id: form.signatory2Id,
    firstSigner: null,
    secondSigner: null,
  };
  TEXT_FIELDS.forEach(({ name }) => {
    const trimmed = form[name].trim();
    content[name] = trimmed === '' ? null : trimmed;
  });
  return content;
}

export function unknownMarkers(text) {
  const found = [...text.matchAll(MARKER_PATTERN)].map((match) => match[1]);
  return [...new Set(found.filter((marker) => !ALLOWED_MARKERS.includes(marker)))];
}

export function fieldError(field, value) {
  if (value.length > field.max) return `Admite hasta ${field.max} caracteres (tiene ${value.length}).`;
  const unknown = unknownMarkers(value);
  if (unknown.length > 0) {
    return `Marcador no permitido: ${unknown.map((marker) => `{${marker}}`).join(', ')}.`;
  }
  return null;
}

export function validateForm(form) {
  const errors = {};
  TEXT_FIELDS.forEach((field) => {
    const message = fieldError(field, form[field.name].trim());
    if (message) errors[field.name] = message;
  });
  if (form.signatory1Id != null && form.signatory1Id === form.signatory2Id) {
    errors.signatory2Id = 'El firmante 1 y el firmante 2 deben ser personas distintas.';
  }
  return errors;
}

export function isSameForm(a, b) {
  return JSON.stringify(a) === JSON.stringify(b);
}

export function validateLogoFile(file) {
  if (file.type !== 'image/png' || file.size > LOGO_MAX_BYTES) return 'La imagen debe ser un PNG de hasta 2 MB.';
  return null;
}

export function statusOf({ template, dirty }) {
  if (dirty) return { tone: 'warning', label: 'Cambios sin guardar' };
  if (template.draft) return { tone: 'accent', label: `Borrador v${template.draft.versionNumber} guardado` };
  if (template.published) return { tone: 'success', label: `Publicada v${template.published.versionNumber} · sin cambios` };
  return { tone: 'neutral', label: 'Sin versión publicada' };
}

export function baselineOf(template) {
  return formFromContent((template.draft ?? template.published)?.content);
}

export const SYSTEM_DATA = [
  { marker: 'holder', label: 'Titular', example: 'Pérez, Camila' },
  { marker: 'documentType', label: 'Tipo de documento', example: 'DNI' },
  { marker: 'documentNumber', label: 'Número de documento', example: '38.512.774' },
  { marker: 'titleKind', label: 'Tipo de título', example: 'Tecnicatura' },
  { marker: 'titleName', label: 'Nombre del título', example: 'Tecnicatura en Administración' },
  { marker: 'hours', label: 'Carga horaria', example: '1.200' },
  { marker: 'graduationDate', label: 'Fecha de egreso', example: '20/11/2026' },
  { marker: 'issuedOn', label: 'Fecha de emisión', example: '21/11/2026' },
  { marker: 'registryNumber', label: 'Número de registro', example: '2026-0412' },
];

export function previewSourceOf({ isDirector, template }) {
  if (isDirector && template.draft) return 'DRAFT';
  return template.published ? 'PUBLISHED' : null;
}

export function canPublish({ template, dirty, form, signatories }) {
  if (!template.draft || dirty) return false;
  const ids = (signatories ?? []).map((signatory) => signatory.id);
  return ids.includes(form.signatory1Id) && ids.includes(form.signatory2Id);
}
