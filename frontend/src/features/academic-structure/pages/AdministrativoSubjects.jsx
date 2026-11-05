import { useState } from 'react';
import { Link, useNavigate, useSearchParams } from 'react-router-dom';
import { Search, Plus, ChevronRight, GlobeOff } from 'lucide-react';
import { toast } from 'sonner';
import ConfirmActionDialog from '@/features/academic-structure/components/ConfirmActionDialog';
import { PageContainer } from '@/components/PageContainer';
import { GlassCard } from '@/components/GlassCard';
import { Tabs, TabsList, TabsTrigger, TabsContent } from '@/components/ui/tabs';
import { Input } from '@/components/ui/input';
import { Button } from '@/components/ui/button';
import { StatusPill } from '@/components/StatusPill';
import { Skeleton } from '@/components/Skeleton';
import { EmptyState } from '@/components/EmptyState';
import { LoadErrorState } from '@/components/LoadErrorState';
import { DataTable } from '@/components/table/DataTable';
import { matchesSearch } from '@/lib/catalog';
import SubjectLifecycleAction from '@/features/academic-structure/components/SubjectLifecycleAction';
import { useSubjects } from '@/features/academic-structure/hooks/useSubjects';
import { usePrograms, useSetProgramCatalogVisibility } from '@/features/academic-structure/hooks/usePrograms';
import { useCourses, useSetCourseCatalogVisibility } from '@/features/academic-structure/hooks/useCourses';

const SUBJECTS_PAGE_SIZE = 10;

const SUBJECT_TABS = [
  { id: 'ACTIVE', label: 'Activas' },
  { id: 'INACTIVE', label: 'Inactivas' },
  { id: 'DRAFT', label: 'Borrador' },
  { id: 'PENDING_APPROVAL', label: 'Pendientes' },
  { id: 'REJECTED', label: 'Rechazadas' },
];

const SUBJECT_STATUS_CONFIG = {
  ACTIVE: { label: 'Activa', tone: 'success' },
  INACTIVE: { label: 'Inactiva', tone: 'neutral' },
  DRAFT: { label: 'Borrador', tone: 'neutral' },
  PENDING_APPROVAL: { label: 'Pendiente', tone: 'warning' },
  REJECTED: { label: 'Rechazada', tone: 'danger' },
};

const PROGRAM_STATUS_CONFIG = {
  ACTIVE: { label: 'Activa', tone: 'success' },
  INACTIVE: { label: 'Inactiva', tone: 'neutral' },
};

const GRADE_MODE_LABELS = {
  NUMERIC: 'Numérica',
  PASS_FAIL: 'Aprobado / Desaprobado',
};

const LEVEL_LABELS = {
  UNDERGRADUATE: 'Pregrado',
  GRADUATE: 'Posgrado',
  EXTENSION: 'Extensión',
  DIPLOMA: 'Diplomatura',
};

const TEACHING_MODE_LABELS = {
  IN_PERSON: 'Presencial',
  ONLINE: 'Virtual',
  HYBRID: 'Híbrido',
};

const COURSE_STATUS_CONFIG = {
  ACTIVE: { label: 'Activo', tone: 'success' },
  INACTIVE: { label: 'Inactivo', tone: 'neutral' },
};

