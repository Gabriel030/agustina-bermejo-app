import { useSearchParams } from 'react-router-dom';
import { PageContainer } from '@/components/PageContainer';
import { Tabs, TabsList, TabsTrigger } from '@/components/ui/tabs';
import { TabCarreras } from '@/features/enrollment/components/TabCarreras';
import { TabCursos } from '@/features/enrollment/components/TabCursos';
import { TabMisSolicitudes } from '@/features/enrollment/components/TabMisSolicitudes';
import { CATALOG_TABS } from '@/lib/catalog';

// Épica 7 Fase 3: reemplaza el Catálogo de acción-directa de la Fase 2 por un
// shell de 3 tabs (Strategy, ver spec técnica) — Carreras/Cursos ahora llevan a
// un Detalle con wizard de inscripción en vez de disparar la acción al toque;
// Mis solicitudes se integra acá, deja de ser un ítem de sidebar aparte.
// Tab/búsqueda/página viven en la URL (?tab=&search=&page=) para que sea
// recargable/compartible sin perder el estado.
export function Catalogo() {
  const [searchParams, setSearchParams] = useSearchParams();
  const requestedTab = searchParams.get('tab');
  const tab = CATALOG_TABS.includes(requestedTab) ? requestedTab : 'carreras';
  const search = searchParams.get('search') ?? '';
  const page = Number(searchParams.get('page') ?? '1');

  function cambiarTab(nuevoTab) {
    setSearchParams({ tab: nuevoTab });
  }

  function cambiarSearch(nuevoSearch) {
    setSearchParams({ tab, search: nuevoSearch, page: '1' });
  }

  function cambiarPage(nuevaPage) {
    setSearchParams({ tab, search, page: String(nuevaPage) });
  }

  return (
    <PageContainer>
      <div>
        <div className="font-heading text-2xl font-bold tracking-tight text-ink">Catálogo</div>
        <div className="mt-1 text-sm text-ink-faint">
          Elegí una carrera o un curso para ver el detalle e inscribirte.
        </div>
      </div>

      <Tabs value={tab} onValueChange={cambiarTab}>
        <TabsList variant="segmented">
          <TabsTrigger value="carreras">Carreras</TabsTrigger>
          <TabsTrigger value="cursos">Cursos</TabsTrigger>
          <TabsTrigger value="mis-solicitudes">Mis solicitudes</TabsTrigger>
        </TabsList>
      </Tabs>

      {tab === 'carreras' && <TabCarreras search={search} onSearchChange={cambiarSearch} page={page || 1} onPageChange={cambiarPage} />}
      {tab === 'cursos' && <TabCursos search={search} onSearchChange={cambiarSearch} page={page || 1} onPageChange={cambiarPage} />}
      {tab === 'mis-solicitudes' && <TabMisSolicitudes />}
    </PageContainer>
  );
}
