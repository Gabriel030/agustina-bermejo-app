import { Navigate, useLocation } from 'react-router-dom';
import { useAuth } from '@/context/AuthContext';

// Variante de ProtectedRoute sin chequeo de rol — /catalogo y /mis-solicitudes*
// son autoservicio: cualquier persona con sesión puede entrar, tenga o no
// todavía un rol asignado (ver Spec técnica - Frontend, registro y catálogo).
export function AnyAuthenticatedRoute({ children }) {
  const { usuario } = useAuth();
  const location = useLocation();

  if (!usuario) {
    return <Navigate to="/login" replace state={{ from: location }} />;
  }

  return children;
}
