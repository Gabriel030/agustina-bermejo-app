import { Table, TableBody, TableCaption, TableCell, TableHead, TableHeader, TableRow } from '@/components/ui/table';
import { cn } from '@/lib/utils';

export function EmailChangeComparisonTable({ caption, columns, rows }) {
  return (
    <div className="w-full overflow-hidden rounded-lg border border-border">
      <Table className="table-fixed text-left text-size-13">
        <TableCaption className="sr-only">{caption}</TableCaption>
        <TableHeader>
          <TableRow>
            <TableCell className="w-16" />
            {columns.map((column) => (
              <TableHead key={column} scope="col" className="h-auto px-2.5 py-2 text-size-12">
                {column}
              </TableHead>
            ))}
          </TableRow>
        </TableHeader>
        <TableBody>
          {rows.map((row) => (
            <TableRow key={row.label}>
              <TableHead scope="row" className="h-auto w-16 px-2.5 py-2.5 align-top font-medium text-ink-soft normal-case">
                {row.label}
              </TableHead>
              {row.cells.map((cell, index) => (
                <TableCell key={columns[index]} className={cn('p-2.5 align-top break-words whitespace-normal text-ink', (row.emphasis ?? index === columns.length - 1) && 'font-bold')}>
                  {cell}
                </TableCell>
              ))}
            </TableRow>
          ))}
        </TableBody>
      </Table>
    </div>
  );
}
