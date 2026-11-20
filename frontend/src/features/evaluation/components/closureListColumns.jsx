import { Link } from 'react-router-dom';
import { StatusPill } from '@/components/StatusPill';
import { Button } from '@/components/ui/button';
import { InfoTip } from '@/components/charts/InfoTip';
import { formatearFecha } from '@/lib/comisiones';
import { CLOSURE_LIST_STATUS, CLOSURE_STATUS_FACT, closureSheetPath, firstInstructorLabel, reportLabel } from '../lib/closureList';

const EMPTY = <span className="text-ink-faint">—</span>;

function countOrDash(value) {
  return value == null ? EMPTY : value;
}

export function buildClosureListColumns(role) {
  return [
    {
      id: 'subject',
      header: 'Materia',
      enableSorting: false,
      meta: { sticky: true, rowHeader: true, cardTitle: true },
      cell: ({ row }) => (
        <div className="flex min-w-36 max-w-56 flex-col whitespace-normal">
          <Link to={closureSheetPath(role, row.original)} className="font-semibold text-ink hover:underline">
            {row.original.subjectName}
          </Link>
          <span className="text-xs text-ink-soft">{row.original.sectionName}</span>
          {row.original.termLabel && <span className="text-xs text-ink-faint">{row.original.termLabel}</span>}
        </div>
      ),
    },
    {
      id: 'instructor',
      header: 'Docente',
      enableSorting: false,
      cell: ({ row }) => (
        <span className="block max-w-36 truncate" title={row.original.instructorNames.join(', ')}>
          {firstInstructorLabel(row.original.instructorNames)}
        </span>
      ),
    },
    {
      id: 'students',
      header: 'Alumnos',
      enableSorting: false,
      meta: { align: 'right' },
      cell: ({ row }) => <span className="tabular-nums">{row.original.studentCount}</span>,
    },
    {
      id: 'promote',
      header: 'Promocionan',
      enableSorting: false,
      meta: { align: 'right' },
      cell: ({ row }) => <span className="tabular-nums">{countOrDash(row.original.promoteCount)}</span>,
    },
    {
      id: 'toReview',
      header: () => (
        <span className="inline-flex items-center gap-1">
          A revisar
          <InfoTip label="Qué es A revisar">
            Alumnos con alertas (por ejemplo, correlativa sin aprobar) o con promoción condicionada. El detalle está en la planilla.
          </InfoTip>
        </span>
      ),
      enableSorting: false,
      meta: { align: 'right' },
      cell: ({ row }) => {
        const value = row.original.toReviewCount;
        if (value == null) return EMPTY;
        return <span className={value > 0 ? 'font-bold tabular-nums' : 'tabular-nums text-ink-faint'}>{value}</span>;
      },
    },
    {
      id: 'status',
      header: 'Estado',
      enableSorting: false,
      cell: ({ row }) => {
        const status = CLOSURE_LIST_STATUS[row.original.status];
        return (
          <StatusPill tone={status.tone} dot={status.dot}>
            {status.label}
          </StatusPill>
        );
      },
    },
    {
      id: 'lastNews',
      header: 'Última novedad',
      enableSorting: false,
      cell: ({ row }) => (
        <div className="flex flex-col">
          <span>{formatearFecha(row.original.statusDate) ?? '—'}</span>
          <span className="text-xs text-ink-faint">{CLOSURE_STATUS_FACT[row.original.statusFact]}</span>
        </div>
      ),
    },
    {
      id: 'report',
      header: 'Acta',
      enableSorting: false,
      cell: ({ row }) => {
        const report = reportLabel(row.original);
        if (!report) return EMPTY;
        return (
          <div className="flex flex-col">
            <span>{report.number}</span>
            <span className="text-xs text-ink-soft">{report.location}</span>
          </div>
        );
      },
    },
    {
      id: 'actions',
      header: '',
      enableSorting: false,
      cell: ({ row }) => {
        const label = row.original.status === 'CLOSED' ? 'Ver acta' : 'Abrir planilla';
        return (
          <Button asChild variant="outline" size="sm">
            <Link to={closureSheetPath(role, row.original)} aria-label={`${label} de ${row.original.subjectName}, ${row.original.sectionName}`}>
              {label}
            </Link>
          </Button>
        );
      },
    },
  ];
}
