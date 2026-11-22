import { useLocation } from 'react-router-dom';

const ROLES = ['ALUMNO', 'DOCENTE', 'PRECEPTOR', 'ADMINISTRATIVO', 'SUBDIRECTOR', 'DIRECTOR', 'PLATFORM_ADMIN'];

export function useActiveRole() {
  const segment = useLocation().pathname.split('/')[1]?.toUpperCase();
  const activeRole = ROLES.includes(segment) ? segment : null;
  return { activeRole, roleBase: activeRole?.toLowerCase(), choices: [], canSwitch: false };
}
