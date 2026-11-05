import { useMemo, useState } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { Plus } from 'lucide-react';
import { GlassCard } from '@/components/GlassCard';
import { EmptyState } from '@/components/EmptyState';
import { InfoTip } from '@/components/charts/InfoTip';
import { DataTable } from '@/components/table/DataTable';
import { Button } from '@/components/ui/button';
import { Tabs, TabsList, TabsTrigger } from '@/components/ui/tabs';
import { useMediaQuery } from '@/hooks/useMediaQuery';

function CreateButton({ to, label, size }) {
  return (
    <Button asChild variant="gradient" size={size}>
      <Link to={to}>
        <Plus />
        {label}
      </Link>
    </Button>
  );
}

export function DefinitionsSection({
  title,
  tip,
  createTo,
  createLabel,
  emptyTitle,
  emptyDescription,
  entries,
  isLoading,
  isError,
  onRetry,
  columns,
  detailPath,
  mobileGroups,
}) {
  const navigate = useNavigate();
  const isCardMode = useMediaQuery('(max-width: 767px)');
  const [group, setGroup] = useState('all');
  const activeGroup = mobileGroups.find((candidate) => candidate.id === group) ?? mobileGroups[0];
  const rows = useMemo(
    () => (isCardMode ? (entries ?? []).filter(activeGroup.match) : entries),
    [entries, isCardMode, activeGroup],
  );
  const isEmpty = !isLoading && !isError && !(entries ?? []).length;

  return (
    <section className="flex flex-col gap-3" aria-label={title}>
      <div className="flex flex-wrap items-center justify-between gap-3">
        <div className="flex items-center gap-2">
          <h2 className="font-heading text-base font-semibold text-ink">{title}</h2>
          <InfoTip>{tip}</InfoTip>
        </div>
        {!isEmpty && <CreateButton to={createTo} label={createLabel} />}
      </div>
      {isCardMode && !isEmpty && !isLoading && !isError && (
        <Tabs value={activeGroup.id} onValueChange={setGroup}>
          <TabsList variant="segmented" className="w-full">
            {mobileGroups.map((candidate) => (
              <TabsTrigger key={candidate.id} value={candidate.id} className="flex-1">
                {candidate.label}
              </TabsTrigger>
            ))}
          </TabsList>
        </Tabs>
      )}
      <GlassCard className="overflow-x-auto p-0">
        <DataTable
          columns={columns}
          data={rows}
          getRowId={(entry) => String(entry.id)}
          isLoading={isLoading}
          isError={isError}
          onRetry={onRetry}
          errorMessage="No pudimos cargar los datos."
          onRowClick={(entry) => navigate(detailPath(entry))}
          minWidth={760}
          emptyState={
            <EmptyState
              title={isEmpty ? emptyTitle : 'Nada coincide con este filtro'}
              description={isEmpty ? emptyDescription : 'Probá con otra opción.'}
              action={isEmpty ? <CreateButton to={createTo} label={createLabel} size="lg" /> : undefined}
            />
          }
        />
      </GlassCard>
    </section>
  );
}
