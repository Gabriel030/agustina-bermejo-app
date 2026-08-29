import { useMemo } from 'react';
import { useNavigate } from 'react-router-dom';
import { GraduationCap } from 'lucide-react';
import { GlassCard } from '@/components/GlassCard';
import { Skeleton } from '@/components/Skeleton';
import { Buscador } from '@/components/catalogo/Buscador';
import { Paginador } from '@/components/catalogo/Paginador';
import { useProgramas } from '@/hooks/useCatalogo';

// 2 columnas en vez de 3, POR_PAGINA más chico — cards más grandes con más
// aire para la info (decisión de producto: menos cards por pantalla, mejor
// legibilidad, en vez de una grilla densa de tarjetas chicas).
const POR_PAGINA = 6;

export function TabCarreras({ search, onSearchChange, page, onPageChange }) {
  const navigate = useNavigate();
  const { data: programas, isLoading, isError } = useProgramas();

  const filtradas = useMemo(
    () => (programas ?? []).filter((p) => p.name.toLowerCase().includes(search.toLowerCase())),
    [programas, search],
  );
  const totalPages = Math.max(1, Math.ceil(filtradas.length / POR_PAGINA));
  const pagina = filtradas.slice((page - 1) * POR_PAGINA, page * POR_PAGINA);

  if (isError) {
    return <GlassCard className="p-6 text-sm text-danger">No pudimos cargar las carreras. Probá recargar la página.</GlassCard>;
  }

  return (
    <div className="flex flex-col gap-4">
      <Buscador value={search} onChange={onSearchChange} placeholder="Buscar carrera…" />

      {!isLoading && filtradas.length === 0 && (
        <div className="text-sm text-ink-faint">No hay carreras que coincidan con la búsqueda.</div>
      )}

      <div className="grid grid-cols-1 gap-4 sm:grid-cols-2">
        {isLoading && Array.from({ length: 6 }).map((_, i) => <Skeleton key={i} className="h-32" />)}
        {pagina.map((programa) => (
          <GlassCard
            key={programa.id}
            onClick={() => navigate(`/catalogo/carreras/${programa.id}`)}
            className="flex cursor-pointer flex-col gap-3 p-6 hover:bg-neutral-bg"
          >
            <div className="flex h-10 w-10 items-center justify-center rounded-xl bg-accent-soft text-primary">
              <GraduationCap className="h-5 w-5" />
            </div>
            <div className="font-heading text-lg font-bold text-ink">{programa.name}</div>
            <div className="text-[13px] text-ink-faint">{programa.totalYears} años · Título oficial</div>
          </GlassCard>
        ))}
      </div>

      <Paginador page={page} totalPages={totalPages} onChange={onPageChange} />
    </div>
  );
}
