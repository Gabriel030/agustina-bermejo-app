import { useEffect, useState } from 'react';
import { useActiveRole } from '@/hooks/useActiveRole';
import { useNavigate, useSearchParams } from 'react-router-dom';
import { Search, ChevronRight, MailWarning, FileWarning, CalendarX, MessageSquareText } from 'lucide-react';
import { PageContainer } from '@/components/PageContainer';
import { GlassCard } from '@/components/GlassCard';
import { Tabs, TabsList, TabsTrigger, TabsContent } from '@/components/ui/tabs';
import { Input } from '@/components/ui/input';
import { Skeleton } from '@/components/Skeleton';
import { EmptyState } from '@/components/EmptyState';
import { LoadErrorState } from '@/components/LoadErrorState';
import { Button } from '@/components/ui/button';
import { StatusPill } from '@/components/StatusPill';
import { formatDate } from '@/lib/billing';
import { Paginador } from '@/components/Paginador';
import { useAlumnos } from '@/features/identity/hooks/useAlumnos';
import { useStaffProspects } from '@/features/identity/hooks/useProspectos';
import { missingDocumentsSummary } from '@/features/identity/lib/studentRegistration';

const TABS = [
  { id: 'activos', label: 'Activos', params: { status: 'ACTIVE', onboardingStatus: 'CONFIRMADO' } },
  { id: 'prospectos', label: 'Prospectos', params: { onboardingStatus: 'PROSPECTO' } },
  { id: 'bajas', label: 'Bajas', params: { status: 'DELETED' } },
];

const STAFF_REGISTRATION_ROLES = ['preceptor', 'administrativo', 'subdirector', 'director'];

const ESTADO_ONBOARDING = {
  CONFIRMADO: 'Confirmado',
  PROSPECTO: 'Prospecto',
};

function useDebounce(value, delay) {
  const [debounced, setDebounced] = useState(value);
  useEffect(() => {
    const timer = setTimeout(() => setDebounced(value), delay);
    return () => clearTimeout(timer);
  }, [value, delay]);
  return debounced;
}

function activeStudentSignals(alumno) {
  const signals = [];
  if (alumno.attendanceRisk === 'FREE') {
    signals.push(<StatusPill key="attendance" tone="danger">Libre por inasistencias</StatusPill>);
  } else if (alumno.attendanceRisk === 'NEAR_LIMIT') {
    signals.push(<StatusPill key="attendance" tone="warning">Asistencia en riesgo</StatusPill>);
  }
  if (alumno.emailVerified === false) {
    signals.push(<StatusPill key="email" tone="neutral" icon={MailWarning}>Email sin verificar</StatusPill>);
  }
  const missingDocuments = alumno.missingRequiredDocuments ?? [];
  if (missingDocuments.length > 0) {
    const detail = missingDocumentsSummary(missingDocuments);
    signals.push(
      <StatusPill key="documents" tone="warning" icon={FileWarning}>
        <span title={detail}>Documentación pendiente</span>
        <span className="sr-only">. {detail}</span>
      </StatusPill>,
    );
  }
  return signals.slice(0, 3);
}

function withdrawnStudentSignals(alumno) {
  const signals = [];
  if (alumno.withdrawnAt) {
    signals.push(<StatusPill key="withdrawnAt" tone="neutral" icon={CalendarX}>Baja el {formatDate(alumno.withdrawnAt)}</StatusPill>);
  }
  if (alumno.withdrawalReason) {
    signals.push(<StatusPill key="reason" tone="neutral" icon={MessageSquareText}>Motivo: {alumno.withdrawalReason}</StatusPill>);
  }
  return signals;
}

