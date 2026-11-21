import { useState } from 'react';
import { CheckCircle2, Info } from 'lucide-react';
import { EmptyState } from '@/components/EmptyState';
import { GlassCard } from '@/components/GlassCard';
import { LoadErrorState } from '@/components/LoadErrorState';
import { Skeleton } from '@/components/Skeleton';
import { StatusPill } from '@/components/StatusPill';
import { Button } from '@/components/ui/button';
import { NativeSelect, NativeSelectOption } from '@/components/ui/native-select';
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from '@/components/ui/table';
import { usePermissionDiscrepancies } from '@/features/permissions/hooks/usePermissionDiscrepancies';
import { DISCREPANCY_DIRECTIONS, moduleLabel, pluralize, roleLabel } from '@/features/permissions/lib/permissionGrid';
import { formatDateTime } from '@/lib/billing';

function DirectionPill({ direction }) {
  const config = DISCREPANCY_DIRECTIONS[direction];
  return <StatusPill tone={config?.tone ?? 'neutral'}>{config?.label ?? direction}</StatusPill>;
}

function PermissionName({ discrepancy, descriptions }) {
  return (
    <>
      <span className="block font-semibold text-ink">{descriptions.get(discrepancy.permissionCode) ?? discrepancy.permissionCode}</span>
      <code className="text-size-11 text-ink-faint">{discrepancy.permissionCode}</code>
    </>
  );
}

function DiscrepanciesTable({ items, descriptions, onShowInGrid }) {
  return (
    <>
      <div className="hidden md:block">
        <Table className="text-size-13">
          <TableHeader>
            <TableRow>
              <TableHead>Permiso</TableHead>
              <TableHead>Módulo</TableHead>
              <TableHead>Rol</TableHead>
              <TableHead>Dirección</TableHead>
              <TableHead className="text-right">Veces</TableHead>
              <TableHead>Última vez</TableHead>
              <TableHead>
                <span className="sr-only">Acciones</span>
              </TableHead>
            </TableRow>
          </TableHeader>
          <TableBody>
            {items.map((item) => (
              <TableRow key={`${item.role}|${item.permissionCode}|${item.direction}`} className="align-top">
                <TableCell className="whitespace-normal">
                  <PermissionName discrepancy={item} descriptions={descriptions} />
                </TableCell>
                <TableCell className="whitespace-normal">{moduleLabel(item.module)}</TableCell>
                <TableCell className="whitespace-normal">{roleLabel(item.role)}</TableCell>
                <TableCell className="whitespace-normal">
                  <DirectionPill direction={item.direction} />
                </TableCell>
                <TableCell className="text-right font-bold tabular-nums">{item.count}</TableCell>
                <TableCell className="tabular-nums">{formatDateTime(item.lastSeenAt)}</TableCell>
                <TableCell className="text-right">
                  <Button type="button" variant="outline" className="min-h-11" onClick={() => onShowInGrid(item.module)}>
                    Ver en la grilla
                  </Button>
                </TableCell>
              </TableRow>
            ))}
          </TableBody>
        </Table>
      </div>
      <ul className="flex flex-col md:hidden">
        {items.map((item) => (
          <li key={`${item.role}|${item.permissionCode}|${item.direction}`} className="flex flex-col gap-2.5 border-b border-border p-3.5 last:border-b-0">
            <div>
              <PermissionName discrepancy={item} descriptions={descriptions} />
            </div>
            <div className="flex flex-wrap items-center gap-1.5 text-size-12 text-ink-soft">
              <StatusPill>{moduleLabel(item.module)}</StatusPill>
              <StatusPill>{roleLabel(item.role)}</StatusPill>
              <StatusPill tone="warning">{pluralize(item.count, 'vez', 'veces')}</StatusPill>
              <span className="tabular-nums">{formatDateTime(item.lastSeenAt)}</span>
            </div>
            <DirectionPill direction={item.direction} />
            <Button type="button" variant="outline" className="min-h-11" onClick={() => onShowInGrid(item.module)}>
              Ver en la grilla
            </Button>
          </li>
        ))}
      </ul>
    </>
  );
}

