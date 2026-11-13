import { useMemo, useState } from 'react';
import { GlassCard } from '@/components/GlassCard';
import { StatCard } from '@/components/StatCard';
import { StatusPill } from '@/components/StatusPill';
import { Stagger } from '@/components/Stagger';
import { Skeleton } from '@/components/Skeleton';
import { EmptyState } from '@/components/EmptyState';
import { Buscador } from '@/components/Buscador';
import { ProgressBar } from '@/components/ProgressBar';
import { Button } from '@/components/ui/button';
import { DataTable } from '@/components/table/DataTable';
import { attendancePercentToPoints } from '@/lib/attendancePercent';
import { attendanceRisk } from '@/features/attendance/lib/attendanceRisk';
import { useRosterDeAsistencia } from '@/features/attendance/hooks/useDocenteAsistencia';

const percentFormatter = new Intl.NumberFormat('es-AR', { maximumFractionDigits: 1 });

function formatPercent(value) {
  return value == null ? '—' : `${percentFormatter.format(value)}%`;
}

function toStudentRow(entry) {
  return {
    ...entry,
    attendancePoints: attendancePercentToPoints(entry.attendancePercent),
    risk: attendanceRisk(entry),
  };
}

function matchesSearch(row, term) {
  const needle = term.trim().toLowerCase();
  if (!needle) return true;
  return `${row.studentName} ${row.dni}`.toLowerCase().includes(needle);
}

const PILL_TONE = { warning: 'warning', danger: 'danger' };

const COLUMNS = [
  {
    id: 'studentName',
    header: 'Alumno',
    accessorKey: 'studentName',
    sortingFn: (a, b) => a.original.studentName.localeCompare(b.original.studentName, 'es'),
    cell: ({ row }) => <span className="font-semibold text-ink">{row.original.studentName}</span>,
  },
  {
    id: 'dni',
    header: 'DNI',
    accessorKey: 'dni',
    enableSorting: false,
    cell: ({ row }) => <span className="text-ink-faint">{row.original.dni}</span>,
  },
  {
    id: 'absenceCount',
    header: 'Inasistencias',
    accessorKey: 'absenceCount',
    cell: ({ row }) => <span className="tabular-nums">{row.original.risk.text}</span>,
  },
  {
    id: 'attendancePercent',
    header: 'Asistencia',
    accessorFn: (row) => row.attendancePoints ?? -1,
    cell: ({ row }) => (
      <div className="flex w-[130px] items-center gap-2">
        <ProgressBar
          className="w-[70px] shrink-0"
          value={row.original.attendancePoints}
          tone={row.original.risk.level === 'danger' ? 'danger' : 'accent'}
          showValue={false}
          label="Asistencia"
        />
        <span className="min-w-[46px] tabular-nums">{formatPercent(row.original.attendancePoints)}</span>
      </div>
    ),
  },
  {
    id: 'risk',
    header: 'Riesgo',
    accessorFn: (row) => row.risk.level,
    enableSorting: false,
    cell: ({ row }) =>
      row.original.risk.pill ? (
        <StatusPill tone={PILL_TONE[row.original.risk.level]}>{row.original.risk.pill}</StatusPill>
      ) : (
        <span className="text-ink-faint">—</span>
      ),
  },
];

function maxRuleNote(rows) {
  const reference = rows.find((row) => row.maxAbsenceRate != null && row.classesHeld > 0);
  if (!reference || reference.risk.permitted == null) return null;
  return `Máximo de la comisión: ${percentFormatter.format(reference.maxAbsenceRate)}% de las clases (${reference.risk.permitted} de ${reference.classesHeld} dictadas)`;
}

export function TabAlumnos({ type, sectionId }) {
  const { data: roster, isLoading, isError, refetch } = useRosterDeAsistencia(type, sectionId);
  const [search, setSearch] = useState('');
  const rows = useMemo(() => (roster ?? []).map(toStudentRow), [roster]);
  const visibleRows = useMemo(() => rows.filter((row) => matchesSearch(row, search)), [rows, search]);

  if (isLoading) {
    return (
      <div className="flex flex-col gap-3" aria-busy="true" aria-label="Cargando">
        <div className="grid grid-cols-3 gap-3">
          {[1, 2, 3].map((key) => (
            <GlassCard key={key} className="flex flex-col gap-2 p-3">
              <Skeleton className="h-2.5 w-14" />
              <Skeleton className="h-6 w-10" />
            </GlassCard>
          ))}
        </div>
        <GlassCard className="flex flex-col gap-2 p-3">
          {Array.from({ length: 6 }, (_, index) => (
            <Skeleton key={index} className="h-11 w-full rounded-xl" />
          ))}
        </GlassCard>
      </div>
    );
  }

  if (isError) {
    return (
      <GlassCard className="p-6">
        <EmptyState
          icon="⚠️"
          title="No pudimos cargar los alumnos"
          description="Revisá tu conexión e intentá de nuevo."
          action={
            <Button variant="outline" onClick={() => refetch()}>
              Reintentar
            </Button>
          }
        />
      </GlassCard>
    );
  }

  if (rows.length === 0) {
    return (
      <GlassCard className="p-6">
        <EmptyState
          icon="👥"
          title="Todavía no hay alumnos inscriptos"
          description="Cuando se inscriba el primero vas a verlo acá con sus inasistencias y su riesgo."
        />
      </GlassCard>
    );
  }

  const atRiskCount = rows.filter((row) => row.risk.level !== 'ok').length;
  const totalAbsences = rows.reduce((sum, row) => sum + row.absenceCount, 0);
  const note = maxRuleNote(rows);

  return (
    <div className="flex flex-col gap-3">
      <div className="grid grid-cols-3 gap-3">
        <Stagger>
          <StatCard label="Inscriptos" className="p-3 md:p-5">
            <span className="font-heading text-size-22 font-bold text-ink md:text-size-26">{rows.length}</span>
          </StatCard>
          <StatCard label="En riesgo" className="p-3 md:p-5">
            <span className="font-heading text-size-22 font-bold text-danger md:text-size-26">{atRiskCount}</span>
          </StatCard>
          <StatCard label="Inasistencias acumuladas" className="p-3 md:p-5">
            <span className="font-heading text-size-22 font-bold text-ink md:text-size-26">{totalAbsences}</span>
          </StatCard>
        </Stagger>
      </div>
      <GlassCard className="flex flex-col gap-3 p-3 md:p-5">
        <div className="flex flex-wrap items-center justify-between gap-2">
          <Buscador value={search} onChange={setSearch} placeholder="Buscar por nombre o DNI" />
          {note && <span className="text-size-12-5 text-ink-faint">{note}</span>}
        </div>
        <DataTable
          columns={COLUMNS}
          data={visibleRows}
          getRowId={(row) => String(row.enrollmentId)}
          initialSorting={[{ id: 'studentName', desc: false }]}
          minWidth={560}
          emptyState={<EmptyState icon="🔎" title="Ningún alumno coincide" description="Probá con otro nombre o DNI." />}
        />
      </GlassCard>
    </div>
  );
}
