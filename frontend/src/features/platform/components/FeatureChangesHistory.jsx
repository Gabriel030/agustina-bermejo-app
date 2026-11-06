import { useState } from 'react';
import { GlassCard } from '@/components/GlassCard';
import { Skeleton } from '@/components/Skeleton';
import { EmptyState } from '@/components/EmptyState';
import { LoadErrorState } from '@/components/LoadErrorState';
import { Input } from '@/components/ui/input';
import { Paginador } from '@/components/Paginador';
import { formatDateTime } from '@/lib/billing';
import { FEATURE_CATALOG } from '@/features/platform/lib/featureCatalog';
import { describeState } from '@/features/platform/lib/featureChangeRows';
import { useFeatureChanges } from '@/features/platform/hooks/useFeatureChanges';

const ALL = 'ALL';
const ACTOR_KINDS = [
  { value: 'HUMAN', label: 'Personas' },
  { value: 'SERVICE', label: 'Sistema' },
];

function actorName(change) {
  if (change.actor?.fullName) return change.actor.fullName;
  return change.actorKind === 'SERVICE' ? 'Sistema' : 'Sin dato';
}

export function FeatureChangesHistory() {
  const [code, setCode] = useState(ALL);
  const [page, setPage] = useState(1);
  const [actorKind, setActorKind] = useState(ALL);
  const [from, setFrom] = useState('');
  const [to, setTo] = useState('');
  const rangeInvalid = Boolean(from && to && from > to);
  const query = useFeatureChanges({
    code: code === ALL ? undefined : code,
    actorKind: actorKind === ALL ? undefined : actorKind,
    from: rangeInvalid ? undefined : from || undefined,
    to: rangeInvalid ? undefined : to || undefined,
    page,
  });

  function changeFilter(setter) {
    return (value) => {
      setter(value);
      setPage(1);
    };
  }

  const selectCode = changeFilter(setCode);
  const selectActorKind = changeFilter(setActorKind);
  const selectFrom = changeFilter(setFrom);
  const selectTo = changeFilter(setTo);
  const filtered = code !== ALL || actorKind !== ALL || Boolean(from) || Boolean(to);

  let body;
  if (query.isLoading) {
    body = (
      <div role="status" className="flex flex-col gap-2" aria-busy="true" aria-label="Cargando el historial">
        <Skeleton className="h-10 w-full" />
        <Skeleton className="h-10 w-full" />
        <Skeleton className="h-10 w-full" />
      </div>
    );
  } else if (query.isError) {
    body = <LoadErrorState bare error={query.error} onRetry={query.refetch} resource="el historial" />;
  } else if (query.data.content.length === 0) {
    body = filtered ? (
      <EmptyState icon="🕘" title="Sin cambios con esos filtros" description="Probá con otro módulo, otro tipo de actor u otras fechas." />
    ) : (
      <EmptyState icon="🕘" title="Todavía no hay cambios" description="Cuando se prenda, apague o cambie un módulo, queda registrado acá." />
    );
  } else {
    body = (
      <div className="overflow-x-auto">
        <table className="w-full text-left text-sm">
          <thead>
            <tr className="text-xs text-ink-faint">
              <th scope="col" className="py-1 pr-3 font-semibold">Fecha</th>
              <th scope="col" className="py-1 pr-3 font-semibold">Módulo</th>
              <th scope="col" className="py-1 pr-3 font-semibold">Quién</th>
              <th scope="col" className="py-1 pr-3 font-semibold">Cambio</th>
              <th scope="col" className="py-1 font-semibold">Motivo</th>
            </tr>
          </thead>
          <tbody>
            {query.data.content.map((change) => (
              <tr key={change.id} className="border-t border-line align-top">
                <td className="whitespace-nowrap py-2 pr-3 text-ink-soft">{formatDateTime(change.changedAt)}</td>
                <td className="py-2 pr-3 font-medium text-ink">{FEATURE_CATALOG[change.code]?.label ?? change.code}</td>
                <td className="py-2 pr-3 text-ink-soft">{actorName(change)}</td>
                <td className="py-2 pr-3 text-ink">
                  <span>{describeState(change.code, change.before)}</span> → <b>{describeState(change.code, change.after)}</b>
                </td>
                <td className="py-2 text-ink-soft">{change.reason}</td>
              </tr>
            ))}
          </tbody>
        </table>
        <Paginador page={page} totalPages={query.data.totalPages ?? 1} onChange={setPage} />
      </div>
    );
  }

  return (
    <section aria-labelledby="feature-history-title" className="flex flex-col gap-3">
      <div className="flex flex-wrap items-center justify-between gap-2">
        <h2 id="feature-history-title" className="font-heading text-[16px] font-semibold text-ink">Historial de cambios</h2>
        <div className="flex flex-wrap items-center gap-3">
          <label className="flex items-center gap-2 text-[13px] font-medium text-ink-soft">
            Módulo
            <select
              value={code}
              onChange={(event) => selectCode(event.target.value)}
              className="rounded-lg border border-line bg-surface px-2 py-1.5 text-sm text-ink"
            >
              <option value={ALL}>Todos</option>
              {Object.entries(FEATURE_CATALOG).map(([value, feature]) => (
                <option key={value} value={value}>{feature.label}</option>
              ))}
            </select>
          </label>
          <label className="flex items-center gap-2 text-[13px] font-medium text-ink-soft">
            Quién
            <select
              value={actorKind}
              onChange={(event) => selectActorKind(event.target.value)}
              className="rounded-lg border border-line bg-surface px-2 py-1.5 text-sm text-ink"
            >
              <option value={ALL}>Todos</option>
              {ACTOR_KINDS.map((kind) => (
                <option key={kind.value} value={kind.value}>{kind.label}</option>
              ))}
            </select>
          </label>
          <label className="flex items-center gap-2 text-[13px] font-medium text-ink-soft">
            Desde
            <Input type="date" value={from} max={to || undefined} onChange={(event) => selectFrom(event.target.value)} className="w-fit" />
          </label>
          <label className="flex items-center gap-2 text-[13px] font-medium text-ink-soft">
            Hasta
            <Input type="date" value={to} min={from || undefined} onChange={(event) => selectTo(event.target.value)} className="w-fit" />
          </label>
        </div>
      </div>
      {rangeInvalid && <p role="alert" className="text-sm font-semibold text-danger">La fecha «Desde» no puede ser posterior a «Hasta».</p>}
      <GlassCard className="p-4">{body}</GlassCard>
    </section>
  );
}