function buildSubjectColumns() {
  return [
    {
      id: 'code',
      header: 'Código',
      accessorKey: 'code',
      cell: ({ row }) => (
        <span className="text-[11px] font-bold uppercase tracking-widest text-ink-faint">{row.original.code}</span>
      ),
    },
    {
      id: 'name',
      header: 'Nombre',
      accessorKey: 'name',
      meta: { cardTitle: true },
      cell: ({ row }) => (
        <div className="min-w-0">
          <Link
            to={`/administrativo/gestion-academica/${row.original.id}`}
            className="block truncate text-[14px] font-medium text-ink hover:underline"
          >
            {row.original.name}
          </Link>
          {row.original.status === 'REJECTED' && row.original.rejectionReason && (
            <span className="block truncate text-[12px] text-danger">Motivo: {row.original.rejectionReason}</span>
          )}
        </div>
      ),
    },
    {
      id: 'weeklyHours',
      header: 'Horas semanales',
      accessorFn: (subject) => (subject.weeklyHours ?? 0) + (subject.weeklyHoursLab ?? 0),
      meta: { align: 'right' },
    },
    {
      id: 'gradeMode',
      header: 'Calificación',
      accessorKey: 'gradeMode',
      enableColumnFilter: true,
      meta: {
        filterable: true,
        filterTitle: 'Calificación',
        filterLabel: (value) => GRADE_MODE_LABELS[value] ?? value,
      },
      cell: ({ row }) => GRADE_MODE_LABELS[row.original.gradeMode] ?? '—',
    },
    {
      id: 'status',
      header: 'Estado',
      accessorKey: 'status',
      cell: ({ row }) => {
        const cfg = SUBJECT_STATUS_CONFIG[row.original.status] ?? { label: row.original.status, tone: 'neutral' };
        return <StatusPill tone={cfg.tone}>{cfg.label}</StatusPill>;
      },
    },
    {
      id: 'actions',
      header: '',
      enableSorting: false,
      cell: ({ row }) => <SubjectLifecycleAction subject={row.original} />,
    },
  ];
}

const SUBJECT_COLUMNS = buildSubjectColumns();

function SubjectList({ status, search, navigate }) {
  const { data, isLoading, isError, refetch } = useSubjects(status);

  const filtered = (data ?? []).filter(
    (s) => !search || matchesSearch(s.name, search) || matchesSearch(s.code, search),
  );

  if (isError) return <LoadErrorState onRetry={refetch} resource="las materias" bare />;

  return (
    <DataTable
      columns={SUBJECT_COLUMNS}
      data={filtered}
      getRowId={(subject) => String(subject.id)}
      isLoading={isLoading}
      onRowClick={(subject) => navigate(`/administrativo/gestion-academica/${subject.id}`)}
      initialSorting={[{ id: 'name', desc: false }]}
      pageSize={SUBJECTS_PAGE_SIZE}
      emptyState={
        <EmptyState
          title="Sin resultados"
          description={search ? 'Ninguna materia coincide con la búsqueda.' : 'No hay materias en esta categoría.'}
        />
      }
    />
  );
}

function ProgramRow({ program, navigate }) {
  const cfg = PROGRAM_STATUS_CONFIG[program.status] ?? { label: program.status, tone: 'neutral' };
  const meta = [
    program.level ? LEVEL_LABELS[program.level] : null,
    program.totalYears ? `${program.totalYears} ${program.totalYears === 1 ? 'año' : 'años'}` : null,
  ]
    .filter(Boolean)
    .join(' · ');

  return (
    <button
      type="button"
      onClick={() => navigate(`/administrativo/gestion-academica/carreras/${program.id}`)}
      className="flex w-full items-center justify-between gap-4 rounded-2xl px-4 py-3 text-left transition-colors hover:bg-neutral-bg/60"
    >
      <div className="min-w-0">
        <span className="block truncate text-[14px] font-medium text-ink">{program.name}</span>
        {meta && <span className="text-[12px] text-ink-faint">{meta}</span>}
      </div>
      <div className="flex shrink-0 items-center gap-3">
        <StatusPill tone={cfg.tone}>{cfg.label}</StatusPill>
        <ChevronRight className="h-4 w-4 text-ink-faint" />
      </div>
    </button>
  );
}

function CourseRow({ course, navigate }) {
  const cfg = COURSE_STATUS_CONFIG[course.status] ?? { label: course.status, tone: 'neutral' };
  const meta = [
    course.teachingMode ? TEACHING_MODE_LABELS[course.teachingMode] : null,
    course.durationMonths ? `${course.durationMonths} meses` : null,
    course.capacity ? `${course.capacity} cupos` : null,
  ]
    .filter(Boolean)
    .join(' · ');

  return (
    <button
      type="button"
      onClick={() => navigate(`/administrativo/gestion-academica/cursos/${course.id}`)}
      className="flex w-full items-center justify-between gap-4 rounded-2xl px-4 py-3 text-left transition-colors hover:bg-neutral-bg/60"
    >
      <div className="min-w-0">
        <span className="block truncate text-[14px] font-medium text-ink">{course.name}</span>
        {meta && <span className="text-[12px] text-ink-faint">{meta}</span>}
      </div>
      <div className="flex shrink-0 items-center gap-3">
        <StatusPill tone={cfg.tone}>{cfg.label}</StatusPill>
        <ChevronRight className="h-4 w-4 text-ink-faint" />
      </div>
    </button>
  );
}

