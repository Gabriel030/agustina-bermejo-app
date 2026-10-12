import { useState } from 'react';
import { useNavigate, useSearchParams } from 'react-router-dom';
import { Search, ChevronRight, Plus } from 'lucide-react';
import { PageContainer } from '@/components/PageContainer';
import { GlassCard } from '@/components/GlassCard';
import { Tabs, TabsList, TabsTrigger, TabsContent } from '@/components/ui/tabs';
import { Input } from '@/components/ui/input';
import { Button } from '@/components/ui/button';
import { StatusPill } from '@/components/StatusPill';
import { Skeleton } from '@/components/Skeleton';
import { EmptyState } from '@/components/EmptyState';
import { useProgramas, useCursos } from '@/hooks/useCatalogo';
import { useCiclos } from '@/features/academic-structure/hooks/useCiclos';
import { formatCourseDuration } from '@/lib/courseDuration';

const TABS = [
  { id: 'carreras', label: 'Carreras' },
  { id: 'cursos', label: 'Cursos' },
];

const CICLO_STATUS_CONFIG = {
  DRAFT: { label: 'En construcción', tone: 'neutral' },
  PENDING_APPROVAL: { label: 'En revisión', tone: 'warning' },
  APPROVED: { label: 'Aprobado', tone: 'success' },
  REJECTED: { label: 'Rechazado', tone: 'danger' },
  CLOSED: { label: 'Cerrado', tone: 'neutral' },
};

function useCiclosPorPrograma(programId) {
  return useCiclos({ programId });
}

function useCiclosPorCurso(courseId) {
  return useCiclos({ courseId });
}

function BadgeCiclo({ ciclo }) {
  if (!ciclo) return null;
  const cfg = CICLO_STATUS_CONFIG[ciclo.status] ?? { label: ciclo.status, tone: 'neutral' };
  return <StatusPill tone={cfg.tone}>{cfg.label}</StatusPill>;
}

function FilaPrograma({ programa, navigate }) {
  const { data: ciclos } = useCiclosPorPrograma(programa.id);
  const cicloActivo = ciclos?.find((c) => c.status === 'APPROVED' || c.status === 'PENDING_APPROVAL' || c.status === 'DRAFT');

  return (
    <button
      type="button"
      onClick={() => navigate(`/preceptor/oferta-academica/carreras/${programa.id}`)}
      className="flex w-full items-center justify-between gap-4 rounded-2xl px-4 py-3 text-left transition-colors hover:bg-neutral-bg/60"
    >
      <div className="min-w-0">
        <span className="block truncate text-[14px] font-medium text-ink">{programa.name}</span>
        <span className="text-[12px] text-ink-faint">{programa.totalYears} {programa.totalYears === 1 ? 'año' : 'años'}</span>
      </div>
      <div className="flex shrink-0 items-center gap-3">
        <BadgeCiclo ciclo={cicloActivo} />
        <ChevronRight className="h-4 w-4 text-ink-faint" />
      </div>
    </button>
  );
}

function FilaCurso({ curso, navigate }) {
  const { data: ciclos } = useCiclosPorCurso(curso.id);
  const cicloActivo = ciclos?.find((c) => c.status === 'APPROVED' || c.status === 'PENDING_APPROVAL' || c.status === 'DRAFT');

  return (
    <button
      type="button"
      onClick={() => navigate(`/preceptor/oferta-academica/cursos/${curso.id}`)}
      className="flex w-full items-center justify-between gap-4 rounded-2xl px-4 py-3 text-left transition-colors hover:bg-neutral-bg/60"
    >
      <div className="min-w-0">
        <span className="block truncate text-[14px] font-medium text-ink">{curso.name}</span>
        <span className="text-[12px] text-ink-faint">{formatCourseDuration(curso.durationMonths)}</span>
      </div>
      <div className="flex shrink-0 items-center gap-3">
        <BadgeCiclo ciclo={cicloActivo} />
        <ChevronRight className="h-4 w-4 text-ink-faint" />
      </div>
    </button>
  );
}

function ListaFiltrada({ items, isLoading, renderFila, emptyText }) {
  if (isLoading) {
    return (
      <div className="flex flex-col gap-1">
        {Array.from({ length: 4 }).map((_, i) => (
          <Skeleton key={i} className="h-[60px] w-full rounded-2xl" />
        ))}
      </div>
    );
  }
  if (!items?.length) return <EmptyState title={emptyText} className="py-8" />;
  return <div className="flex flex-col gap-1">{items.map(renderFila)}</div>;
}

export function PreceptorOfertaAcademica() {
  const navigate = useNavigate();
  const [searchParams, setSearchParams] = useSearchParams();
  const tabDesdeUrl = searchParams.get('tab');
  const tabInicial = TABS.find((t) => t.id === tabDesdeUrl) ? tabDesdeUrl : 'carreras';
  const [tabActivo, setTabActivo] = useState(tabInicial);
  const [busqueda, setBusqueda] = useState('');

  function handleTabChange(tab) {
    setTabActivo(tab);
    setBusqueda('');
    setSearchParams((prev) => { prev.set('tab', tab); return prev; }, { replace: true });
  }

  const { data: programas, isLoading: loadingProgramas } = useProgramas();
  const { data: cursos, isLoading: loadingCursos } = useCursos();

  const programasFiltrados = (programas ?? []).filter((p) =>
    p.name.toLowerCase().includes(busqueda.toLowerCase())
  );
  const cursosFiltrados = (cursos ?? []).filter((c) =>
    c.name.toLowerCase().includes(busqueda.toLowerCase())
  );

  return (
    <PageContainer>
      <div className="flex flex-col gap-4">
        <div className="flex items-center justify-between">
          <h1 className="font-heading text-xl font-bold text-ink">Oferta académica</h1>
          <Button variant="gradient" onClick={() => navigate('/preceptor/oferta-academica/nuevo-ciclo')}>
            <Plus className="mr-1.5 h-4 w-4" />
            Iniciar alta de ciclo
          </Button>
        </div>

        <GlassCard className="flex flex-col gap-3 p-4">
          <div className="relative">
            <Search size={15} className="absolute left-3 top-1/2 -translate-y-1/2 text-ink-faint" />
            <Input
              placeholder={tabActivo === 'carreras' ? 'Buscar carrera…' : 'Buscar curso…'}
              value={busqueda}
              onChange={(e) => setBusqueda(e.target.value)}
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

            <TabsContent value="carreras" className="mt-2">
              <ListaFiltrada
                items={programasFiltrados}
                isLoading={loadingProgramas}
                emptyText="No se encontraron carreras."
                renderFila={(p) => <FilaPrograma key={p.id} programa={p} navigate={navigate} />}
              />
            </TabsContent>

            <TabsContent value="cursos" className="mt-2">
              <ListaFiltrada
                items={cursosFiltrados}
                isLoading={loadingCursos}
                emptyText="No se encontraron cursos."
                renderFila={(c) => <FilaCurso key={c.id} curso={c} navigate={navigate} />}
              />
            </TabsContent>
          </Tabs>
        </GlassCard>
      </div>
    </PageContainer>
  );
}
