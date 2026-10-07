import { StablePageHeight } from '@/components/StablePageHeight';
import { useMemo } from 'react';
import { useNavigate } from 'react-router-dom';
import { GraduationCap } from 'lucide-react';
import { GlassCard } from '@/components/GlassCard';
import { Skeleton } from '@/components/Skeleton';
import { Stagger } from '@/components/Stagger';
import { Buscador } from '@/components/Buscador';
import { Paginador } from '@/components/Paginador';
import { useCatalogPrograms } from '@/features/enrollment/hooks/useCatalogo';
import { matchesSearch, pageWithinRange, programSummary } from '@/lib/catalog';

// 2 columnas en vez de 3, POR_PAGINA más chico — cards más grandes con más
// aire para la info (decisión de producto: menos cards por pantalla, mejor
// legibilidad, en vez de una grilla densa de tarjetas chicas).
const POR_PAGINA = 6;

export function TabCarreras({ search, onSearchChange, page, onPageChange }) {
  const navigate = useNavigate();
  const { data: programas, isLoading, isError } = useCatalogPrograms();

  const filtradas = useMemo(
    () => (programas ?? []).filter((p) => matchesSearch(p.name, search)),
    [programas, search],
  );
  const totalPages = Math.max(1, Math.ceil(filtradas.length / POR_PAGINA));
  const currentPage = pageWithinRange(page, totalPages);
  const pagina = filtradas.slice((currentPage - 1) * POR_PAGINA, currentPage * POR_PAGINA);

  if (isError) {
    return <GlassCard className="p-6 text-sm text-danger">No pudimos cargar las carreras. Probá recargar la página.</GlassCard>;
  }

  return (
    <div className="flex flex-1 flex-col gap-4">
      <Buscador value={search} onChange={onSearchChange} placeholder="Buscar carrera…" />

      {!isLoading && filtradas.length === 0 && (
        <div className="text-sm text-ink-faint">No hay carreras que coincidan con la búsqueda.</div>
      )}

      <StablePageHeight resetKey={search}>
      <div className="grid grid-cols-1 gap-4 sm:grid-cols-2">
        {isLoading && Array.from({ length: 6 }).map((_, i) => <Skeleton key={i} className="h-32" />)}
        <Stagger>
          {pagina.map((programa) => (
            <GlassCard
              key={programa.id}
              interactive
              onClick={() => navigate(`/catalogo/carreras/${programa.id}`)}
              className="flex flex-col gap-3 p-6"
            >
              <div className="flex h-10 w-10 items-center justify-center rounded-xl bg-accent-soft text-primary">
                <GraduationCap className="h-5 w-5" />
              </div>
              <div className="font-heading text-lg font-bold text-ink">{programa.name}</div>
              {/* mt-auto: la grilla ya estira las cards de una misma fila a la
                  misma altura (default de CSS Grid), esto ancla la meta al pie
                  de CADA card en vez de quedar pegada al título — si el título
                  de al lado ocupa 2 líneas, sin esto la meta queda "flotando"
                  en alturas distintas entre cards de la misma fila. */}
              <div className="mt-auto text-[13px] text-ink-faint">{programSummary(programa)}</div>
            </GlassCard>
          ))}
        </Stagger>
      </div>
      </StablePageHeight>

      <Paginador page={currentPage} totalPages={totalPages} onChange={onPageChange} />
    </div>
  );
}
