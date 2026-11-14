import { Navigate, useLocation } from 'react-router-dom';
import { useEffect } from 'react';
import { toast } from 'sonner';
import { useAuth } from '@/context/AuthContext';
import { clearLastRole, readLastRole, roleLabel, rolePath, selectableRoles, writeLastRole } from '@/lib/activeRole';

function RoleRemovedRedirect({ userId, removedRole, roles }) {
  const choices = selectableRoles(roles);
  const singleRole = choices.length === 1 ? choices[0] : null;

  useEffect(() => {
    clearLastRole(userId);
    if (singleRole) {
      writeLastRole(userId, singleRole);
      toast(`Ya no tenés el rol ${roleLabel(removedRole)}. Ahora estás como ${roleLabel(singleRole)}.`);
    } else {
      toast(`Ya no tenés el rol ${roleLabel(removedRole)}. Elegí con cuál seguir.`);
    }
  }, [userId, removedRole, singleRole]);

  return <Navigate to={singleRole ? rolePath(singleRole) : '/elegir-rol'} replace />;
}

export function ProtectedRoute({ rol, children }) {
  const { usuario, roles, loggedOut, sessionExpired } = useAuth();
  const location = useLocation();

  if (!usuario) {
    return <Navigate to="/login" replace state={loggedOut ? undefined : { from: location, expired: sessionExpired }} />;
  }

  if (!roles.includes(rol)) {
    if (roles.length && readLastRole(usuario.id) === rol) {
      return <RoleRemovedRedirect userId={usuario.id} removedRole={rol} roles={roles} />;
    }
    return <Navigate to="/no-autorizado" replace />;
  }

  return children;
}
