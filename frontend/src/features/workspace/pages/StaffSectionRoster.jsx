import { useMemo, useState } from 'react';
import { useNavigate, useParams } from 'react-router-dom';
import { Megaphone } from 'lucide-react';
import { PageContainer } from '@/components/PageContainer';
import { GlassCard } from '@/components/GlassCard';
import { StatCard } from '@/components/StatCard';
import { StatusPill } from '@/components/StatusPill';
import { Stagger } from '@/components/Stagger';
import { Skeleton } from '@/components/Skeleton';
import { EmptyState } from '@/components/EmptyState';
import { Breadcrumb } from '@/components/Breadcrumb';
import { Buscador } from '@/components/Buscador';
import { ProgressBar } from '@/components/ProgressBar';
import { Button } from '@/components/ui/button';
import { Tooltip, TooltipContent, TooltipProvider, TooltipTrigger } from '@/components/ui/tooltip';
import { DataTable } from '@/components/table/DataTable';
import { attendancePercentToPoints } from '@/lib/attendancePercent';
import { useReporteAusencias } from '@/features/attendance';
import { useCycle, useCicloSecciones, useRoleBasePath, describeSection } from '@/features/academic-structure';

const percentFormatter = new Intl.NumberFormat('es-AR', { maximumFractionDigits: 1 });

function formatPercent(value) {
  return value == null ? '—' : `${percentFormatter.format(value)}%`;
}

function RiskPill() {
  return (
    <TooltipProvider>
      <Tooltip>
        <TooltipTrigger asChild>
          <span tabIndex={0} className="inline-flex w-fit rounded-full focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring">
            <StatusPill tone="danger">En riesgo</StatusPill>
          </span>
        </TooltipTrigger>
        <TooltipContent>Supera el máximo de inasistencias</TooltipContent>
      </Tooltip>
    </TooltipProvider>
  );
}

function toRosterRow(entry) {
  return { ...entry, attendancePoints: attendancePercentToPoints(entry.attendancePercent) };
}

function buildColumns() {
  return [
    {
      id: 'studentName',
      header: 'Alumno',
      accessorKey: 'studentName',
      sortingFn: (a, b) => a.original.studentName.localeCompare(b.original.studentName, 'es'),
      meta: { stickyStart: true, filterColumnId: 'atRisk', headerClassName: 'min-w-[148px] max-w-[148px]' },
      cell: ({ row }) => (
        <div className="flex min-w-0 flex-col items-start gap-0.5 whitespace-normal">
          <span className="text-[13px] font-semibold leading-tight text-ink">{row.original.studentName}</span>
          {row.original.atRisk && <RiskPill />}
        </div>
      ),
    },
    {
      id: 'atRisk',
      header: 'En riesgo',
      accessorKey: 'atRisk',
      enableSorting: false,
      enableColumnFilter: true,
      meta: {
        filterable: true,
        filterTitle: 'riesgo',
        filterLabel: (value) => (value ? 'En riesgo' : 'Sin riesgo'),
      },
      cell: () => null,
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
      header: 'Ausencias',
      accessorKey: 'absenceCount',
      meta: { align: 'right' },
      cell: ({ row }) => <span className="tabular-nums">{row.original.absenceCount}</span>,
    },
    {
      id: 'attendancePercent',
      header: 'Asistencia',
      accessorFn: (entry) => entry.attendancePoints ?? -1,
      cell: ({ row }) => (
        <div className="flex w-[130px] items-center gap-2">
          <ProgressBar
            className="w-[70px] shrink-0"
            value={row.original.attendancePoints}
            tone={row.original.atRisk ? 'danger' : 'accent'}
            showValue={false}
          />
          <span className="min-w-[46px] tabular-nums">{formatPercent(row.original.attendancePoints)}</span>
        </div>
      ),
    },
  ];
}

function matchesSearch(entry, term) {
  if (!term) return true;
  const needle = term.trim().toLowerCase();
  return `${entry.studentName} ${entry.dni}`.toLowerCase().includes(needle);
}

