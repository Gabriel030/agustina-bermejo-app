const EGRESS_MARKERS = [
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

export const TEMPLATE_TABS = [
  { value: 'PROGRAM', label: 'Egreso carrera', group: 'EGRESS', kind: true, signatoryDocumentType: 'GRADUATION_APPROVAL', documentName: 'Certificado de egreso de carrera', markers: EGRESS_MARKERS },
  { value: 'COURSE', label: 'Egreso curso', group: 'EGRESS', kind: true, signatoryDocumentType: 'GRADUATION_APPROVAL', documentName: 'Certificado de egreso de curso', markers: EGRESS_MARKERS },
  {
    value: 'REGULAR_STUDENT_CERTIFICATE',
    label: 'Alumno regular',
    group: 'DOCUMENTS',
    signatoryDocumentType: 'REGULAR_STUDENT_CERTIFICATE',
    documentName: 'Constancia de alumno regular',
  },
  {
    value: 'EXAM_REGISTRATION_CERTIFICATE',
    label: 'Inscripción a examen',
    group: 'DOCUMENTS',
    signatoryDocumentType: 'EXAM_REGISTRATION_CERTIFICATE',
    documentName: 'Constancia de inscripción a examen',
  },
  {
    value: 'EXAM_CERTIFICATE',
    label: 'Examen rendido',
    group: 'DOCUMENTS',
    signatoryDocumentType: 'EXAM_CERTIFICATE',
    documentName: 'Constancia de examen rendido',
  },
  {
    value: 'APPROVED_SUBJECTS_CERTIFICATE',
    label: 'Materias aprobadas',
    group: 'DOCUMENTS',
    signatoryDocumentType: 'APPROVED_SUBJECTS_CERTIFICATE',
    documentName: 'Constancia de materias aprobadas',
  },
];

export const TEMPLATE_GROUPS = [
  { value: 'EGRESS', label: 'Egresos' },
  { value: 'DOCUMENTS', label: 'Constancias' },
];

export function tabOf(value) {
  return TEMPLATE_TABS.find((tab) => tab.value === value) ?? TEMPLATE_TABS[0];
}

export function templatePath(value) {
  return tabOf(value).kind ? `/certificate-templates/${value}` : `/certificate-templates/documents/${value}`;
}

export const LOGO_MAX_BYTES = 2 * 1024 * 1024;
export const REQUIRED_MESSAGE = 'Obligatorio.';

const EGRESS_TEXT_FIELDS = [
  { name: 'instituteName', label: 'Nombre del instituto', max: 120, required: true },
  { name: 'title', label: 'Título del certificado', max: 80, required: true },
  { name: 'introText', label: 'Texto de apertura', max: 240 },
  { name: 'approvalText', label: 'Texto de aprobación', max: 160, required: true },
  { name: 'dictationText', label: 'Texto de dictado y evaluación', max: 160 },
  { name: 'qrLabel', label: 'Rótulo del QR', max: 160 },
  { name: 'footerLegend', label: 'Leyenda al pie', max: 240, multiline: true },
];

const DOCUMENT_TEXT_FIELDS = [
  { name: 'instituteName', label: 'Nombre del instituto', max: 120, required: true },
  { name: 'title', label: 'Título del documento', max: 80, required: true },
  { name: 'introText', label: 'Texto principal', max: 240, required: true, multiline: true },
  { name: 'qrLabel', label: 'Rótulo del QR', max: 160 },
  { name: 'footerLegend', label: 'Leyenda al pie', max: 240, multiline: true },
];

export const TEXT_FIELDS = EGRESS_TEXT_FIELDS;

export function textFieldsOf(value) {
  return tabOf(value).kind ? EGRESS_TEXT_FIELDS : DOCUMENT_TEXT_FIELDS;
}

export function markersOf(value, allowedMarkers) {
  return allowedMarkers ?? tabOf(value).markers ?? [];
}

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

export function unknownMarkers(text, markers) {
  const found = [...text.matchAll(MARKER_PATTERN)].map((match) => match[1]);
  return [...new Set(found.filter((marker) => !markers.includes(marker)))];
}

export function fieldError(field, value, markers) {
  if (value.length > field.max) return `Admite hasta ${field.max} caracteres (tiene ${value.length}).`;
  const unknown = unknownMarkers(value, markers);
  if (unknown.length > 0) {
    return `Marcador no permitido: ${unknown.map((marker) => `{${marker}}`).join(', ')}.`;
  }
  return null;
}

export function validateForm(form, templateKey, allowedMarkers) {
  const errors = {};
  const markers = markersOf(templateKey, allowedMarkers);
  textFieldsOf(templateKey).forEach((field) => {
    const value = form[field.name].trim();
    const message = field.required && value === '' ? REQUIRED_MESSAGE : fieldError(field, value, markers);
    if (message) errors[field.name] = message;
  });
  if (form.signatory1Id == null) errors.signatory1Id = 'Elegí al menos un firmante.';
  if (form.signatory1Id != null && form.signatory1Id === form.signatory2Id) {
    errors.signatory2Id = 'El primer y el segundo firmante deben ser personas distintas.';
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
  if (dirty) return { tone: 'accent', label: 'Cambios sin guardar' };
  if (template.draft) {
    const base = template.published?.versionNumber ?? 0;
    return { tone: 'warning', label: `Borrador sobre v${base}` };
  }
  if (template.published) return { tone: 'success', label: `Publicada v${template.published.versionNumber}` };
  return { tone: 'neutral', label: 'Sin plantilla' };
}

export function baselineOf(template) {
  return formFromContent((template.draft ?? template.published)?.content);
}

export function selectedSignatoriesOf(form, signatories) {
  return [form.signatory1Id, form.signatory2Id]
    .filter((id) => id != null)
    .map((id) => ({ id, signatory: (signatories ?? []).find((candidate) => candidate.id === id) ?? null }));
}

export function publishBlockerOf({ template, dirty, form, signatories, templateKey, allowedMarkers }) {
  if (dirty) return Object.keys(validateForm(form, templateKey, allowedMarkers)).length > 0 ? 'Corregí los errores para guardar.' : 'Guardá el borrador para poder publicar.';
  if (!template.draft) return null;
  if (signatories == null) return 'Esperá a que carguen los firmantes para poder publicar.';
  const errors = validateForm(form, templateKey, allowedMarkers);
  if (errors.signatory1Id && Object.keys(errors).length === 1) return 'Elegí al menos un firmante para poder publicar.';
  if (Object.keys(errors).length > 0) return 'Completá los datos obligatorios y corregí los errores para poder publicar.';
  const chosen = selectedSignatoriesOf(form, signatories);
  if (chosen.some(({ signatory }) => signatory === null)) return 'El firmante elegido ya no está disponible. Elegí otro para poder publicar.';
  const unsigned = chosen.find(({ signatory }) => !signatory.hasSignature);
  if (unsigned) return `${unsigned.signatory.name} no tiene firma cargada. Cargala en Firmantes para poder publicar.`;
  return null;
}

export function canPublish({ template, dirty, form, signatories, templateKey, allowedMarkers }) {
  return Boolean(template.draft) && publishBlockerOf({ template, dirty, form, signatories, templateKey, allowedMarkers }) === null;
}

export function previewSourceOf({ showDraft, canSeeDraft, template }) {
  const draftAvailable = canSeeDraft && Boolean(template.draft);
  if (draftAvailable && (showDraft || !template.published)) return 'DRAFT';
  return template.published ? 'PUBLISHED' : null;
}

export function previewStampOf({ source, template }) {
  if (source === 'DRAFT') return template.draft?.updatedAt ?? null;
  if (source === 'PUBLISHED') return template.published?.versionNumber ?? null;
  return null;
}
