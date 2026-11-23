import { useState } from 'react';
import { AlertTriangle } from 'lucide-react';
import { StatusPill } from '@/components/StatusPill';
import { Button } from '@/components/ui/button';
import { Checkbox } from '@/components/ui/checkbox';
import { Dialog, DialogContent, DialogDescription, DialogFooter, DialogHeader, DialogTitle } from '@/components/ui/dialog';
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from '@/components/ui/table';
import { Textarea } from '@/components/ui/textarea';
import {
  MAX_REASON_LENGTH,
  MIN_REASON_LENGTH,
  PERMISSION_MODES,
  describeChangeCount,
  modeFor,
  roleLabel,
} from '@/features/permissions/lib/permissionGrid';

function ChangesTable({ changes, catalogIndex, grid }) {
  return (
    <Table className="text-size-13">
      <TableHeader>
        <TableRow>
          <TableHead>Permiso</TableHead>
          <TableHead>Rol</TableHead>
          <TableHead>Cambio</TableHead>
        </TableRow>
      </TableHeader>
      <TableBody>
        {changes.map((change) => {
          const permission = catalogIndex.get(change.permissionCode);
          const mode = PERMISSION_MODES[modeFor(grid, change.role, permission?.module)];
          return (
            <TableRow key={`${change.role}|${change.permissionCode}`} className="align-top">
              <TableCell className="whitespace-normal">
                <span className="block font-semibold text-ink">{permission?.description ?? change.permissionCode}</span>
                <code className="text-size-11 text-ink-faint">{change.permissionCode}</code>
              </TableCell>
              <TableCell className="whitespace-normal">{roleLabel(change.role)}</TableCell>
              <TableCell className="whitespace-normal">
                <span className="flex flex-wrap items-center gap-1.5">
                  <StatusPill tone={change.granted ? 'neutral' : 'success'}>{change.granted ? 'Sin permiso' : 'Con permiso'}</StatusPill>
                  <span aria-hidden="true">→</span>
                  <StatusPill tone={change.granted ? 'success' : 'danger'}>{change.granted ? 'Con permiso' : 'Sin permiso'}</StatusPill>
                  <span className="text-size-12 text-ink-soft">{mode.effect}</span>
                </span>
              </TableCell>
            </TableRow>
          );
        })}
      </TableBody>
    </Table>
  );
}

export function ReviewChangesDialog({ open, onClose, changes, catalogIndex, grid, saving, error, onConfirm }) {
  const [reason, setReason] = useState('');
  const [touched, setTouched] = useState(false);
  const [sensitiveAcknowledged, setSensitiveAcknowledged] = useState(false);
  const trimmed = reason.trim();
  const reasonTooShort = trimmed.length < MIN_REASON_LENGTH;
  const sensitiveChanges = changes.filter((change) => catalogIndex.get(change.permissionCode)?.sensitive);
  const regularChanges = changes.filter((change) => !catalogIndex.get(change.permissionCode)?.sensitive);
  const hasSensitive = sensitiveChanges.length > 0;
  const hasActiveModule = changes.some((change) => modeFor(grid, change.role, catalogIndex.get(change.permissionCode)?.module) === 'ENFORCE');
  const sensitiveTitle = `Confirmación reforzada: ${sensitiveChanges.length} ${sensitiveChanges.length === 1 ? 'permiso sensible' : 'permisos sensibles'}`;

  return (
    <Dialog open={open} onOpenChange={(next) => !next && !saving && onClose()}>
      <DialogContent className="flex max-h-[calc(100dvh-2rem)] flex-col sm:max-w-170">
        <DialogHeader>
          <DialogTitle>Revisar {describeChangeCount(changes.length)} antes de guardar</DialogTitle>
          <DialogDescription>Mirá qué cambia y por qué. Esta información queda en el registro de auditoría.</DialogDescription>
        </DialogHeader>
        <div className="flex min-h-0 flex-col gap-3.5 overflow-auto">
          {hasActiveModule && (
            <div className="rounded-control bg-warning-bg px-3.5 py-3 text-size-13 text-warning-ink">
              Hay cambios en módulos en modo <b>Activo</b>: se aplican apenas guardes y afectan a personas reales.
            </div>
          )}
          {regularChanges.length > 0 && <ChangesTable changes={regularChanges} catalogIndex={catalogIndex} grid={grid} />}
          {hasSensitive && (
            <section aria-label={sensitiveTitle} className="flex flex-col gap-2.5 rounded-card border-2 border-warning bg-warning-bg p-3.5">
              <h3 className="flex items-center gap-2 text-size-14 font-extrabold text-warning-ink">
                <AlertTriangle className="size-4 shrink-0" aria-hidden="true" />
                {sensitiveTitle}
              </h3>
              <ChangesTable changes={sensitiveChanges} catalogIndex={catalogIndex} grid={grid} />
              <p className="text-size-13 text-ink">Se registra como evento destacado en la auditoría, con tu motivo. Podés quitar la delegación más adelante.</p>
              <label className="flex min-h-11 cursor-pointer items-center gap-2.5 rounded-control bg-surface px-3 text-size-13 text-ink">
                <Checkbox checked={sensitiveAcknowledged} onCheckedChange={(next) => setSensitiveAcknowledged(next === true)} />
                Entiendo que delego un permiso sensible
              </label>
            </section>
          )}
          <div className="flex flex-col gap-1.5">
            <div className="flex justify-between gap-2 text-size-13 font-bold text-ink">
              <label htmlFor="permission-change-reason">{hasSensitive ? 'Motivo del cambio (obligatorio)' : 'Motivo del cambio'}</label>
              <small className="font-medium tabular-nums text-ink-faint" aria-live="polite">
                {reason.length}/{MAX_REASON_LENGTH}
              </small>
            </div>
            <Textarea
              id="permission-change-reason"
              value={reason}
              onChange={(event) => setReason(event.target.value)}
              onBlur={() => setTouched(true)}
              maxLength={MAX_REASON_LENGTH}
              rows={3}
              placeholder="Por ejemplo: el equipo administrativo necesita revisar comprobantes sin esperar al Director."
              aria-describedby="permission-change-reason-hint"
            />
            <p id="permission-change-reason-hint" className="text-size-12 text-ink-faint">
              Mínimo {MIN_REASON_LENGTH} caracteres.
            </p>
            {touched && reasonTooShort && <p className="text-size-12 font-semibold text-danger-ink">Escribí un motivo de al menos {MIN_REASON_LENGTH} caracteres.</p>}
          </div>
          {error && (
            <div role="alert" className="rounded-control bg-danger-bg px-3.5 py-3 text-size-13 text-danger-ink">
              <b>{error.title}</b> {error.message}
            </div>
          )}
        </div>
        <DialogFooter>
          <Button className="min-h-11" type="button" variant="outline" disabled={saving} onClick={onClose}>
            Seguir editando
          </Button>
          <Button
            type="button"
            variant="primary"
            className="min-h-11"
            loading={saving}
            disabled={reasonTooShort || (hasSensitive && !sensitiveAcknowledged)}
            onClick={() => onConfirm(trimmed, hasSensitive && sensitiveAcknowledged)}
          >
            Guardar {describeChangeCount(changes.length)}
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  );
}
