import { useMemo } from 'react';
import { useNavigate } from 'react-router-dom';
import { BookOpen } from 'lucide-react';
import { GlassCard } from '@/components/GlassCard';
import { Skeleton } from '@/components/Skeleton';
import { Stagger } from '@/components/Stagger';
import { Buscador } from '@/components/catalogo/Buscador';
import { Paginador } from '@/components/catalogo/Paginador';
import { useCursos } from '@/hooks/useCatalogo';

// 2 columnas en vez de 3, POR_PAGINA más chico — cards más grandes con más
// aire para la info (decisión de producto: menos cards por pantalla, mejor
// legibilidad, en vez de una grilla densa de tarjetas chicas).
const POR_PAGINA = 6;

export function TabCursos({ search, onSearchChange, page, onPageChange }) {
  const navigate = useNavigate();
  const { data: cursos, isLoading, isError } = useCursos();

  const filtrados = useMemo(
    () => (cursos ?? []).filter((c) => c.name.toLowerCase().includes(search.toLowerCase())),
    [cursos, search],
  );
  const totalPages = Math.max(1, Math.ceil(filtrados.length / POR_PAGINA));
  const pagina = filtrados.slice((page - 1) * POR_PAGINA, page * POR_PAGINA);

  if (isError) {
    return <GlassCard className="p-6 text-sm text-danger">No pudimos cargar los cursos. Probá recargar la página.</GlassCard>;
  }

  return (
    <div className="flex min-h-full flex-1 flex-col gap-4">
      <Buscador value={search} onChange={onSearchChange} placeholder="Buscar curso…" />

      {!isLoading && filtrados.length === 0 && (
        <div className="text-sm text-ink-faint">No hay cursos que coincidan con la búsqueda.</div>
      )}

      <div className="grid grid-cols-1 gap-4 sm:grid-cols-2">
        {isLoading && Array.from({ length: 6 }).map((_, i) => <Skeleton key={i} className="h-32" />)}
        <Stagger>
          {pagina.map((curso) => (
            <GlassCard
              key={curso.id}
              interactive
              onClick={() => navigate(`/catalogo/cursos/${curso.id}`)}
              className="flex flex-col gap-3 p-6"
            >
              <div className="flex h-10 w-10 items-center justify-center rounded-xl bg-accent-soft text-primary">
                <BookOpen className="h-5 w-5" />
              </div>
              <div className="font-heading text-lg font-bold text-ink">{curso.name}</div>
              {/* mt-auto: mismo criterio que TabCarreras — ancla la meta al pie
                  de cada card, para que no quede a distinta altura entre cards
                  de la misma fila cuando el título de una ocupa más líneas. */}
              <div className="mt-auto text-[13px] text-ink-faint">
                {curso.durationMonths} meses{curso.schedule ? ` · ${curso.schedule}` : ''}
              </div>
            </GlassCard>
          ))}
        </Stagger>
      </div>

      <Paginador page={page} totalPages={totalPages} onChange={onPageChange} />
    </div>
  );
}
