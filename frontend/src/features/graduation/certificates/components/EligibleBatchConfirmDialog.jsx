import ConfirmActionDialog from '@/components/ConfirmActionDialog';
import { issuanceDescription, issuanceTitle } from '@/features/graduation/certificates/lib/staffGraduationCertificates';

export function EligibleBatchConfirmDialog({ rows, pending, onCancel, onConfirm }) {
  const count = rows?.length ?? 0;
  return (
    <ConfirmActionDialog
      open={count > 0}
      onOpenChange={(open) => !open && onCancel()}
      title={issuanceTitle(count)}
      description={issuanceDescription(rows ?? [])}
      confirmLabel={count === 1 ? 'Confirmar y emitir' : `Confirmar y emitir ${count}`}
      confirmVariant="primary"
      pending={pending}
      onConfirm={onConfirm}
    />
  );
}
