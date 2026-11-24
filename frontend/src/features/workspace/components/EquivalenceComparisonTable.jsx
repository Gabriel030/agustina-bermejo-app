import { Table, TableBody, TableCaption, TableCell, TableHead, TableHeader, TableRow } from '@/components/ui/table';
import { PlanStatusPill } from '@/features/workspace/components/PlanStatusPill';
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
      before: <PlanStatusPill status={effect.statusBefore} />,
      after: <PlanStatusPill status="aprobada" />,
      delta: 'Aprobada por equivalencia',
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
    <Table className="table-fixed text-size-13">
      <colgroup>
        <col className="w-[34%]" />
        <col className="w-[22%]" />
        <col className="w-[24%]" />
        <col className="w-[20%]" />
      </colgroup>
      <TableCaption className="sr-only">Efecto de la equivalencia sobre el plan del alumno</TableCaption>
      <TableHeader>
        <TableRow>
          <TableHead scope="col">Dato</TableHead>
          <TableHead scope="col">Hoy</TableHead>
          <TableHead scope="col" className="bg-accent-soft text-ink">
            Nuevo
          </TableHead>
          <TableHead scope="col">Diferencia</TableHead>
        </TableRow>
      </TableHeader>
      <TableBody>
        {buildRows(effect).map((row) => (
          <TableRow key={row.key}>
            <TableHead scope="row" className="whitespace-normal font-semibold text-ink">
              {row.label}
            </TableHead>
            <TableCell className="whitespace-normal tabular-nums text-ink-soft">{row.before}</TableCell>
            <TableCell className="whitespace-normal bg-accent-soft font-semibold tabular-nums text-ink">{row.after}</TableCell>
            <TableCell className="whitespace-normal tabular-nums text-ink">{row.delta}</TableCell>
          </TableRow>
        ))}
      </TableBody>
    </Table>
  );
}
