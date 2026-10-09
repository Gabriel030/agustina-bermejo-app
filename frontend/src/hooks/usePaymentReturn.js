import { useLocation, useNavigate } from 'react-router-dom';

const DEFAULT_ORIGIN = '/alumno/pagos';
const DEFAULT_LABEL = 'Volver a Mis pagos';

export function paymentOriginState(volverA, volverALabel) {
  return { volverA, volverALabel };
}

function isInternalPath(path) {
  return typeof path === 'string' && path.startsWith('/') && !path.startsWith('//');
}

export function usePaymentReturn() {
  const navigate = useNavigate();
  const { state } = useLocation();
  const origin = isInternalPath(state?.volverA) ? state.volverA : DEFAULT_ORIGIN;
  const originLabel = state?.volverALabel ?? DEFAULT_LABEL;
  return {
    originLabel,
    goToOrigin: () => navigate(origin),
    goTo: (path) => navigate(path, { state }),
  };
}
