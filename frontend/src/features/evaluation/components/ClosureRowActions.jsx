import { useState } from 'react';
import ConfirmActionDialog from '@/components/ConfirmActionDialog';
import { ModalMotivo } from '@/components/ModalMotivo';
import { Button } from '@/components/ui/button';
import { MIN_REASON_LENGTH, canGrantException, canRevokeException, studentFullName } from '../lib/closureSheet';

export function ClosureRowActions({ row, sheet, pending, onGrant, onRevoke }) {
  const [dialog, setDialog] = useState(null);
  const name = studentFullName(row);
  if (canGrantException(row, sheet)) {
    return (
      <>
        <Button type="button" variant="outline" size="sm" className="min-h-11 md:min-h-8" aria-label={`Exceptuar a ${name}`} onClick={() => setDialog('grant')}>
          Exceptuar con motivo
        </Button>
        <ModalMotivo
          open={dialog === 'grant'}
          onClose={() => setDialog(null)}
          confirmando={pending}
          requerido
          minLength={MIN_REASON_LENGTH}
          titulo={`Exceptuar a ${name}`}
          descripcion="Promociona aunque el pago o el legajo no estén al día. Queda en la auditoría con tu motivo."
          placeholder="Motivo de la excepción"
          confirmarLabel="Exceptuar"
          mensajeRequerido="Escribí el motivo de la excepción."
          onConfirmar={async (reason) => {
            const done = await onGrant(row, reason);
            if (done) setDialog(null);
            return done;
          }}
        />
      </>
    );
  }
  if (canRevokeException(row, sheet)) {
    return (
      <>
        <Button type="button" variant="outline" size="sm" className="min-h-11 md:min-h-8" aria-label={`Quitar la excepción de ${name}`} onClick={() => setDialog('revoke')}>
          Quitar excepción
        </Button>
        <ConfirmActionDialog
          open={dialog === 'revoke'}
          onOpenChange={(open) => !open && setDialog(null)}
          title={`Quitar la excepción de ${name}`}
          description="Vuelve a Promoción condicionada y, al cerrar el acta, va a final. Queda en la auditoría."
          confirmLabel="Quitar excepción"
          confirmVariant="gradient"
          pending={pending}
          onConfirm={async () => {
            const done = await onRevoke(row);
            if (done) setDialog(null);
          }}
        />
      </>
    );
  }
  return null;
}
