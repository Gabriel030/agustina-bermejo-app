import { ArrowLeft } from 'lucide-react';
import { Link, useNavigate } from 'react-router-dom';
import { LoadErrorState } from '@/components/LoadErrorState';
import { PageContainer } from '@/components/PageContainer';
import { Skeleton } from '@/components/Skeleton';
import { Button } from '@/components/ui/button';
import { NoAccessState } from '../components/NoAccessState';
import { WaveForm } from '../components/WaveForm';
import { notifyInvitationError } from '../hooks/useWaveActionController';
import { useInvitationsBasePath } from '../hooks/useInvitationsBasePath';
import { useCreateInvitationWave, useImportBatches, useInvitationWaveAccess, useInvitationWaveSettings } from '../hooks/useInvitationWaves';

const MANAGE_PERMISSION = 'notification.manage_invitation_waves';
const TITLE = 'Nueva oleada';
const DESCRIPTION = 'Elegí a quiénes invitar y a qué ritmo. Al crearla queda como borrador: todavía no se envía ningún mail.';

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

export function InvitationWaveCreatePage() {
  const navigate = useNavigate();
  const basePath = useInvitationsBasePath();
  const access = useInvitationWaveAccess();
  const canCreate = access.read && access.manage;
  const settings = useInvitationWaveSettings({ enabled: canCreate });
  const batches = useImportBatches({ enabled: canCreate });
  const create = useCreateInvitationWave();

  if (!canCreate) {
    return (
      <PageContainer>
        <BackLink to={basePath} />
        <PageContainer title={TITLE}>
          <NoAccessState permission={MANAGE_PERMISSION} />
        </PageContainer>
      </PageContainer>
    );
  }

  async function submit(payload) {
    try {
      const wave = await create.mutateAsync(payload);
      navigate(`${basePath}/${wave.id}/vista-previa`, { replace: true });
    } catch (err) {
      notifyInvitationError(err, access, 'manage');
      throw err;
    }
  }

  let content;
  if (settings.isLoading || batches.isLoading) {
    content = (
      <div className="flex flex-col gap-3" role="status" aria-label="Cargando">
        <Skeleton className="h-12 w-full" />
        <Skeleton className="h-12 w-full" />
        <Skeleton className="h-12 w-full" />
      </div>
    );
  } else if (settings.isError || batches.isError) {
    const failed = settings.isError ? settings : batches;
    content = (
      <LoadErrorState
        error={failed.error}
        onRetry={() => {
          settings.refetch();
          batches.refetch();
        }}
        resource="los datos para crear la oleada"
        distinguishForbidden
      />
    );
  } else {
    content = (
      <WaveForm
        settings={settings.data}
        batches={batches.data}
        pending={create.isPending}
        onSubmit={submit}
        onCancel={() => navigate(basePath)}
      />
    );
  }

  return (
    <PageContainer>
      <BackLink to={basePath} />
      <PageContainer title={TITLE} description={DESCRIPTION}>
        {content}
      </PageContainer>
    </PageContainer>
  );
}
