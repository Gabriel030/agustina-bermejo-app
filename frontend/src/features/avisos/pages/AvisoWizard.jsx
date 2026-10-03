import { useMemo, useState } from 'react';
import { useNavigate, useParams, useSearchParams } from 'react-router-dom';
import { toast } from 'sonner';
import { GlassCard } from '@/components/GlassCard';
import { PageContainer } from '@/components/PageContainer';
import { Stepper } from '@/components/Stepper';
import { StatusPill } from '@/components/StatusPill';
import { Button } from '@/components/ui/button';
import { Dialog, DialogContent, DialogDescription, DialogFooter, DialogHeader, DialogTitle } from '@/components/ui/dialog';
import { SeccionDestinatarios } from '../components/SeccionDestinatarios';
import { SeccionMensaje } from '../components/SeccionMensaje';
import { EnvioYCaducidad } from '../components/EnvioYCaducidad';
import { TiraDeConsecuencias } from '../components/TiraDeConsecuencias';
import { RosterSegmento } from '../components/RosterSegmento';
import { useSegmentosDeAudiencia } from '../hooks/useSegmentosDeAudiencia';
import { usePreviewDeAudiencia } from '../hooks/usePreviewDeAudiencia';
import { useCrearAviso } from '../hooks/useCrearAviso';
import { aPayload, SPEC_INICIAL, VALORES_INICIALES } from '../lib/formularioDeAviso';

const PASOS = [
  { key: 'destinatarios', label: 'Destinatarios' },
  { key: 'mensaje', label: 'Mensaje' },
];

