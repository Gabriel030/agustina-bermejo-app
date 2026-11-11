import { useRef, useState } from 'react';
import { useSearchParams } from 'react-router-dom';
import { PageContainer } from '@/components/PageContainer';
import { GlassCard } from '@/components/GlassCard';
import { KpiFilterChip } from '@/components/KpiFilterChip';
import { Buscador } from '@/components/Buscador';
import { Paginador } from '@/components/Paginador';
import { LoadErrorState } from '@/components/LoadErrorState';
import { Button } from '@/components/ui/button';
import { NativeSelect, NativeSelectOption } from '@/components/ui/native-select';
import { DataTable } from '@/components/table/DataTable';
import { EmptyState } from '@/components/EmptyState';
import { useClosureList, useClosureTerms } from '../hooks/useClosureList';
import { buildClosureListColumns } from '../components/closureListColumns';
import { CLOSURE_KPIS, CLOSURE_LIST_PAGE_SIZE, CLOSURE_LIST_STATUS } from '../lib/closureList';

function readFilters(searchParams) {
  const status = searchParams.get('status');
  const page = Number(searchParams.get('page'));
  return {
    status: status in CLOSURE_LIST_STATUS ? status : null,
    termId: searchParams.get('termId') ?? '',
    q: searchParams.get('q') ?? '',
    page: Number.isInteger(page) && page > 0 ? page : 1,
  };
}

function useClosureFilters() {
  const [searchParams, setSearchParams] = useSearchParams();
  const filters = readFilters(searchParams);
  const update = (changes) => {
    const next = { ...filters, page: 1, ...changes };
    const params = new URLSearchParams();
    if (next.status) params.set('status', next.status);
    if (next.termId) params.set('termId', next.termId);
    if (next.q) params.set('q', next.q);
    if (next.page > 1) params.set('page', String(next.page));
    setSearchParams(params, { replace: true });
  };
  return { filters, update };
}

export function ClosureListPage({ role }) {
  const { filters, update } = useClosureFilters();
  const [searchText, setSearchText] = useState(filters.q);
  const searchTimer = useRef(null);
  const { data, isLoading, isError, refetch } = useClosureList(filters);
  const { data: terms = [] } = useClosureTerms();

  const changeSearch = (text) => {
    setSearchText(text);
    clearTimeout(searchTimer.current);
    searchTimer.current = setTimeout(() => update({ q: text.trim() }), 300);
  };

  const hasFilters = Boolean(filters.status || filters.termId || filters.q);
  const clearFilters = () => {
    clearTimeout(searchTimer.current);
    setSearchText('');
    update({ status: null, termId: '', q: '' });
  };

  if (isError && !data) {
    return (
      <PageContainer title="Cierres de comisión">
        <LoadErrorState resource="los cierres de comisión" onRetry={refetch} />
      </PageContainer>
    );
  }

  const total = data?.totalElements ?? 0;
  const totalPages = Math.max(1, Math.ceil(total / CLOSURE_LIST_PAGE_SIZE));

  return (
    <PageContainer title="Cierres de comisión" description="Aprobá el acta de cada comisión cuando el docente confirmó la planilla.">
      <div role="group" aria-label="Filtrar por estado del cierre" className="flex flex-wrap gap-2">
        {CLOSURE_KPIS.map((kpi) => (
          <KpiFilterChip
            key={kpi.countKey}
            label={kpi.label}
            count={data ? data.counts[kpi.countKey] : '–'}
            pressed={filters.status === kpi.status}
            onClick={() => update({ status: filters.status === kpi.status ? null : kpi.status })}
          />
        ))}
      </div>
      <GlassCard className="flex flex-col gap-3 p-4">
        <div className="flex flex-wrap items-center gap-3">
          <Buscador value={searchText} onChange={changeSearch} placeholder="Buscar por materia o comisión" />
          <NativeSelect
            aria-label="Término"
            className="w-auto min-w-48"
            value={filters.termId}
            onChange={(event) => update({ termId: event.target.value })}
          >
            <NativeSelectOption value="">Todos los términos</NativeSelectOption>
            {terms.map((term) => (
              <NativeSelectOption key={term.id} value={String(term.id)}>
                {term.description}
              </NativeSelectOption>
            ))}
          </NativeSelect>
          {hasFilters && (
            <Button variant="link" type="button" onClick={clearFilters}>
              Limpiar filtros
            </Button>
          )}
          <span role="status" aria-live="polite" className="text-sm text-ink-soft sm:ml-auto">
            {data ? `${total} ${total === 1 ? 'comisión' : 'comisiones'}` : ''}
          </span>
        </div>
        <DataTable
          columns={buildClosureListColumns(role)}
          data={data?.items}
          getRowId={(item) => `${item.sectionKind}-${item.sectionId}`}
          isLoading={isLoading}
          mobileLayout="scroll"
          minWidth={1160}
          emptyState={
            <EmptyState
              title="Ninguna comisión coincide con la búsqueda."
              description="Probá con otro nombre o quitá los filtros."
              action={hasFilters ? <Button variant="outline" onClick={clearFilters}>Limpiar filtros</Button> : null}
            />
          }
        />
        <p className="text-xs text-ink-soft">Ordenadas: las confirmadas por el docente primero.</p>
        <Paginador page={filters.page} totalPages={totalPages} onChange={(page) => update({ page })} />
      </GlassCard>
    </PageContainer>
  );
}
