import { useMemo, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { GlassCard } from '@/components/GlassCard';
import { Buscador } from '@/components/catalogo/Buscador';
import { Paginador } from '@/components/catalogo/Paginador';
import { useProgramas } from '@/hooks/useCatalogo';

const POR_PAGINA = 9;

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

      {isLoading && <div className="text-sm text-ink-faint">Cargando…</div>}
      {!isLoading && filtradas.length === 0 && (
        <div className="text-sm text-ink-faint">No hay carreras que coincidan con la búsqueda.</div>
      )}

      <div className="grid grid-cols-1 gap-4 sm:grid-cols-2 lg:grid-cols-3">
        {pagina.map((programa) => (
          <GlassCard
            key={programa.id}
            onClick={() => navigate(`/catalogo/carreras/${programa.id}`)}
            className="flex cursor-pointer flex-col gap-2 p-5 hover:bg-neutral-bg"
          >
            <div className="font-heading text-base font-bold text-ink">{programa.name}</div>
            <div className="text-xs text-ink-faint">{programa.totalYears} años · Título oficial</div>
          </GlassCard>
        ))}
      </div>

      <Paginador page={page} totalPages={totalPages} onChange={onPageChange} />
    </div>
  );
}