function FilaAlumno({ alumno, onClick, withdrawn }) {
  const signals = withdrawn ? withdrawnStudentSignals(alumno) : activeStudentSignals(alumno);
  return (
    <Button
      variant="row"
      size="row"
      type="button"
      onClick={onClick}
      className="flex-wrap gap-y-1.5"
      >
      <div className="flex min-w-0 flex-1 flex-col items-start">
        <span className="max-w-full truncate text-size-14 font-medium text-ink">
          {alumno.apellido}, {alumno.nombre}
        </span>
        <span className="max-w-full truncate text-size-12 text-ink-faint">{alumno.email}</span>
      </div>
      <div className="flex shrink-0 items-center gap-3">
        <div className="flex flex-col items-end gap-0.5">
          <span className="font-mono text-size-13 text-ink-soft">{alumno.dni}</span>
          <span className="text-size-11 text-ink-faint">
            {ESTADO_ONBOARDING[alumno.onboardingStatus] ?? alumno.onboardingStatus}
          </span>
        </div>
        <ChevronRight className="h-4 w-4 shrink-0 text-ink-faint" />
      </div>
      {signals.length > 0 && <div className="flex basis-full flex-wrap gap-1.5 text-left">{signals}</div>}
    </Button>
  );
}

function FilaProspecto({ prospect, onClick }) {
  const complete = prospect.missingDocuments.length === 0;
  return (
    <Button
      variant="row"
      size="row"
      type="button"
      onClick={onClick}
      >
      <div className="flex min-w-0 flex-col">
        <span className="truncate text-size-14 font-medium text-ink">
          {prospect.apellido}, {prospect.nombre}
        </span>
        <span className="text-size-12 text-ink-faint">{prospect.email}</span>
      </div>
      <div className="flex shrink-0 items-center gap-3">
        <span className={`text-size-12 ${complete ? 'text-success' : 'text-warning-ink'}`}>
          {missingDocumentsSummary(prospect.missingDocuments)}
        </span>
        <ChevronRight className="h-4 w-4 shrink-0 text-ink-faint" />
      </div>
    </Button>
  );
}

function ListaProspectos({ search, page, onPageChange, navigate, rolBase, tabActivo }) {
  const { data, isLoading, isError, error, refetch } = useStaffProspects({ search: search || undefined, page });
  if (isLoading) {
    return (
      <div className="flex flex-col gap-1">
        {Array.from({ length: 5 }).map((_, i) => (
          <Skeleton key={i} className="h-[60px] w-full rounded-2xl" />
        ))}
      </div>
    );
  }
  if (isError) return <LoadErrorState bare error={error} onRetry={refetch} resource="los prospectos" />;
  const prospects = data?.items ?? [];
  const totalPages = data ? Math.max(1, Math.ceil(data.totalElements / data.size)) : 1;
  if (!prospects.length) {
    return (
      <EmptyState
        title={search ? 'Ningún prospecto coincide con la búsqueda' : 'No hay prospectos'}
        description={search ? 'Probá con otro nombre o email.' : 'Los alumnos dados de alta sin confirmar aparecen acá.'}
      />
    );
  }
  return (
    <>
      <div className="flex flex-col gap-1">
        {prospects.map((prospect) => (
          <FilaProspecto
            key={prospect.id}
            prospect={prospect}
            onClick={() => navigate(`/${rolBase}/alumnos/${prospect.id}?tab=${tabActivo}`)}
          />
        ))}
      </div>
      <Paginador page={page + 1} totalPages={totalPages} onChange={(next) => onPageChange(next - 1)} />
    </>
  );
}

function ListaAlumnos({ alumnos, isLoading, isError, error, onRetry, navigate, rolBase, tabActivo }) {
  if (isLoading) {
    return (
      <div className="flex flex-col gap-1">
        {Array.from({ length: 5 }).map((_, i) => (
          <Skeleton key={i} className="h-[60px] w-full rounded-2xl" />
        ))}
      </div>
    );
  }

  if (isError) return <LoadErrorState bare error={error} onRetry={onRetry} resource="los alumnos" />;

  if (!alumnos?.length) {
    return (
      <div className="flex min-h-[160px] items-center justify-center text-size-13 text-ink-faint">
        No se encontraron alumnos que coincidan con la búsqueda.
      </div>
    );
  }

  return (
    <div className="flex flex-col gap-1">
      {alumnos.map((alumno) => (
        <FilaAlumno
          key={alumno.id}
          alumno={alumno}
          withdrawn={tabActivo === 'bajas'}
          onClick={() => navigate(`/${rolBase}/alumnos/${alumno.id}?tab=${tabActivo}`)}
        />
      ))}
    </div>
  );
}

