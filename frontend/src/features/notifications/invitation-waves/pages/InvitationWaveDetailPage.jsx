import { ArrowLeft } from 'lucide-react';
import { Link, useParams, useSearchParams } from 'react-router-dom';
import { EmptyState } from '@/components/EmptyState';
import { GlassCard } from '@/components/GlassCard';
import { KpiFilterChip } from '@/components/KpiFilterChip';
import { LoadErrorState } from '@/components/LoadErrorState';
import { PageContainer } from '@/components/PageContainer';
import { Paginador } from '@/components/Paginador';
import { Skeleton } from '@/components/Skeleton';
import { Button } from '@/components/ui/button';
import { formatDecimal } from '@/lib/formatNumber';
import { formatInstantDate, totalPagesOf } from '../lib/invitationWaveMath';
import { RECIPIENT_STATUS, RECIPIENT_STATUS_ORDER, lacksPersonalData } from '../lib/invitationWaveLabels';
import { NoAccessState } from '../components/NoAccessState';
import { PersonalDataNotice } from '../components/PersonalDataNotice';
import { RecipientsTable } from '../components/RecipientsTable';
import { WaveAccessNotice } from '../components/WaveAccessNotice';
import { WaveActionButtons } from '../components/WaveActionButtons';
import { WaveActionDialog } from '../components/WaveActionDialog';
import { WaveBanner } from '../components/WaveBanner';
import { WaveMobileBar } from '../components/WaveMobileBar';
import { WaveNotFound } from '../components/WaveNotFound';
import { WaveProgress } from '../components/WaveProgress';
import { WaveStatusPill } from '../components/WaveStatusPill';
import { useInvitationsBasePath } from '../hooks/useInvitationsBasePath';
import { useInvitationWave, useInvitationWaveAccess, useInvitationWaveRecipients, useInvitationWaveSettings } from '../hooks/useInvitationWaves';
import { useWaveActionController } from '../hooks/useWaveActionController';

const PAGE_SIZE = 20;
const READ_PERMISSION = 'notification.read_invitation_waves';
const COUNT_FIELD = {
  PENDING: 'eligibleCount',
  QUEUED: 'queuedTotal',
  SENT: 'sentTotal',
  FAILED: 'failedTotal',
  EXCLUDED: 'excludedTotal',
  ACTIVATED: 'activatedTotal',
};

function BackLink({ to }) {
  return (
    <Button asChild variant="text" className="min-h-11 w-fit px-0 md:min-h-8">
      <Link to={to}>
        <ArrowLeft aria-hidden="true" />
        Volver a Invitaciones
      </Link>
    </Button>
  );
}

function WaveHeader({ wave, controller }) {
  return (
    <div className="flex flex-wrap items-start justify-between gap-3">
      <div className="flex min-w-0 flex-col gap-1.5">
        <h1 className="font-heading text-2xl font-bold tracking-tight text-ink">{wave.name}</h1>
        <p className="text-sm text-ink-soft">
          Lote: {wave.importBatchName ?? '—'} · Creada el {formatInstantDate(wave.createdAt)}
        </p>
        <WaveStatusPill status={wave.status} />
      </div>
      <WaveActionButtons controller={controller} />
    </div>
  );
}

function WaveSummary({ wave }) {
  return (
    <GlassCard className="flex flex-col gap-2.5 p-4">
      <div className="flex flex-wrap items-center justify-between gap-2">
        <b className="text-ink">Avance</b>
        <span className="text-size-13 text-ink-soft">
          Tandas de {formatDecimal(wave.batchSize)} · cada {formatDecimal(wave.intervalMinutes)} min · tope {formatDecimal(wave.dailyCap)} por día
        </span>
      </div>
      <WaveProgress wave={wave} />
      <p className="text-size-12-5 text-ink-faint">Invitamos por mail de activación. Nunca enviamos contraseñas.</p>
    </GlassCard>
  );
}

function StatusFilters({ wave, status, onChange }) {
  return (
    <div className="grid grid-cols-2 gap-2.5 sm:grid-cols-3 lg:grid-cols-6" role="group" aria-label="Filtrar destinatarios por estado">
      {RECIPIENT_STATUS_ORDER.map((key) => {
        const count = wave[COUNT_FIELD[key]] ?? 0;
        return (
          <KpiFilterChip
            key={key}
            label={RECIPIENT_STATUS[key].plural}
            count={formatDecimal(count)}
            pressed={status === key}
            tone={key === 'FAILED' && count > 0 ? 'danger' : undefined}
            onClick={() => onChange(status === key ? null : key)}
          />
        );
      })}
    </div>
  );
}

