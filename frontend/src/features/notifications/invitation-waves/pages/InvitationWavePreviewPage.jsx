import { useState } from 'react';
import { ArrowLeft, Check } from 'lucide-react';
import { Link, useNavigate, useParams } from 'react-router-dom';
import { EmptyState } from '@/components/EmptyState';
import { GlassCard } from '@/components/GlassCard';
import { LoadErrorState } from '@/components/LoadErrorState';
import { Notice } from '@/components/Notice';
import { PageContainer } from '@/components/PageContainer';
import { Paginador } from '@/components/Paginador';
import { Skeleton } from '@/components/Skeleton';
import { StatCard } from '@/components/StatCard';
import { StatusPill } from '@/components/StatusPill';
import { StickyActionBar } from '@/components/StickyActionBar';
import { Button } from '@/components/ui/button';
import { Tabs, TabsContent, TabsList, TabsTrigger } from '@/components/ui/tabs';
import { formatDecimal } from '@/lib/formatNumber';
import { estimateSchedule, totalPagesOf } from '../lib/invitationWaveMath';
import { EXCLUSION_LABELS, lacksPersonalData } from '../lib/invitationWaveLabels';
import { PersonalDataNotice } from '../components/PersonalDataNotice';
import { NoAccessState } from '../components/NoAccessState';
import { RecipientsTable } from '../components/RecipientsTable';
import { WaveNotFound } from '../components/WaveNotFound';
import { useInvitationsBasePath } from '../hooks/useInvitationsBasePath';
import { useInvitationWave, useInvitationWaveAccess, useInvitationWaveRecipients, useInvitationWaveSettings } from '../hooks/useInvitationWaves';

const PAGE_SIZE = 10;
const READ_PERMISSION = 'notification.read_invitation_waves';

function ExcludedSummary({ byReason }) {
  const entries = Object.entries(byReason ?? {}).filter(([, count]) => count > 0);
  if (entries.length === 0) return null;
  return (
    <ul className="flex flex-wrap gap-2" aria-label="Excluidos por motivo">
      {entries.map(([reason, count]) => (
        <li key={reason}>
          <StatusPill tone="warning">
            {EXCLUSION_LABELS[reason] ?? reason} · {formatDecimal(count)}
          </StatusPill>
        </li>
      ))}
    </ul>
  );
}

