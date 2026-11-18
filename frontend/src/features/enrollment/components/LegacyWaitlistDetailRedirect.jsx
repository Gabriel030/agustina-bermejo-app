import { Navigate, useParams } from 'react-router-dom';

export function LegacyWaitlistDetailRedirect() {
  const { kind, id } = useParams();
  return <Navigate to={`../inscripciones/listas-de-espera/${kind}/${id}`} replace />;
}
