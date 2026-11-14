import { beforeEach, describe, expect, it } from 'vitest';
import {
  homePathFor,
  postLoginPathFor,
  readAskAlways,
  readLastRole,
  resolveActiveRole,
  selectableRoles,
  writeAskAlways,
  writeLastRole,
} from '@/lib/activeRole';

beforeEach(() => {
  localStorage.clear();
});

describe('selectableRoles', () => {
  it('sorts roles in the fixed order regardless of how the backend lists them', () => {
    expect(selectableRoles(['ALUMNO', 'DOCENTE', 'DIRECTOR', 'PRECEPTOR'])).toEqual([
      'DIRECTOR',
      'PRECEPTOR',
      'DOCENTE',
      'ALUMNO',
    ]);
  });

  it('leaves the platform role out when the person also has institute roles', () => {
    expect(selectableRoles(['PLATFORM_ADMIN', 'DOCENTE'])).toEqual(['DOCENTE']);
  });

  it('keeps the platform role when it is the only one', () => {
    expect(selectableRoles(['PLATFORM_ADMIN'])).toEqual(['PLATFORM_ADMIN']);
  });
});

describe('resolveActiveRole', () => {
  const roles = ['DOCENTE', 'PRECEPTOR'];

  it('prefers the role in the URL when the person has it', () => {
    expect(resolveActiveRole({ roles, urlSegment: 'docente', lastRole: 'PRECEPTOR' })).toBe('DOCENTE');
  });

  it('ignores a URL segment that is not one of the roles of the person', () => {
    expect(resolveActiveRole({ roles, urlSegment: 'director', lastRole: 'DOCENTE' })).toBe('DOCENTE');
    expect(resolveActiveRole({ roles, urlSegment: 'perfil', lastRole: 'DOCENTE' })).toBe('DOCENTE');
  });

  it('falls back to the last used role when the URL says nothing', () => {
    expect(resolveActiveRole({ roles, urlSegment: 'perfil', lastRole: 'DOCENTE' })).toBe('DOCENTE');
  });

  it('ignores a stored role the person no longer has', () => {
    expect(resolveActiveRole({ roles, urlSegment: 'perfil', lastRole: 'DIRECTOR' })).toBe('PRECEPTOR');
  });

  it('falls back to the fixed order when there is nothing else', () => {
    expect(resolveActiveRole({ roles: ['ALUMNO', 'DOCENTE', 'PRECEPTOR'], urlSegment: 'perfil' })).toBe('PRECEPTOR');
  });

  it('returns the only role when the person has one', () => {
    expect(resolveActiveRole({ roles: ['ALUMNO'], urlSegment: 'catalogo' })).toBe('ALUMNO');
  });

  it('returns null when the person has no roles', () => {
    expect(resolveActiveRole({ roles: [], urlSegment: 'catalogo' })).toBeNull();
  });
});

describe('storage', () => {
  it('keeps the last role and the ask-always flag per person', () => {
    writeLastRole(1, 'DOCENTE');
    writeAskAlways(1, true);

    expect(readLastRole(1)).toBe('DOCENTE');
    expect(readLastRole(2)).toBeNull();
    expect(readAskAlways(1)).toBe(true);
    expect(readAskAlways(2)).toBe(false);
  });

  it('turns the ask-always flag off by removing it', () => {
    writeAskAlways(1, true);
    writeAskAlways(1, false);

    expect(readAskAlways(1)).toBe(false);
  });
});

describe('postLoginPathFor', () => {
  const twoRoles = { id: 1, roles: ['DOCENTE', 'PRECEPTOR'] };

  it('asks for the role the first time', () => {
    expect(postLoginPathFor(twoRoles)).toBe('/elegir-rol');
  });

  it('enters directly with the last used role afterwards', () => {
    writeLastRole(1, 'DOCENTE');

    expect(postLoginPathFor(twoRoles)).toBe('/docente/inicio');
  });

  it('asks every time when the person turned that option on', () => {
    writeLastRole(1, 'DOCENTE');
    writeAskAlways(1, true);

    expect(postLoginPathFor(twoRoles)).toBe('/elegir-rol');
  });

  it('asks again when the stored role was taken away', () => {
    writeLastRole(1, 'DIRECTOR');

    expect(postLoginPathFor(twoRoles)).toBe('/elegir-rol');
  });

  it('never asks a person with a single role', () => {
    writeAskAlways(1, true);

    expect(postLoginPathFor({ id: 1, roles: ['DOCENTE'] })).toBe('/docente/inicio');
  });

  it('sends a person without roles to the catalog', () => {
    expect(postLoginPathFor({ id: 1, roles: [] })).toBe('/catalogo');
    expect(homePathFor({ id: 1, roles: [] })).toBe('/catalogo');
  });

  it('enters a person with a single institute role plus platform without asking', () => {
    expect(postLoginPathFor({ id: 1, roles: ['PLATFORM_ADMIN', 'DOCENTE'] })).toBe('/docente/inicio');
  });
});
