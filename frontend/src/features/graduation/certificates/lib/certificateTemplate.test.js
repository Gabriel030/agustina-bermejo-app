import { describe, expect, it } from 'vitest';
import {
  contentFromForm,
  emptyForm,
  fieldError,
  formFromContent,
  unknownMarkers,
  validateForm,
  validateLogoFile,
} from '@/features/graduation/certificates/lib/certificateTemplate';

describe('certificateTemplate lib', () => {
  it('finds only the markers outside the allowed list', () => {
    expect(unknownMarkers('{holder} {foo} {foo} {bar}')).toEqual(['foo', 'bar']);
    expect(unknownMarkers('sin marcadores')).toEqual([]);
  });

  it('reports length before markers', () => {
    expect(fieldError({ max: 5 }, '123456')).toBe('Admite hasta 5 caracteres (tiene 6).');
    expect(fieldError({ max: 20 }, '{x}')).toBe('Marcador no permitido: {x}.');
    expect(fieldError({ max: 20 }, '{holder}')).toBeNull();
  });

  it('sends blank texts as null and the signers as null', () => {
    const content = contentFromForm({ ...emptyForm(), title: '  Hola  ', signatory1Id: 1, signatory2Id: 2 });
    expect(content).toMatchObject({ title: 'Hola', introText: null, signatory1Id: 1, signatory2Id: 2, firstSigner: null, secondSigner: null });
  });

  it('builds a blank form from missing content', () => {
    expect(formFromContent(null)).toEqual(emptyForm());
  });

  it('rejects equal signatories but accepts two empty ones', () => {
    expect(validateForm({ ...emptyForm(), signatory1Id: 3, signatory2Id: 3 }).signatory2Id).toBeDefined();
    expect(validateForm(emptyForm())).toEqual({});
  });

  it('accepts a PNG of up to 2 MB and rejects the rest', () => {
    expect(validateLogoFile({ type: 'image/png', size: 2 * 1024 * 1024 })).toBeNull();
    expect(validateLogoFile({ type: 'image/png', size: 2 * 1024 * 1024 + 1 })).not.toBeNull();
    expect(validateLogoFile({ type: 'image/jpeg', size: 10 })).not.toBeNull();
  });
});
