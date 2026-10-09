import { useLocation, useNavigate } from 'react-router-dom';

const DEFAULT_ORIGIN = '/alumno/pagos';
const DEFAULT_LABEL = 'Volver a Mis pagos';

export function paymentOriginState(volverA, volverALabel) {
  return { volverA, volverALabel };
}

export function usePaymentReturn() {
  const navigate = useNavigate();
  const { state } = useLocation();
  const origin = state?.volverA ?? DEFAULT_ORIGIN;
  const originLabel = state?.volverALabel ?? DEFAULT_LABEL;
  return {
    originLabel,
    goToOrigin: () => navigate(origin),
    goTo: (path) => navigate(path, { state }),
  };
}
