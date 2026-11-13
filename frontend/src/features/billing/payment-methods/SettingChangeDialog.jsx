import { useState } from 'react';
import { toast } from 'sonner';
import { TriangleAlert } from 'lucide-react';
import ConfirmActionDialog from '@/components/ConfirmActionDialog';
import { ChangesTable } from '@/components/ChangesTable';
import { Textarea } from '@/components/ui/textarea';
import { REASON_MAX, REASON_MIN, SETTING_CATALOG, buildChangeRows, dialogTitle, isStaleSetting, settingErrorMessage, toChangeRequest } from './paymentMethods';
import { useChangeInstituteSetting } from './useInstituteSettings';

export function SettingChangeDialog({ entry, onClose, onRefresh }) {
  const [reason, setReason] = useState('');
  const mutation = useChangeInstituteSetting();
  const stale = mutation.isError && isStaleSetting(mutation.error);
  const failure = mutation.isError ? settingErrorMessage(mutation.error) : null;
  const reasonLength = reason.trim().length;
  const reasonValid = reasonLength >= REASON_MIN && reason.length <= REASON_MAX;

  function close() {
    mutation.reset();
    onClose();
  }

  function changeReason(value) {
    if (mutation.isError && !stale) mutation.reset();
    setReason(value);
  }

  function confirm() {
    if (stale) {
      mutation.reset();
      onRefresh();
      return;
    }
    mutation.mutate(
      { key: entry.key, version: entry.version, request: toChangeRequest(entry, reason) },
      {
        onSuccess: () => {
          toast.success(`${SETTING_CATALOG[entry.key].label} ${entry.enabled ? 'desactivado' : 'activado'}`);
          onClose();
        },
      },
    );
  }

  const blocked = mutation.isError && !stale;

  return (
    <ConfirmActionDialog
      open
      onOpenChange={(open) => !open && close()}
      title={dialogTitle(entry)}
      description="Revisá qué cambia. Queda en la auditoría con tu motivo."
      confirmLabel={stale ? 'Recargar y reintentar' : 'Confirmar'}
      confirmVariant="gradient"
      pending={mutation.isPending}
      confirmDisabled={stale ? false : blocked || !reasonValid}
      onConfirm={confirm}
    >
      <div className="flex flex-col gap-3">
        <ChangesTable rows={buildChangeRows(entry)} />
        {failure && (
          <div role="alert" className="flex items-start gap-2 rounded-xl bg-danger-bg p-3 text-sm text-danger">
            <TriangleAlert className="mt-0.5 size-4 shrink-0" aria-hidden="true" />
            <span>
              {failure}
              {stale && ' Recargá para ver cómo quedó; tu motivo se conserva.'}
            </span>
          </div>
        )}
        <div className="flex flex-col gap-1.5">
          <label htmlFor="setting-change-reason" className="text-size-13 font-medium text-ink-soft">
            Motivo
          </label>
          <Textarea
            id="setting-change-reason"
            value={reason}
            onChange={(event) => changeReason(event.target.value)}
            maxLength={REASON_MAX}
            rows={3}
            placeholder="Por qué hacés este cambio"
            aria-describedby="setting-change-reason-help"
          />
          <div id="setting-change-reason-help" className="flex justify-between text-xs text-ink-faint">
            <span>{reasonLength > 0 && reasonLength < REASON_MIN ? `Mínimo ${REASON_MIN} caracteres` : ''}</span>
            <span aria-live="polite">
              {reason.length}/{REASON_MAX}
            </span>
          </div>
        </div>
      </div>
    </ConfirmActionDialog>
  );
}
