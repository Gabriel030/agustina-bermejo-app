import { Fragment } from 'react';
import {
  flexRender,
  getCoreRowModel,
  getExpandedRowModel,
  getFacetedRowModel,
  getFacetedUniqueValues,
  getFilteredRowModel,
  getSortedRowModel,
  useReactTable,
} from '@tanstack/react-table';
import { ChevronDown, ChevronUp, ChevronsUpDown } from 'lucide-react';
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from '@/components/ui/table';
import { Button } from '@/components/ui/button';
import { Skeleton } from '@/components/Skeleton';
import { EmptyState } from '@/components/EmptyState';
import { useMediaQuery } from '@/hooks/useMediaQuery';
import { cn } from '@/lib/utils';
import { ColumnFilter } from './ColumnFilter';
import { multiSelectFilter } from './multiSelectFilter';

const EXPANDER_COLUMN_ID = 'expander';
const LOADING_ROWS = 4;

function ExpandToggle({ row, label }) {
  'use no memo';
  return (
    <Button
      type="button"
      variant="ghost"
      size="icon"
      aria-label={label}
      aria-expanded={row.getIsExpanded()}
      onClick={row.getToggleExpandedHandler()}
    >
      <ChevronDown className={cn('size-4 transition-transform', row.getIsExpanded() && 'rotate-180')} />
    </Button>
  );
}

function SortIcon({ direction }) {
  if (direction === 'asc') return <ChevronUp className="size-3.5 text-primary" />;
  if (direction === 'desc') return <ChevronDown className="size-3.5 text-primary" />;
  return <ChevronsUpDown className="size-3.5 opacity-30" />;
}

function HeaderCell({ header }) {
  'use no memo';
  const column = header.column;
  const meta = column.columnDef.meta ?? {};
  const label = flexRender(column.columnDef.header, header.getContext());
  const sortDirection = column.getIsSorted();
  const ariaSort = sortDirection === 'asc' ? 'ascending' : sortDirection === 'desc' ? 'descending' : undefined;

  const filterOptions = meta.filterable
    ? Array.from(column.getFacetedUniqueValues().entries())
        .map(([value, count]) => ({ value, count, label: meta.filterLabel ? meta.filterLabel(value) : String(value) }))
        .sort((a, b) => a.label.localeCompare(b.label, 'es'))
    : [];

  return (
    <TableHead aria-sort={ariaSort} className={cn(meta.align === 'right' && 'text-right', meta.headerClassName)}>
      <div className={cn('flex items-center gap-1', meta.align === 'right' && 'justify-end')}>
        {column.getCanSort() ? (
          <button
            type="button"
            onClick={column.getToggleSortingHandler()}
            className="flex items-center gap-1 uppercase hover:text-foreground"
          >
            {label}
            <SortIcon direction={sortDirection} />
          </button>
        ) : (
          label
        )}
        {meta.filterable && filterOptions.length > 1 && (
          <ColumnFilter
            label={meta.filterTitle ?? String(column.columnDef.header)}
            options={filterOptions}
            selected={column.getFilterValue()}
            onChange={(value) => column.setFilterValue(value)}
          />
        )}
      </div>
    </TableHead>
  );
}

function isInteractiveTarget(event) {
  return Boolean(event.target.closest('a, button, input, [role="checkbox"]'));
}

function RowCard({ row, renderExpanded, expandLabel, onRowClick }) {
  'use no memo';
  const cells = row.getVisibleCells().filter((cell) => cell.column.id !== EXPANDER_COLUMN_ID);
  return (
    <div
      className={cn('flex flex-col gap-2 rounded-2xl border border-border bg-surface p-4', onRowClick && 'cursor-pointer')}
      onClick={onRowClick ? (event) => !isInteractiveTarget(event) && onRowClick(row.original) : undefined}
    >
      {cells.map((cell) => {
        const meta = cell.column.columnDef.meta ?? {};
        const header = cell.column.columnDef.header;
        if (meta.hideInCard) return null;
        return (
          <div key={cell.id} className="flex items-center justify-between gap-3">
            {typeof header === 'string' && header !== '' && (
              <span className="text-xs font-semibold uppercase tracking-wide text-ink-faint">{header}</span>
            )}
            <div className="ml-auto min-w-0 text-right text-sm text-ink">
              {flexRender(cell.column.columnDef.cell, cell.getContext())}
            </div>
          </div>
        );
      })}
      {renderExpanded && (
        <div className="flex justify-end">
          <ExpandToggle row={row} label={expandLabel} />
        </div>
      )}
      {renderExpanded && row.getIsExpanded() && <div className="border-t border-border pt-2">{renderExpanded(row)}</div>}
    </div>
  );
}

