import { useMemo } from 'react';
import { useNavigate } from 'react-router-dom';
import { GlassCard } from '@/components/GlassCard';
import { Buscador } from '@/components/catalogo/Buscador';
import { Paginador } from '@/components/catalogo/Paginador';
import { useCursos } from '@/hooks/useCatalogo';

const POR_PAGINA = 9;

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
    <div className="flex flex-col gap-4">
      <Buscador value={search} onChange={onSearchChange} placeholder="Buscar curso…" />

      {isLoading && <div className="text-sm text-ink-faint">Cargando…</div>}
      {!isLoading && filtrados.length === 0 && (
        <div className="text-sm text-ink-faint">No hay cursos que coincidan con la búsqueda.</div>
      )}

      <div className="grid grid-cols-1 gap-4 sm:grid-cols-2 lg:grid-cols-3">
        {pagina.map((curso) => (
          <GlassCard
            key={curso.id}
            onClick={() => navigate(`/catalogo/cursos/${curso.id}`)}
            className="flex cursor-pointer flex-col gap-2 p-5 hover:bg-neutral-bg"
          >
            <div className="font-heading text-base font-bold text-ink">{curso.name}</div>
            <div className="text-xs text-ink-faint">
              {curso.durationMonths} meses{curso.schedule ? ` · ${curso.schedule}` : ''}
            </div>
          </GlassCard>
        ))}
      </div>

      <Paginador page={page} totalPages={totalPages} onChange={onPageChange} />
    </div>
  );
}
