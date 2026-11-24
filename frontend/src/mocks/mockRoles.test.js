import { describe, expect, it } from 'vitest';
import { buildBrowserHandlers } from '@/mocks/browserHandlers';
import { DEFAULT_MOCK_ROLE, MOCK_ROLE_KEYS, MOCK_ROLE_STORAGE_KEY, mockProfileFor, resolveMockRole } from '@/mocks/mockRoles';

const storageWith = (value) => ({ getItem: (key) => (key === MOCK_ROLE_STORAGE_KEY ? value : null) });

describe('resolveMockRole', () => {
  it('prefers the role from the query string', () => {
    expect(resolveMockRole({ search: '?role=docente', storage: storageWith('alumno') })).toBe('docente');
  });

  it('accepts platform admin spelled with separators and uppercase', () => {
    expect(resolveMockRole({ search: '?role=PLATFORM_ADMIN' })).toBe('platformadmin');
  });

  it('falls back to the stored role when the query has none', () => {
    expect(resolveMockRole({ search: '', storage: storageWith('alumno') })).toBe('alumno');
  });

  it('ignores unknown roles and uses the default', () => {
    expect(resolveMockRole({ search: '?role=hacker', storage: storageWith('nope') })).toBe(DEFAULT_MOCK_ROLE);
  });
});

describe('mockProfileFor', () => {
  it('returns a profile whose backend role matches each key', () => {
    const roles = MOCK_ROLE_KEYS.map((key) => mockProfileFor(key).roles[0]);
    expect(roles).toEqual(['DIRECTOR', 'ADMINISTRATIVO', 'DOCENTE', 'ALUMNO', 'PLATFORM_ADMIN']);
  });
});

describe('buildBrowserHandlers', () => {
  it('puts the role specific /me before the shared handlers', () => {
    const [first] = buildBrowserHandlers('alumno');
    expect(first.info.path).toBe('http://localhost:8080/me');
    expect(first.info.method).toBe('GET');
  });
});
