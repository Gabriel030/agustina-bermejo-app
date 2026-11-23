import { describe, expect, it } from 'vitest';
import {
  TEMPLATE_TABS,
  baselineOf,
  canPublish,
  contentFromForm,
  emptyForm,
  fieldError,
  formFromContent,
  markersOf,
  previewSourceOf,
  previewStampOf,
  publishBlockerOf,
  statusOf,
  tabOf,
  templatePath,
  textFieldsOf,
  unknownMarkers,
  validateForm,
  validateLogoFile,
} from '@/features/graduation/certificates/lib/certificateTemplate';

const EGRESS_MARKERS = markersOf('PROGRAM');

function validForm(overrides = {}) {
  return { ...emptyForm(), instituteName: 'Instituto', title: 'Título', approvalText: 'Texto', introText: 'Texto', signatory1Id: 1, ...overrides };
}

describe('certificateTemplate lib', () => {
  it('finds only the markers outside the allowed list', () => {
    expect(unknownMarkers('{holder} {foo} {foo} {bar}', EGRESS_MARKERS)).toEqual(['foo', 'bar']);
    expect(unknownMarkers('sin marcadores', EGRESS_MARKERS)).toEqual([]);
  });

  it('reports length before markers', () => {
    expect(fieldError({ max: 5 }, '123456', EGRESS_MARKERS)).toBe('Admite hasta 5 caracteres (tiene 6).');
    expect(fieldError({ max: 20 }, '{x}', EGRESS_MARKERS)).toBe('Marcador no permitido: {x}.');
    expect(fieldError({ max: 20 }, '{holder}', EGRESS_MARKERS)).toBeNull();
  });

  it('resolves the endpoint of each tab', () => {
    expect(templatePath('PROGRAM')).toBe('/certificate-templates/PROGRAM');
    expect(templatePath('COURSE')).toBe('/certificate-templates/COURSE');
    expect(templatePath('EXAM_CERTIFICATE')).toBe('/certificate-templates/documents/EXAM_CERTIFICATE');
    expect(TEMPLATE_TABS).toHaveLength(6);
    expect(tabOf('APPROVED_SUBJECTS_CERTIFICATE').signatoryDocumentType).toBe('APPROVED_SUBJECTS_CERTIFICATE');
    expect(tabOf('PROGRAM').signatoryDocumentType).toBe('GRADUATION_APPROVAL');
  });

  it('gives each document its own markers and text fields', () => {
    expect(markersOf('EXAM_CERTIFICATE', ['holder', 'subjectName', 'examDate'])).toEqual(['holder', 'subjectName', 'examDate']);
    expect(markersOf('PROGRAM')).toContain('titleName');
    expect(markersOf('EXAM_CERTIFICATE')).toEqual([]);
    expect(textFieldsOf('EXAM_CERTIFICATE').find((field) => field.name === 'introText')).toMatchObject({ label: 'Texto principal', max: 240 });
    expect(textFieldsOf('EXAM_CERTIFICATE').map((field) => field.name)).not.toContain('dictationText');
    expect(textFieldsOf('PROGRAM').map((field) => field.name)).toContain('dictationText');
  });

  it('sends blank texts as null and the signers as null', () => {
    const content = contentFromForm({ ...emptyForm(), title: '  Hola  ', signatory1Id: 1, signatory2Id: 2 });
    expect(content).toMatchObject({ title: 'Hola', introText: null, signatory1Id: 1, signatory2Id: 2, firstSigner: null, secondSigner: null });
  });

  it('builds a blank form from missing content', () => {
    expect(formFromContent(null)).toEqual(emptyForm());
  });

  it('requires institute name, title, main text and the first signatory', () => {
    expect(Object.keys(validateForm(emptyForm(), 'PROGRAM')).sort()).toEqual(['approvalText', 'instituteName', 'signatory1Id', 'title']);
    expect(validateForm(validForm(), 'EXAM_CERTIFICATE')).toEqual({});
  });

  it('rejects equal signatories but accepts an empty second one', () => {
    expect(validateForm(validForm({ signatory2Id: 1 }), 'PROGRAM').signatory2Id).toBeDefined();
    expect(validateForm(validForm({ signatory2Id: null }), 'PROGRAM')).toEqual({});
  });

  it('validates markers against the document of the tab', () => {
    const form = validForm({ introText: '{subjectName}' });
    expect(validateForm(form, 'EXAM_CERTIFICATE', ['subjectName']).introText).toBeUndefined();
    expect(validateForm(form, 'REGULAR_STUDENT_CERTIFICATE', ['titleName']).introText).toBe('Marcador no permitido: {subjectName}.');
  });

  it('validates the length on the trimmed value', () => {
    const form = validForm({ title: `${'a'.repeat(80)}  ` });
    expect(validateForm(form, 'PROGRAM').title).toBeUndefined();
    expect(validateForm({ ...form, title: 'a'.repeat(81) }, 'PROGRAM').title).toBeDefined();
  });

  it('accepts a PNG of up to 2 MB and rejects the rest', () => {
    expect(validateLogoFile({ type: 'image/png', size: 2 * 1024 * 1024 })).toBeNull();
    expect(validateLogoFile({ type: 'image/png', size: 2 * 1024 * 1024 + 1 })).not.toBeNull();
    expect(validateLogoFile({ type: 'image/jpeg', size: 10 })).not.toBeNull();
  });

  describe('status and publishing', () => {
    const signatories = [
      { id: 1, name: 'Ana', hasSignature: true },
      { id: 2, name: 'Beto', hasSignature: false },
    ];
    const published = { versionNumber: 3, content: validForm() };
    const draft = { versionNumber: 4, updatedAt: '2026-11-20T10:00:00', content: validForm() };

    it('labels the template state', () => {
      expect(statusOf({ template: { published: null, draft: null }, dirty: false })).toEqual({ tone: 'neutral', label: 'Sin plantilla' });
      expect(statusOf({ template: { published, draft: null }, dirty: false })).toEqual({ tone: 'success', label: 'Publicada v3' });
      expect(statusOf({ template: { published, draft }, dirty: false })).toEqual({ tone: 'warning', label: 'Borrador sobre v3' });
      expect(statusOf({ template: { published: null, draft }, dirty: false }).label).toBe('Borrador sobre v0');
      expect(statusOf({ template: { published, draft }, dirty: true })).toEqual({ tone: 'accent', label: 'Cambios sin guardar' });
    });

    it('starts from the draft when there is one', () => {
      expect(baselineOf({ published, draft: null }).title).toBe('Título');
      expect(baselineOf({ published: null, draft: null })).toEqual(emptyForm());
    });

    it('allows publishing a clean draft with one signed signatory', () => {
      const input = { template: { published, draft }, dirty: false, form: validForm(), signatories, templateKey: 'PROGRAM' };
      expect(publishBlockerOf(input)).toBeNull();
      expect(canPublish(input)).toBe(true);
    });

    it('blocks publishing with a signatory without signature', () => {
      const input = { template: { published, draft }, dirty: false, form: validForm({ signatory1Id: 1, signatory2Id: 2 }), signatories, templateKey: 'PROGRAM' };
      expect(publishBlockerOf(input)).toBe('Beto no tiene firma cargada. Cargala en Firmantes para poder publicar.');
      expect(canPublish(input)).toBe(false);
    });

    it('explains each other blocker', () => {
      const base = { template: { published, draft }, dirty: false, form: validForm(), signatories, templateKey: 'PROGRAM' };
      expect(publishBlockerOf({ ...base, dirty: true })).toBe('Guardá el borrador para poder publicar.');
      expect(publishBlockerOf({ ...base, dirty: true, form: validForm({ title: '' }) })).toBe('Corregí los errores para guardar.');
      expect(publishBlockerOf({ ...base, template: { published, draft: null } })).toBeNull();
      expect(canPublish({ ...base, template: { published, draft: null } })).toBe(false);
      expect(publishBlockerOf({ ...base, signatories: undefined })).toBe('Esperá a que carguen los firmantes para poder publicar.');
      expect(publishBlockerOf({ ...base, form: validForm({ signatory1Id: null }) })).toBe('Elegí al menos un firmante para poder publicar.');
      expect(publishBlockerOf({ ...base, form: validForm({ title: '' }) })).toBe('Completá los datos obligatorios y corregí los errores para poder publicar.');
      expect(publishBlockerOf({ ...base, form: validForm({ signatory1Id: 9 }) })).toBe('El firmante elegido ya no está disponible. Elegí otro para poder publicar.');
    });

    it('picks the preview source', () => {
      const both = { published, draft };
      expect(previewSourceOf({ showDraft: false, canSeeDraft: true, template: both })).toBe('PUBLISHED');
      expect(previewSourceOf({ showDraft: true, canSeeDraft: true, template: both })).toBe('DRAFT');
      expect(previewSourceOf({ showDraft: true, canSeeDraft: false, template: both })).toBe('PUBLISHED');
      expect(previewSourceOf({ showDraft: false, canSeeDraft: true, template: { published: null, draft } })).toBe('DRAFT');
      expect(previewSourceOf({ showDraft: false, canSeeDraft: false, template: { published: null, draft } })).toBeNull();
    });

    it('stamps the preview with what it shows', () => {
      expect(previewStampOf({ source: 'DRAFT', template: { published, draft } })).toBe('2026-11-20T10:00:00');
      expect(previewStampOf({ source: 'PUBLISHED', template: { published, draft } })).toBe(3);
      expect(previewStampOf({ source: null, template: { published, draft } })).toBeNull();
    });
  });
});
