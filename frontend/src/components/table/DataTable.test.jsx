import { afterEach, describe, expect, it, vi } from 'vitest';
import { render, screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { DataTable } from './DataTable';

const columns = [
  { id: 'name', header: 'Nombre', accessorFn: (row) => row.name, cell: ({ row }) => row.original.name },
  {
    id: 'status',
    header: 'Estado',
    accessorFn: (row) => row.status,
    enableSorting: false,
    cell: ({ row }) => row.original.statusLabel,
    meta: { filterable: true, filterTitle: 'estado', filterLabel: (value) => (value === 'A' ? 'Activo' : 'Baja') },
  },
];

const data = [
  { id: 1, name: 'Beta', status: 'A', statusLabel: 'Activo' },
  { id: 2, name: 'Alfa', status: 'B', statusLabel: 'Baja' },
  { id: 3, name: 'Gamma', status: 'A', statusLabel: 'Activo' },
];

function bodyNames() {
  return screen.getAllByRole('row').slice(1).map((row) => within(row).getAllByRole('cell')[0].textContent);
}

function renderTable(props = {}) {
  return render(<DataTable columns={columns} data={data} getRowId={(row) => String(row.id)} {...props} />);
}

afterEach(() => {
  vi.restoreAllMocks();
});

describe('DataTable', () => {
  it('renders one row per item', () => {
    renderTable();

    expect(bodyNames()).toEqual(['Beta', 'Alfa', 'Gamma']);
  });

  it('sorts by a sortable column when its header is clicked', async () => {
    const user = userEvent.setup();
    renderTable();

    await user.click(screen.getByRole('button', { name: /Nombre/ }));

    expect(bodyNames()).toEqual(['Alfa', 'Beta', 'Gamma']);
    await user.click(screen.getByRole('button', { name: /Nombre/ }));
    expect(bodyNames()).toEqual(['Gamma', 'Beta', 'Alfa']);
  });

  it('filters by the values present in the data with an Excel style column filter', async () => {
    const user = userEvent.setup();
    renderTable();

    await user.click(screen.getByRole('button', { name: 'Filtrar por estado' }));
    expect(screen.getByRole('checkbox', { name: /Activo/ })).toBeChecked();
    expect(screen.getByRole('checkbox', { name: /Baja/ })).toBeChecked();
    await user.click(screen.getByRole('checkbox', { name: /Baja/ }));

    expect(bodyNames()).toEqual(['Beta', 'Gamma']);
  });

  it('shows a distinct empty state when filters leave no rows and clears them', async () => {
    const user = userEvent.setup();
    renderTable();

    await user.click(screen.getByRole('button', { name: 'Filtrar por estado' }));
    await user.click(screen.getByRole('checkbox', { name: 'Seleccionar todo' }));

    expect(screen.getByText('Ningún resultado con estos filtros')).toBeInTheDocument();
    await user.click(screen.getByRole('button', { name: 'Limpiar filtros' }));
    expect(bodyNames()).toHaveLength(3);
  });

  it('shows the provided empty state when there is no data', () => {
    renderTable({ data: [], emptyState: <p>Nada por acá</p> });

    expect(screen.getByText('Nada por acá')).toBeInTheDocument();
  });

  it('shows a loading placeholder', () => {
    renderTable({ isLoading: true });

    expect(screen.getByLabelText('Cargando')).toBeInTheDocument();
    expect(screen.queryByRole('table')).not.toBeInTheDocument();
  });

  it('shows the error with a retry action', async () => {
    const onRetry = vi.fn();
    const user = userEvent.setup();
    renderTable({ isError: true, onRetry, errorMessage: 'No pudimos cargar' });

    expect(screen.getByText('No pudimos cargar')).toBeInTheDocument();
    await user.click(screen.getByRole('button', { name: 'Reintentar' }));

    expect(onRetry).toHaveBeenCalledTimes(1);
  });

  it('expands a row to show its detail', async () => {
    const user = userEvent.setup();
    renderTable({ renderExpanded: (row) => <p>Detalle de {row.original.name}</p>, expandLabel: 'Ver partes' });

    expect(screen.queryByText('Detalle de Beta')).not.toBeInTheDocument();
    await user.click(screen.getAllByRole('button', { name: 'Ver partes' })[0]);

    expect(screen.getByText('Detalle de Beta')).toBeInTheDocument();
  });

  it('renders cards instead of a table on mobile', () => {
    vi.spyOn(window, 'matchMedia').mockImplementation(() => ({ matches: true, addEventListener() {}, removeEventListener() {} }));
    renderTable();

    expect(screen.queryByRole('table')).not.toBeInTheDocument();
    expect(screen.getByText('Gamma')).toBeInTheDocument();
    expect(screen.getAllByText('Nombre')).toHaveLength(3);
  });

  it('keeps the table with horizontal scroll on mobile when the layout is scroll', () => {
    vi.spyOn(window, 'matchMedia').mockImplementation(() => ({ matches: true, addEventListener() {}, removeEventListener() {} }));
    renderTable({ mobileLayout: 'scroll' });

    expect(screen.getByRole('table')).toBeInTheDocument();
  });

  it('shows the card title column first and without a label on mobile', () => {
    vi.spyOn(window, 'matchMedia').mockImplementation(() => ({ matches: true, addEventListener() {}, removeEventListener() {} }));
    const titled = [{ ...columns[0], meta: { cardTitle: true } }, columns[1]];
    renderTable({ columns: titled });

    expect(screen.getByText('Gamma')).toBeInTheDocument();
    expect(screen.queryByText('Nombre')).not.toBeInTheDocument();
    expect(screen.getAllByText('Estado')).toHaveLength(3);
  });

  it('keeps sorting and filtering reachable on mobile cards', async () => {
    vi.spyOn(window, 'matchMedia').mockImplementation(() => ({ matches: true, addEventListener() {}, removeEventListener() {} }));
    const user = userEvent.setup();
    renderTable();

    expect(screen.getByRole('combobox', { name: 'Ordenar por' })).toBeInTheDocument();
    await user.click(screen.getByRole('button', { name: 'Filtrar por estado' }));
    await user.click(screen.getByRole('checkbox', { name: /Baja/ }));

    expect(screen.queryByText('Alfa')).not.toBeInTheDocument();
    expect(screen.getByText('Beta')).toBeInTheDocument();
  });

  it('renders header-less columns as a full width footer of the card', () => {
    vi.spyOn(window, 'matchMedia').mockImplementation(() => ({ matches: true, addEventListener() {}, removeEventListener() {} }));
    const withAction = [...columns, { id: 'action', header: '', enableSorting: false, cell: () => <a href="/x">Abrir</a> }];
    renderTable({ columns: withAction });

    expect(screen.getAllByRole('link', { name: 'Abrir' })[0].parentElement).toHaveClass('border-t');
  });

  it('lays out a card block column with its label above the value', () => {
    vi.spyOn(window, 'matchMedia').mockImplementation(() => ({ matches: true, addEventListener() {}, removeEventListener() {} }));
    const block = [columns[0], { ...columns[1], meta: { cardBlock: true } }];
    renderTable({ columns: block });

    expect(screen.getAllByText('Estado')[0].parentElement).toHaveClass('flex-col');
  });

  it('hides columns marked hideInCard on mobile', () => {
    vi.spyOn(window, 'matchMedia').mockImplementation(() => ({ matches: true, addEventListener() {}, removeEventListener() {} }));
    const hidden = [columns[0], { ...columns[1], meta: { hideInCard: true } }];
    renderTable({ columns: hidden });

    expect(screen.queryByText('Estado')).not.toBeInTheDocument();
  });
});
