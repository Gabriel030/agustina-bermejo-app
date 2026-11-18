import { useEffect, useMemo, useRef, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { toast } from 'sonner';
import { CheckCircle2 } from 'lucide-react';
import { Notice } from '@/components/Notice';
import { PageContainer } from '@/components/PageContainer';
import { GlassCard } from '@/components/GlassCard';
import { Stepper } from '@/components/Stepper';
import { Button } from '@/components/ui/button';
import { Dialog, DialogContent, DialogDescription, DialogFooter, DialogHeader, DialogTitle } from '@/components/ui/dialog';
import { describeError } from '@/api/describeError';
import { useAuth } from '@/context/AuthContext';
import { ChannelStep } from '../components/ChannelStep';
import { AudienceStep } from '../components/AudienceStep';
import { MessageStep } from '../components/MessageStep';
import { ReviewStep } from '../components/ReviewStep';
import { useAudiencePreview, useConfirmBroadcast, useCreateBroadcast, useSegmentCatalog } from '../hooks/useBroadcasts';
import { CHANNEL_LABELS, INITIAL_WIZARD_STATE, buildCreatePayload, clearDraft, estimateSendingDays, loadDraft, saveDraft, toSegmentSpec, validateMessage } from '../lib/broadcastWizard';

const STEPS = [
  { key: 1, label: 'Canal y categoría' },
  { key: 2, label: 'Destinatarios' },
  { key: 3, label: 'Mensaje' },
  { key: 4, label: 'Revisión' },
];

export function BroadcastWizard() {
  const { usuario } = useAuth();
  const navigate = useNavigate();
  const userId = usuario?.id;
  const [state, setState] = useState(() => ({ ...INITIAL_WIZARD_STATE, ...(loadDraft(userId) ?? {}) }));
  const [visited, setVisited] = useState([1]);
  const [leaving, setLeaving] = useState(false);
  const [created, setCreated] = useState(false);
  const [confirming, setConfirming] = useState(false);
  const draft = useRef(null);
  const submitting = useRef(false);
  const [emptyAudience, setEmptyAudience] = useState(null);
  const { data: catalog = [] } = useSegmentCatalog();
  const create = useCreateBroadcast();
  const confirm = useConfirmBroadcast();
  const catalogKeys = useMemo(() => catalog.map((entry) => entry.key), [catalog]);
  const segmentSpec = useMemo(() => toSegmentSpec(state.criteria, catalogKeys), [state.criteria, catalogKeys]);
  const previewParams = {
    channel: state.channel,
    category: 'INSTITUTIONAL',
    segmentSpec,
    excludeMissingVariables: state.excludeMissingVariables,
    page: 0,
    size: 10,
  };
  const previewQuery = useAudiencePreview(previewParams);
  const preview = previewQuery.data;

  useEffect(() => {
    if (!created) saveDraft(userId, state);
  }, [state, userId, created]);

  const patch = (changes) => setState((current) => ({ ...current, ...changes }));
  const patchCriteria = (changes) => setState((current) => ({ ...current, criteria: { ...current.criteria, ...changes } }));

  const canAdvance = {
    1: state.name.trim() !== '',
    2: (preview?.included ?? 0) > 0,
    3: Object.keys(validateMessage(state)).length === 0,
    4: true,
  }[state.step];

  function goTo(step) {
    setVisited((previous) => [...new Set([...previous, step])]);
    patch({ step });
  }

  async function confirmBroadcast() {
    if (submitting.current) return;
    submitting.current = true;
    try {
      const payload = buildCreatePayload(state, segmentSpec);
      const fingerprint = JSON.stringify(payload);
      if (draft.current?.fingerprint !== fingerprint) {
        const created = await create.mutateAsync(payload);
        draft.current = { id: created.id, fingerprint };
      }
      try {
        await confirm.mutateAsync(draft.current.id);
      } catch (error) {
        if (error.code !== 'ILLEGAL_BROADCAST_STATUS_TRANSITION') throw error;
      }
      clearDraft(userId);
      setConfirming(false);
      setCreated(true);
    } catch (error) {
      setConfirming(false);
      if (error.code === 'EMPTY_AUDIENCE') {
        draft.current = null;
        setEmptyAudience(error);
        return;
      }
      toast.error(describeError(error, { fallback: 'No pudimos confirmar el envío. Probá de nuevo.' }).message);
    } finally {
      submitting.current = false;
    }
  }

  function chooseAnotherSegment() {
    setEmptyAudience(null);
    draft.current = null;
    goTo(2);
  }

  if (created) {
    return (
      <PageContainer>
        <GlassCard className="flex flex-col items-center gap-3 p-8 text-center">
          <CheckCircle2 size={40} className="text-success" />
          <h1 className="font-heading text-2xl font-bold text-ink">Envío confirmado</h1>
          <p className="text-size-13 text-ink-soft">Los correos empiezan a salir hoy. Podés cancelar los pendientes desde el detalle del envío.</p>
          <Button variant="outline" onClick={() => navigate('/')}>
            Volver al inicio
          </Button>
        </GlassCard>
      </PageContainer>
    );
  }

  return (
    <PageContainer>
      <h1 className="font-heading text-2xl font-bold text-ink">Nuevo envío</h1>
      <GlassCard className="p-5">
        <Stepper pasos={STEPS} pasoActual={state.step} pasosVisitados={visited} onStepClick={goTo} />
      </GlassCard>

      {state.step === 1 && <ChannelStep state={state} onChange={patch} />}
      {state.step === 2 && (
        <AudienceStep
          state={state}
          onCriteria={patchCriteria}
          catalogKeys={catalogKeys}
          preview={preview}
          previewState={{
            isError: previewQuery.isError,
            error: previewQuery.error,
            onRetry: previewQuery.refetch,
            recalculating: previewQuery.recalculating,
          }}
        />
      )}
      {state.step === 3 && <MessageStep state={state} onChange={patch} preview={preview} onExcludeMissing={() => patch({ excludeMissingVariables: true })} />}
      {state.step === 4 && <ReviewStep state={state} preview={preview} />}
      {emptyAudience && (
        <Notice tone="danger" title="No hay destinatarios elegibles.">
          {describeError(emptyAudience).message}
        </Notice>
      )}

      <div className="flex flex-wrap items-center justify-end gap-2.5">
        <Button type="button" variant="outline" className="mr-auto" onClick={() => setLeaving(true)}>
          Salir sin guardar
        </Button>
        {state.step > 1 && (
          <Button type="button" variant="outline" onClick={() => goTo(state.step - 1)}>
            Atrás
          </Button>
        )}
        {state.step < 4 ? (
          <Button type="button" variant="gradient" disabled={!canAdvance} onClick={() => goTo(state.step + 1)}>
            Siguiente
          </Button>
        ) : (
          <Button type="button" variant="gradient" onClick={emptyAudience ? chooseAnotherSegment : () => setConfirming(true)}>
            {emptyAudience ? 'Elegir otro segmento' : 'Confirmar envío'}
          </Button>
        )}
      </div>

      <Dialog open={confirming} onOpenChange={setConfirming}>
        <DialogContent>
          <DialogHeader>
            <DialogTitle>Confirmar envío</DialogTitle>
            <DialogDescription>
              {`Se enviará a ${preview?.included ?? 0} personas en ${estimateSendingDays(preview?.included ?? 0)} días. Esta acción no se puede deshacer, pero podés cancelar los pendientes.`}
            </DialogDescription>
            <dl className="grid grid-cols-[100px_1fr] gap-1 text-size-13">
              <dt className="text-ink-soft">Canal</dt>
              <dd className="text-ink">{CHANNEL_LABELS[state.channel]}</dd>
              <dt className="text-ink-soft">Categoría</dt>
              <dd className="text-ink">Institucional</dd>
            </dl>
          </DialogHeader>
          <DialogFooter>
            <Button variant="outline" autoFocus onClick={() => setConfirming(false)}>
              Volver
            </Button>
            <Button variant="gradient" loading={create.isPending || confirm.isPending} onClick={confirmBroadcast}>
              Enviar
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>

      <Dialog open={leaving} onOpenChange={setLeaving}>
        <DialogContent>
          <DialogHeader>
            <DialogTitle>Salir del asistente</DialogTitle>
            <DialogDescription>Guardamos un borrador en este navegador. Podés retomarlo cuando vuelvas.</DialogDescription>
          </DialogHeader>
          <DialogFooter>
            <Button variant="outline" autoFocus onClick={() => setLeaving(false)}>
              Seguir editando
            </Button>
            <Button variant="outline" onClick={() => navigate('/')}>
              Salir y conservar borrador
            </Button>
            <Button
              variant="destructive"
              onClick={() => {
                clearDraft(userId);
                setCreated(false);
                navigate('/');
              }}
            >
              Descartar borrador
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>
    </PageContainer>
  );
}
