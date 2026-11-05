import { Fragment, useCallback, useEffect, useRef, useState } from 'react';
import {
  flexRender,
  getCoreRowModel,
  getExpandedRowModel,
  getFacetedRowModel,
  getFacetedUniqueValues,
  getFilteredRowModel,
  getPaginationRowModel,
  getSortedRowModel,
  useReactTable,
} from '@tanstack/react-table';
import { ChevronDown, ChevronRight, ChevronUp, ChevronsUpDown } from 'lucide-react';
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from '@/components/ui/table';
import { Button } from '@/components/ui/button';
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from '@/components/ui/select';
import { Skeleton } from '@/components/Skeleton';
import { Paginador } from '@/components/Paginador';
import { EmptyState } from '@/components/EmptyState';
import { useMediaQuery } from '@/hooks/useMediaQuery';
import { cn } from '@/lib/utils';
import { ColumnFilter } from './ColumnFilter';
import { multiSelectFilter } from './multiSelectFilter';

const EXPANDER_COLUMN_ID = 'expander';
const LOADING_ROWS = 4;
const STICKY_SHADOW = 'shadow-[6px_0_8px_-6px_oklch(30%_0.05_260/0.25)]';
const STICKY_HEAD_CLASS = `sticky left-0 z-20 bg-dialog-bg ${STICKY_SHADOW}`;
const STICKY_CELL_CLASS = `sticky left-0 z-10 bg-dialog-bg ${STICKY_SHADOW}`;

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

const STICKY_START_CELL = 'sticky left-0 z-20 bg-dialog-bg shadow-[6px_0_8px_-6px_oklch(30%_0.05_260/0.25)]';

function stickyStartClass(meta, compact) {
  return compact && meta.stickyStart ? STICKY_START_CELL : undefined;
}

function useHorizontalOverflow(dependency) {
  const scrollRef = useRef(null);
  const [hasMoreToRight, setHasMoreToRight] = useState(false);
  const measure = useCallback(() => {
    const node = scrollRef.current;
    if (!node) return;
    setHasMoreToRight(node.scrollWidth - node.clientWidth - node.scrollLeft > 6);
  }, []);
  useEffect(() => {
    measure();
    window.addEventListener('resize', measure);
    return () => window.removeEventListener('resize', measure);
  }, [measure, dependency]);
  return { scrollRef, hasMoreToRight, measure };
}

function HeaderCell({ header, compact }) {
  'use no memo';
  const column = header.column;
  const meta = column.columnDef.meta ?? {};
  const label =
    column.columnDef.header === '' ? (
      <span className="sr-only">Acciones</span>
    ) : (
      flexRender(column.columnDef.header, header.getContext())
    );
  const sortDirection = column.getIsSorted();
  const ariaSort = sortDirection === 'asc' ? 'ascending' : sortDirection === 'desc' ? 'descending' : undefined;

  const filterColumn = meta.filterColumnId ? header.getContext().table.getColumn(meta.filterColumnId) : column;
  const filterMeta = filterColumn.columnDef.meta ?? {};

  const filterOptions = filterMeta.filterable
    ? Array.from(filterColumn.getFacetedUniqueValues().entries())
        .map(([value, count]) => ({ value, count, label: filterMeta.filterLabel ? filterMeta.filterLabel(value) : String(value) }))
        .sort((a, b) => a.label.localeCompare(b.label, 'es'))
    : [];

  return (
    <TableHead
      aria-sort={ariaSort}
      className={cn(
        meta.align === 'right' && 'text-right',
        meta.sticky && STICKY_HEAD_CLASS,
        meta.headerClassName,
        compact && 'bg-dialog-bg',
        compact && meta.stickyStart && 'sticky left-0 z-30 shadow-[6px_0_8px_-6px_oklch(30%_0.05_260/0.25)]',
      )}
    >
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
        {filterMeta.filterable && filterOptions.length > 1 && (
          <ColumnFilter
            label={filterMeta.filterTitle ?? String(column.columnDef.header)}
            options={filterOptions}
            selected={filterColumn.getFilterValue()}
            onChange={(value) => filterColumn.setFilterValue(value)}
          />
        )}
      </div>
    </TableHead>
  );
}

