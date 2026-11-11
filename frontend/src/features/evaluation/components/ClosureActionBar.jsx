import { useState } from 'react';
import { GlassCard } from '@/components/GlassCard';
import { ModalMotivo } from '@/components/ModalMotivo';
import { Button } from '@/components/ui/button';
import { MIN_REASON_LENGTH, approveHint, canApproveClosure, closedWithoutTeacher } from '../lib/closureSheet';
import { ClosureApproveDialog } from './ClosureApproveDialog';

function ApproveBar({ sheet, pending, onApprove }) {
  const [open, setOpen] = useState(false);
  return (
    <GlassCard className="flex flex-wrap items-center justify-between gap-3 p-4">
      <div className="min-w-0 flex-1 basis-64">
        <h2 className="font-heading text-[15px] font-semibold text-ink">Cierre del acta</h2>
        <p id="closure-approve-hint" className="mt-1 text-xs text-ink-soft">
          {approveHint(sheet)}
        </p>
      </div>
      <Button type="button" variant="gradient" disabled={!canApproveClosure(sheet)} aria-describedby="closure-approve-hint" onClick={() => setOpen(true)}>
        {closedWithoutTeacher(sheet) ? 'Cerrar sin confirmación del docente' : 'Aprobar y cerrar'}
      </Button>
      <ClosureApproveDialog open={open} sheet={sheet} pending={pending} onClose={() => setOpen(false)} onApprove={onApprove} />
    </GlassCard>
  );
}

function ReopenBar({ pending, onReopen }) {
  const [open, setOpen] = useState(false);
  return (
    <GlassCard className="flex flex-wrap items-center justify-between gap-3 p-4">
      <div className="min-w-0 flex-1 basis-64">
        <h2 className="font-heading text-[15px] font-semibold text-ink">Reapertura</h2>
        <p id="closure-reopen-hint" className="mt-1 text-xs text-ink-soft">
          Reabrir pide un motivo, deja auditoría y avisa al docente y a los alumnos. El acta pasa a la versión siguiente al volver a cerrarse.
        </p>
      </div>
      <Button type="button" variant="outline" aria-describedby="closure-reopen-hint" onClick={() => setOpen(true)}>
        Reabrir con motivo
      </Button>
      <ModalMotivo
        open={open}
        onClose={() => setOpen(false)}
        confirmando={pending}
        requerido
        minLength={MIN_REASON_LENGTH}
        titulo="Reabrir el cierre"
        descripcion="Se avisa al docente y a los alumnos. Queda en la auditoría con tu motivo."
        placeholder="Motivo de la reapertura"
        confirmarLabel="Reabrir"
        mensajeRequerido="Escribí el motivo de la reapertura."
        onConfirmar={async (reason) => {
          const done = await onReopen(reason);
          if (done) setOpen(false);
          return done;
        }}
      />
    </GlassCard>
  );
}

export function ClosureActionBar({ role, sheet, pending, onApprove, onReopen }) {
  if (role === 'administrativo' && sheet.status !== 'CLOSED') return <ApproveBar sheet={sheet} pending={pending} onApprove={onApprove} />;
  if (role === 'director' && sheet.status === 'CLOSED') return <ReopenBar pending={pending} onReopen={onReopen} />;
  return null;
}
