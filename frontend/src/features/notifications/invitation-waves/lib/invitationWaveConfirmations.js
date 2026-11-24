import { formatDecimal } from '@/lib/formatNumber';
import { windowLabel } from './invitationWaveMath';

function rhythm(wave) {
  return `${formatDecimal(wave.batchSize)} mails cada ${formatDecimal(wave.intervalMinutes)} min`;
}

function dailyCap(wave) {
  return `${formatDecimal(wave.dailyCap)} por día`;
}

export function buildConfirmation(key, wave, settings) {
  const open = windowLabel(settings?.sendWindow);
  const toRelease = wave.eligibleCount + wave.queuedTotal + wave.failedTotal;
  const alreadySent = wave.sentTotal + wave.activatedTotal;
  if (key === 'start') {
    return {
      title: `Iniciar «${wave.name}»`,
      description: 'Vas a enviar mails de activación a las personas pendientes. Cada una elige su propia contraseña; no se envían contraseñas.',
      items: [
        ['Destinatarios pendientes', formatDecimal(wave.eligibleCount)],
        ['Ritmo', rhythm(wave)],
        ['Tope diario', dailyCap(wave)],
        ...(open ? [['Horario de envío', open]] : []),
      ],
      confirmLabel: 'Iniciar oleada',
      confirmVariant: 'primary',
    };
  }
  if (key === 'resume') {
    return {
      title: `Reanudar «${wave.name}»`,
      description: 'La oleada sigue enviando desde donde quedó. Cada persona recibe un mail de activación.',
      items: [
        ['Quedan por enviar', formatDecimal(wave.eligibleCount + wave.queuedTotal)],
        ['Ritmo', rhythm(wave)],
        ['Tope diario', dailyCap(wave)],
      ],
      confirmLabel: 'Reanudar oleada',
      confirmVariant: 'primary',
    };
  }
  if (key === 'cancel') {
    return {
      title: `Cancelar «${wave.name}»`,
      description: 'Esto no se puede deshacer.',
      items: [
        ['Se liberan', `${formatDecimal(toRelease)} pendientes, en cola o fallidos`],
        ['Motivo que queda', 'Oleada cancelada'],
        ['Mails ya enviados', `${formatDecimal(alreadySent)} (no se retiran)`],
      ],
      extra: 'Las personas liberadas quedan excluidas de esta oleada y se pueden invitar en otra. Los enlaces ya enviados siguen vigentes.',
      confirmLabel: 'Cancelar oleada',
      confirmVariant: 'destructive',
      cancelLabel: 'Volver',
    };
  }
  return {
    title: 'Reintentar envíos fallidos',
    description: 'Vamos a volver a intentar enviar el mail de activación a quienes falló.',
    items: [
      ['Envíos a reintentar', formatDecimal(wave.failedTotal)],
      ['Ritmo', rhythm(wave)],
    ],
    confirmLabel: `Reintentar ${formatDecimal(wave.failedTotal)}`,
    confirmVariant: 'primary',
  };
}
