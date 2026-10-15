import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from '@/components/ui/table';
import { Button } from '@/components/ui/button';
import { formatChartValue } from './chartFormat';

export function ChartDataTable({ data, series, categoryLabel = 'Período', unit, currency, onRowOpen, openLabel = 'Abrir' }) {
  return (
    <Table>
      <TableHeader>
        <TableRow>
          <TableHead>{categoryLabel}</TableHead>
          {series.map((item) => (
            <TableHead key={item.key} className="text-right">
              {item.label}
            </TableHead>
          ))}
          {onRowOpen && (
            <TableHead className="w-0 text-right">
              <span className="sr-only">Acciones</span>
            </TableHead>
          )}
        </TableRow>
      </TableHeader>
      <TableBody>
        {data.map((row, index) => (
          <TableRow key={`${row.label}-${index}`}>
            <TableCell className="font-medium">{row.label}</TableCell>
            {series.map((item) => (
              <TableCell key={item.key} className="text-right tabular-nums">
                {formatChartValue(row[item.key], { unit: item.unit ?? unit, currency })}
              </TableCell>
            ))}
            {onRowOpen && (
              <TableCell className="text-right">
                <Button type="button" variant="ghost" size="sm" aria-label={`${openLabel}: ${row.label}`} onClick={() => onRowOpen(row)}>
                  {openLabel}
                </Button>
              </TableCell>
            )}
          </TableRow>
        ))}
      </TableBody>
    </Table>
  );
}
