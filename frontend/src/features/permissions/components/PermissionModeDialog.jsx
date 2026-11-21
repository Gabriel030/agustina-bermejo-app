import { useState } from 'react';
import { AlertTriangle, Info } from 'lucide-react';
import { toast } from 'sonner';
import { describeMutationFailure } from '@/api/mutationErrorPolicy';
import { StatusPill } from '@/components/StatusPill';
import { Button } from '@/components/ui/button';
import { Checkbox } from '@/components/ui/checkbox';
import { Dialog, DialogContent, DialogDescription, DialogFooter, DialogHeader, DialogTitle } from '@/components/ui/dialog';
import { RadioGroup, RadioGroupItem } from '@/components/ui/radio-group';
import { Textarea } from '@/components/ui/textarea';
import { useChangePermissionMode } from '@/features/permissions/hooks/useChangePermissionMode';
import { MAX_REASON_LENGTH, MIN_REASON_LENGTH, MODE_OPTIONS, PERMISSION_MODES, moduleLabel, pluralize, roleLabel } from '@/features/permissions/lib/permissionGrid';

export function PermissionModeDialog({ role, module, currentMode, discrepancyCount, discrepancyStatus, onRetryDiscrepancies, hasUnsavedChanges, onClose }) {
  const [selected, setSelected] = useState(currentMode);
  const [reason, setReason] = useState('');
  const [touched, setTouched] = useState(false);
  const [acknowledged, setAcknowledged] = useState(false);
  const [error, setError] = useState(null);
  const change = useChangePermissionMode();
  const trimmed = reason.trim();
  const reasonTooShort = trimmed.length < MIN_REASON_LENGTH;
  const needsAcknowledgement = selected === 'ENFORCE' && discrepancyCount > 0;
  const unchanged = selected === currentMode;
  const verifying = selected === 'ENFORCE' && discrepancyStatus !== 'ready';
  const roleName = roleLabel(role);
  const moduleName = moduleLabel(module);

  const confirm = async () => {
    setError(null);
    try {
      await change.mutateAsync({ reason: trimmed, role, module, mode: selected });
      onClose();
      toast.success(`${roleName} en ${moduleName}: modo ${PERMISSION_MODES[selected].label}.`);
    } catch (failure) {
      setError(describeMutationFailure(failure));
    }
  };

  return (
    <Dialog open onOpenChange={(next) => !next && !change.isPending && onClose()}>
      <DialogContent className="flex max-h-[calc(100dvh-2rem)] flex-col sm:max-w-130">
        <DialogHeader>
          <DialogTitle>
            Modo de {roleName} en {moduleName}
          </DialogTitle>
          <DialogDescription className="flex flex-wrap items-center gap-1.5">
            Elegí cómo se decide el acceso de este rol en este módulo. Hoy está en
            <StatusPill tone={PERMISSION_MODES[currentMode].tone} dot>
              {PERMISSION_MODES[currentMode].label}
            </StatusPill>
          </DialogDescription>
        </DialogHeader>
        <div className="flex min-h-0 flex-col gap-3.5 overflow-auto p-0.5">
          <RadioGroup value={selected} onValueChange={setSelected} aria-label="Modo">
            {MODE_OPTIONS.map((mode) => (
              <label
                key={mode}
                className="flex min-h-11 cursor-pointer items-start gap-3 rounded-card border border-border bg-surface p-3 has-data-[state=checked]:border-brand has-data-[state=checked]:bg-accent-soft has-focus-visible:ring-2 has-focus-visible:ring-ring"
              >
                <RadioGroupItem value={mode} className="mt-0.5" />
                <span className="flex flex-col gap-0.5 text-size-13 text-ink-soft">
                  <b className="flex items-center gap-2 text-size-14 text-ink">
                    {PERMISSION_MODES[mode].label}
                    {mode === currentMode && <StatusPill tone="accent">Actual</StatusPill>}
                  </b>
                  {PERMISSION_MODES[mode].description}
                </span>
              </label>
            ))}
          </RadioGroup>
          {selected === 'ENFORCE' && (
            <div className="flex items-start gap-2.5 rounded-control bg-warning-bg px-3.5 py-3 text-size-13 text-warning-ink">
              <AlertTriangle className="mt-0.5 size-4 shrink-0" aria-hidden="true" />
              <p>
                Al pasar a Activo, {roleName} solo va a poder lo que la grilla le dé.
                {discrepancyCount > 0 && (
                  <>
                    {' '}
                    Hoy hay <b>{pluralize(discrepancyCount, 'discrepancia', 'discrepancias')}</b> en este módulo: revisalas antes.
                  </>
                )}
              </p>
            </div>
          )}
          {verifying && (
            <div role={discrepancyStatus === 'error' ? 'alert' : 'status'} className="flex flex-wrap items-center gap-2.5 rounded-control bg-warning-bg px-3.5 py-3 text-size-13 text-warning-ink">
              <AlertTriangle className="size-4 shrink-0" aria-hidden="true" />
              <p className="flex-1">
                {discrepancyStatus === 'error' ? 'No pudimos verificar las discrepancias. Reintentá antes de pasar a Activo.' : 'Estamos verificando las discrepancias de este módulo…'}
              </p>
              {discrepancyStatus === 'error' && (
                <Button type="button" variant="outline" className="min-h-11" onClick={onRetryDiscrepancies}>
                  Reintentar
                </Button>
              )}
            </div>
          )}
          {hasUnsavedChanges && (
            <div className="flex items-start gap-2.5 rounded-control bg-warning-bg px-3.5 py-3 text-size-13 text-warning-ink">
              <AlertTriangle className="mt-0.5 size-4 shrink-0" aria-hidden="true" />
              <p>Tenés cambios sin guardar de este rol en este módulo. Guardalos primero para ver cómo cambia su efecto.</p>
            </div>
          )}
          {needsAcknowledgement && (
            <label className="flex min-h-11 cursor-pointer items-center gap-2.5 rounded-control bg-surface px-3 text-size-13 text-ink">
              <Checkbox checked={acknowledged} onCheckedChange={(next) => setAcknowledged(next === true)} />
              Entiendo que {pluralize(discrepancyCount, 'acceso hoy daría', 'accesos hoy darían')} un resultado distinto.
            </label>
          )}
          {selected === 'LEGACY' && currentMode !== 'LEGACY' && (
            <div className="flex items-start gap-2.5 rounded-control bg-accent-soft px-3.5 py-3 text-size-13 text-ink-soft">
              <Info className="mt-0.5 size-4 shrink-0 text-brand" aria-hidden="true" />
              <p>Volver a Legado es seguro: la regla vieja recupera el control y la grilla se conserva para cuando quieras retomarla.</p>
            </div>
          )}
          <div className="flex flex-col gap-1.5">
            <div className="flex justify-between gap-2 text-size-13 font-bold text-ink">
              <label htmlFor="permission-mode-reason">Motivo</label>
              <small className="font-medium tabular-nums text-ink-faint" aria-live="polite">
                {reason.length}/{MAX_REASON_LENGTH}
              </small>
            </div>
            <Textarea
              id="permission-mode-reason"
              value={reason}
              onChange={(event) => setReason(event.target.value)}
              onBlur={() => setTouched(true)}
              maxLength={MAX_REASON_LENGTH}
              rows={3}
              placeholder="Por ejemplo: sin discrepancias durante dos semanas."
            />
            {touched && reasonTooShort && <p className="text-size-12 font-semibold text-danger-ink">Escribí un motivo de al menos {MIN_REASON_LENGTH} caracteres.</p>}
          </div>
          {error && (
            <div role="alert" className="rounded-control bg-danger-bg px-3.5 py-3 text-size-13 text-danger-ink">
              <b>No pudimos cambiar el modo.</b> {error}
            </div>
          )}
        </div>
        <DialogFooter>
          {currentMode !== 'LEGACY' && (
            <Button type="button" variant="destructive" className="min-h-11 sm:mr-auto" disabled={change.isPending} onClick={() => setSelected('LEGACY')}>
              Volver a Legado
            </Button>
          )}
          <Button type="button" variant="outline" className="min-h-11" disabled={change.isPending} onClick={onClose}>
            Cancelar
          </Button>
          <Button
            type="button"
            variant="primary"
            className="min-h-11"
            loading={change.isPending}
            disabled={unchanged || verifying || reasonTooShort || (needsAcknowledgement && !acknowledged)}
            onClick={confirm}
          >
            Cambiar modo
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  );
}