export function InvitationWavePreviewPage() {
  const { waveId } = useParams();
  const navigate = useNavigate();
  const basePath = useInvitationsBasePath();
  const access = useInvitationWaveAccess();
  const [tab, setTab] = useState('eligible');
  const [pages, setPages] = useState({ eligible: 1, excluded: 1 });
  const waveQuery = useInvitationWave(waveId, { enabled: access.read });
  const settings = useInvitationWaveSettings({ enabled: access.read });
  const recipients = useInvitationWaveRecipients(waveId, {
    status: tab === 'eligible' ? 'PENDING' : 'EXCLUDED',
    page: pages[tab],
    size: PAGE_SIZE,
    enabled: access.read,
  });

  if (!access.read) {
    return (
      <PageContainer title="Vista previa de la oleada">
        <NoAccessState permission={READ_PERMISSION} />
      </PageContainer>
    );
  }
  if (waveQuery.isLoading) {
    return (
      <PageContainer>
        <Skeleton className="h-10 w-64" />
        <Skeleton className="h-64 w-full" />
      </PageContainer>
    );
  }
  if (waveQuery.error?.code === 'INVITATION_WAVE_NOT_FOUND') return <WaveNotFound error={waveQuery.error} basePath={basePath} />;
  if (waveQuery.isError) {
    return (
      <PageContainer>
        <LoadErrorState error={waveQuery.error} onRetry={waveQuery.refetch} resource="la oleada" backTo={basePath} backLabel="Invitaciones" distinguishForbidden />
      </PageContainer>
    );
  }

  const wave = waveQuery.data;
  const rows = recipients.data?.items ?? [];
  const eligible = wave.eligibleCount;
  const excluded = wave.excludedTotal;
  const estimate = estimateSchedule({
    recipients: wave.invitableCount,
    batchSize: wave.batchSize,
    intervalMinutes: wave.intervalMinutes,
    dailyCap: wave.dailyCap,
    sendWindow: settings.data?.sendWindow,
  });
  const goToWave = () => navigate(`${basePath}/${wave.id}`);
  const goToList = () => navigate(basePath);

  return (
    <PageContainer>
      <Button asChild variant="text" className="min-h-11 w-fit px-0 md:min-h-8">
        <Link to={basePath}>
          <ArrowLeft aria-hidden="true" />
          Volver a Invitaciones
        </Link>
      </Button>
      <PageContainer
        title="Vista previa de la oleada"
        description="Revisá a quién se invita antes de iniciarla."
        action={
          <div className="hidden gap-2 md:flex">
            <Button type="button" variant="outline" className="md:min-h-8" onClick={goToList}>
              Volver a la lista
            </Button>
            <Button type="button" variant="primary" className="md:min-h-8" onClick={goToWave}>
              Ir a la oleada
            </Button>
          </div>
        }
      >
        <Notice tone="success" role="status">
          <span className="flex items-start gap-2">
            <Check className="mt-0.5 size-4 shrink-0" aria-hidden="true" />
            <span>
              Creamos el borrador <b>«{wave.name}»</b>. Todavía no se envió ningún mail: se envía recién cuando la inicies.
            </span>
          </span>
        </Notice>

        <div className="grid grid-cols-2 gap-3">
          <StatCard label="Elegibles">
            <span className="font-heading text-3xl font-bold text-ink" data-testid="eligible-total">{formatDecimal(eligible)}</span>
          </StatCard>
          <StatCard label="Excluidos">
            <span className="font-heading text-3xl font-bold text-ink" data-testid="excluded-total">{formatDecimal(excluded)}</span>
          </StatCard>
        </div>

        {wave.invitableCount > 0 ? (
          <p className="rounded-xl bg-neutral-bg px-3 py-2.5 text-size-13 text-ink">
            Estimado: {formatDecimal(estimate.batches)} {estimate.batches === 1 ? 'tanda' : 'tandas'} de hasta {formatDecimal(wave.batchSize)} cada {formatDecimal(wave.intervalMinutes)} minutos, con tope de{' '}
            {formatDecimal(wave.dailyCap)} por día. Termina en {formatDecimal(estimate.days)} {estimate.days === 1 ? 'día' : 'días'}.
          </p>
        ) : (
          <Notice tone="warning">No hay personas para invitar en esta oleada. Podés volver a la lista y armar otra con un lote distinto.</Notice>
        )}

        <GlassCard className="flex flex-col gap-3 p-3 sm:p-4">
          <Tabs value={tab} onValueChange={setTab}>
            <TabsList variant="segmented" className="group-data-horizontal/tabs:h-11 md:group-data-horizontal/tabs:h-8">
              <TabsTrigger value="eligible" className="min-h-11 md:min-h-0">Elegibles · {formatDecimal(eligible)}</TabsTrigger>
              <TabsTrigger value="excluded" className="min-h-11 md:min-h-0">Excluidos · {formatDecimal(excluded)}</TabsTrigger>
            </TabsList>
            {['eligible', 'excluded'].map((value) => (
              <TabsContent key={value} value={value} className="mt-3 flex flex-col gap-3">
                {value === 'excluded' && <ExcludedSummary byReason={wave.excludedByReason} />}
                {lacksPersonalData(rows) && <PersonalDataNotice />}
                <RecipientsTable
                  rows={rows}
                  variant={value}
                  zone={settings.data?.sendWindow?.zone}
                  isLoading={recipients.isLoading}
                  isError={recipients.isError}
                  onRetry={recipients.refetch}
                  emptyState={<EmptyState title={value === 'eligible' ? 'No hay personas elegibles para invitar.' : 'No hay personas excluidas.'} />}
                />
              </TabsContent>
            ))}
          </Tabs>
          <Paginador
            page={pages[tab]}
            totalPages={totalPagesOf(recipients.data)}
            onChange={(next) => setPages((current) => ({ ...current, [tab]: next }))}
          />
        </GlassCard>

        <StickyActionBar className="md:hidden" role="region" aria-label="Acciones de la pantalla" summary={`${formatDecimal(eligible)} elegibles · ${formatDecimal(excluded)} excluidos`}>
          <div className="flex gap-2">
            <Button type="button" variant="outline" className="min-h-11 flex-1" onClick={goToList}>
              Volver a la lista
            </Button>
            <Button type="button" variant="primary" className="min-h-11 flex-[1.7]" onClick={goToWave}>
              Ir a la oleada
            </Button>
          </div>
        </StickyActionBar>
      </PageContainer>
    </PageContainer>
  );
}