export function PadronAlumnos() {
  const navigate = useNavigate();
  const [searchParams, setSearchParams] = useSearchParams();
  const { roleBase: rolBase } = useActiveRole();

  const tabDesdeUrl = searchParams.get('tab');
  const tabInicial = TABS.find((t) => t.id === tabDesdeUrl) ? tabDesdeUrl : 'activos';
  const [tabActivo, setTabActivo] = useState(tabInicial);
  const [searchInput, setSearchInput] = useState('');
  const searchDebounced = useDebounce(searchInput, 300);

  function handleTabChange(tab) {
    setTabActivo(tab);
    setPage(0);
    setSearchParams((prev) => { prev.set('tab', tab); return prev; }, { replace: true });
  }

  const tabParams = TABS.find((t) => t.id === tabActivo)?.params ?? {};
  const [page, setPage] = useState(0);
  const { data: alumnosPage, isLoading, isError, error, refetch } = useAlumnos({ ...tabParams, search: searchDebounced || undefined, page, enabled: tabActivo !== 'prospectos' });
  const alumnos = alumnosPage?.items;
  const totalPages = alumnosPage ? Math.max(1, Math.ceil(alumnosPage.totalElements / alumnosPage.size)) : 1;

  return (
    <PageContainer>
      <div className="flex flex-col gap-4">
        <div className="flex items-center justify-between">
          <h1 className="font-heading text-xl font-bold text-ink">Padrón de alumnos</h1>
          {STAFF_REGISTRATION_ROLES.includes(rolBase) && (
            <Button variant="primary" onClick={() => navigate(`/${rolBase}/alumnos/nuevo`)}>Nuevo alumno</Button>
          )}
        </div>

        <GlassCard className="flex flex-col gap-3 p-4">
          <div className="relative">
            <Search size={15} className="absolute left-3 top-1/2 -translate-y-1/2 text-ink-faint" />
            <Input
              placeholder="Buscar por nombre, apellido, DNI o legajo…"
              value={searchInput}
              onChange={(e) => {
                setSearchInput(e.target.value);
                setPage(0);
              }}
              className="pl-9"
            />
          </div>

          <Tabs value={tabActivo} onValueChange={handleTabChange}>
            <TabsList variant="segmented" className="w-full">
              {TABS.map((tab) => (
                <TabsTrigger key={tab.id} value={tab.id} className="flex-1">
                  {tab.label}
                </TabsTrigger>
              ))}
            </TabsList>

            {TABS.map((tab) => (
              <TabsContent key={tab.id} value={tab.id} className="mt-2">
                {tab.id === 'prospectos' ? (
                  <ListaProspectos
                    search={searchDebounced}
                    page={page}
                    onPageChange={setPage}
                    navigate={navigate}
                    rolBase={rolBase}
                    tabActivo={tabActivo}
                  />
                ) : (
                  <>
                    <ListaAlumnos
                      alumnos={alumnos}
                      isLoading={isLoading}
                      isError={isError}
                      error={error}
                      onRetry={refetch}
                      navigate={navigate}
                      rolBase={rolBase}
                      tabActivo={tabActivo}
                    />
                    <Paginador page={page + 1} totalPages={totalPages} onChange={(next) => setPage(next - 1)} />
                  </>
                )}
              </TabsContent>
            ))}
          </Tabs>
        </GlassCard>
      </div>
    </PageContainer>
  );
}