function PublishedProgramRow({ program, onRetire, isRetiring }) {
  const cfg = PROGRAM_STATUS_CONFIG[program.status] ?? { label: program.status, tone: 'neutral' };
  return (
    <div className="flex w-full items-center justify-between gap-4 rounded-2xl px-4 py-3 transition-colors hover:bg-neutral-bg/60">
      <div className="min-w-0 flex-1">
        <span className="block truncate text-[14px] font-medium text-ink">{program.name}</span>
        <div className="flex items-center gap-2 mt-0.5">
          <StatusPill tone={cfg.tone}>{cfg.label}</StatusPill>
        </div>
      </div>
      <Button
        variant="outline"
        size="sm"
        onClick={() => onRetire(program.id)}
        loading={isRetiring}
        className="shrink-0 text-danger hover:border-danger/50 hover:text-danger"
      >
        <GlobeOff className="mr-1.5 h-3.5 w-3.5" />
        Retirar del catálogo
      </Button>
    </div>
  );
}

function PublishedProgramList({ navigate }) {
  const { data, isLoading, isError, refetch } = usePrograms();
  const retireProgram = useSetProgramCatalogVisibility();

  const published = (data ?? []).filter((p) => p.visibleInCatalog);

  const [toRetire, setToRetire] = useState(null);

  async function handleConfirmRetire() {
    try {
      await retireProgram.mutateAsync({ id: toRetire.id, visible: false });
      toast.success('La carrera fue retirada del catálogo');
      setToRetire(null);
    } catch {
      toast.error('No se pudo retirar la carrera del catálogo.');
    }
  }

  if (isLoading) return <Skeleton rows={4} />;
  if (isError) return <LoadErrorState onRetry={refetch} resource="las carreras" bare />;
  if (!published.length)
    return (
      <EmptyState
        title="Sin carreras publicadas"
        description="No hay carreras publicadas en el catálogo."
        action={
          <Button variant="outline" size="sm" onClick={() => navigate('?section=carreras')}>
            Ir al listado de carreras
          </Button>
        }
      />
    );

  return (
    <>
    <div className="divide-y divide-neutral-bg">
      {published.map((p) => (
        <PublishedProgramRow
          key={p.id}
          program={p}
          onRetire={() => setToRetire(p)}
          isRetiring={retireProgram.isPending && retireProgram.variables?.id === p.id}
        />
      ))}
    </div>
    <ConfirmActionDialog
      open={toRetire !== null}
      onOpenChange={(o) => !o && setToRetire(null)}
      title="Retirar del catálogo"
      description={`${toRetire?.name} deja de aparecer en el catálogo de inscripciones. Los alumnos actuales no se ven afectados.`}
      confirmLabel="Retirar"
      pending={retireProgram.isPending}
      onConfirm={handleConfirmRetire}
    />
    </>
  );
}

function PublishedCourseRow({ course, onRetire, isRetiring }) {
  const cfg = COURSE_STATUS_CONFIG[course.status] ?? { label: course.status, tone: 'neutral' };
  return (
    <div className="flex w-full items-center justify-between gap-4 rounded-2xl px-4 py-3 transition-colors hover:bg-neutral-bg/60">
      <div className="min-w-0 flex-1">
        <span className="block truncate text-[14px] font-medium text-ink">{course.name}</span>
        <div className="flex items-center gap-2 mt-0.5">
          <StatusPill tone={cfg.tone}>{cfg.label}</StatusPill>
        </div>
      </div>
      <Button
        variant="outline"
        size="sm"
        onClick={() => onRetire(course.id)}
        loading={isRetiring}
        className="shrink-0 text-danger hover:border-danger/50 hover:text-danger"
      >
        <GlobeOff className="mr-1.5 h-3.5 w-3.5" />
        Retirar del catálogo
      </Button>
    </div>
  );
}

