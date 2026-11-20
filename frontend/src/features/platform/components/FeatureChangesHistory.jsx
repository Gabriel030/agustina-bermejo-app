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
import { NativeSelect } from '@/components/ui/native-select';
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from '@/components/ui/table';

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
        <Table className="w-full text-left text-sm">
          <TableHeader>
            <TableRow className="text-xs text-ink-faint">
              <TableHead scope="col" className="py-1 pr-3 font-semibold">Fecha</TableHead>
              <TableHead scope="col" className="py-1 pr-3 font-semibold">Módulo</TableHead>
              <TableHead scope="col" className="py-1 pr-3 font-semibold">Quién</TableHead>
              <TableHead scope="col" className="py-1 pr-3 font-semibold">Cambio</TableHead>
              <TableHead scope="col" className="py-1 font-semibold">Motivo</TableHead>
            </TableRow>
          </TableHeader>
          <TableBody>
            {query.data.content.map((change) => (
              <TableRow key={change.id} className="border-t border-line align-top">
                <TableCell className="whitespace-nowrap py-2 pr-3 text-ink-soft">{formatDateTime(change.changedAt)}</TableCell>
                <TableCell className="py-2 pr-3 font-medium text-ink">{FEATURE_CATALOG[change.code]?.label ?? change.code}</TableCell>
                <TableCell className="py-2 pr-3 text-ink-soft">{actorName(change)}</TableCell>
                <TableCell className="py-2 pr-3 text-ink">
                  <span>{describeState(change.code, change.before)}</span> → <b>{describeState(change.code, change.after)}</b>
                </TableCell>
                <TableCell className="py-2 text-ink-soft">{change.reason}</TableCell>
              </TableRow>
            ))}
          </TableBody>
        </Table>
        <Paginador page={page} totalPages={query.data.totalPages ?? 1} onChange={setPage} />
      </div>
    );
  }

  return (
    <section aria-labelledby="feature-history-title" className="flex flex-col gap-3">
      <div className="flex flex-wrap items-center justify-between gap-2">
        <h2 id="feature-history-title" className="font-heading text-size-16 font-semibold text-ink">Historial de cambios</h2>
        <div className="flex flex-wrap items-center gap-3">
          <label className="flex items-center gap-2 text-size-13 font-medium text-ink-soft">
            Módulo
            <NativeSelect
              className="w-auto"
              value={code}
              onChange={(event) => selectCode(event.target.value)}
            >
              <option value={ALL}>Todos</option>
              {Object.entries(FEATURE_CATALOG).map(([value, feature]) => (
                <option key={value} value={value}>{feature.label}</option>
              ))}
            </NativeSelect>
          </label>
          <label className="flex items-center gap-2 text-size-13 font-medium text-ink-soft">
            Quién
            <NativeSelect
              className="w-auto"
              value={actorKind}
              onChange={(event) => selectActorKind(event.target.value)}
            >
              <option value={ALL}>Todos</option>
              {ACTOR_KINDS.map((kind) => (
                <option key={kind.value} value={kind.value}>{kind.label}</option>
              ))}
            </NativeSelect>
          </label>
          <label className="flex items-center gap-2 text-size-13 font-medium text-ink-soft">
            Desde
            <Input type="date" value={from} max={to || undefined} onChange={(event) => selectFrom(event.target.value)} className="w-fit" />
          </label>
          <label className="flex items-center gap-2 text-size-13 font-medium text-ink-soft">
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
