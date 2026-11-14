import { useLocation } from 'react-router-dom';
import { useAuth } from '@/context/AuthContext';
import { usePendingLegalAcceptances } from '@/features/identity/hooks/useLegal';
import { GuardianWaitingScreen } from '@/features/identity/legal/GuardianWaitingScreen';

const OPEN_PATH_PREFIXES = ['/legal/', '/confirmar-responsable'];

export function GuardianGate({ children }) {
  const { usuario } = useAuth();
  const { pathname } = useLocation();
  const { data } = usePendingLegalAcceptances(Boolean(usuario));
  const isOpenPath = OPEN_PATH_PREFIXES.some((prefix) => pathname.startsWith(prefix));

  if (usuario && data?.guardianConfirmationRequired && !isOpenPath) return <GuardianWaitingScreen />;
  return children;
}
