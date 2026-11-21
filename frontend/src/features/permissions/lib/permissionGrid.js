export const MAX_CHANGES_PER_CALL = 200;
export const MIN_REASON_LENGTH = 10;
export const MAX_REASON_LENGTH = 500;

export const DIRECTOR_ROLE = 'DIRECTOR';

export const GRID_ROLES = [
  { id: 'DIRECTOR', label: 'Director' },
  { id: 'ADMINISTRATIVO', label: 'Administrativo' },
  { id: 'PRECEPTOR', label: 'Preceptor' },
  { id: 'DOCENTE', label: 'Docente' },
];

export const EDITABLE_ROLES = GRID_ROLES.filter((role) => role.id !== DIRECTOR_ROLE);

export const PERMISSION_MODES = {
  LEGACY: {
    label: 'Legado',
    tone: 'neutral',
    effect: 'Todavía sin efecto',
    description: 'Manda la regla vieja. Lo que marques en la grilla se guarda, pero todavía no se aplica.',
  },
  SHADOW: {
    label: 'Sombra',
    tone: 'warning',
    effect: 'Solo se compara',
    description: 'Sigue mandando la regla vieja. El sistema compara cada acceso con la grilla y anota las diferencias.',
  },
  ENFORCE: {
    label: 'Activo',
    tone: 'success',
    effect: 'Se aplica ya',
    description: 'Manda la grilla. La regla vieja deja de usarse para ese rol y ese módulo.',
  },
};

const MODULE_LABELS = {
  announcement: 'Avisos',
  billing: 'Facturación',
  enrollment: 'Inscripciones',
  attendance: 'Asistencia',
  evaluation: 'Evaluación',
  academic: 'Estructura académica',
  graduation: 'Egreso',
  identity: 'Personas y accesos',
  notification: 'Notificaciones',
};

export function moduleLabel(module) {
  if (MODULE_LABELS[module]) return MODULE_LABELS[module];
  const spaced = module.replace(/[._-]+/g, ' ');
  return spaced.charAt(0).toUpperCase() + spaced.slice(1);
}

export const roleLabel = (roleId) => GRID_ROLES.find((role) => role.id === roleId)?.label ?? roleId;

export const changeKey = (role, code) => `${role}|${code}`;

export function grantedByRole(grid) {
  const byRole = new Map();
  grid.roles.forEach(({ role, permissionCodes }) => byRole.set(role, new Set(permissionCodes)));
  return byRole;
}

export function modeFor(grid, role, module) {
  return grid.modes.find((entry) => entry.role === role && entry.module === module)?.mode ?? 'LEGACY';
}

export function permissionMatches(module, permission, query) {
  const needle = query.trim().toLowerCase();
  if (!needle) return true;
  return [permission.description, permission.code, moduleLabel(module.module)].some((text) => text.toLowerCase().includes(needle));
}

export function chunkChanges(changes, size = MAX_CHANGES_PER_CALL) {
  const chunks = [];
  for (let start = 0; start < changes.length; start += size) chunks.push(changes.slice(start, start + size));
  return chunks;
}

export function describeChangeCount(count) {
  return `${count} ${count === 1 ? 'cambio' : 'cambios'}`;
}
