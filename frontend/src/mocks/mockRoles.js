import { profileFixture } from '@/test/fixtures/profile';

export const MOCK_ROLE_STORAGE_KEY = 'mockRole';
export const DEFAULT_MOCK_ROLE = 'director';

const MOCK_PROFILES = {
  director: { id: 101, nombre: 'Diana', apellido: 'Directora', email: 'director@demo.test', roles: ['DIRECTOR'] },
  administrativo: { id: 102, nombre: 'Alberto', apellido: 'Administrativo', email: 'administrativo@demo.test', roles: ['ADMINISTRATIVO'] },
  docente: { id: 103, nombre: 'Daniel', apellido: 'Docente', email: 'docente@demo.test', roles: ['DOCENTE'] },
  alumno: { id: 104, nombre: 'Ana', apellido: 'Alumna', email: 'alumno@demo.test', roles: ['ALUMNO'] },
  platformadmin: { id: 105, nombre: 'Pablo', apellido: 'Plataforma', email: 'platform@demo.test', roles: ['PLATFORM_ADMIN'] },
};

export const MOCK_ROLE_KEYS = Object.keys(MOCK_PROFILES);

const normalizeRoleKey = (value) => value?.toLowerCase().replace(/[^a-z]/g, '');

export function resolveMockRole({ search = '', storage = null } = {}) {
  const fromUrl = normalizeRoleKey(new URLSearchParams(search).get('role'));
  if (MOCK_PROFILES[fromUrl]) return fromUrl;
  const stored = normalizeRoleKey(storage?.getItem(MOCK_ROLE_STORAGE_KEY));
  if (MOCK_PROFILES[stored]) return stored;
  return DEFAULT_MOCK_ROLE;
}

export function mockProfileFor(roleKey) {
  return profileFixture(MOCK_PROFILES[roleKey] ?? MOCK_PROFILES[DEFAULT_MOCK_ROLE]);
}
