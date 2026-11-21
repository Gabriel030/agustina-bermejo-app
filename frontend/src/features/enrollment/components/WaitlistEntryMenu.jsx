import { useState } from 'react';
import { ArrowUpToLine, MoreHorizontal, Trash2, UserCheck } from 'lucide-react';
import { toast } from 'sonner';
import { describeError } from '@/api/describeError';
import { Button } from '@/components/ui/button';
import ConfirmActionDialog from '@/components/ConfirmActionDialog';
import { DropdownMenu, DropdownMenuContent, DropdownMenuItem, DropdownMenuSeparator, DropdownMenuTrigger } from '@/components/ui/dropdown-menu';
import { Tooltip, TooltipContent, TooltipProvider, TooltipTrigger } from '@/components/ui/tooltip';
import { WaitlistActionDialog } from '@/features/enrollment/components/WaitlistActionDialog';
import { useWaitlistEntryAction } from '@/features/enrollment/hooks/useWaitlists';
import { promoteBlockedReason } from '@/features/enrollment/lib/waitlist';

function formatSeats(used, capacity) {
  return capacity == null ? String(used) : `${used} / ${capacity}`;
}

function GuardedItem({ reason, onSelect, icon: Icon, children }) {
  const item = (
    <DropdownMenuItem disabled={!!reason} onSelect={onSelect}>
      <Icon aria-hidden="true" />
      {children}
    </DropdownMenuItem>
  );
  if (!reason) return item;
  return (
    <Tooltip>
      <TooltipTrigger asChild>
        <span tabIndex={0} className="block">
          {item}
        </span>
      </TooltipTrigger>
      <TooltipContent className="max-w-60">{reason}</TooltipContent>
    </Tooltip>
  );
}

export function WaitlistEntryMenu({ queue, entry }) {
  const [dialog, setDialog] = useState(null);
  const promote = useWaitlistEntryAction(queue.kind, entry.enrollmentId, 'promote');
  const prioritize = useWaitlistEntryAction(queue.kind, entry.enrollmentId, 'prioritize');
  const remove = useWaitlistEntryAction(queue.kind, entry.enrollmentId, 'remove');
  const name = entry.student.fullName;
  const promoteReason = promoteBlockedReason(entry, queue.free);
  const moveReason = entry.position === 1 ? 'Ya está en el primer lugar.' : null;

  async function run(mutation, reason, successMessage, failureMessage) {
    try {
      await mutation.mutateAsync(reason);
      toast.success(successMessage);
      setDialog(null);
      return true;
    } catch (failure) {
      toast.error(describeError(failure, { fallback: failureMessage }).message);
      return false;
    }
  }

  return (
    <>
      <TooltipProvider>
        <DropdownMenu>
          <DropdownMenuTrigger asChild>
            <Button variant="ghost" size="icon" aria-label={`Acciones para ${name}`}>
              <MoreHorizontal aria-hidden="true" />
            </Button>
          </DropdownMenuTrigger>
          <DropdownMenuContent align="end">
            <GuardedItem icon={UserCheck} reason={promoteReason} onSelect={() => setDialog('promote')}>
              Promover ahora
            </GuardedItem>
            <GuardedItem icon={ArrowUpToLine} reason={moveReason} onSelect={() => setDialog('prioritize')}>
              Mover a primer lugar
            </GuardedItem>
            <DropdownMenuSeparator />
            <DropdownMenuItem variant="destructive" onSelect={() => setDialog('remove')}>
              <Trash2 aria-hidden="true" />
              Quitar de la lista
            </DropdownMenuItem>
          </DropdownMenuContent>
        </DropdownMenu>
      </TooltipProvider>

      <ConfirmActionDialog
        open={dialog === 'promote'}
        onOpenChange={(open) => !open && setDialog(null)}
        title="Promover ahora"
        description={`${name} pasa a inscripto y recibe un aviso.`}
        confirmLabel="Promover"
        confirmVariant="primary"
        pending={promote.isPending}
        onConfirm={() => run(promote, undefined, 'Persona promovida.', 'No pudimos promover a la persona. Probá de nuevo.')}
      >
        <div className="flex items-center justify-center gap-4 rounded-md bg-neutral-bg px-4 py-3" aria-label="Cupo antes y después">
          <div className="flex flex-col items-center">
            <span className="text-size-12 text-ink-faint">Cupo antes</span>
            <span className="font-heading text-xl font-bold tabular-nums text-ink">{formatSeats(queue.occupied, queue.capacity)}</span>
          </div>
          <span aria-hidden="true">→</span>
          <div className="flex flex-col items-center">
            <span className="text-size-12 text-ink-faint">Cupo después</span>
            <span className="font-heading text-xl font-bold tabular-nums text-primary">{formatSeats(queue.occupied + 1, queue.capacity)}</span>
          </div>
        </div>
      </ConfirmActionDialog>

      <WaitlistActionDialog
        open={dialog === 'prioritize'}
        title="Mover a primer lugar"
        description={`${name} hoy está en el puesto ${entry.position}. Pasa al 1 y queda con prioridad manual. El motivo queda en el historial.`}
        placeholder="Por qué pasa primero"
        confirmLabel="Mover"
        confirmVariant="primary"
        pending={prioritize.isPending}
        onClose={() => setDialog(null)}
        onConfirm={(reason) => run(prioritize, reason, 'Persona movida al primer lugar.', 'No pudimos mover a la persona. Probá de nuevo.')}
      />

      <WaitlistActionDialog
        open={dialog === 'remove'}
        title="Quitar de la lista"
        description={`${name} se da de baja de la espera. El motivo es interno: la persona recibe un mensaje genérico.`}
        placeholder="Por qué se la quita"
        confirmLabel="Quitar"
        confirmVariant="destructive"
        pending={remove.isPending}
        onClose={() => setDialog(null)}
        onConfirm={(reason) => run(remove, reason, 'Persona quitada de la lista.', 'No pudimos quitar a la persona. Probá de nuevo.')}
      />
    </>
  );
}
