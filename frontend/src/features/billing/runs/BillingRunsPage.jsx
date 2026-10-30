import { PageContainer } from '@/components/PageContainer';
import { TabCount } from '@/components/TabCount';
import { Tabs, TabsContent, TabsList, TabsTrigger } from '@/components/ui/tabs';
import { useNightlyRuns } from '@/features/billing/hooks/useNightlyRuns';
import { NightlyRunsTab } from './NightlyRunsTab';
import { countNightsWithErrors } from './nightlyRunLabels';

export function BillingRunsPage() {
  const runs = useNightlyRuns();

  return (
    <PageContainer title="Corridas de facturación" description="Qué hizo el sistema cada noche">
      <Tabs defaultValue="nightly" className="gap-4">
        <TabsList variant="segmented" className="w-full sm:w-fit">
          <TabsTrigger value="nightly" className="flex-1">
            Procesos nocturnos
            <TabCount count={countNightsWithErrors(runs.data)} />
          </TabsTrigger>
        </TabsList>
        <TabsContent value="nightly">
          <NightlyRunsTab />
        </TabsContent>
      </Tabs>
    </PageContainer>
  );
}
