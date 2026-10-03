import { Navigate, useLocation } from 'react-router-dom';
import { useAuth } from '@/context/AuthContext';

export function ProtectedRoute({ rol, children }) {
  const { usuario, roles, loggedOut } = useAuth();
  const location = useLocation();

  if (!usuario) {
    return <Navigate to="/login" replace state={loggedOut ? undefined : { from: location }} />;
  }

  if (!roles.includes(rol)) {
    return <Navigate to="/no-autorizado" replace />;
  }

  return children;
}
