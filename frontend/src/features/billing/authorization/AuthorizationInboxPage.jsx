import { useMemo, useState } from 'react';
import { CheckCircle2, ListChecks } from 'lucide-react';
import { useNavigate } from 'react-router-dom';
import { PageContainer } from '@/components/PageContainer';
import { GlassCard } from '@/components/GlassCard';
import { StatCard } from '@/components/StatCard';
import { EmptyState } from '@/components/EmptyState';
import { Buscador } from '@/components/Buscador';
import { Paginador } from '@/components/Paginador';
import { InfoTip } from '@/components/charts/InfoTip';
import { DataTable } from '@/components/table/DataTable';
import { Tabs, TabsList, TabsTrigger } from '@/components/ui/tabs';
import { useAuth } from '@/context/AuthContext';
import { formatMoney } from '@/lib/billing';
import { useAuthorizationRequests } from '../hooks/useAuthorizationRequests';
import { WithdrawRequestDialog } from './WithdrawRequestDialog';
import { buildAuthorizationColumns } from './authorizationColumns';
import {
  detailPath,
  HISTORY_STATUSES,
  INBOX_PAGE_SIZE,
  isExpiringSoon,
  PENDING_STATUSES,
  roleBaseOf,
  sumRefundAmount,
} from './authorizationRequests';

const PENDING_SEARCH = { statuses: PENDING_STATUSES, sort: 'EXPIRES_ON', descending: false };
const HISTORY_SEARCH = { statuses: HISTORY_STATUSES, sort: 'REQUESTED_AT', descending: true };

function normalize(text) {
  return text.normalize('NFD').replace(/[̀-ͯ]/g, '').toLowerCase();
}

function emptyStateFor({ isDirector, history, searching }) {
  if (searching) {
    return { title: 'Nada coincide con la búsqueda', description: 'Cambiá lo que escribiste para ver más pedidos.' };
  }
  if (history) {
    return { title: 'Todavía no hay decisiones', description: 'Lo que se decida o se retire aparece acá.' };
  }
  if (isDirector) {
    return { title: 'Nada espera tu decisión', description: 'Cuando alguien pida algo que necesite tu aprobación, aparece acá.' };
  }
  return { title: 'No tenés pedidos pendientes', description: 'Los pedidos que mandes a la dirección se ven acá hasta que se decidan.' };
}

export function AuthorizationInboxPage() {
  const { usuario } = useAuth();
  const navigate = useNavigate();
  const roleBase = roleBaseOf(usuario);
  const isDirector = roleBase === 'director';

  const [tab, setTab] = useState('pending');
  const [pendingPage, setPendingPage] = useState(0);
  const [historyPage, setHistoryPage] = useState(0);
  const [search, setSearch] = useState('');
  const [withdrawing, setWithdrawing] = useState(null);

  const history = tab === 'history';
  const pendingQuery = useAuthorizationRequests({ ...PENDING_SEARCH, page: pendingPage, size: INBOX_PAGE_SIZE });
  const historyQuery = useAuthorizationRequests({ ...HISTORY_SEARCH, page: historyPage, size: INBOX_PAGE_SIZE }, { enabled: history });
  const query = history ? historyQuery : pendingQuery;
  const page = history ? historyPage : pendingPage;

  const pendingItems = useMemo(() => pendingQuery.data?.items ?? [], [pendingQuery.data]);
  const pendingTotal = pendingQuery.data?.totalElements;
  const expiringSoon = pendingItems.filter((request) => isExpiringSoon(request)).length;
  const toReturn = sumRefundAmount(pendingItems);

  const items = useMemo(() => {
    const loaded = query.data?.items ?? [];
    if (!history || !search.trim()) return loaded;
    const term = normalize(search.trim());
    return loaded.filter((request) => normalize(`${request.studentName ?? ''} ${request.summary}`).includes(term));
  }, [query.data, history, search]);

  const columns = useMemo(
    () => buildAuthorizationColumns({ isDirector, history, user: usuario, roleBase, onWithdraw: setWithdrawing }),
    [isDirector, history, usuario, roleBase],
  );

  const totalPages = Math.max(1, Math.ceil((query.data?.totalElements ?? 0) / INBOX_PAGE_SIZE));
  const empty = emptyStateFor({ isDirector, history, searching: history && search.trim() !== '' });
  const pendingLabel = isDirector ? 'Pendientes' : 'Mis pedidos';

  function changePage(next) {
    if (history) setHistoryPage(next - 1);
    else setPendingPage(next - 1);
  }

  return (
    <PageContainer
      title={isDirector ? 'Operaciones de facturación' : 'Aprobaciones'}
      description={isDirector ? 'Todo lo de facturación que espera tu decisión' : 'El estado de lo que pediste a la dirección'}
    >
      <div className="grid grid-cols-1 gap-3 sm:grid-cols-2">
        <StatCard
          label={
            <span className="inline-flex items-center gap-1">
              Vencen en 7 días
              <InfoTip>Si nadie decide a tiempo el pedido expira y no se ejecuta.</InfoTip>
            </span>
          }
        >
          <span className="text-2xl font-bold text-warning">{pendingQuery.isLoading ? '…' : expiringSoon}</span>
        </StatCard>
        <StatCard
          label={
            <span className="inline-flex items-center gap-1">
              A devolver
              <InfoTip>Reembolsos pendientes de aprobación.</InfoTip>
            </span>
          }
        >
          <span className="text-2xl font-bold text-ink">{pendingQuery.isLoading ? '…' : toReturn ? formatMoney(toReturn) : '—'}</span>
        </StatCard>
      </div>

      <Tabs value={tab} onValueChange={setTab} className="gap-3">
        <TabsList variant="segmented" className="self-start">
          <TabsTrigger value="pending">
            {pendingLabel}
            {pendingTotal != null && pendingTotal > 0 ? ` (${pendingTotal})` : ''}
          </TabsTrigger>
          <TabsTrigger value="history">Historial</TabsTrigger>
        </TabsList>
      </Tabs>

      {history && (
        <div className="flex flex-wrap items-center justify-between gap-3">
          <Buscador value={search} onChange={setSearch} placeholder="Buscar por alumno" />
          <span className="text-xs text-ink-faint">
            {items.length} {items.length === 1 ? 'pedido' : 'pedidos'}
          </span>
        </div>
      )}

      <GlassCard className="overflow-hidden">
        <DataTable
          key={tab}
          columns={columns}
          data={items}
          getRowId={(request) => String(request.id)}
          isLoading={query.isLoading}
          isError={query.isError}
          onRetry={query.refetch}
          errorMessage="No pudimos cargar los pedidos."
          onRowClick={(request) => navigate(detailPath(roleBase, request.id))}
          minWidth={760}
          emptyState={
            <EmptyState
              icon={history || empty.title.startsWith('Nada coincide') ? <ListChecks className="mx-auto size-6 text-ink-faint" /> : <CheckCircle2 className="mx-auto size-6 text-success" />}
              title={empty.title}
              description={empty.description}
            />
          }
        />
        {!isDirector && <p className="px-5 py-3 text-xs text-ink-faint">Podés retirar tus pedidos mientras sigan pendientes.</p>}
      </GlassCard>

      <Paginador page={page + 1} totalPages={totalPages} onChange={changePage} />

      {withdrawing && <WithdrawRequestDialog request={withdrawing} onClose={() => setWithdrawing(null)} />}
    </PageContainer>
  );
}
