import { Table, TableBody, TableCaption, TableCell, TableHead, TableHeader, TableRow } from '@/components/ui/table';
import { PLAN_STATUS_PILL } from '@/features/workspace/components/planStatusPills';
import { formatCredits } from '@/features/workspace/lib/curriculumPlan';
import { formatDecimal, formatPercent } from '@/lib/formatNumber';

function signed(value, format) {
  if (value == null) return '—';
  if (value === 0) return format(0);
  return `${value > 0 ? '+' : '−'}${format(Math.abs(value))}`;
}

const average = (value) => (value == null ? '—' : formatDecimal(value, { minimumFractionDigits: 2 }));

function buildRows(effect) {
  const averageDelta = effect.averageBefore == null || effect.averageAfter == null ? null : effect.averageAfter - effect.averageBefore;
  return [
    {
      key: 'status',
      label: 'Estado de la materia',
      before: PLAN_STATUS_PILL[effect.statusBefore].label,
      after: PLAN_STATUS_PILL.aprobada.label,
      delta: 'Equivalencia',
    },
    {
      key: 'approved',
      label: 'Materias aprobadas',
      before: formatCredits(effect.approvedBefore),
      after: formatCredits(effect.approvedAfter),
      delta: signed(effect.approvedAfter - effect.approvedBefore, formatCredits),
    },
    {
      key: 'credits',
      label: 'Créditos',
      before: formatCredits(effect.creditsBefore),
      after: formatCredits(effect.creditsAfter),
      delta: signed(effect.creditsAfter - effect.creditsBefore, formatCredits),
    },
    {
      key: 'progress',
      label: 'Avance del plan',
      before: formatPercent(effect.percentBefore),
      after: formatPercent(effect.percentAfter),
      delta: signed(effect.percentAfter - effect.percentBefore, (value) => formatPercent(value)),
    },
    {
      key: 'average',
      label: 'Promedio',
      before: average(effect.averageBefore),
      after: average(effect.averageAfter),
      delta: signed(averageDelta, (value) => formatDecimal(value, { minimumFractionDigits: 2 })),
    },
  ];
}

export function EquivalenceComparisonTable({ effect }) {
  return (
    <Table className="table-fixed text-size-12 md:text-size-13">
      <colgroup>
        <col className="w-[31%]" />
        <col className="w-[23%]" />
        <col className="w-[23%]" />
        <col className="w-[23%]" />
      </colgroup>
      <TableCaption className="sr-only">Efecto de la equivalencia sobre el plan del alumno</TableCaption>
      <TableHeader>
        <TableRow>
          <TableHead scope="col" className="px-1.5 md:px-2">Dato</TableHead>
          <TableHead scope="col" className="px-1.5 md:px-2">Hoy</TableHead>
          <TableHead scope="col" className="bg-accent-soft px-1.5 text-ink md:px-2">
            Nuevo
          </TableHead>
          <TableHead scope="col" className="px-1.5 md:px-2">Diferencia</TableHead>
        </TableRow>
      </TableHeader>
      <TableBody>
        {buildRows(effect).map((row) => (
          <TableRow key={row.key}>
            <TableHead scope="row" className="whitespace-normal px-1.5 font-semibold text-ink md:px-2">
              {row.label}
            </TableHead>
            <TableCell className="whitespace-normal px-1.5 tabular-nums text-ink-soft md:px-2">{row.before}</TableCell>
            <TableCell className="whitespace-normal bg-accent-soft px-1.5 font-semibold tabular-nums text-ink md:px-2">{row.after}</TableCell>
            <TableCell className="whitespace-normal px-1.5 tabular-nums text-ink md:px-2">{row.delta}</TableCell>
          </TableRow>
        ))}
      </TableBody>
    </Table>
  );
}
