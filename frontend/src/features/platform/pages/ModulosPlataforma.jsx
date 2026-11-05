import { useState } from 'react';
import { PageContainer } from '@/components/PageContainer';
import { GlassCard } from '@/components/GlassCard';
import { Skeleton } from '@/components/Skeleton';
import { EmptyState } from '@/components/EmptyState';
import { LoadErrorState } from '@/components/LoadErrorState';
import { BillingModePanel } from '@/features/platform/components/BillingModePanel';
import { FeatureChangeDialog } from '@/features/platform/components/FeatureChangeDialog';
import { FeatureRow } from '@/features/platform/components/FeatureRow';
import { usePlatformFeatures } from '@/features/platform/hooks/usePlatformFeatures';
import { BILLING_CODE, FEATURE_CATALOG, FEATURE_GROUPS, isFeatureAvailable } from '@/features/platform/lib/featureCatalog';

function LoadingList() {
  return (
    <div className="flex flex-col gap-3" aria-busy="true" aria-label="Cargando módulos">
      <Skeleton className="h-24 w-full" />
      <Skeleton className="h-24 w-full" />
      <Skeleton className="h-24 w-full" />
    </div>
  );
}

export function ModulosPlataforma() {
  const query = usePlatformFeatures();
  const [pending, setPending] = useState(null);

  if (query.isLoading) {
    return (
      <PageContainer title="Módulos">
        <LoadingList />
      </PageContainer>
    );
  }
  if (query.isError) {
    return (
      <PageContainer title="Módulos">
        <LoadErrorState error={query.error} onRetry={query.refetch} resource="los módulos" />
      </PageContainer>
    );
  }

  const features = (query.data ?? []).filter((feature) => FEATURE_CATALOG[feature.code]);
  if (features.length === 0) {
    return (
      <PageContainer title="Módulos">
        <GlassCard className="flex flex-1 flex-col p-12">
          <EmptyState icon="🧩" title="Todavía no hay módulos configurados" description="Cuando el instituto tenga módulos, vas a poder prenderlos y apagarlos desde acá." />
        </GlassCard>
      </PageContainer>
    );
  }

  const byCode = Object.fromEntries(features.map((feature) => [feature.code, feature]));
  const billing = byCode[BILLING_CODE];

  return (
    <PageContainer title="Módulos">
      <div className="flex flex-col gap-6">
        {FEATURE_GROUPS.map((group) => {
          const rows = group.codes.map((code) => byCode[code]).filter(Boolean);
          if (rows.length === 0) return null;
          const active = rows.filter((row) => row.code !== BILLING_CODE && isFeatureAvailable(row.code, features) && row.enabled).length;
          return (
            <section key={group.id} aria-labelledby={`group-${group.id}`} className="flex flex-col gap-3">
              <h2 id={`group-${group.id}`} className="font-heading text-[16px] font-semibold text-ink">
                {group.title}
                <span className="ml-2 text-xs font-medium text-ink-faint">{active} activos</span>
              </h2>
              {group.codes.includes(BILLING_CODE) && billing && (
                <BillingModePanel billing={billing} onSelectMode={(mode) => setPending({ code: BILLING_CODE, target: { mode } })} />
              )}
              <GlassCard className="p-0">
                <ul className="divide-y divide-line">
                  {rows.map((row) => (
                    <FeatureRow
                      key={row.code}
                      feature={row}
                      available={isFeatureAvailable(row.code, features)}
                      onToggle={(code, enabled) => setPending({ code, target: { enabled } })}
                    />
                  ))}
                </ul>
              </GlassCard>
            </section>
          );
        })}
      </div>
      <FeatureChangeDialog
        key={pending ? `${pending.code}-${JSON.stringify(pending.target)}` : 'none'}
        change={pending}
        features={features}
        onClose={() => setPending(null)}
        onRefresh={() => query.refetch()}
      />
    </PageContainer>
  );
}
