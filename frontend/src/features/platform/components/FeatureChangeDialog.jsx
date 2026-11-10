import { useState } from 'react';
import ConfirmActionDialog from '@/components/ConfirmActionDialog';
import { Textarea } from '@/components/ui/textarea';
import { FEATURE_CATALOG, BILLING_MODE_INFO, BILLING_CODE } from '@/features/platform/lib/featureCatalog';
import { changeRows, isSameState, requiresPreview, toChangeRequest } from '@/features/platform/lib/featureChangeRows';
import { featureErrorMessage, isStaleFeature } from '@/features/platform/lib/featureErrors';
import { useChangePlatformFeature } from '@/features/platform/hooks/useChangePlatformFeature';
import { usePreviewFeatureChange } from '@/features/platform/hooks/usePreviewFeatureChange';
import { useCountdown } from '@/features/platform/hooks/useCountdown';
import { FeatureImpactStep } from '@/features/platform/components/FeatureImpactStep';
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from '@/components/ui/table';

const REASON_MIN = 5;
const REASON_MAX = 500;

function titleOf(code, target) {
  const label = FEATURE_CATALOG[code].label;
  if (code === BILLING_CODE) return `Pasar Facturación a «${BILLING_MODE_INFO[target.mode].label}»`;
  return `${target.enabled ? 'Habilitar' : 'Deshabilitar'} ${label}`;
}

function DiffTable({ rows }) {
  return (
    <Table className="w-full text-left text-sm">
      <TableHeader>
        <TableRow className="text-xs text-ink-faint">
          <TableHead scope="col" className="py-1 pr-2 font-semibold"><span className="sr-only">Concepto</span></TableHead>
          <TableHead scope="col" className="py-1 pr-2 font-semibold">Hoy</TableHead>
          <TableHead scope="col" className="py-1 pr-2 font-semibold">Nuevo</TableHead>
          <TableHead scope="col" className="py-1 font-semibold">Diferencia</TableHead>
        </TableRow>
      </TableHeader>
      <TableBody>
        {rows.map((row) => (
          <TableRow key={row.label} className="border-t border-line">
            <TableHead scope="row" className="h-auto py-1.5 pr-2 text-sm font-medium normal-case tracking-normal text-ink whitespace-normal">{row.label}</TableHead>
            <TableCell className="py-1.5 pr-2 text-ink-soft">{row.today}</TableCell>
            <TableCell className="py-1.5 pr-2 font-semibold text-ink">{row.next}</TableCell>
            <TableCell className="py-1.5 text-ink-soft">{row.diff}</TableCell>
          </TableRow>
        ))}
      </TableBody>
    </Table>
  );
}

export function FeatureChangeDialog({ change, features, onClose, onRefresh }) {
  const [reason, setReason] = useState('');
  const [reviewed, setReviewed] = useState(false);
  const mutation = useChangePlatformFeature();
  const code = change?.code;
  const target = change?.target;
  const current = change ? features.find((feature) => feature.code === code) : undefined;
  const alreadyThere = current ? isSameState(code, current, target) : false;
  const needsPreview = current ? !alreadyThere && requiresPreview(code, current, target) : false;
  const preview = usePreviewFeatureChange({ code, targetMode: target?.mode, enabled: needsPreview });
  const secondsLeft = useCountdown(preview.data?.confirmationExpiresAt);
  if (!change || !current) return null;

  const expired = secondsLeft === 0;
  const onImpactStep = needsPreview && (!reviewed || expired || !preview.data);
  const stale = mutation.isError && isStaleFeature(mutation.error);
  const failure = mutation.isError && !stale ? featureErrorMessage(mutation.error) : null;
  const reasonLength = reason.trim().length;
  const reasonValid = reasonLength >= REASON_MIN && reason.length <= REASON_MAX;

  function close() {
    mutation.reset();
    onClose();
  }

  function backToImpact() {
    mutation.reset();
    setReviewed(false);
    preview.refetch();
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
    if (onImpactStep) {
      setReviewed(true);
      return;
    }
    mutation.mutate(
      { code, version: current.version, request: toChangeRequest(code, target, reason, preview.data?.confirmationToken) },
      {
        onSuccess: close,
        onError: (error) => {
          if (error?.code === 'INVALID_TRANSITION_CONFIRMATION') backToImpact();
        },
      },
    );
  }

  const confirmLabel = alreadyThere ? 'Cerrar' : stale ? 'Recargar y reintentar' : onImpactStep ? 'Continuar' : 'Confirmar';
  const confirmDisabled = alreadyThere || stale ? false : onImpactStep ? !preview.data || expired : !reasonValid;

  return (
    <ConfirmActionDialog
      open
      onOpenChange={(open) => !open && close()}
      title={titleOf(code, target)}
      description={
        onImpactStep
          ? 'Paso 1 de 2 · Mirá qué se ve afectado antes de seguir.'
          : needsPreview
            ? 'Paso 2 de 2 · Revisá qué cambia y escribí el motivo. El cambio queda en el historial.'
            : 'Revisá qué cambia antes de confirmar. El cambio queda en el historial con tu motivo.'
      }
      confirmLabel={confirmLabel}
      confirmVariant="gradient"
      pending={mutation.isPending}
      confirmDisabled={confirmDisabled}
      onConfirm={confirm}
    >
      <div className="flex flex-col gap-3">
        {stale && (
          <div role="alert" className="rounded-xl bg-warning-bg p-3 text-sm text-warning">
            <b>{featureErrorMessage(mutation.error)}</b> Recargá para ver cómo quedó; tu motivo se conserva.
          </div>
        )}
        {alreadyThere && (
          <div role="status" className="rounded-xl bg-accent-soft p-3 text-sm text-primary">
            <b>Ya estaba así.</b> No hace falta confirmar.
          </div>
        )}
        {onImpactStep && !alreadyThere && (
          <FeatureImpactStep query={preview} secondsLeft={secondsLeft} onRecalculate={backToImpact} />
        )}
        {!onImpactStep && !alreadyThere && <DiffTable rows={changeRows(code, current, target)} />}
        {!onImpactStep && !alreadyThere && FEATURE_CATALOG[code].paymentMethod && target.enabled && (
          <p className="rounded-xl bg-accent-soft p-3 text-sm text-primary">Esto lo habilita para el instituto. El Director decide si lo prende.</p>
        )}
        {!onImpactStep && (
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
        )}
        {failure && <div role="alert" className="text-sm font-semibold text-danger">{failure}</div>}
      </div>
    </ConfirmActionDialog>
  );
}