function RecipientsSection({ wave, settings, status, page, onStatusChange, onPageChange }) {
  const recipients = useInvitationWaveRecipients(wave.id, { status, page, size: PAGE_SIZE });
  const rows = recipients.data?.items ?? [];
  const total = recipients.data?.totalElements;
  const filterLabel = status ? RECIPIENT_STATUS[status].plural.toLowerCase() : null;
  return (
    <GlassCard className="flex flex-col gap-3 p-3 sm:p-4">
      <div className="flex flex-wrap items-center justify-between gap-2">
        <h2 className="font-heading text-base font-semibold text-ink">
          Destinatarios{total != null ? ` · ${formatDecimal(total)}` : ''}
          {filterLabel ? ` ${filterLabel}` : ''}
        </h2>
        {status && (
          <Button type="button" variant="ghost" className="min-h-11 md:min-h-8" onClick={() => onStatusChange(null)}>
            Quitar filtro
          </Button>
        )}
      </div>
      {lacksPersonalData(rows) && <PersonalDataNotice />}
      <RecipientsTable
        rows={rows}
        variant="detail"
        zone={settings?.sendWindow?.zone}
        isLoading={recipients.isLoading}
        isError={recipients.isError}
        onRetry={recipients.refetch}
        emptyState={<EmptyState title="No hay destinatarios en este estado" />}
      />
      <Paginador page={page} totalPages={totalPagesOf(recipients.data)} onChange={onPageChange} />
    </GlassCard>
  );
}

function WaveContent({ wave, settings, access, status, page, onStatusChange, onPageChange }) {
  const controller = useWaveActionController({ wave, access });
  return (
    <>
      <WaveHeader wave={wave} controller={controller} />
      <WaveAccessNotice access={access} />
      <WaveBanner wave={wave} sendWindow={settings?.sendWindow} />
      <WaveSummary wave={wave} />
      <StatusFilters wave={wave} status={status} onChange={onStatusChange} />
      <RecipientsSection wave={wave} settings={settings} status={status} page={page} onStatusChange={onStatusChange} onPageChange={onPageChange} />
      <WaveMobileBar controller={controller} />
      <WaveActionDialog controller={controller} settings={settings} />
    </>
  );
}

export function InvitationWaveDetailPage() {
  const { waveId } = useParams();
  const basePath = useInvitationsBasePath();
  const access = useInvitationWaveAccess();
  const [params, setParams] = useSearchParams();
  const status = RECIPIENT_STATUS_ORDER.includes(params.get('status')) ? params.get('status') : null;
  const page = Math.max(1, Number(params.get('page')) || 1);
  const waveQuery = useInvitationWave(waveId, { enabled: access.read });
  const settings = useInvitationWaveSettings({ enabled: access.read });

  function update(nextStatus, nextPage) {
    const next = {};
    if (nextStatus) next.status = nextStatus;
    if (nextPage > 1) next.page = String(nextPage);
    setParams(next);
  }

  if (!access.read || waveQuery.error?.status === 403) {
    return (
      <PageContainer>
        <BackLink to={basePath} />
        <NoAccessState permission={READ_PERMISSION} />
      </PageContainer>
    );
  }
  if (waveQuery.isLoading) {
    return (
      <PageContainer>
        <BackLink to={basePath} />
        <div role="status" aria-label="Cargando" className="flex flex-col gap-4">
          <Skeleton className="h-8 w-64" />
          <div className="grid grid-cols-2 gap-2.5 sm:grid-cols-3 lg:grid-cols-6">
            {Array.from({ length: 6 }, (_, index) => (
              <Skeleton key={index} className="h-16 w-full rounded-xl" />
            ))}
          </div>
          <Skeleton className="h-64 w-full" />
        </div>
      </PageContainer>
    );
  }
  if (waveQuery.error?.code === 'INVITATION_WAVE_NOT_FOUND') return <WaveNotFound error={waveQuery.error} basePath={basePath} />;
  if (waveQuery.isError) {
    return (
      <PageContainer>
        <BackLink to={basePath} />
        <LoadErrorState error={waveQuery.error} onRetry={waveQuery.refetch} resource="la oleada" />
      </PageContainer>
    );
  }

  return (
    <PageContainer>
      <BackLink to={basePath} />
      <WaveContent
        wave={waveQuery.data}
        settings={settings.data}
        access={access}
        status={status}
        page={page}
        onStatusChange={(next) => update(next, 1)}
        onPageChange={(next) => update(status, next)}
      />
    </PageContainer>
  );
}
