import { formatInstantDate, formatInstantTime } from './invitationWaveMath';

function nextRunPhrase(nextRunAt, zone, now) {
  const day = formatInstantDate(nextRunAt, zone);
  const time = formatInstantTime(nextRunAt, zone);
  return day === formatInstantDate(now.toISOString(), zone) ? `hoy a las ${time}` : `el ${day} a las ${time}`;
}

export function bannerText(wave, sendWindow, now = new Date()) {
  const zone = sendWindow?.zone;
  if (wave.status === 'DRAFT') return 'Borrador: todavía no se envió ningún mail. Revisá los destinatarios y, cuando estés lista o listo, iniciala.';
  if (wave.status === 'RUNNING') {
    const next = wave.nextRunAt
      ? `la próxima tanda de ${wave.batchSize} mails sale ${nextRunPhrase(wave.nextRunAt, zone, now)}.`
      : 'los mails salen de a tandas dentro del horario de envío.';
    return `En curso: ${next} Cada persona recibe un enlace para elegir su contraseña.`;
  }
  if (wave.status === 'PAUSED') return 'Pausada: no se envía nada hasta que la reanudes.';
  if (wave.status === 'COMPLETED') return 'Completada: se procesaron todos los destinatarios.';
  const when = wave.cancelledAt ? ` el ${formatInstantDate(wave.cancelledAt, zone)}` : '';
  return `Cancelada${when}. Los pendientes quedaron liberados y se pueden invitar en otra oleada. Los mails ya enviados no se retiran y sus enlaces siguen vigentes.`;
}
