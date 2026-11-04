import { useLocation } from 'react-router-dom';

const STAFF_ROLES = ['preceptor', 'administrativo'];
const DEFAULT_ROLE = 'preceptor';

export function useRoleBasePath() {
  const { pathname } = useLocation();
  const role = pathname.split('/')[1];
  return `/${STAFF_ROLES.includes(role) ? role : DEFAULT_ROLE}`;
}
