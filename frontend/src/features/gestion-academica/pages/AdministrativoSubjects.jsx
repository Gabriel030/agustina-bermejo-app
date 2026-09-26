import { useState } from 'react';
import { useNavigate, useSearchParams } from 'react-router-dom';
import { Search, Plus, ChevronRight } from 'lucide-react';
import { PageContainer } from '@/components/PageContainer';
import { GlassCard } from '@/components/GlassCard';
import { Tabs, TabsList, TabsTrigger, TabsContent } from '@/components/ui/tabs';
import { Input } from '@/components/ui/input';
import { Button } from '@/components/ui/button';
import { StatusPill } from '@/components/StatusPill';
import { Skeleton } from '@/components/Skeleton';
import { EmptyState } from '@/components/EmptyState';
import { useSubjects } from '@/features/gestion-academica/hooks/useSubjects';

const TABS = [
  { id: 'ACTIVE', label: 'Activas' },
  { id: 'INACTIVE', label: 'Inactivas' },
  { id: 'DRAFT', label: 'Borrador' },
  { id: 'PENDING_APPROVAL', label: 'Pendientes' },
];

const STATUS_CONFIG = {
  ACTIVE: { label: 'Activa', tone: 'success' },
  INACTIVE: { label: 'Inactiva', tone: 'neutral' },
  DRAFT: { label: 'Borrador', tone: 'neutral' },
  PENDING_APPROVAL: { label: 'Pendiente', tone: 'warning' },
  REJECTED: { label: 'Rechazada', tone: 'danger' },
};

const GRADE_MODE_LABELS = {
  NUMERIC: 'Numérica',
  PASS_FAIL: 'Aprobado / Desaprobado',
};

function SubjectRow({ subject, navigate, rol }) {
  const cfg = STATUS_CONFIG[subject.status] ?? { label: subject.status, tone: 'neutral' };
  return (
    <button
      type="button"
      onClick={() => navigate(`/${rol}/gestion-academica/${subject.id}`)}
      className="flex w-full items-center justify-between gap-4 rounded-2xl px-4 py-3 text-left transition-colors hover:bg-neutral-bg/60"
    >
      <div className="min-w-0">
        <div className="flex items-center gap-2">
          <span className="text-[11px] font-bold uppercase tracking-widest text-ink-faint">{subject.code}</span>
        </div>
        <span className="block truncate text-[14px] font-medium text-ink">{subject.name}</span>
        {subject.gradeMode && (
          <span className="text-[12px] text-ink-faint">{GRADE_MODE_LABELS[subject.gradeMode] ?? subject.gradeMode}</span>
        )}
      </div>
      <div className="flex shrink-0 items-center gap-3">
        <StatusPill tone={cfg.tone}>{cfg.label}</StatusPill>
        <ChevronRight className="h-4 w-4 text-ink-faint" />
      </div>
    </button>
  );
}

function SubjectList({ status, search, navigate, rol }) {
  const { data, isLoading, isError } = useSubjects(status);

  const filtered = (data ?? []).filter((s) => {
    if (!search) return true;
    const q = search.toLowerCase();
    return s.name.toLowerCase().includes(q) || s.code.toLowerCase().includes(q);
  });

  if (isLoading) return <Skeleton rows={5} />;
  if (isError) return <EmptyState title="Error al cargar materias" description="Intentá de nuevo en unos momentos." />;
  if (!filtered.length)
    return <EmptyState title="Sin resultados" description={search ? 'Ninguna materia coincide con la búsqueda.' : 'No hay materias en esta categoría.'} />;

  return (
    <div className="divide-y divide-neutral-bg">
      {filtered.map((s) => (
        <SubjectRow key={s.id} subject={s} navigate={navigate} rol={rol} />
      ))}
    </div>
  );
}

export function AdministrativoSubjects() {
  const navigate = useNavigate();
  const [searchParams, setSearchParams] = useSearchParams();
  const [search, setSearch] = useState('');

  const activeTab = searchParams.get('tab') ?? 'ACTIVE';

  function handleTabChange(tab) {
    setSearchParams({ tab });
    setSearch('');
  }

  return (
    <PageContainer
      titulo="Banco de materias"
      accion={
        <Button variant="gradient" onClick={() => navigate('/administrativo/gestion-academica/nueva')}>
          <Plus className="mr-2 h-4 w-4" />
          Nueva materia
        </Button>
      }
    >
      <GlassCard className="p-4">
        <div className="mb-4">
          <div className="relative">
            <Search className="absolute left-3 top-1/2 h-4 w-4 -translate-y-1/2 text-ink-faint" />
            <Input
              placeholder="Buscar por nombre o código…"
              value={search}
              onChange={(e) => setSearch(e.target.value)}
              className="pl-9"
            />
          </div>
        </div>

        <Tabs value={activeTab} onValueChange={handleTabChange}>
          <TabsList variant="segmented" className="mb-4">
            {TABS.map((t) => (
              <TabsTrigger key={t.id} value={t.id}>
                {t.label}
              </TabsTrigger>
            ))}
          </TabsList>
          {TABS.map((t) => (
            <TabsContent key={t.id} value={t.id}>
              <SubjectList status={t.id} search={search} navigate={navigate} rol="administrativo" />
            </TabsContent>
          ))}
        </Tabs>
      </GlassCard>
    </PageContainer>
  );
}
