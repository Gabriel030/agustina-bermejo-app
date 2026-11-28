export const HELP_STAFF_ROLES = ['docente', 'preceptor', 'administrativo', 'subdirector', 'director', 'platform_admin'];

export function canReadHelp(roleBase) {
  return HELP_STAFF_ROLES.includes(roleBase);
}
