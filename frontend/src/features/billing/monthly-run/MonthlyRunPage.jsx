import { useState } from 'react';
import { FlaskConical } from 'lucide-react';
import { EmptyState } from '@/components/EmptyState';
import { GlassCard } from '@/components/GlassCard';
import { PageContainer } from '@/components/PageContainer';
import { Skeleton } from '@/components/Skeleton';
import { TabCount } from '@/components/TabCount';
import { Button } from '@/components/ui/button';
import { Tabs, TabsContent, TabsList, TabsTrigger } from '@/components/ui/tabs';
import { useAuth } from '@/context/AuthContext';
import { useMonthlyRuns, useSimulateMonthlyRun } from '@/features/billing/hooks/useMonthlyRuns';
import { ManualIssuanceSimulationPanel } from './ManualIssuanceSimulationPanel';
import { MonthlyRunHeader } from './MonthlyRunHeader';
import { MonthlyRunHistoryTable } from './MonthlyRunHistoryTable';
import { MonthlyRunItemsTable } from './MonthlyRunItemsTable';
import { RunCounterCards } from './RunCounterCards';
import { RunProgressCard } from './RunProgressCard';
import { RunSupervisionBanner } from './RunSupervisionBanner';
import { countProblemItems, currentPeriod, findRealRun, formatPeriod, periodOf } from './monthlyRunLabels';

export function MonthlyRunPage() {
  const maxPeriod = currentPeriod();
  const [period, setPeriod] = useState(maxPeriod);
  const [simulationOpen, setSimulationOpen] = useState(false);
  const { usuario } = useAuth();
  const isDirector = Boolean(usuario?.roles?.includes('DIRECTOR'));
  const simulation = useSimulateMonthlyRun();
  const periodRuns = useMonthlyRuns({ period });
  const allRuns = useMonthlyRuns();
  const run = findRealRun(periodRuns.data?.items);
  const history = (allRuns.data?.items ?? []).filter((candidate) => candidate.mode === 'REAL' && periodOf(candidate) !== period);
  const isRunning = run?.status === 'RUNNING';

  function runSimulation() {
    setSimulationOpen(true);
    simulation.mutate({ period });
  }

  function closeSimulation() {
    setSimulationOpen(false);
    simulation.reset();
  }

  function changePeriod(nextPeriod) {
    closeSimulation();
    setPeriod(nextPeriod);
  }

  const problemCount = countProblemItems(run);

  return (
    <PageContainer
      title="Emisión mensual"
      description="Estado de la corrida de cada período, reintentos y recuperación manual"
      action={
        <Button type="button" variant="gradient" disabled={isRunning || simulation.isPending} onClick={runSimulation}>
          <FlaskConical aria-hidden="true" />
          Simular emisión
        </Button>
      }
    >
      <MonthlyRunHeader period={period} maxPeriod={maxPeriod} run={run} onPeriodChange={changePeriod} />
      {periodRuns.isLoading && <Skeleton className="h-28 w-full rounded-2xl" />}
      {periodRuns.isError && (
        <GlassCard>
          <EmptyState
            title="No pudimos cargar la corrida del período."
            action={
              <Button type="button" variant="outline" onClick={() => periodRuns.refetch()}>
                Reintentar
              </Button>
            }
          />
        </GlassCard>
      )}
      {periodRuns.isSuccess && (
        <>
          <RunSupervisionBanner period={period} run={run} isCurrentPeriod={period === maxPeriod} />
          {simulationOpen && (
            <ManualIssuanceSimulationPanel
              period={period}
              run={run}
              isCurrentPeriod={period === maxPeriod}
              isDirector={isDirector}
              simulation={simulation}
              onRerun={runSimulation}
              onClose={closeSimulation}
              onIssued={closeSimulation}
            />
          )}
          {isRunning && <RunProgressCard counters={run.counters} />}
          {run && !isRunning && <RunCounterCards counters={run.counters} />}
          <Tabs defaultValue="items" className="gap-4">
            <TabsList variant="segmented" className="w-full sm:w-fit">
              <TabsTrigger value="items" className="flex-1">
                Ítems
                <TabCount count={problemCount} />
              </TabsTrigger>
              <TabsTrigger value="history" className="flex-1">
                Historial
              </TabsTrigger>
            </TabsList>
            <TabsContent value="items">
              <GlassCard className="p-0">
                {run ? (
                  <MonthlyRunItemsTable key={run.id} monthlyRunId={run.id} />
                ) : (
                  <EmptyState icon="🗓️" title={`No hay corrida de ${formatPeriod(period)}`} description="Cuando corra, vas a ver acá cada inscripción y su resultado." />
                )}
              </GlassCard>
            </TabsContent>
            <TabsContent value="history">
              <GlassCard className="p-0">
                <MonthlyRunHistoryTable
                  runs={history}
                  total={history.length}
                  isLoading={allRuns.isLoading}
                  isError={allRuns.isError}
                  onRetry={allRuns.refetch}
                  onSelectRun={(selected) => changePeriod(periodOf(selected))}
                />
              </GlassCard>
            </TabsContent>
          </Tabs>
        </>
      )}
    </PageContainer>
  );
}