export function PermissionDiscrepanciesTab({ summary, catalogIndex, onShowInGrid }) {
  const [module, setModule] = useState('');
  const [page, setPage] = useState(0);
  const query = usePermissionDiscrepancies({ module, page });
  const descriptions = new Map([...catalogIndex].map(([code, permission]) => [code, permission.description]));
  const data = query.data;
  const occurrences = summary.modules
    .filter((entry) => !module || entry.module === module)
    .reduce((sum, entry) => sum + entry.total, 0);
  const firstShown = data ? data.page * data.size + 1 : 0;
  const lastShown = data ? data.page * data.size + data.items.length : 0;

  const changeModule = (next) => {
    setModule(next);
    setPage(0);
  };

  let body;
  if (query.isLoading) {
    body = (
      <div role="status" aria-busy="true" aria-label="Cargando discrepancias" className="flex flex-col gap-3 p-4">
        {Array.from({ length: 4 }, (_, index) => (
          <Skeleton key={index} className="h-10 w-full" />
        ))}
      </div>
    );
  } else if (query.error) {
    body = <LoadErrorState error={query.error} resource="las discrepancias" onRetry={() => query.refetch()} bare />;
  } else if (!data.items.length) {
    body = (
      <EmptyState
        icon={<CheckCircle2 className="mx-auto size-6 text-success" aria-hidden="true" />}
        title="Sin discrepancias"
        description="En los módulos en modo Sombra, la regla vieja y la grilla dieron siempre la misma respuesta. Es un buen momento para pasar un módulo a Activo."
        className="py-12"
      />
    );
  } else {
    body = (
      <>
        <DiscrepanciesTable items={data.items} descriptions={descriptions} onShowInGrid={onShowInGrid} />
        <div className="flex flex-wrap items-center justify-between gap-2 border-t border-border px-4 py-3 text-size-12 text-ink-faint">
          <span className="tabular-nums" aria-live="polite">
            Mostrando {firstShown}–{lastShown} de {data.totalElements}
          </span>
          <span className="flex gap-1.5">
            <Button type="button" variant="outline" className="min-h-11" disabled={data.page <= 0} onClick={() => setPage(data.page - 1)}>
              Anterior
            </Button>
            <Button type="button" variant="outline" className="min-h-11" disabled={lastShown >= data.totalElements} onClick={() => setPage(data.page + 1)}>
              Siguiente
            </Button>
          </span>
        </div>
      </>
    );
  }

  return (
    <div className="flex flex-col gap-3.5">
      <div className="flex items-start gap-2.5 rounded-card bg-accent-soft px-3.5 py-3 text-size-13 text-ink-soft">
        <Info className="mt-0.5 size-4 shrink-0 text-brand" aria-hidden="true" />
        <p>
          <b>¿Qué es una discrepancia?</b> Un momento en que la regla vieja y la grilla darían respuestas distintas para el mismo permiso. Se registran en los módulos en modo Sombra. Antes de pasar un módulo a Activo, revisá que no queden pendientes.
        </p>
      </div>
      <div className="flex flex-wrap items-center gap-2.5">
        <NativeSelect value={module} onChange={(event) => changeModule(event.target.value)} aria-label="Filtrar discrepancias por módulo" className="w-auto min-w-48">
          <NativeSelectOption value="">Todos los módulos</NativeSelectOption>
          {summary.modules.map((entry) => (
            <NativeSelectOption key={entry.module} value={entry.module}>
              {moduleLabel(entry.module)}
            </NativeSelectOption>
          ))}
        </NativeSelect>
        {data && (
          <span className="text-size-12 tabular-nums text-ink-faint" aria-live="polite">
            {pluralize(data.totalElements, 'permiso con diferencias', 'permisos con diferencias')}, {pluralize(occurrences, 'ocurrencia', 'ocurrencias')}
          </span>
        )}
      </div>
      <GlassCard className="overflow-clip p-0">{body}</GlassCard>
    </div>
  );
}
