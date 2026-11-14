import { useEffect } from 'react';
import { useLocation } from 'react-router-dom';
import { toast } from 'sonner';
import { useAuth } from '@/context/AuthContext';
import { readLastRole, roleLabel, selectableRoles, writeLastRole } from '@/lib/activeRole';

export function RoleSync() {
  const { usuario } = useAuth();
  const { pathname } = useLocation();
  const userId = usuario?.id;
  const roles = usuario?.roles;
  const segment = pathname.split('/')[1]?.toUpperCase();

  useEffect(() => {
    if (userId == null || !roles || !segment || !roles.includes(segment)) return;
    const lastRole = readLastRole(userId);
    if (lastRole === segment) return;
    writeLastRole(userId, segment);
    if (lastRole && roles.includes(lastRole) && selectableRoles(roles).length > 1) {
      toast(`Ahora estás como ${roleLabel(segment)}`);
    }
  }, [userId, roles, segment]);

  return null;
}
