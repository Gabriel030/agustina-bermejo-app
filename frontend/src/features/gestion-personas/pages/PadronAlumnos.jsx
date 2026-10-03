import { useEffect, useState } from 'react';
import { useNavigate, useSearchParams } from 'react-router-dom';
import { Search, ChevronRight } from 'lucide-react';
import { PageContainer } from '@/components/PageContainer';
import { GlassCard } from '@/components/GlassCard';
import { Tabs, TabsList, TabsTrigger, TabsContent } from '@/components/ui/tabs';
import { Input } from '@/components/ui/input';
import { Skeleton } from '@/components/Skeleton';
import { Button } from '@/components/ui/button';
import { useAuth } from '@/context/AuthContext';
import { useAlumnos } from '@/features/gestion-personas/hooks/useAlumnos';

const TABS = [
  { id: 'activos', label: 'Activos', params: { status: 'ACTIVE', onboardingStatus: 'CONFIRMADO' } },
  { id: 'prospectos', label: 'Prospectos', params: { onboardingStatus: 'PROSPECTO' } },
  { id: 'bajas', label: 'Bajas', params: { status: 'DELETED' } },
];

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

function FilaAlumno({ alumno, onClick }) {
  return (
    <button
      type="button"
      onClick={onClick}
      className="flex w-full items-center justify-between gap-4 rounded-2xl px-4 py-3 text-left transition-colors hover:bg-neutral-bg/60"
    >
      <div className="flex min-w-0 flex-col">
        <span className="truncate text-[14px] font-medium text-ink">
          {alumno.apellido}, {alumno.nombre}
        </span>
        <span className="text-[12px] text-ink-faint">{alumno.email}</span>
      </div>
      <div className="flex shrink-0 items-center gap-3">
        <div className="flex flex-col items-end gap-0.5">
          <span className="font-mono text-[13px] text-ink-soft">{alumno.dni}</span>
          <span className="text-[11px] text-ink-faint">
            {ESTADO_ONBOARDING[alumno.onboardingStatus] ?? alumno.onboardingStatus}
          </span>
        </div>
        <ChevronRight className="h-4 w-4 shrink-0 text-ink-faint" />
      </div>
    </button>
  );
}

function ListaAlumnos({ alumnos, isLoading, navigate, rolBase, tabActivo }) {
  if (isLoading) {
    return (
      <div className="flex flex-col gap-1">
        {Array.from({ length: 5 }).map((_, i) => (
          <Skeleton key={i} className="h-[60px] w-full rounded-2xl" />
        ))}
      </div>
    );
  }

  if (!alumnos?.length) {
    return (
      <div className="flex min-h-[160px] items-center justify-center text-[13px] text-ink-faint">
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
          onClick={() => navigate(`/${rolBase}/alumnos/${alumno.id}?tab=${tabActivo}`)}
        />
      ))}
    </div>
  );
}

export function PadronAlumnos() {
  const { usuario } = useAuth();
  const navigate = useNavigate();
  const [searchParams, setSearchParams] = useSearchParams();
  const rolBase = usuario?.roles[0]?.toLowerCase();

  const tabDesdeUrl = searchParams.get('tab');
  const tabInicial = TABS.find((t) => t.id === tabDesdeUrl) ? tabDesdeUrl : 'activos';
  const [tabActivo, setTabActivo] = useState(tabInicial);
  const [searchInput, setSearchInput] = useState('');
  const searchDebounced = useDebounce(searchInput, 300);

  function handleTabChange(tab) {
    setTabActivo(tab);
    setSearchParams((prev) => { prev.set('tab', tab); return prev; }, { replace: true });
  }

  const tabParams = TABS.find((t) => t.id === tabActivo)?.params ?? {};
  const { data: alumnos, isLoading } = useAlumnos({ ...tabParams, search: searchDebounced || undefined });

  return (
    <PageContainer>
      <div className="flex flex-col gap-4">
        <div className="flex items-center justify-between">
          <h1 className="font-heading text-xl font-bold text-ink">Padrón de alumnos</h1>
          {(rolBase === 'preceptor' || rolBase === 'administrativo') && (
            <Button variant="gradient" onClick={() => navigate(`/${rolBase}/alumnos/nuevo`)}>Nuevo alumno</Button>
          )}
        </div>

        <GlassCard className="flex flex-col gap-3 p-4">
          <div className="relative">
            <Search size={15} className="absolute left-3 top-1/2 -translate-y-1/2 text-ink-faint" />
            <Input
              placeholder="Buscar por nombre, apellido, DNI o legajo…"
              value={searchInput}
              onChange={(e) => setSearchInput(e.target.value)}
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
                <ListaAlumnos
                  alumnos={alumnos}
                  isLoading={isLoading}
                  navigate={navigate}
                  rolBase={rolBase}
                  tabActivo={tabActivo}
                />
              </TabsContent>
            ))}
          </Tabs>
        </GlassCard>
      </div>
    </PageContainer>
  );
}