export function AvisoWizard() {
  const { sectionId } = useParams();
  const [searchParams] = useSearchParams();
  const navigate = useNavigate();
  const tipo = searchParams.get('type') === 'COURSE' ? 'COURSE' : 'PROGRAM';
  const esCurso = tipo === 'COURSE';

  const [paso, setPaso] = useState(esCurso ? 'mensaje' : 'destinatarios');
  const [pasosVisitados, setPasosVisitados] = useState(['destinatarios']);
  const [spec, setSpec] = useState(SPEC_INICIAL);
  const [valores, setValores] = useState(VALORES_INICIALES);
  const [envioAbierto, setEnvioAbierto] = useState(false);
  const [rosterAbierto, setRosterAbierto] = useState(false);
  const [confirmarSalida, setConfirmarSalida] = useState(false);

  const { data: segmentos, isLoading: cargandoSegmentos } = useSegmentosDeAudiencia(sectionId, tipo);
  const { data: preview, recalculando } = usePreviewDeAudiencia(sectionId, tipo, spec);
  const crearAviso = useCrearAviso(sectionId, tipo);

  const volverA = `/docente/mis-comisiones/${sectionId}?tab=avisos${esCurso ? '&type=COURSE' : ''}`;
  const alcanzados = preview?.count ?? 0;
  const programado = valores.cuando === 'programar';
  const sucio = useMemo(
    () => valores.title.trim() !== '' || valores.body.trim() !== '' || spec.key !== SPEC_INICIAL.key,
    [valores.title, valores.body, spec.key],
  );
  const puedePublicar =
    valores.title.trim() !== '' &&
    valores.body.trim() !== '' &&
    (esCurso || alcanzados > 0) &&
    (!programado || !!valores.scheduledFor);

  function salir() {
    if (sucio) {
      setConfirmarSalida(true);
      return;
    }
    navigate(volverA);
  }

  function irAMensaje() {
    setPasosVisitados((previos) => [...new Set([...previos, 'destinatarios', 'mensaje'])]);
    setPaso('mensaje');
  }

  async function publicar() {
    try {
      await crearAviso.mutateAsync(aPayload({ valores, spec, esCurso }));
      toast.success(programado ? 'Aviso programado' : 'Aviso publicado');
      navigate(volverA);
    } catch (error) {
      toast.error(error.message ?? 'No pudimos publicar el aviso. Probá de nuevo.');
    }
  }

  return (
    <PageContainer>
      <h1 className="font-heading text-2xl font-bold text-ink">Nuevo aviso</h1>

      {!esCurso && (
        <GlassCard className="p-5">
          <Stepper
            pasos={PASOS}
            pasoActual={paso}
            pasosVisitados={pasosVisitados}
            onStepClick={(key) => setPaso(key)}
          />
        </GlassCard>
      )}

      {paso === 'destinatarios' ? (
        <>
          <SeccionDestinatarios
            segmentos={segmentos}
            cargandoSegmentos={cargandoSegmentos}
            spec={spec}
            onChange={setSpec}
            preview={preview}
            recalculando={recalculando}
            onVerDestinatarios={() => setRosterAbierto(true)}
          />
          <div className="flex flex-wrap items-center justify-end gap-2.5">
            <Button type="button" variant="outline" className="mr-auto" onClick={salir}>
              Cancelar
            </Button>
            <Button type="button" variant="gradient" disabled={alcanzados === 0} onClick={irAMensaje}>
              Siguiente
            </Button>
          </div>
        </>
      ) : (
        <>
          {esCurso ? (
            <div className="flex flex-wrap items-center gap-2.5 rounded-2xl border border-brand/20 bg-accent-soft/55 px-4 py-2.5 text-[12.5px]">
              <span className="text-[11px] font-bold uppercase tracking-[0.05em] text-ink-faint">Para</span>
              <span className="font-bold text-ink">Todo el curso</span>
            </div>
          ) : (
            <div className="flex flex-wrap items-center gap-2.5 rounded-2xl border border-brand/20 bg-accent-soft/55 px-4 py-2.5 text-[12.5px]">
              <span className="text-[11px] font-bold uppercase tracking-[0.05em] text-ink-faint">Para</span>
              <span className="font-bold text-ink">{preview?.label ?? 'Toda la comisión'}</span>
              <StatusPill tone="accent">{alcanzados === 1 ? '1 alumno' : `${alcanzados} alumnos`}</StatusPill>
              <Button type="button" variant="link" className="h-auto p-0" onClick={() => setRosterAbierto(true)}>
                Ver quiénes son
              </Button>
              <span className="text-ink-faint">·</span>
              <Button type="button" variant="link" className="h-auto p-0" onClick={() => setPaso('destinatarios')}>
                Cambiar grupo
              </Button>
            </div>
          )}

          <SeccionMensaje
            valores={valores}
            onChange={setValores}
            mostrarHintPrivacidad={!esCurso && spec.key !== 'ALL'}
            alcanzados={alcanzados}
            programado={programado}
          >
            <div className="h-px bg-border" />
            <EnvioYCaducidad
              abierto={envioAbierto}
              onAbrir={() => setEnvioAbierto(true)}
              valores={valores}
              onChange={setValores}
            />
            <TiraDeConsecuencias
              alcanzados={esCurso ? preview?.totalInSection ?? 0 : alcanzados}
              total={preview?.totalInSection ?? 0}
              sinCorregir={!!preview?.hasUngradedInstances}
              scheduledFor={programado ? valores.scheduledFor : null}
            />
          </SeccionMensaje>

          <div className="flex flex-wrap items-center justify-end gap-2.5">
            {!esCurso && (
              <Button type="button" variant="outline" className="mr-auto" onClick={() => setPaso('destinatarios')}>
                Atrás
              </Button>
            )}
            <Button type="button" variant="outline" className={esCurso ? 'mr-auto' : ''} onClick={salir}>
              Cancelar
            </Button>
            <Button
              type="button"
              variant="gradient"
              disabled={!puedePublicar}
              loading={crearAviso.isPending}
              onClick={publicar}
            >
              {programado ? 'Programar envío' : 'Publicar aviso'}
            </Button>
          </div>
        </>
      )}

      <RosterSegmento
        abierto={rosterAbierto}
        titulo={preview?.label ?? 'Destinatarios'}
        descripcion={`${alcanzados} de los ${preview?.totalInSection ?? 0} alumnos de la comisión.`}
        destinatarios={preview?.students}
        segmentKey={spec.key}
        onClose={() => setRosterAbierto(false)}
      />

      <Dialog open={confirmarSalida} onOpenChange={(abierto) => !abierto && setConfirmarSalida(false)}>
        <DialogContent>
          <DialogHeader>
            <DialogTitle>Salir sin publicar</DialogTitle>
            <DialogDescription>Escribiste un aviso que todavía no mandaste. Si salís, se pierde.</DialogDescription>
          </DialogHeader>
          <DialogFooter>
            <Button variant="outline" autoFocus onClick={() => setConfirmarSalida(false)}>
              Seguir escribiendo
            </Button>
            <Button variant="destructive" onClick={() => navigate(volverA)}>
              Salir y descartar
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>
    </PageContainer>
  );
}