function facetOptions(column) {
  const meta = column.columnDef.meta ?? {};
  return Array.from(column.getFacetedUniqueValues().entries())
    .map(([value, count]) => ({ value, count, label: meta.filterLabel ? meta.filterLabel(value) : String(value) }))
    .sort((a, b) => a.label.localeCompare(b.label, 'es'));
}

function CardToolbar({ table }) {
  'use no memo';
  const columns = table.getAllLeafColumns();
  const sortable = columns.filter((column) => column.getCanSort() && typeof column.columnDef.header === 'string' && column.columnDef.header !== '');
  const filterable = columns.filter((column) => column.columnDef.meta?.filterable && facetOptions(column).length > 1);
  if (!sortable.length && !filterable.length) return null;
  const current = table.getState().sorting[0];
  const value = current ? `${current.id}:${current.desc ? 'desc' : 'asc'}` : undefined;
  return (
    <div className="flex flex-wrap items-center gap-x-4 gap-y-2 px-1 pb-1">
      {sortable.length > 0 && (
        <Select
          value={value}
          onValueChange={(next) => {
            const [id, direction] = next.split(':');
            table.setSorting([{ id, desc: direction === 'desc' }]);
          }}
        >
          <SelectTrigger aria-label="Ordenar por" className="h-8 w-auto gap-2 text-xs">
            <SelectValue placeholder="Ordenar por" />
          </SelectTrigger>
          <SelectContent>
            {sortable.flatMap((column) => [
              <SelectItem key={`${column.id}:asc`} value={`${column.id}:asc`}>
                {column.columnDef.header} (menor a mayor)
              </SelectItem>,
              <SelectItem key={`${column.id}:desc`} value={`${column.id}:desc`}>
                {column.columnDef.header} (mayor a menor)
              </SelectItem>,
            ])}
          </SelectContent>
        </Select>
      )}
      {filterable.map((column) => {
        const meta = column.columnDef.meta;
        return (
          <span key={column.id} className="inline-flex items-center gap-1 text-xs font-semibold text-ink-soft">
            {meta.filterTitle ?? column.columnDef.header}
            <ColumnFilter
              label={meta.filterTitle ?? String(column.columnDef.header)}
              options={facetOptions(column)}
              selected={column.getFilterValue()}
              onChange={(next) => column.setFilterValue(next)}
            />
          </span>
        );
      })}
    </div>
  );
}

function isInteractiveTarget(event) {
  return Boolean(event.target.closest('a, button, input, [role="checkbox"]'));
}

