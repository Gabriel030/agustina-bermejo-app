import { useMemo, useState } from 'react';
import { Link } from 'react-router-dom';
import { ChevronRight } from 'lucide-react';
import { useAuth } from '@/context/AuthContext';
import { Buscador } from '@/components/Buscador';
import { EmptyState } from '@/components/EmptyState';
import { GlassCard } from '@/components/GlassCard';
import { LoadErrorState } from '@/components/LoadErrorState';
import { PageContainer } from '@/components/PageContainer';
import { Skeleton } from '@/components/Skeleton';
import { StatCard } from '@/components/StatCard';
import { StatusPill } from '@/components/StatusPill';
import { FEE_TYPE_LABELS, formatDate, formatMoney } from '@/lib/billing';
import { roleBaseOf } from '../authorization/authorizationRequests';
import { useOfferNames } from '../hooks/useOfferNames';
import { PRICE_STATUS, advanceVersionOf, countByPresence, offerKind, offerName, priceStatusKey, priceStatusKeys } from './pricing';
import { usePrices } from './usePrices';

const FILTERS = [
  { key: 'PROPOSAL', label: 'Propuestas pendientes' },
  { key: 'SCHEDULED', label: 'Aumentos programados' },
  { key: 'NONE', label: 'Conceptos sin precio' },
];

const STATUS_NOTES = {
  NONE: 'No se factura y la oferta no aparece en el catálogo',
  PROPOSAL: 'La decide el director',
};

function conceptOf(price) {
  return FEE_TYPE_LABELS[price.feeType] ?? price.description;
}

function PriceRow({ price, to }) {
  const statusKey = priceStatusKey(price);
  const status = PRICE_STATUS[statusKey];
  const advance = advanceVersionOf(price);
  const scheduled = price.nextScheduledVersion;
  const note = statusKey === 'SCHEDULED' ? `Rige desde el ${formatDate(scheduled.effectiveFrom)}` : STATUS_NOTES[statusKey];
  return (
    <Link
      to={to}
      className="grid grid-cols-[1fr_auto] items-center gap-x-3 gap-y-1 rounded-2xl px-4 py-3 transition-colors hover:bg-neutral-bg/60 md:grid-cols-[1.4fr_1fr_1.2fr_auto]"
    >
      <div className="min-w-0">
        <p className="text-[14px] font-semibold text-ink">{conceptOf(price)}</p>
        {advance && (
          <p className="text-[12px] text-ink-faint">
            Anticipada {advance.appliesToLectiveYear} · {formatMoney(advance.finalAmount)} desde {formatDate(advance.effectiveFrom)}
          </p>
        )}
      </div>
      <div className="col-start-1 text-[13px] text-ink md:col-start-auto">
        {price.currentVersion ? (
          <>
            <span className="font-semibold tabular-nums">{formatMoney(price.currentVersion.finalAmount)}</span>
            <span className="ml-2 text-[12px] text-ink-faint">desde {formatDate(price.currentVersion.effectiveFrom)}</span>
          </>
        ) : (
          <span className="text-ink-faint">—</span>
        )}
      </div>
      <div className="col-start-1 flex flex-col gap-0.5 md:col-start-auto">
        <StatusPill tone={status.tone}>{status.label}</StatusPill>
        {note && <span className="text-[12px] text-ink-faint">{note}</span>}
      </div>
      <ChevronRight className="col-start-2 row-span-3 row-start-1 h-4 w-4 text-ink-faint md:col-start-auto md:row-span-1 md:row-start-auto" aria-hidden="true" />
    </Link>
  );
}

export function PricesPage() {
  const { usuario } = useAuth();
  const roleBase = roleBaseOf(usuario);
  const prices = usePrices();
  const names = useOfferNames();
  const [filter, setFilter] = useState(null);
  const [search, setSearch] = useState('');

  const list = useMemo(() => prices.data ?? [], [prices.data]);
  const counts = useMemo(() => countByPresence(list), [list]);

  const groups = useMemo(() => {
    const term = search.trim().toLowerCase();
    const filtered = list.filter((price) => {
      if (filter && !priceStatusKeys(price).includes(filter)) return false;
      return !term || offerName(price, names).toLowerCase().includes(term);
    });
    const byOffer = new Map();
    filtered.forEach((price) => {
      const key = `${offerKind(price)}-${price.programId ?? price.courseId}`;
      if (!byOffer.has(key)) byOffer.set(key, { key, name: offerName(price, names), kind: offerKind(price), prices: [] });
      byOffer.get(key).prices.push(price);
    });
    return [...byOffer.values()].sort((a, b) => a.name.localeCompare(b.name, 'es'));
  }, [list, filter, search, names]);

  return (
    <PageContainer title="Precios" description="El precio de cada concepto de cada carrera y curso, con sus cambios programados.">
      {prices.isLoading ? (
        <Skeleton className="h-64 w-full rounded-3xl" aria-label="Cargando precios" />
      ) : prices.isError ? (
        <LoadErrorState error={prices.error} onRetry={prices.refetch} resource="los precios" bare />
      ) : list.length === 0 ? (
        <GlassCard className="p-6">
          <EmptyState title="Todavía no hay precios" description="Los precios nacen junto con cada carrera o curso." />
        </GlassCard>
      ) : (
        <>
          <div className="grid gap-3 sm:grid-cols-3">
            {FILTERS.map((item) => (
              <button
                key={item.key}
                type="button"
                aria-pressed={filter === item.key}
                onClick={() => setFilter(filter === item.key ? null : item.key)}
                className="rounded-3xl text-left focus:outline-none focus-visible:ring-2 focus-visible:ring-brand/40"
              >
                <StatCard label={item.label} className={filter === item.key ? 'ring-2 ring-brand/50' : ''}>
                  <span className="font-heading text-3xl font-bold text-ink">{counts[item.key]}</span>
                </StatCard>
              </button>
            ))}
          </div>
          <div className="flex flex-wrap items-center gap-3">
            <Buscador value={search} onChange={setSearch} placeholder="Buscar por carrera o curso" />
            {filter && (
              <button type="button" onClick={() => setFilter(null)} className="text-sm font-semibold text-primary hover:underline">
                Quitar filtro: {FILTERS.find((item) => item.key === filter).label}
              </button>
            )}
          </div>
          {groups.length === 0 ? (
            <GlassCard className="p-6">
              <EmptyState title="Ningún precio coincide con el filtro" />
            </GlassCard>
          ) : (
            groups.map((group) => (
              <GlassCard key={group.key} className="flex flex-col gap-1 p-3">
                <div className="flex items-center gap-2 px-4 pb-1 pt-2">
                  <h2 className="font-heading text-[15px] font-semibold text-ink">{group.name}</h2>
                  <StatusPill>{group.kind}</StatusPill>
                </div>
                {group.prices.map((price) => (
                  <PriceRow key={price.priceId} price={price} to={`/${roleBase}/precios/${price.priceId}`} />
                ))}
              </GlassCard>
            ))
          )}
        </>
      )}
    </PageContainer>
  );
}
