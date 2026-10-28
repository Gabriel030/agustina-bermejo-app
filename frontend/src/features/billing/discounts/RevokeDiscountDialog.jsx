import { toast } from 'sonner';
import { ModalMotivo } from '@/components/ModalMotivo';
import { useRevokeStudentDiscount } from '../hooks/useStudentDiscounts';
import { isAwaitingAuthorization, formatMonth } from './discountLabels';
import { discountKind, discountValue } from './studentDiscountView';

export const REVOCATION_REASON_MAX_LENGTH = 450;

export function RevokeDiscountDialog({ discount, isDirector, onClose, onSeeRequests }) {
  const mutation = useRevokeStudentDiscount();
  const label = `${discountKind(discount).toLowerCase()} de ${discountValue(discount)}`;

  async function confirm(reason) {
    try {
      const result = await mutation.mutateAsync({ id: discount.id, reason });
      if (isAwaitingAuthorization(result)) {
        toast.success('Pedido de revocación enviado al director.', { action: { label: 'Ver mis pedidos', onClick: onSeeRequests } });
      } else {
        toast.success(result?.revokedFromPeriod ? `Descuento revocado desde ${formatMonth(result.revokedFromPeriod)}.` : 'Descuento revocado.');
      }
      onClose();
      return true;
    } catch (error) {
      toast.error(error?.violations?.[0]?.message ?? 'No pudimos revocar el descuento. Probá de nuevo.');
      return false;
    }
  }

  return (
    <ModalMotivo
      open
      onClose={onClose}
      onConfirmar={confirm}
      confirmando={mutation.isPending}
      requerido
      titulo={isDirector ? `Revocar ${label}` : `Proponer revocar ${label}`}
      descripcion={
        isDirector
          ? 'Deja de aplicarse desde la próxima cuota a emitir. Las cuotas ya emitidas no cambian y no se devuelve dinero. No se puede deshacer.'
          : 'El director decide. Si nadie lo decide antes de fin de mes, la propuesta vence y el descuento sigue vigente.'
      }
      placeholder={`Qué lo motiva (hasta ${REVOCATION_REASON_MAX_LENGTH} caracteres)`}
      confirmarLabel={isDirector ? 'Revocar descuento' : 'Enviar al director'}
      confirmarVariant={isDirector ? 'destructive' : 'default'}
      maxLength={REVOCATION_REASON_MAX_LENGTH}
      mensajeRequerido="El motivo es obligatorio."
    />
  );
}