function PublishedCourseList({ navigate }) {
  const { data, isLoading, isError, refetch } = useCourses();
  const retireCourse = useSetCourseCatalogVisibility();

  const published = (data ?? []).filter((c) => c.visibleInCatalog);

  const [toRetire, setToRetire] = useState(null);

  async function handleConfirmRetire() {
    try {
      await retireCourse.mutateAsync({ id: toRetire.id, visible: false });
      toast.success('El curso fue retirado del catálogo');
      setToRetire(null);
    } catch {
      toast.error('No se pudo retirar el curso del catálogo.');
    }
  }

  if (isLoading) return <Skeleton rows={4} />;
  if (isError) return <LoadErrorState onRetry={refetch} resource="los cursos" bare />;
  if (!published.length)
    return (
      <EmptyState
        title="Sin cursos publicados"
        description="No hay cursos publicados en el catálogo."
        action={
          <Button variant="outline" size="sm" onClick={() => navigate('?section=cursos')}>
            Ir al listado de cursos
          </Button>
        }
      />
    );

  return (
    <>
    <div className="divide-y divide-neutral-bg">
      {published.map((c) => (
        <PublishedCourseRow
          key={c.id}
          course={c}
          onRetire={() => setToRetire(c)}
          isRetiring={retireCourse.isPending && retireCourse.variables?.id === c.id}
        />
      ))}
    </div>
    <ConfirmActionDialog
      open={toRetire !== null}
      onOpenChange={(o) => !o && setToRetire(null)}
      title="Retirar del catálogo"
      description={`${toRetire?.name} deja de aparecer en el catálogo de inscripciones. Los alumnos actuales no se ven afectados.`}
      confirmLabel="Retirar"
      pending={retireCourse.isPending}
      onConfirm={handleConfirmRetire}
    />
    </>
  );
}

function CourseList({ search, navigate }) {
  const { data, isLoading, isError, refetch } = useCourses();

  const filtered = (data ?? []).filter((c) => {
    if (!search) return true;
    return matchesSearch(c.name, search);
  });

  if (isLoading) return <Skeleton rows={4} />;
  if (isError) return <LoadErrorState onRetry={refetch} resource="los cursos" bare />;
  if (!filtered.length)
    return (
      <EmptyState
        title="Sin cursos"
        description={search ? 'Ningún curso coincide con la búsqueda.' : 'Todavía no hay cursos registrados.'}
        action={
          !search && (
            <Button variant="outline" size="sm" onClick={() => navigate('/administrativo/gestion-academica/cursos/nueva')}>
              <Plus className="mr-1.5 h-3.5 w-3.5" />
              Nuevo curso
            </Button>
          )
        }
      />
    );

  return (
    <div className="divide-y divide-neutral-bg">
      {filtered.map((c) => (
        <CourseRow key={c.id} course={c} navigate={navigate} />
      ))}
    </div>
  );
}

function ProgramList({ search, navigate }) {
  const { data, isLoading, isError, refetch } = usePrograms();

  const filtered = (data ?? []).filter((p) => {
    if (!search) return true;
    return matchesSearch(p.name, search);
  });

  if (isLoading) return <Skeleton rows={4} />;
  if (isError) return <LoadErrorState onRetry={refetch} resource="las carreras" bare />;
  if (!filtered.length)
    return (
      <EmptyState
        title="Sin carreras"
        description={search ? 'Ninguna carrera coincide con la búsqueda.' : 'Todavía no hay carreras registradas.'}
        action={
          !search && (
            <Button variant="outline" size="sm" onClick={() => navigate('/administrativo/gestion-academica/carreras/nueva')}>
              <Plus className="mr-1.5 h-3.5 w-3.5" />
              Nueva carrera
            </Button>
          )
        }
      />
    );

  return (
    <div className="divide-y divide-neutral-bg">
      {filtered.map((p) => (
        <ProgramRow key={p.id} program={p} navigate={navigate} />
      ))}
    </div>
  );
}