export function DataTable({
  columns,
  data,
  getRowId,
  isLoading = false,
  isError = false,
  onRetry,
  errorMessage = 'No pudimos cargar los datos.',
  emptyState,
  renderExpanded,
  expandLabel = 'Ver detalle',
  onRowClick,
  initialSorting = [],
  minWidth = 640,
  className,
}) {
  'use no memo';
  const isCardMode = useMediaQuery('(max-width: 767px)');

  const allColumns = renderExpanded
    ? [
        ...columns,
        {
          id: EXPANDER_COLUMN_ID,
          header: '',
          enableSorting: false,
          enableColumnFilter: false,
          meta: { headerClassName: 'w-12' },
          cell: ({ row }) => <ExpandToggle row={row} label={expandLabel} />,
        },
      ]
    : columns;

  const table = useReactTable({
    data: data ?? [],
    columns: allColumns,
    getRowId,
    initialState: { sorting: initialSorting },
    defaultColumn: { filterFn: multiSelectFilter, enableColumnFilter: false },
    getRowCanExpand: () => Boolean(renderExpanded),
    autoResetExpanded: false,
    getCoreRowModel: getCoreRowModel(),
    getFilteredRowModel: getFilteredRowModel(),
    getSortedRowModel: getSortedRowModel(),
    getExpandedRowModel: getExpandedRowModel(),
    getFacetedRowModel: getFacetedRowModel(),
    getFacetedUniqueValues: getFacetedUniqueValues(),
  });

  if (isLoading) {
    return (
      <div className="flex flex-col gap-2 p-4" aria-busy="true" aria-label="Cargando">
        {Array.from({ length: LOADING_ROWS }, (_, index) => (
          <Skeleton key={index} className="h-12 w-full rounded-xl" />
        ))}
      </div>
    );
  }

  if (isError) {
    return (
      <EmptyState
        title={errorMessage}
        action={
          onRetry && (
            <Button type="button" variant="outline" onClick={onRetry}>
              Reintentar
            </Button>
          )
        }
      />
    );
  }

  if (!data?.length) {
    return emptyState ?? <EmptyState title="Sin resultados" />;
  }

  const rows = table.getRowModel().rows;

  if (!rows.length) {
    return (
      <EmptyState
        title="Ningún resultado con estos filtros"
        description="Probá quitar algún filtro para ver más filas."
        action={
          <Button type="button" variant="outline" onClick={() => table.resetColumnFilters()}>
            Limpiar filtros
          </Button>
        }
      />
    );
  }

  if (isCardMode) {
    return (
      <div className={cn('flex flex-col gap-2 p-3', className)}>
        {rows.map((row) => (
          <RowCard key={row.id} row={row} renderExpanded={renderExpanded} expandLabel={expandLabel} onRowClick={onRowClick} />
        ))}
      </div>
    );
  }

  return (
    <Table className={className} style={{ minWidth }}>
      <TableHeader>
        {table.getHeaderGroups().map((headerGroup) => (
          <TableRow key={headerGroup.id}>
            {headerGroup.headers.map((header) => (
              <HeaderCell key={header.id} header={header} />
            ))}
          </TableRow>
        ))}
      </TableHeader>
      <TableBody>
        {rows.map((row) => (
          <Fragment key={row.id}>
            <TableRow
              className={cn(onRowClick && 'cursor-pointer')}
              onClick={onRowClick ? (event) => !isInteractiveTarget(event) && onRowClick(row.original) : undefined}
            >
              {row.getVisibleCells().map((cell) => (
                <TableCell key={cell.id} className={cn(cell.column.columnDef.meta?.align === 'right' && 'text-right')}>
                  {flexRender(cell.column.columnDef.cell, cell.getContext())}
                </TableCell>
              ))}
            </TableRow>
            {renderExpanded && row.getIsExpanded() && (
              <TableRow className="hover:bg-transparent">
                <TableCell colSpan={allColumns.length} className="bg-neutral-bg/40 p-0 whitespace-normal">
                  {renderExpanded(row)}
                </TableCell>
              </TableRow>
            )}
          </Fragment>
        ))}
      </TableBody>
    </Table>
  );
}
