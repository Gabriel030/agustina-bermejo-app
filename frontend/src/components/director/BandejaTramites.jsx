import { StablePageHeight } from '@/components/StablePageHeight';
import { useMemo, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { GlassCard } from '@/components/GlassCard';
import { EmptyState } from '@/components/EmptyState';
import { Skeleton } from '@/components/Skeleton';
import { Tabs, TabsList, TabsTrigger } from '@/components/ui/tabs';
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from '@/components/ui/select';
import { Buscador } from '@/components/Buscador';
import { Paginador } from '@/components/Paginador';

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
}) {
  const navigate = useNavigate();
  const [tab, setTab] = useState('pendientes');
  const [search, setSearch] = useState('');
  const [orden, setOrden] = useState('recientes');
  const [desde, setDesde] = useState('');
  const [hasta, setHasta] = useState('');
  const [page, setPage] = useState(1);

  const { data: pendientes, isLoading: cargandoPendientes, isError: pendingFailed } = useListaPendientes();
  const { data: historial, isLoading: cargandoHistorial, isError: historyFailed } = useListaHistorial();

  const cargando = tab === 'pendientes' ? cargandoPendientes : cargandoHistorial;
  const failed = tab === 'pendientes' ? pendingFailed : historyFailed;
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

  function cambiarTab(nuevoTab) {
    setTab(nuevoTab);
    setPage(1);
  }

  function cambiarBusqueda(valor) {
    setSearch(valor);
    setPage(1);
  }

  return (
    <div className="flex flex-1 flex-col gap-4">
      <button
        type="button"
        onClick={() => navigate(volverA)}
        className="flex w-fit items-center gap-1.5 text-[13px] font-semibold text-ink-soft hover:text-ink"
      >
        ← {volverLabel}
      </button>

      <h1 className="font-heading text-2xl font-bold text-ink">{titulo}</h1>

      <Tabs value={tab} onValueChange={cambiarTab}>
        <TabsList variant="segmented">
          <TabsTrigger value="pendientes">Pendientes</TabsTrigger>
          <TabsTrigger value="historial">Historial</TabsTrigger>
        </TabsList>
      </Tabs>

      <div className="flex flex-wrap items-center gap-3">
        <Buscador value={search} onChange={cambiarBusqueda} placeholder="Buscar por nombre…" />
        <Select value={orden} onValueChange={setOrden}>
          <SelectTrigger className="w-[220px]"><SelectValue /></SelectTrigger>
          <SelectContent>
            <SelectItem value="recientes">Más recientes primero</SelectItem>
            <SelectItem value="antiguos">Más antiguos primero</SelectItem>
          </SelectContent>
        </Select>
        <div className="flex min-w-0 flex-wrap items-center gap-2 text-[12.5px] text-ink-faint">
          <label className="flex items-center gap-2">
            Desde
            <input
              type="date"
              value={desde}
              onChange={(e) => { setDesde(e.target.value); setPage(1); }}
              className="rounded-lg border border-border bg-surface px-2 py-1.5 text-[12.5px] text-ink"
            />
          </label>
          <label className="flex items-center gap-2">
            Hasta
            <input
              type="date"
              value={hasta}
              onChange={(e) => { setHasta(e.target.value); setPage(1); }}
              className="rounded-lg border border-border bg-surface px-2 py-1.5 text-[12.5px] text-ink"
            />
          </label>
        </div>
      </div>

      <StablePageHeight resetKey={`${tab}-${search}-${desde}-${hasta}`}>
      <GlassCard className="flex flex-col gap-1 p-4">
        {cargando ? (
          <div className="flex flex-col gap-2 p-2">
            <Skeleton className="h-12" />
            <Skeleton className="h-12" />
          </div>
        ) : failed ? (
          <EmptyState title="No pudimos cargar los trámites" description="Probá recargar la página en un momento." className="py-6" />
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

      <Paginador page={page} totalPages={totalPages} onChange={setPage} />
    </div>
  );
}
