import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from '@/components/ui/table';
import { formatChartValue } from './chartFormat';

export function ChartDataTable({ data, series, categoryLabel = 'Período', unit, currency }) {
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
          </TableRow>
        ))}
      </TableBody>
    </Table>
  );
}