export function StaffSectionRoster() {
  const { cycleId, sectionId } = useParams();
  const navigate = useNavigate();
  const basePath = useRoleBasePath();
  const [search, setSearch] = useState('');
  const { data: cycle } = useCycle(cycleId);
  const { data: sections } = useCicloSecciones(cycleId);
  const sectionType = cycle?.courseId ? 'COURSE' : 'PROGRAM';
  const offeringName = cycle?.courseId ? cycle.courseName : cycle?.programName;
  const offeringPath = cycle?.courseId
    ? `${basePath}/oferta-academica/cursos/${cycle.courseId}`
    : `${basePath}/oferta-academica/carreras/${cycle?.programId}`;
  const { data: entries, isLoading, isError, refetch } = useReporteAusencias(cycle ? sectionType : undefined, cycle ? sectionId : undefined);

  const section = sections?.find((candidate) => String(candidate.id) === String(sectionId));
  const columns = useMemo(buildColumns, []);
  const rosterRows = useMemo(() => (entries ?? []).map(toRosterRow), [entries]);
  const visibleEntries = useMemo(() => rosterRows.filter((entry) => matchesSearch(entry, search)), [rosterRows, search]);

  const total = entries?.length ?? 0;
  const atRiskCount = (entries ?? []).filter((entry) => entry.atRisk).length;
  const withPercent = rosterRows.filter((entry) => entry.attendancePoints != null);
  const averagePercent = withPercent.length
    ? withPercent.reduce((sum, entry) => sum + entry.attendancePoints, 0) / withPercent.length
    : null;
  const loading = isLoading || !cycle;

  const breadcrumb = [
    { label: 'Oferta académica', to: `${basePath}/oferta-academica` },
    ...(cycle ? [{ label: offeringName, to: offeringPath }, { label: cycle.termName }] : []),
  ];

  return (
    <PageContainer className="gap-3 md:gap-6">
      <div className="flex flex-col gap-1.5">
        <Breadcrumb items={breadcrumb} />
        <div className="flex flex-wrap items-center justify-between gap-2">
          <h1 className="font-heading text-xl font-bold text-ink md:text-2xl">Inscriptos y asistencia</h1>
          <Button variant="outline" className="max-md:w-full" onClick={() => navigate(`${basePath}/oferta-academica/ciclos/${cycleId}/comisiones/${sectionId}/avisos`)}>
            <Megaphone className="mr-1.5 h-4 w-4" />
            Avisos y lectura
          </Button>
        </div>
        {section && <p className="text-[12.5px] text-ink-faint">{describeSection(section)}</p>}
      </div>

      {loading ? (
        <>
          <div className="grid grid-cols-3 gap-3" aria-busy="true" aria-label="Cargando">
            {[1, 2, 3].map((key) => (
              <GlassCard key={key} className="flex flex-col gap-2 p-3">
                <Skeleton className="h-2.5 w-14" />
                <Skeleton className="h-6 w-10" />
              </GlassCard>
            ))}
          </div>
          <GlassCard className="flex flex-col gap-2 p-3">
            {Array.from({ length: 6 }, (_, index) => <Skeleton key={index} className="h-11 w-full rounded-xl" />)}
          </GlassCard>
        </>
      ) : isError ? (
        <GlassCard className="p-6">
          <EmptyState
            icon="⚠️"
            title="No se pudieron cargar los inscriptos"
            description="Revisá tu conexión e intentá de nuevo."
            action={<Button variant="outline" onClick={() => refetch()}>Reintentar</Button>}
          />
        </GlassCard>
      ) : !total ? (
        <GlassCard className="p-6">
          <EmptyState
            icon="👥"
            title="Esta comisión todavía no tiene inscriptos"
            description="Cuando se inscriba el primer alumno va a aparecer acá."
          />
        </GlassCard>
      ) : (
        <>
          <div className="grid grid-cols-3 gap-3">
            <Stagger>
              <StatCard label="Inscriptos" className="p-3 md:p-5">
                <span className="font-heading text-[22px] font-bold text-ink md:text-[26px]">{total}</span>
              </StatCard>
              <StatCard label="En riesgo" className="p-3 md:p-5">
                <span className="font-heading text-[22px] font-bold text-danger md:text-[26px]">{atRiskCount}</span>
              </StatCard>
              <StatCard label="Asist. media" className="p-3 md:p-5">
                <span className="font-heading text-[22px] font-bold text-ink md:text-[26px]">{formatPercent(averagePercent)}</span>
              </StatCard>
            </Stagger>
          </div>
          <GlassCard className="flex flex-col gap-3 p-3 md:p-5">
            <Buscador value={search} onChange={setSearch} placeholder="Buscar por nombre o DNI" />
            <DataTable
              compact
              mobileLayout="scroll"
              columns={columns}
              data={visibleEntries}
              getRowId={(entry) => String(entry.enrollmentId)}
              initialSorting={[{ id: 'studentName', desc: false }]}
              minWidth={520}
              initialColumnVisibility={{ atRisk: false }}
              onRowClick={(entry) => navigate(`${basePath}/alumnos/${entry.studentId}`)}
              emptyState={<EmptyState icon="🔎" title="Sin resultados" description="Probá con otro filtro o búsqueda." />}
            />
          </GlassCard>
        </>
      )}
    </PageContainer>
  );
}
