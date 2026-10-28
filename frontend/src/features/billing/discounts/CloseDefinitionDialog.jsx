import { toast } from 'sonner';
import { ModalMotivo } from '@/components/ModalMotivo';
import { useCloseDiscountDefinition } from '../hooks/useDiscountDefinitions';
import { isAwaitingAuthorization } from './discountLabels';

export const CLOSURE_REASON_MAX_LENGTH = 450;

function closureCopy({ isDirector, isPromotion, name }) {
  const noun = isPromotion ? 'promoción' : 'descuento';
  const article = isPromotion ? 'la' : 'lo';
  const state = isPromotion ? 'abierta' : 'vigente';
  return {
    title: isDirector ? `Cerrar "${name}"` : `Proponer cerrar "${name}"`,
    description: isDirector
      ? `Deja de ofrecerse desde hoy. Los alumnos que ya ${article} obtuvieron ${article} conservan. No se puede reabrir.`
      : `El director decide. Mientras tanto ${isPromotion ? 'la' : 'el'} ${noun} sigue ${state}.`,
    confirmLabel: isDirector ? `Cerrar ${noun}` : 'Enviar al director',
    successMessage: isDirector ? (isPromotion ? 'Promoción cerrada.' : 'Descuento cerrado.') : 'Pedido de cierre enviado al director.',
  };
}

export function CloseDefinitionDialog({ open, onClose, definition, isDirector, onClosed, onSeeRequests }) {
  const mutation = useCloseDiscountDefinition();
  const copy = closureCopy({ isDirector, isPromotion: definition.discountType === 'COHORT_PROMOTION', name: definition.name });

  async function confirm(reason) {
    try {
      const result = await mutation.mutateAsync({ id: definition.id, reason });
      toast.success(
        copy.successMessage,
        isAwaitingAuthorization(result) ? { action: { label: 'Ver mis pedidos', onClick: onSeeRequests } } : undefined,
      );
      onClosed();
      return true;
    } catch (error) {
      toast.error(error?.violations?.[0]?.message ?? 'No pudimos cerrarlo. Probá de nuevo.');
      return false;
    }
  }

  return (
    <ModalMotivo
      open={open}
      onClose={onClose}
      onConfirmar={confirm}
      confirmando={mutation.isPending}
      requerido
      titulo={copy.title}
      descripcion={copy.description}
      placeholder={`Qué lo motiva (hasta ${CLOSURE_REASON_MAX_LENGTH} caracteres)`}
      confirmarLabel={copy.confirmLabel}
      confirmarVariant={isDirector ? 'destructive' : 'default'}
      maxLength={CLOSURE_REASON_MAX_LENGTH}
      mensajeRequerido="El motivo es obligatorio."
    />
  );
}
