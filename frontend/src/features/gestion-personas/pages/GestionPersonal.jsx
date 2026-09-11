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
import { usePersonal } from '@/features/gestion-personas/hooks/usePersonal';
import { useCrearPersonal } from '@/features/gestion-personas/hooks/useAccionesPersonal';
import { AltaPersonalModal } from '@/features/gestion-personas/components/AltaPersonalModal';

const TABS = [
  { id: 'activos', label: 'Activos', params: { status: 'ACTIVE' } },
  { id: 'bajas', label: 'Bajas', params: { status: 'INACTIVE' } },
];

const ETIQUETA_ROL = {
  DOCENTE: 'Docente',
  PRECEPTOR: 'Preceptor',
  ADMINISTRATIVO: 'Administrativo',
};

function useDebounce(value, delay) {
  const [debounced, setDebounced] = useState(value);
  useEffect(() => {
    const timer = setTimeout(() => setDebounced(value), delay);
    return () => clearTimeout(timer);
  }, [value, delay]);
  return debounced;
}

function FilaPersonal({ persona, onClick }) {
  return (
    <button
      type="button"
      onClick={onClick}
      className="flex w-full items-center justify-between gap-4 rounded-2xl px-4 py-3 text-left transition-colors hover:bg-neutral-bg/60"
    >
      <div className="flex min-w-0 flex-col">
        <span className="truncate text-[14px] font-medium text-ink">
          {persona.apellido}, {persona.nombre}
        </span>
        <span className="text-[12px] text-ink-faint">{persona.email}</span>
      </div>
      <div className="flex shrink-0 items-center gap-3">
        <div className="flex flex-col items-end gap-0.5">
          <span className="font-mono text-[13px] text-ink-soft">{persona.dni}</span>
          <span className="text-[11px] text-ink-faint">
            {ETIQUETA_ROL[persona.role] ?? persona.role}
          </span>
        </div>
        <ChevronRight className="h-4 w-4 shrink-0 text-ink-faint" />
      </div>
    </button>
  );
}

function ListaPersonal({ personal, isLoading, navigate, rolBase, tabActivo }) {
  if (isLoading) {
    return (
      <div className="flex flex-col gap-1">
        {Array.from({ length: 5 }).map((_, i) => (
          <Skeleton key={i} className="h-[60px] w-full rounded-2xl" />
        ))}
      </div>
    );
  }

  if (!personal?.length) {
    return (
      <div className="flex min-h-[160px] items-center justify-center text-[13px] text-ink-faint">
        No se encontraron miembros del personal que coincidan.
      </div>
    );
  }

  return (
    <div className="flex flex-col gap-1">
      {personal.map((persona) => (
        <FilaPersonal
          key={persona.id}
          persona={persona}
          onClick={() => navigate(`/${rolBase}/personal/${persona.id}?tab=${tabActivo}`)}
        />
      ))}
    </div>
  );
}

export function GestionPersonal() {
  const { usuario } = useAuth();
  const navigate = useNavigate();
  const [searchParams, setSearchParams] = useSearchParams();
  const rolBase = usuario?.roles[0]?.toLowerCase();

  const tabDesdeUrl = searchParams.get('tab');
  const tabInicial = TABS.find((t) => t.id === tabDesdeUrl) ? tabDesdeUrl : 'activos';
  const [tabActivo, setTabActivo] = useState(tabInicial);
  const [searchInput, setSearchInput] = useState('');
  const searchDebounced = useDebounce(searchInput, 300);
  const [altaOpen, setAltaOpen] = useState(false);

  function handleTabChange(tab) {
    setTabActivo(tab);
    setSearchParams((prev) => { prev.set('tab', tab); return prev; }, { replace: true });
  }

  const tabParams = TABS.find((t) => t.id === tabActivo)?.params ?? {};
  const { data: personal, isLoading } = usePersonal({ ...tabParams, search: searchDebounced || undefined });
  const crearMutation = useCrearPersonal();

  return (
    <PageContainer>
      <div className="flex flex-col gap-4">
        <div className="flex items-center justify-between">
          <h1 className="font-heading text-xl font-bold text-ink">Gestión de personal</h1>
          <Button variant="gradient" onClick={() => setAltaOpen(true)}>Nuevo personal</Button>
        </div>

        <GlassCard className="flex flex-col gap-3 p-4">
          <div className="relative">
            <Search size={15} className="absolute left-3 top-1/2 -translate-y-1/2 text-ink-faint" />
            <Input
              placeholder="Buscar por nombre, apellido o DNI…"
              value={searchInput}
              onChange={(e) => setSearchInput(e.target.value)}
              className="pl-9"
            />
          </div>

          <Tabs value={tabActivo} onValueChange={handleTabChange}>
            <TabsList variant="line" className="w-full">
              {TABS.map((tab) => (
                <TabsTrigger key={tab.id} value={tab.id} className="flex-1">
                  {tab.label}
                </TabsTrigger>
              ))}
            </TabsList>

            {TABS.map((tab) => (
              <TabsContent key={tab.id} value={tab.id} className="mt-2">
                <ListaPersonal
                  personal={personal}
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

      <AltaPersonalModal
        open={altaOpen}
        onClose={() => setAltaOpen(false)}
        onConfirmar={(data) =>
          crearMutation.mutate(data, { onSuccess: () => setAltaOpen(false) })
        }
        isPending={crearMutation.isPending}
      />
    </PageContainer>
  );
}
