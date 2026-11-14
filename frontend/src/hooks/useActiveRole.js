import { useMemo } from 'react';
import { useLocation } from 'react-router-dom';
import { useAuth } from '@/context/AuthContext';
import { readLastRole, resolveActiveRole, selectableRoles } from '@/lib/activeRole';

export function useActiveRole() {
  const { usuario } = useAuth();
  const { pathname } = useLocation();
  const roles = usuario?.roles;
  const userId = usuario?.id;
  const urlSegment = pathname.split('/')[1];

  return useMemo(() => {
    const activeRole = resolveActiveRole({
      roles,
      urlSegment,
      lastRole: userId == null ? null : readLastRole(userId),
    });
    const choices = selectableRoles(roles);
    return {
      activeRole,
      roleBase: activeRole?.toLowerCase(),
      choices,
      canSwitch: choices.length > 1,
    };
  }, [roles, userId, urlSegment]);
}
