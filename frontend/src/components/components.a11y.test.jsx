import { describe, expect, it } from 'vitest';
import { render } from '@testing-library/react';
import { axe } from '@/test/axe';
import { Button } from '@/components/ui/button';
import { ChoiceChip } from '@/components/ChoiceChip';
import { KpiFilterChip } from '@/components/KpiFilterChip';
import { StatusPill } from '@/components/StatusPill';
import { EmptyState } from '@/components/EmptyState';
import { Notice } from '@/components/Notice';
import { Paginador } from '@/components/Paginador';
import { DataTable } from '@/components/table/DataTable';
import { Sheet, SheetContent, SheetDescription, SheetTitle } from '@/components/ui/sheet';
import { Dialog, DialogContent, DialogDescription, DialogTitle } from '@/components/ui/dialog';

const columns = [
  { id: 'name', header: 'Nombre', accessorFn: (row) => row.name, cell: ({ row }) => row.original.name },
  { id: 'status', header: 'Estado', accessorFn: (row) => row.status, enableSorting: false, cell: ({ row }) => row.original.status },
];

const rows = [
  { id: 1, name: 'Beta', status: 'Activo' },
  { id: 2, name: 'Alfa', status: 'Baja' },
];

describe('shared components accessibility (jsdom does not measure color contrast, covered by tokens.test.js)', () => {
  it('buttonVariantsHaveNoAxeViolations', async () => {
    const { container } = render(
      <div>
        <Button variant="primary">Guardar</Button>
        <Button variant="outline">Cancelar</Button>
        <Button variant="destructive">Eliminar</Button>
        <Button variant="ghost" size="icon" aria-label="Más opciones" />
        <Button variant="primary" disabled>Deshabilitado</Button>
      </div>,
    );
    expect(await axe(container)).toHaveNoViolations();
  });

  it('choiceChipHasNoAxeViolations', async () => {
    const { container } = render(
      <div>
        <ChoiceChip selected onClick={() => {}}>Todos</ChoiceChip>
        <ChoiceChip selected={false} onClick={() => {}}>Activos</ChoiceChip>
      </div>,
    );
    expect(await axe(container)).toHaveNoViolations();
  });

  it('kpiFilterChipHasNoAxeViolations', async () => {
    const { container } = render(
      <div>
        <KpiFilterChip label="Pendientes" count={4} pressed onClick={() => {}} />
        <KpiFilterChip label="Resueltos" count={12} pressed={false} variant="inline" onClick={() => {}} />
      </div>,
    );
    expect(await axe(container)).toHaveNoViolations();
  });

  it('statusPillHasNoAxeViolations', async () => {
    const { container } = render(
      <div>
        {['success', 'warning', 'danger', 'accent', 'neutral'].map((tone) => (
          <StatusPill key={tone} tone={tone} dot>Estado {tone}</StatusPill>
        ))}
      </div>,
    );
    expect(await axe(container)).toHaveNoViolations();
  });

  it('emptyStateHasNoAxeViolations', async () => {
    const { container } = render(
      <EmptyState icon="🔔" title="Sin novedades" description="Cuando haya algo nuevo lo vas a ver acá." action={<Button variant="outline">Actualizar</Button>} />,
    );
    expect(await axe(container)).toHaveNoViolations();
  });

  it('noticeHasNoAxeViolations', async () => {
    const { container } = render(
      <div>
        <Notice tone="warning" title="Atención">Revisá los datos antes de confirmar.</Notice>
        <Notice tone="danger">No pudimos guardar.</Notice>
      </div>,
    );
    expect(await axe(container)).toHaveNoViolations();
  });

  it('paginadorHasNoAxeViolations', async () => {
    const { container } = render(<Paginador page={2} totalPages={5} onChange={() => {}} />);
    expect(await axe(container)).toHaveNoViolations();
  });

  it('dataTableHasNoAxeViolations', async () => {
    const { container } = render(<DataTable columns={columns} data={rows} getRowId={(row) => String(row.id)} />);
    expect(await axe(container)).toHaveNoViolations();
  });

  it('openSheetHasNoAxeViolations', async () => {
    render(
      <Sheet open>
        <SheetContent>
          <SheetTitle>Filtros</SheetTitle>
          <SheetDescription>Elegí cómo filtrar la lista.</SheetDescription>
          <Button variant="primary">Aplicar</Button>
        </SheetContent>
      </Sheet>,
    );
    expect(await axe(document.body)).toHaveNoViolations();
  });

  it('openDialogHasNoAxeViolations', async () => {
    render(
      <Dialog open>
        <DialogContent>
          <DialogTitle>Confirmar</DialogTitle>
          <DialogDescription>Esta acción no se puede deshacer.</DialogDescription>
          <Button variant="primary">Aceptar</Button>
        </DialogContent>
      </Dialog>,
    );
    expect(await axe(document.body)).toHaveNoViolations();
  });
});
