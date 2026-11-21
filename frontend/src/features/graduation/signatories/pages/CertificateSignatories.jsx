import { useState } from 'react';
import { Lock, Plus } from 'lucide-react';
import { PageContainer } from '@/components/PageContainer';
import { GlassCard } from '@/components/GlassCard';
import { EmptyState } from '@/components/EmptyState';
import { LoadErrorState } from '@/components/LoadErrorState';
import { Notice } from '@/components/Notice';
import { Skeleton } from '@/components/Skeleton';
import { Button } from '@/components/ui/button';
import { useAuth } from '@/context/AuthContext';
import { SignatoryAccordionItem } from '@/features/graduation/signatories/components/SignatoryAccordionItem';
import { SignatoryForm } from '@/features/graduation/signatories/components/SignatoryForm';
import { useCertificateSignatories } from '@/features/graduation/signatories/hooks/useCertificateSignatories';

function ReadOnlyNotice() {
  return (
    <Notice>
      <Lock aria-hidden="true" className="mr-1.5 inline size-3.5 align-text-bottom" />
      <b className="font-bold">Solo lectura.</b> Los firmantes los administra el Director.
    </Notice>
  );
}

export function CertificateSignatories() {
  const { usuario } = useAuth();
  const isDirector = (usuario?.roles ?? []).includes('DIRECTOR');
  const [openId, setOpenId] = useState(null);
  const [creating, setCreating] = useState(false);
  const signatories = useCertificateSignatories();

  const rows = signatories.data?.content ?? [];
  const forbidden = signatories.error?.status === 403;

  function startCreating() {
    setOpenId(null);
    setCreating(true);
  }

  const action = isDirector && !creating && !signatories.isError && (
    <Button type="button" variant="primary" onClick={startCreating}>
      <Plus aria-hidden="true" className="size-4" />
      Nuevo firmante
    </Button>
  );

  function renderBody() {
    if (signatories.isPending) {
      return (
        <div role="status" aria-label="Cargando firmantes" className="flex flex-col gap-3">
          <Skeleton className="h-14 w-full" />
          <Skeleton className="h-14 w-full" />
          <Skeleton className="h-14 w-full" />
        </div>
      );
    }
    if (forbidden) {
      return (
        <GlassCard className="flex flex-1 flex-col p-12" role="alert">
          <EmptyState icon="🔒" title="No tenés permiso para ver los firmantes" description="Pedile acceso al Director del instituto." />
        </GlassCard>
      );
    }
    if (signatories.isError) {
      return <LoadErrorState error={signatories.error} onRetry={signatories.refetch} resource="los firmantes" />;
    }
    if (rows.length === 0 && !creating) {
      return (
        <GlassCard className="flex flex-1 flex-col p-8">
          <EmptyState
            icon="✍️"
            title="Todavía no hay firmantes cargados"
            description={
              isDirector
                ? 'Sin firmantes no se pueden emitir certificados ni constancias. Cargá al primero con su rol, su firma escaneada y los documentos que puede firmar.'
                : 'Sin firmantes no se pueden emitir certificados ni constancias. Pedile al Director que cargue los firmantes.'
            }
            action={
              isDirector && (
                <Button type="button" variant="primary" onClick={startCreating}>
                  Crear el primer firmante
                </Button>
              )
            }
          />
        </GlassCard>
      );
    }
    return (
      <div className="flex flex-col gap-4">
        {creating && (
          <GlassCard className="flex flex-col gap-1 p-4">
            <h2 className="font-heading text-size-16 font-semibold text-ink">Nuevo firmante</h2>
            <SignatoryForm signatory={null} canEdit onClose={() => setCreating(false)} />
          </GlassCard>
        )}
        {rows.length > 0 && (
          <GlassCard className="flex flex-col gap-1 p-2">
            {rows.map((signatory) => (
              <SignatoryAccordionItem
                key={signatory.id}
                signatory={signatory}
                canEdit={isDirector}
                open={openId === signatory.id}
                onOpenChange={(open) => setOpenId(open ? signatory.id : null)}
              />
            ))}
          </GlassCard>
        )}
      </div>
    );
  }

  return (
    <PageContainer
      title="Firmantes"
      description="Quiénes firman los certificados y constancias y qué documentos puede firmar cada uno."
      action={action || undefined}
    >
      {!isDirector && !signatories.isError && <ReadOnlyNotice />}
      {renderBody()}
    </PageContainer>
  );
}
