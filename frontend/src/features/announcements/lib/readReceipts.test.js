import { describe, expect, it } from 'vitest';
import { announcementStatus, canInspectReaders, matchesRecipient, readPercent, splitRecipients } from './readReceipts';

const NOW = new Date('2026-11-06T12:00:00');

describe('announcementStatus', () => {
  it('marks scheduled announcements as scheduled', () => {
    expect(announcementStatus({ status: 'SCHEDULED', expiresAt: null }, NOW).label).toBe('Programado');
  });

  it('marks sent announcements past their expiry as expired', () => {
    expect(announcementStatus({ status: 'SENT', expiresAt: '2026-11-05' }, NOW).label).toBe('Vencido');
  });

  it('keeps an announcement sent on its expiry day as sent', () => {
    expect(announcementStatus({ status: 'SENT', expiresAt: '2026-11-06' }, NOW).label).toBe('Enviado');
  });
});

describe('canInspectReaders', () => {
  it('requires a sent announcement with read tracking', () => {
    expect(canInspectReaders({ status: 'SENT', readTracked: true })).toBe(true);
    expect(canInspectReaders({ status: 'SENT', readTracked: false })).toBe(false);
    expect(canInspectReaders({ status: 'SCHEDULED', readTracked: true })).toBe(false);
  });
});

describe('readPercent', () => {
  it('rounds the share of recipients that opened it and tolerates zero recipients', () => {
    expect(readPercent({ readCount: 1, recipientCount: 3 })).toBe(33);
    expect(readPercent({ readCount: 0, recipientCount: 0 })).toBe(0);
  });
});

describe('splitRecipients', () => {
  it('separates recipients by whether they have a read date', () => {
    const recipients = [{ studentId: 1, readAt: null }, { studentId: 2, readAt: '2026-11-03T15:02:00' }];
    const { unread, read } = splitRecipients(recipients);
    expect(unread.map((r) => r.studentId)).toEqual([1]);
    expect(read.map((r) => r.studentId)).toEqual([2]);
  });
});

describe('matchesRecipient', () => {
  it('matches by name or file ignoring case and surrounding spaces', () => {
    const recipient = { name: 'Acosta, Joaquín', file: 'A-2301' };
    expect(matchesRecipient(recipient, ' acosta ')).toBe(true);
    expect(matchesRecipient(recipient, 'a-2301')).toBe(true);
    expect(matchesRecipient(recipient, 'benitez')).toBe(false);
    expect(matchesRecipient(recipient, '')).toBe(true);
  });
});
