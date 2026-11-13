import { StablePageHeight } from '@/components/StablePageHeight';
import { useMemo } from 'react';
import { Link, useSearchParams } from 'react-router-dom';
import { GlassCard } from '@/components/GlassCard';
import { EmptyState } from '@/components/EmptyState';
import { Skeleton } from '@/components/Skeleton';
import { Tabs, TabsContent, TabsList, TabsTrigger } from '@/components/ui/tabs';
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from '@/components/ui/select';
import { Buscador } from '@/components/Buscador';
import { Paginador } from '@/components/Paginador';
import { LoadErrorState } from '@/components/LoadErrorState';
import { Input } from '@/components/ui/input';

const POR_PAGINA = 8;

function normalizar(texto) {
  return texto.normalize('NFD').replace(/[̀-ͯ]/g, '').toLowerCase();
}

export function BandejaTramites({
  titulo,
  volverA = '/director/aprobaciones',
  volverLabel = 'Aprobaciones',
  useListaPendientes,
  useListaHistorial,
  renderFila,
  extraerTextoBusqueda,
  extraerFecha,
  tituloVacioPendientes = 'Nada pendiente',
  tituloVacioHistorial = 'Todavía no decidiste nada',
  etiquetaPendientes = 'Pendientes',
  contadorHistorial = true,
  recurso = 'los trámites',
}) {
  const [searchParams, setSearchParams] = useSearchParams();
  const tab = searchParams.get('tab') === 'historial' ? 'historial' : 'pendientes';
  const search = searchParams.get('q') ?? '';
  const orden = searchParams.get('orden') ?? (tab === 'pendientes' ? 'antiguos' : 'recientes');
  const desde = searchParams.get('desde') ?? '';
  const hasta = searchParams.get('hasta') ?? '';
  const page = Math.max(1, Number.parseInt(searchParams.get('page') ?? '1', 10) || 1);

  function updateParams(changes, { resetPage = true } = {}) {
    setSearchParams(
      (current) => {
        const next = new URLSearchParams(current);
        const entries = resetPage ? { ...changes, page: '' } : changes;
        Object.entries(entries).forEach(([key, value]) => {
          if (value) {
            next.set(key, value);
          } else {
            next.delete(key);
          }
        });
        return next;
      },
      { replace: true },
    );
  }

  const { data: pendientes, isLoading: cargandoPendientes, isError: pendingFailed, error: pendingError, refetch: refetchPendientes } = useListaPendientes();
  const { data: historial, isLoading: cargandoHistorial, isError: historyFailed, error: historyError, refetch: refetchHistorial } = useListaHistorial();

  const cargando = tab === 'pendientes' ? cargandoPendientes : cargandoHistorial;
  const failed = tab === 'pendientes' ? pendingFailed : historyFailed;
  const failure = tab === 'pendientes' ? pendingError : historyError;
  const refetch = tab === 'pendientes' ? refetchPendientes : refetchHistorial;
  const lista = useMemo(
    () => (tab === 'pendientes' ? (pendientes ?? []) : (historial ?? [])),
    [tab, pendientes, historial],
  );

  const filtrada = useMemo(() => {
    let resultado = lista.filter((item) => normalizar(extraerTextoBusqueda(item)).includes(normalizar(search)));
    if (desde) {
      resultado = resultado.filter((item) => {
        const fecha = extraerFecha(item, tab);
        return fecha && fecha >= new Date(`${desde}T00:00:00`);
      });
    }
    if (hasta) {
      resultado = resultado.filter((item) => {
        const fecha = extraerFecha(item, tab);
        return fecha && fecha <= new Date(`${hasta}T23:59:59`);
      });
    }
    resultado = [...resultado].sort((a, b) => {
      const fechaA = extraerFecha(a, tab) ?? new Date(0);
      const fechaB = extraerFecha(b, tab) ?? new Date(0);
      return orden === 'recientes' ? fechaB - fechaA : fechaA - fechaB;
    });
    return resultado;
  }, [lista, search, desde, hasta, orden, tab, extraerTextoBusqueda, extraerFecha]);

  const totalPages = Math.max(1, Math.ceil(filtrada.length / POR_PAGINA));
  const pagina = filtrada.slice((page - 1) * POR_PAGINA, page * POR_PAGINA);


  return (
    <div className="flex flex-1 flex-col gap-4">
      {volverA && (
        <Link
          to={volverA}
          className="flex w-fit items-center gap-1.5 text-size-13 font-semibold text-ink-soft hover:text-ink"
        >
          ← {volverLabel}
        </Link>
      )}

      <h1 className="font-heading text-2xl font-bold text-ink">{titulo}</h1>

      <Tabs className="gap-4" value={tab} onValueChange={(value) => updateParams({ tab: value === 'historial' ? 'historial' : '', orden: '' })}>
        <TabsList variant="segmented">
          <TabsTrigger value="pendientes">{etiquetaPendientes}{pendientes ? ` (${pendientes.length})` : ''}</TabsTrigger>
          <TabsTrigger value="historial">Historial{contadorHistorial && historial ? ` (${historial.length})` : ''}</TabsTrigger>
        </TabsList>

      <div className="flex flex-wrap items-center gap-3">
        <Buscador value={search} onChange={(valor) => updateParams({ q: valor })} placeholder="Buscar por nombre…" />
        <Select value={orden} onValueChange={(value) => updateParams({ orden: value })}>
          <SelectTrigger className="w-[220px]" aria-label="Ordenar"><SelectValue /></SelectTrigger>
          <SelectContent>
            <SelectItem value="recientes">Más recientes primero</SelectItem>
            <SelectItem value="antiguos">Más antiguos primero</SelectItem>
          </SelectContent>
        </Select>
        <div className="flex min-w-0 flex-wrap items-center gap-2 text-size-12-5 text-ink-faint">
          <label className="flex items-center gap-2">
            Desde
            <Input
              type="date"
              value={desde}
              onChange={(e) => updateParams({ desde: e.target.value })}
              className="w-auto"
            />
          </label>
          <label className="flex items-center gap-2">
            Hasta
            <Input
              type="date"
              value={hasta}
              onChange={(e) => updateParams({ hasta: e.target.value })}
              className="w-auto"
            />
          </label>
        </div>
      </div>

      <TabsContent value={tab} className="mt-0">
      <StablePageHeight resetKey={`${tab}-${search}-${desde}-${hasta}`}>
      <GlassCard className="flex flex-col gap-1 p-4">
        {cargando ? (
          <div className="flex flex-col gap-2 p-2">
            <Skeleton className="h-12" />
            <Skeleton className="h-12" />
          </div>
        ) : failed ? (
          <LoadErrorState bare error={failure} onRetry={refetch} resource={recurso} />
        ) : filtrada.length === 0 ? (
          <EmptyState
            title={lista.length === 0 ? (tab === 'pendientes' ? tituloVacioPendientes : tituloVacioHistorial) : 'Nada coincide con la búsqueda'}
            className="py-6"
          />
        ) : (
          pagina.map((item) => renderFila(item, { esHistorial: tab === 'historial' }))
        )}
      </GlassCard>
      </StablePageHeight>
      </TabsContent>
      </Tabs>

      <Paginador page={page} totalPages={totalPages} onChange={(value) => updateParams({ page: value > 1 ? String(value) : '' }, { resetPage: false })} />
    </div>
  );
}
