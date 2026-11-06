import { useState } from 'react';
import ConfirmActionDialog from '@/components/ConfirmActionDialog';
import { Textarea } from '@/components/ui/textarea';
import { FEATURE_CATALOG, BILLING_MODE_INFO, BILLING_CODE } from '@/features/platform/lib/featureCatalog';
import { changeRows, isSameState, toChangeRequest } from '@/features/platform/lib/featureChangeRows';
import { featureErrorMessage, isStaleFeature } from '@/features/platform/lib/featureErrors';
import { useChangePlatformFeature } from '@/features/platform/hooks/useChangePlatformFeature';

const REASON_MIN = 5;
const REASON_MAX = 500;

function titleOf(code, target) {
  const label = FEATURE_CATALOG[code].label;
  if (code === BILLING_CODE) return `Pasar Facturación a «${BILLING_MODE_INFO[target.mode].label}»`;
  return `${target.enabled ? 'Habilitar' : 'Deshabilitar'} ${label}`;
}

function DiffTable({ rows }) {
  return (
    <table className="w-full text-left text-sm">
      <thead>
        <tr className="text-xs text-ink-faint">
          <th scope="col" className="py-1 pr-2 font-semibold"><span className="sr-only">Concepto</span></th>
          <th scope="col" className="py-1 pr-2 font-semibold">Hoy</th>
          <th scope="col" className="py-1 pr-2 font-semibold">Nuevo</th>
          <th scope="col" className="py-1 font-semibold">Diferencia</th>
        </tr>
      </thead>
      <tbody>
        {rows.map((row) => (
          <tr key={row.label} className="border-t border-line">
            <th scope="row" className="py-1.5 pr-2 font-medium text-ink">{row.label}</th>
            <td className="py-1.5 pr-2 text-ink-soft">{row.today}</td>
            <td className="py-1.5 pr-2 font-semibold text-ink">{row.next}</td>
            <td className="py-1.5 text-ink-soft">{row.diff}</td>
          </tr>
        ))}
      </tbody>
    </table>
  );
}

export function FeatureChangeDialog({ change, features, onClose, onRefresh }) {
  const [reason, setReason] = useState('');
  const mutation = useChangePlatformFeature();
  if (!change) return null;

  const { code, target } = change;
  const current = features.find((feature) => feature.code === code);
  if (!current) return null;
  const alreadyThere = isSameState(code, current, target);
  const stale = mutation.isError && isStaleFeature(mutation.error);
  const failure = mutation.isError && !stale ? featureErrorMessage(mutation.error) : null;
  const reasonLength = reason.trim().length;
  const reasonValid = reasonLength >= REASON_MIN && reason.length <= REASON_MAX;

  function close() {
    mutation.reset();
    onClose();
  }

  function confirm() {
    if (alreadyThere) {
      close();
      return;
    }
    if (stale) {
      onRefresh();
      mutation.reset();
      return;
    }
    mutation.mutate({ code, version: current.version, request: toChangeRequest(code, target, reason) }, { onSuccess: close });
  }

  return (
    <ConfirmActionDialog
      open
      onOpenChange={(open) => !open && close()}
      title={titleOf(code, target)}
      description="Revisá qué cambia antes de confirmar. El cambio queda en el historial con tu motivo."
      confirmLabel={alreadyThere ? 'Cerrar' : stale ? 'Ver estado actual' : 'Confirmar'}
      confirmVariant="gradient"
      pending={mutation.isPending}
      confirmDisabled={!alreadyThere && !stale && !reasonValid}
      onConfirm={confirm}
    >
      <div className="flex flex-col gap-3">
        {stale && (
          <div role="alert" className="rounded-xl bg-warning-bg p-3 text-sm text-warning">
            <b>{featureErrorMessage(mutation.error)}</b> Mirá cómo quedó antes de decidir; tu motivo se conserva.
          </div>
        )}
        {alreadyThere && (
          <div role="status" className="rounded-xl bg-accent-soft p-3 text-sm text-primary">
            <b>Ya estaba así.</b> No hace falta confirmar.
          </div>
        )}
        {!alreadyThere && <DiffTable rows={changeRows(code, current, target)} />}
        {!alreadyThere && FEATURE_CATALOG[code].paymentMethod && target.enabled && (
          <p className="rounded-xl bg-accent-soft p-3 text-sm text-primary">Esto lo habilita para el instituto. El Director decide si lo prende.</p>
        )}
        <div className="flex flex-col gap-1.5">
          <label htmlFor="feature-change-reason" className="text-[13px] font-medium text-ink-soft">Motivo</label>
          <Textarea
            id="feature-change-reason"
            value={reason}
            onChange={(event) => setReason(event.target.value)}
            maxLength={REASON_MAX}
            rows={3}
            aria-describedby="feature-change-reason-help"
          />
          <div id="feature-change-reason-help" className="flex justify-between text-xs text-ink-faint">
            <span>Entre {REASON_MIN} y {REASON_MAX} caracteres. Queda en el historial.</span>
            <span>{reason.length}/{REASON_MAX}</span>
          </div>
        </div>
        {failure && <div role="alert" className="text-sm font-semibold text-danger">{failure}</div>}
      </div>
    </ConfirmActionDialog>
  );
}
