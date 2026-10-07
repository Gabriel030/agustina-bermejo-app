import { useEffect, useState } from 'react';
import { useNavigate, useParams, useSearchParams } from 'react-router-dom';
import { toast } from 'sonner';
import { GlassCard } from '@/components/GlassCard';
import { PageContainer } from '@/components/PageContainer';
import { Skeleton } from '@/components/Skeleton';
import { StatusPill } from '@/components/StatusPill';
import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';
import { SeccionDestinatarios } from '../components/SeccionDestinatarios';
import { SeccionMensaje } from '../components/SeccionMensaje';
import { EnvioYCaducidad } from '../components/EnvioYCaducidad';
import { TiraDeConsecuencias } from '../components/TiraDeConsecuencias';
import { RosterSegmento } from '../components/RosterSegmento';
import { useAvisos, useDestinatariosDeAviso } from '../hooks/useAvisos';
import { useSegmentosDeAudiencia } from '../hooks/useSegmentosDeAudiencia';
import { usePreviewDeAudiencia } from '../hooks/usePreviewDeAudiencia';
import { useEditarAviso } from '../hooks/useEditarAviso';
import { aPayload, desdeAviso, SPEC_INICIAL } from '../lib/formularioDeAviso';
import { formatearFechaHora } from '@/lib/avisos';

export function AvisoEditar() {
  const { sectionId, avisoId } = useParams();
  const [searchParams] = useSearchParams();
  const navigate = useNavigate();
  const tipo = searchParams.get('type') === 'COURSE' ? 'COURSE' : 'PROGRAM';
  const esCurso = tipo === 'COURSE';

  const { data: avisos, isLoading } = useAvisos(sectionId, tipo);
  const aviso = (avisos ?? []).find((a) => String(a.id) === avisoId);
  const programado = aviso?.status === 'SCHEDULED';

  const [valores, setValores] = useState(null);
  const [spec, setSpec] = useState(SPEC_INICIAL);
  const [envioAbierto, setEnvioAbierto] = useState(false);
  const [rosterAbierto, setRosterAbierto] = useState(false);

  useEffect(() => {
    if (aviso && valores === null) setValores(desdeAviso(aviso));
  }, [aviso, valores]);

  const { data: segmentos, isLoading: cargandoSegmentos } = useSegmentosDeAudiencia(
    programado ? sectionId : undefined,
    tipo,
  );
  const { data: preview, recalculando } = usePreviewDeAudiencia(sectionId, tipo, programado ? spec : SPEC_INICIAL);
  const destinatarios = useDestinatariosDeAviso(programado ? undefined : avisoId);
  const editarAviso = useEditarAviso(sectionId, tipo, avisoId);

  const volverA = `/docente/mis-comisiones/${sectionId}?tab=avisos${esCurso ? '&type=COURSE' : ''}`;

  if (isLoading || !aviso || !valores) {
    return (
      <PageContainer>
        <Skeleton className="h-10" />
        <Skeleton className="h-80" />
      </PageContainer>
    );
  }

  async function guardar() {
    try {
      const payload = aPayload({ valores, spec, esCurso });
      await editarAviso.mutateAsync({ ...payload, audience: programado ? payload.audience : null });
      toast.success(programado ? 'Cambios guardados' : 'Aviso actualizado: les volvió a llegar la notificación');
      navigate(volverA);
    } catch (error) {
      toast.error(error.message ?? 'No pudimos guardar los cambios. Probá de nuevo.');
    }
  }

  return (
    <PageContainer>
      <div className="flex flex-wrap items-center gap-3">
        <h1 className="font-heading text-2xl font-bold text-ink">Editar aviso</h1>
        {programado ? (
          <StatusPill tone="warning">Programado</StatusPill>
        ) : (
          <StatusPill tone="success">Enviado el {formatearFechaHora(aviso.sentAt)}</StatusPill>
        )}
      </div>

      {programado && !esCurso ? (
        <SeccionDestinatarios
          segmentos={segmentos}
          cargandoSegmentos={cargandoSegmentos}
          spec={spec}
          onChange={setSpec}
          preview={preview}
          recalculando={recalculando}
          onVerDestinatarios={() => setRosterAbierto(true)}
        />
      ) : (
        <GlassCard className="flex flex-col gap-3 p-5">
          <h2 className="font-heading text-[15px] font-semibold text-ink">Destinatarios</h2>
          <Input readOnly className="cursor-default" value={aviso.audienceLabel} />
          <span className="text-[11.5px] text-ink-faint">
            {esCurso
              ? 'Un curso suelto no tiene esquema de evaluación, así que el aviso le llega a todo el curso.'
              : 'Un aviso ya enviado no cambia de destinatarios: la lista quedó congelada en el momento del envío.'}
          </span>
          {!esCurso && (
            <Button type="button" variant="link" className="h-auto w-fit p-0" onClick={() => setRosterAbierto(true)}>
              Ver quiénes lo recibieron
            </Button>
          )}
        </GlassCard>
      )}

      <SeccionMensaje
        valores={valores}
        onChange={setValores}
        mostrarHintPrivacidad={!esCurso && aviso.audienceLabel !== 'Toda la comisión'}
        alcanzados={programado ? preview?.count ?? 0 : destinatarios.data?.length ?? 0}
        programado={programado}
      >
        <div className="h-px bg-border" />
        <EnvioYCaducidad
          abierto={envioAbierto}
          onAbrir={() => setEnvioAbierto(true)}
          valores={valores}
          onChange={setValores}
          yaEnviadoEl={programado ? null : aviso.sentAt}
        />
        <TiraDeConsecuencias
          alcanzados={programado ? preview?.count ?? 0 : destinatarios.data?.length ?? 0}
          total={preview?.totalInSection ?? destinatarios.data?.length ?? 0}
          sinCorregir={!!preview?.hasUngradedInstances}
          scheduledFor={valores.cuando === 'programar' ? valores.scheduledFor : null}
          reNotifica={!programado}
        />
      </SeccionMensaje>

      <div className="flex flex-wrap items-center justify-end gap-2.5">
        <Button type="button" variant="outline" className="mr-auto" onClick={() => navigate(volverA)}>
          Cancelar
        </Button>
        <Button
          type="button"
          variant="gradient"
          loading={editarAviso.isPending}
          disabled={!valores.title.trim() || !valores.body.trim()}
          onClick={guardar}
        >
          {programado ? 'Guardar cambios' : 'Guardar y volver a notificar'}
        </Button>
      </div>

      <RosterSegmento
        abierto={rosterAbierto}
        titulo={programado ? preview?.label ?? aviso.audienceLabel : aviso.audienceLabel}
        descripcion={
          programado
            ? `Hoy serían ${preview?.count ?? 0} alumnos; el grupo se calcula al enviarse.`
            : `Les llegó a ${destinatarios.data?.length ?? '—'} alumnos.`
        }
        destinatarios={programado ? preview?.students : destinatarios.data}
        segmentKey={programado ? spec.key : null}
        isLoading={!programado && destinatarios.isLoading}
        isError={!programado && destinatarios.isError}
        onClose={() => setRosterAbierto(false)}
      />
    </PageContainer>
  );
}