function RowCard({ row, renderExpanded, expandLabel, onRowClick }) {
  'use no memo';
  const cells = row.getVisibleCells().filter((cell) => cell.column.id !== EXPANDER_COLUMN_ID);
  const titleCells = cells.filter((cell) => cell.column.columnDef.meta?.cardTitle);
  const isActionCell = (cell) => cell.column.columnDef.header === '';
  const fieldCells = cells.filter((cell) => !cell.column.columnDef.meta?.cardTitle && !isActionCell(cell));
  const actionCells = cells.filter(isActionCell);
  return (
    <div
      className={cn('flex flex-col gap-2 rounded-2xl border border-border bg-surface p-4', onRowClick && 'cursor-pointer')}
      onClick={onRowClick ? (event) => !isInteractiveTarget(event) && onRowClick(row.original) : undefined}
    >
      {titleCells.map((cell) => (
        <div key={cell.id} className="min-w-0 text-sm text-ink">
          {flexRender(cell.column.columnDef.cell, cell.getContext())}
        </div>
      ))}
      {fieldCells.map((cell) => {
        const meta = cell.column.columnDef.meta ?? {};
        const header = cell.column.columnDef.header;
        if (meta.hideInCard) return null;
        if (meta.cardBlock) {
          return (
            <div key={cell.id} className="flex flex-col gap-0.5">
              {typeof header === 'string' && header !== '' && (
                <span className="text-xs font-semibold uppercase tracking-wide text-ink-faint">{header}</span>
              )}
              <div className="min-w-0 text-sm text-ink">{flexRender(cell.column.columnDef.cell, cell.getContext())}</div>
            </div>
          );
        }
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
      {actionCells.map((cell) => (
        <div key={cell.id} className="border-t border-border pt-2 text-sm">
          {flexRender(cell.column.columnDef.cell, cell.getContext())}
        </div>
      ))}
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
  mobileLayout = 'cards',
  compact = false,
  initialColumnVisibility = {},
  pageSize,
  className,
}) {
  'use no memo';
  const isCompactViewport = useMediaQuery('(max-width: 767px)');
  const isCardMode = isCompactViewport && mobileLayout === 'cards';

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

  const overflow = useHorizontalOverflow(data);

  const table = useReactTable({
    data: data ?? [],
    columns: allColumns,
    getRowId,
    initialState: {
      sorting: initialSorting,
      columnVisibility: initialColumnVisibility,
      ...(pageSize ? { pagination: { pageIndex: 0, pageSize } } : {}),
    },
    defaultColumn: { filterFn: multiSelectFilter, enableColumnFilter: false },
    getRowCanExpand: () => Boolean(renderExpanded),
    autoResetExpanded: false,
    getCoreRowModel: getCoreRowModel(),
    getFilteredRowModel: getFilteredRowModel(),
    getSortedRowModel: getSortedRowModel(),
    ...(pageSize ? { getPaginationRowModel: getPaginationRowModel() } : {}),
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
  const pager = pageSize ? (
    <Paginador
      page={table.getState().pagination.pageIndex + 1}
      totalPages={table.getPageCount()}
      onChange={(next) => table.setPageIndex(next - 1)}
    />
  ) : null;

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
        <CardToolbar table={table} />
        {rows.map((row) => (
          <RowCard key={row.id} row={row} renderExpanded={renderExpanded} expandLabel={expandLabel} onRowClick={onRowClick} />
        ))}
        {pager}
      </div>
    );
  }

  const tableNode = (
    <Table
      className={cn(compact && 'w-max min-w-full border-separate border-spacing-0', className)}
      containerClassName={compact ? 'overflow-visible' : undefined}
      style={{ minWidth }}
    >
      <TableHeader className={compact ? 'bg-dialog-bg' : undefined}>
        {table.getHeaderGroups().map((headerGroup) => (
          <TableRow key={headerGroup.id}>
            {headerGroup.headers.map((header) => (
              <HeaderCell key={header.id} header={header} compact={compact} />
            ))}
          </TableRow>
        ))}
      </TableHeader>
      <TableBody>
        {rows.map((row) => (
          <Fragment key={row.id}>
            <TableRow
              className={cn(onRowClick && 'cursor-pointer', compact && 'group h-[46px]')}
              onClick={onRowClick ? (event) => !isInteractiveTarget(event) && onRowClick(row.original) : undefined}
            >
              {row.getVisibleCells().map((cell) => {
                const meta = cell.column.columnDef.meta ?? {};
                return (
                  <TableCell
                    key={cell.id}
                    className={cn(
                      meta.align === 'right' && 'text-right',
                      meta.sticky && STICKY_CELL_CLASS,
                      compact && 'border-t border-border py-1',
                      stickyStartClass(meta, compact),
                      compact && meta.stickyStart && 'group-hover:bg-accent-soft',
                    )}
                  >
                    {flexRender(cell.column.columnDef.cell, cell.getContext())}
                  </TableCell>
                );
              })}
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

  if (!compact) {
    return pager ? (
      <div className="flex flex-col gap-2">
        {tableNode}
        {pager}
      </div>
    ) : (
      tableNode
    );
  }

  return (
    <div className="relative">
      <div
        ref={overflow.scrollRef}
        onScroll={overflow.measure}
        className="max-h-[calc(100dvh-13rem)] w-full overflow-auto rounded-xl"
      >
        {tableNode}
      </div>
      <div
        aria-hidden="true"
        data-testid="overflow-indicator"
        className={cn(
          'pointer-events-none absolute inset-y-0 right-0 flex w-9 items-center justify-end rounded-r-xl bg-gradient-to-r from-transparent to-dialog-bg pr-1.5 text-ink-soft transition-opacity motion-reduce:transition-none',
          overflow.hasMoreToRight ? 'opacity-100' : 'opacity-0',
        )}
      >
        <ChevronRight className="size-5" />
      </div>
      {pager}
    </div>
  );
}
