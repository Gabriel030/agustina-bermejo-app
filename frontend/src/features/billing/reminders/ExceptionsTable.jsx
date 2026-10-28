import { useMemo, useState } from 'react';
import { Plus } from 'lucide-react';
import { EmptyState } from '@/components/EmptyState';
import { StatusPill } from '@/components/StatusPill';
import { DataTable } from '@/components/table/DataTable';
import { Button } from '@/components/ui/button';
import { Dialog, DialogContent, DialogDescription, DialogFooter, DialogHeader, DialogTitle } from '@/components/ui/dialog';
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from '@/components/ui/select';
import { formatDate } from '@/lib/billing';
import { GROUP_LABELS } from './reminderSchedule';

function AddExceptionDialog({ group, options, onClose, onPick }) {
  const [targetId, setTargetId] = useState('');
  const noun = group === 'PROGRAMS' ? 'carrera' : 'curso';
  return (
    <Dialog open onOpenChange={(open) => !open && onClose()}>
      <DialogContent className="sm:max-w-md">
        <DialogHeader>
          <DialogTitle>Agregar excepción</DialogTitle>
          <DialogDescription>Elegí la {noun} que va a tener sus propios avisos. Parte de la política de {GROUP_LABELS[group].toLowerCase()}.</DialogDescription>
        </DialogHeader>
        {options.length === 0 ? (
          <p className="text-sm text-ink-soft">No quedan {noun === 'carrera' ? 'carreras' : 'cursos'} sin excepción.</p>
        ) : (
          <Select value={targetId} onValueChange={setTargetId}>
            <SelectTrigger aria-label={`Elegí una ${noun}`}>
              <SelectValue placeholder={`Elegí una ${noun}`} />
            </SelectTrigger>
            <SelectContent>
              {options.map((option) => (
                <SelectItem key={option.id} value={String(option.id)}>
                  {option.name}
                </SelectItem>
              ))}
            </SelectContent>
          </Select>
        )}
        <DialogFooter>
          <Button variant="ghost" onClick={onClose}>
            Cancelar
          </Button>
          <Button variant="outline" disabled={!targetId} onClick={() => onPick(Number(targetId))}>
            Continuar
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  );
}

export function ExceptionsTable({ group, exceptions, names, selectedId, canAdd, onSelect, onAdd }) {
  const [adding, setAdding] = useState(false);
  const nameOf = group === 'PROGRAMS' ? names.programName : names.courseName;
  const offer = group === 'PROGRAMS' ? names.programs : names.courses;
  const taken = new Set(exceptions.map((exception) => exception.targetId));
  const columns = useMemo(
    () => [
      {
        id: 'name',
        header: group === 'PROGRAMS' ? 'Carrera' : 'Curso',
        accessorFn: (exception) => nameOf(exception.targetId),
        cell: ({ row }) => (
          <button type="button" onClick={() => onSelect(row.original.targetId)} className="text-left font-semibold text-primary hover:underline">
            {nameOf(row.original.targetId)}
          </button>
        ),
      },
      { id: 'inherits', header: 'Hereda de', enableSorting: false, accessorFn: () => GROUP_LABELS[group] },
      { id: 'since', header: 'Vigente desde', accessorFn: (exception) => exception.effectiveFrom, cell: ({ row }) => formatDate(row.original.effectiveFrom) },
      { id: 'notices', header: 'Avisos', accessorFn: (exception) => exception.noticeCount, meta: { align: 'right' } },
      {
        id: 'status',
        header: 'Estado',
        accessorFn: (exception) => (exception.hasOpenProposal ? 'PENDING' : 'EFFECTIVE'),
        cell: ({ row }) =>
          row.original.hasOpenProposal ? <StatusPill tone="warning">Pendiente de aprobación</StatusPill> : <StatusPill tone="success">Vigente</StatusPill>,
        meta: { filterable: true, filterTitle: 'estado', filterLabel: (value) => (value === 'PENDING' ? 'Pendiente de aprobación' : 'Vigente') },
      },
    ],
    [group, nameOf, onSelect],
  );

  return (
    <div className="flex flex-col gap-3">
      <div className="flex flex-wrap items-center justify-between gap-2">
        <h3 className="font-heading text-base font-semibold text-ink">Excepciones</h3>
        {canAdd && (
          <Button variant="outline" size="sm" onClick={() => setAdding(true)}>
            <Plus className="size-4" /> Agregar excepción
          </Button>
        )}
      </div>
      <DataTable
        columns={columns}
        data={exceptions}
        getRowId={(exception) => String(exception.targetId)}
        emptyState={<EmptyState title="Sin excepciones" description="Todos siguen la política general de este grupo." />}
        mobileLayout="scroll"
        minWidth={640}
        key={selectedId ?? 'none'}
      />
      {adding && (
        <AddExceptionDialog
          group={group}
          options={offer.filter((option) => !taken.has(option.id))}
          onClose={() => setAdding(false)}
          onPick={(targetId) => {
            setAdding(false);
            onAdd(targetId);
          }}
        />
      )}
    </div>
  );
}
