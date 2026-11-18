import { ModalMotivo } from '@/components/ModalMotivo';
import { WAITLIST_REASON_MAX_LENGTH, WAITLIST_REASON_MIN_LENGTH } from '@/features/enrollment/lib/waitlist';

export function WaitlistActionDialog({ open, title, description, placeholder, confirmLabel, confirmVariant, pending, onClose, onConfirm }) {
  return (
    <ModalMotivo
      open={open}
      onClose={onClose}
      onConfirmar={onConfirm}
      confirmando={pending}
      requerido
      titulo={title}
      descripcion={description}
      placeholder={placeholder}
      confirmarLabel={confirmLabel}
      confirmarVariant={confirmVariant}
      minLength={WAITLIST_REASON_MIN_LENGTH}
      maxLength={WAITLIST_REASON_MAX_LENGTH}
      showCounter
      disableUntilMinLength
      mensajeRequerido="Escribí al menos 10 caracteres."
    />
  );
}
