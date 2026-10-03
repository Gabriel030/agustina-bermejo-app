export const STAFF_ROLE_LABELS = {
  DOCENTE: 'Docente',
  PRECEPTOR: 'Preceptor',
  ADMINISTRATIVO: 'Administrativo',
};

const MANAGEABLE_STAFF_ROLES_BY_ACTOR = {
  ADMINISTRATIVO: ['DOCENTE', 'PRECEPTOR'],
  DIRECTOR: ['ADMINISTRATIVO'],
};

export function manageableStaffRoles(actorRoles) {
  return [...new Set((actorRoles ?? []).flatMap((role) => MANAGEABLE_STAFF_ROLES_BY_ACTOR[role] ?? []))];
}

export function canManageStaffRole(actorRoles, staffRole) {
  return manageableStaffRoles(actorRoles).includes(staffRole);
}
