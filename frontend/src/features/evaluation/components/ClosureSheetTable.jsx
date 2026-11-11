import { useMemo } from 'react';
import { DataTable } from '@/components/table/DataTable';
import { EmptyState } from '@/components/EmptyState';
import { RESULT_FILTER_ID } from '../lib/closureSheet';
import { buildClosureColumns } from './closureColumns';

const HIDDEN_COLUMNS = { [RESULT_FILTER_ID]: false };
const INITIAL_SORTING = [{ id: 'student', desc: false }];

export function ClosureSheetTable({ sheet, columnFilters, onColumnFiltersChange, onOpenPrerequisites, detail = false, rowActions = null }) {
  const columns = useMemo(() => buildClosureColumns(sheet, { onOpenPrerequisites, detail, rowActions }), [sheet, onOpenPrerequisites, detail, rowActions]);
  return (
    <DataTable
      columns={columns}
      data={sheet.students}
      getRowId={(row) => String(row.enrollmentId)}
      compact
      mobileLayout="table"
      minWidth={1080}
      initialColumnVisibility={HIDDEN_COLUMNS}
      initialSorting={INITIAL_SORTING}
      columnFilters={columnFilters}
      onColumnFiltersChange={onColumnFiltersChange}
      emptyState={<EmptyState title="Esta comisión no tiene alumnos inscriptos" />}
    />
  );
}
