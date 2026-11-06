import { formatearFechaHora } from '@/lib/avisos';

export function announcementStatus(announcement, now = new Date()) {
  if (announcement.status === 'SCHEDULED') return { label: 'Programado', tone: 'accent' };
  if (announcement.expiresAt && new Date(`${announcement.expiresAt}T23:59:59`) < now) return { label: 'Vencido', tone: 'neutral' };
  return { label: 'Enviado', tone: 'success' };
}

export function dispatchDate(announcement) {
  return formatearFechaHora(announcement.status === 'SCHEDULED' ? announcement.scheduledFor : announcement.sentAt);
}

export function canInspectReaders(announcement) {
  return announcement.status === 'SENT' && announcement.readTracked;
}

export function readPercent(announcement) {
  return announcement.recipientCount ? Math.round((announcement.readCount / announcement.recipientCount) * 100) : 0;
}

export function splitRecipients(recipients) {
  return {
    unread: recipients.filter((recipient) => !recipient.readAt),
    read: recipients.filter((recipient) => recipient.readAt),
  };
}

export function matchesRecipient(recipient, term) {
  const needle = term.trim().toLowerCase();
  if (!needle) return true;
  return `${recipient.name} ${recipient.file}`.toLowerCase().includes(needle);
}