function OfertaAcademica({ navigate }) {
  const [ofertaTab, setOfertaTab] = useState('carreras');
  return (
    <div className="flex flex-col gap-4">
      <Tabs value={ofertaTab} onValueChange={setOfertaTab}>
        <TabsList variant="segmented">
          <TabsTrigger value="carreras">Carreras publicadas</TabsTrigger>
          <TabsTrigger value="cursos">Cursos publicados</TabsTrigger>
        </TabsList>
      </Tabs>
      {ofertaTab === 'carreras' ? (
        <PublishedProgramList navigate={navigate} />
      ) : (
        <PublishedCourseList navigate={navigate} />
      )}
    </div>
  );
}

export function AdministrativoSubjects() {
  const navigate = useNavigate();
  const [searchParams, setSearchParams] = useSearchParams();
  const [search, setSearch] = useState('');

  const section = searchParams.get('section') ?? 'materias';
  const activeTab = searchParams.get('tab') ?? 'ACTIVE';
  const [lastSubjectTab, setLastSubjectTab] = useState(activeTab);

  function handleSectionChange(s) {
    setSearchParams(s === 'materias' ? { section: s, tab: lastSubjectTab } : { section: s });
    setSearch('');
  }

  function handleTabChange(tab) {
    setLastSubjectTab(tab);
    setSearchParams({ section: 'materias', tab });
    setSearch('');
  }

  const accionBoton =
    section === 'carreras' ? (
      <Button variant="gradient" onClick={() => navigate('/administrativo/gestion-academica/carreras/nueva')}>
        <Plus className="mr-2 h-4 w-4" />
        Nueva carrera
      </Button>
    ) : section === 'cursos' ? (
      <Button variant="gradient" onClick={() => navigate('/administrativo/gestion-academica/cursos/nueva')}>
        <Plus className="mr-2 h-4 w-4" />
        Nuevo curso
      </Button>
    ) : section === 'oferta' ? null : (
      <Button variant="gradient" onClick={() => navigate('/administrativo/gestion-academica/nueva')}>
        <Plus className="mr-2 h-4 w-4" />
        Nueva materia
      </Button>
    );

  return (
    <PageContainer title="Gestión académica" action={accionBoton}>
      <GlassCard className="p-4">
        <div className="mb-4 flex flex-col gap-3">
          <Tabs value={section} onValueChange={handleSectionChange}>
            <TabsList variant="segmented">
              <TabsTrigger value="materias">Banco de materias</TabsTrigger>
              <TabsTrigger value="carreras">Carreras</TabsTrigger>
              <TabsTrigger value="cursos">Cursos</TabsTrigger>
              <TabsTrigger value="oferta">Oferta académica</TabsTrigger>
            </TabsList>
          </Tabs>
          {section !== 'oferta' && (
            <div className="relative">
              <Search className="absolute left-3 top-1/2 h-4 w-4 -translate-y-1/2 text-ink-faint" />
              <Input
                aria-label="Buscar"
                placeholder={section === 'carreras' ? 'Buscar por nombre…' : 'Buscar por nombre o código…'}
                value={search}
                onChange={(e) => setSearch(e.target.value)}
                className="pl-9"
              />
            </div>
          )}
        </div>

        {section === 'oferta' ? (
          <OfertaAcademica navigate={navigate} />
        ) : section === 'cursos' ? (
          <CourseList search={search} navigate={navigate} />
        ) : section === 'carreras' ? (
          <ProgramList search={search} navigate={navigate} />
        ) : (
          <Tabs value={activeTab} onValueChange={handleTabChange}>
            <TabsList variant="segmented" className="mb-4">
              {SUBJECT_TABS.map((t) => (
                <TabsTrigger key={t.id} value={t.id}>
                  {t.label}
                </TabsTrigger>
              ))}
            </TabsList>
            {SUBJECT_TABS.map((t) => (
              <TabsContent key={t.id} value={t.id}>
                <SubjectList status={t.id} search={search} navigate={navigate} />
              </TabsContent>
            ))}
          </Tabs>
        )}
      </GlassCard>
    </PageContainer>
  );
}
