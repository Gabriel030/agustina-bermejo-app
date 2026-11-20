export const PLATFORM_ROLE = 'PLATFORM_ADMIN';

export const ROLE_ORDER = ['DIRECTOR', 'ADMINISTRATIVO', 'PRECEPTOR', 'DOCENTE', 'ALUMNO'];

export const ROLE_LABELS = {
  DIRECTOR: 'Director',
  ADMINISTRATIVO: 'Administrativo',
  PRECEPTOR: 'Preceptor',
  DOCENTE: 'Docente',
  ALUMNO: 'Alumno',
  PLATFORM_ADMIN: 'Plataforma',
};

function orderIndex(role) {
  const index = ROLE_ORDER.indexOf(role);
  return index < 0 ? ROLE_ORDER.length : index;
}

export function roleLabel(role) {
  return ROLE_LABELS[role] ?? role;
}

export function sortRoles(roles) {
  return [...roles].sort((a, b) => orderIndex(a) - orderIndex(b));
}

export function selectableRoles(roles = []) {
  const instituteRoles = roles.filter((role) => role !== PLATFORM_ROLE);
  return sortRoles(instituteRoles.length ? instituteRoles : roles);
}

export function resolveActiveRole({ roles = [], urlSegment, lastRole }) {
  const fromUrl = urlSegment?.toUpperCase();
  if (fromUrl && roles.includes(fromUrl)) return fromUrl;
  if (lastRole && roles.includes(lastRole)) return lastRole;
  return selectableRoles(roles)[0] ?? null;
}

const lastRoleKey = (userId) => `activeRole.last.${userId}`;
const askAlwaysKey = (userId) => `activeRole.askAlways.${userId}`;

function safeGet(key) {
  try {
    return localStorage.getItem(key);
  } catch {
    return null;
  }
}

function safeSet(key, value) {
  try {
    localStorage.setItem(key, value);
  } catch {
    return;
  }
}

function safeRemove(key) {
  try {
    localStorage.removeItem(key);
  } catch {
    return;
  }
}

export function readLastRole(userId) {
  return safeGet(lastRoleKey(userId));
}

export function writeLastRole(userId, role) {
  safeSet(lastRoleKey(userId), role);
}

export function clearLastRole(userId) {
  safeRemove(lastRoleKey(userId));
}

export function readAskAlways(userId) {
  return safeGet(askAlwaysKey(userId)) === 'true';
}

export function writeAskAlways(userId, value) {
  if (value) safeSet(askAlwaysKey(userId), 'true');
  else safeRemove(askAlwaysKey(userId));
}

export function rolePath(role) {
  return `/${role.toLowerCase()}/inicio`;
}

export function homePathFor(usuario) {
  if (!usuario?.roles?.length) return '/catalogo';
  const role = resolveActiveRole({ roles: usuario.roles, lastRole: readLastRole(usuario.id) });
  return rolePath(role);
}

export function postLoginPathFor(usuario) {
  if (!usuario?.roles?.length) return '/catalogo';
  const choices = selectableRoles(usuario.roles);
  const lastRole = readLastRole(usuario.id);
  const mustChoose = choices.length > 1 && (readAskAlways(usuario.id) || !choices.includes(lastRole));
  return mustChoose ? '/elegir-rol' : homePathFor(usuario);
}
