import { useSearchParams } from 'react-router-dom';
import { PageContainer } from '@/components/PageContainer';
import { TabCarreras } from '@/components/catalogo/TabCarreras';
import { TabCursos } from '@/components/catalogo/TabCursos';
import { TabMisSolicitudes } from '@/components/catalogo/TabMisSolicitudes';

// Épica 7 Fase 3: reemplaza el Catálogo de acción-directa de la Fase 2 por un
// shell de 3 tabs (Strategy, ver spec técnica) — Carreras/Cursos ahora llevan a
// un Detalle con wizard de inscripción en vez de disparar la acción al toque;
// Mis solicitudes se integra acá, deja de ser un ítem de sidebar aparte.
// Tab/búsqueda/página viven en la URL (?tab=&search=&page=) para que sea
// recargable/compartible sin perder el estado.
const TABS = [
  { id: 'carreras', label: 'Carreras' },
  { id: 'cursos', label: 'Cursos' },
  { id: 'mis-solicitudes', label: 'Mis solicitudes' },
];

export function Catalogo() {
  const [searchParams, setSearchParams] = useSearchParams();
  const tab = searchParams.get('tab') ?? 'carreras';
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

      <div className="flex gap-1 overflow-x-auto rounded-xl border border-border bg-surface p-1">
        {TABS.map(({ id, label }) => (
          <button
            key={id}
            type="button"
            onClick={() => cambiarTab(id)}
            className={`whitespace-nowrap rounded-lg px-4 py-2 text-[13px] font-semibold transition-colors ${
              tab === id ? 'bg-gradient-to-br from-accent to-accent-2 text-white' : 'text-ink-soft hover:bg-neutral-bg'
            }`}
          >
            {label}
          </button>
        ))}
      </div>

      {tab === 'carreras' && <TabCarreras search={search} onSearchChange={cambiarSearch} page={page || 1} onPageChange={cambiarPage} />}
      {tab === 'cursos' && <TabCursos search={search} onSearchChange={cambiarSearch} page={page || 1} onPageChange={cambiarPage} />}
      {tab === 'mis-solicitudes' && <TabMisSolicitudes />}
    </PageContainer>
  );
}
