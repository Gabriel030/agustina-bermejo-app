import { useState } from 'react';
import { toast } from 'sonner';
import { describeError } from '@/api/describeError';
import { saveBlob } from '@/lib/saveBlob';
import { ACTION_DEFINITIONS, orderedSecondary, waveActions } from '../lib/invitationWaveActions';
import { useDownloadInvitationReport, useInvitationWaveAction } from './useInvitationWaves';

export function notifyInvitationError(err, access, capability) {
  if (err?.status === 403 && capability) access.deny(capability);
  const { message } = describeError(err);
  toast.error(message, { description: err?.code ? `Código: ${err.code}` : undefined });
}

export function requeuedMessage(requeued) {
  if (requeued === 1) return '1 destinatario en cola de reintento';
  if (requeued > 0) return `${requeued} destinatarios en cola de reintento`;
  return 'No había destinatarios para reintentar ahora';
}

function successDescription(key, result) {
  if (key === 'cancel') return 'Los pendientes quedaron liberados.';
  if (key === 'retry') return requeuedMessage(result?.requeued ?? 0);
  return undefined;
}

export function useWaveActionController({ wave, access }) {
  const [confirming, setConfirming] = useState(null);
  const [runningKey, setRunningKey] = useState(null);
  const mutation = useInvitationWaveAction(wave.id);
  const download = useDownloadInvitationReport(wave.id);
  const actions = waveActions(wave, access);

  function run(key) {
    const definition = ACTION_DEFINITIONS[key];
    setRunningKey(key);
    mutation.mutate(definition.endpoint, {
      onSuccess: (result) => {
        setConfirming(null);
        toast.success(definition.success, { description: successDescription(key, result) });
      },
      onError: (err) => {
        setConfirming(null);
        notifyInvitationError(err, access, key === 'pause' ? 'manage' : 'send');
      },
      onSettled: () => setRunningKey(null),
    });
  }

  function downloadReport() {
    download.mutate(undefined, {
      onSuccess: ({ blob, filename }) => {
        saveBlob(blob, filename);
        toast.success('Reporte generado', { description: `El archivo se descarga como ${filename}.` });
      },
      onError: (err) => notifyInvitationError(err, access, 'read'),
    });
  }

  function request(item) {
    if (item.disabled || mutation.isPending) return;
    if (item.key === 'csv') downloadReport();
    else if (item.confirm) setConfirming(item.key);
    else run(item.key);
  }

  return {
    wave,
    actions,
    menuItems: orderedSecondary(actions),
    confirming,
    pending: mutation.isPending,
    runningKey,
    downloading: download.isPending,
    request,
    confirm: () => run(confirming),
    dismiss: () => setConfirming(null),
  };
}
