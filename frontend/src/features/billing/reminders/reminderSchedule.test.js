import { describe, expect, it } from 'vitest';
import {
  buildNotice,
  describeNotice,
  diffSchedules,
  diffSettingValues,
  inheritsAllOverrides,
  localViolations,
  pickEffective,
} from './reminderSchedule';

const overrides = {
  suspensionNoticeEnabled: null,
  postWithdrawalSummaryMonths: null,
  emailWindowStart: null,
  emailWindowEnd: null,
  dailyEmailCap: null,
  contactListDaysOverdue: null,
  contactListOverdueInstallments: null,
};

const beforeDue = buildNotice({ noticeKey: 'a', messageType: 'BEFORE_DUE_REMINDER', days: 3, channels: ['IN_APP', 'EMAIL'] });
const afterDue = buildNotice({ noticeKey: 'b', messageType: 'AFTER_DUE_REMINDER', days: 5, channels: ['EMAIL'] });
const base = { notices: [beforeDue, afterDue], sendingOverrides: overrides };

describe('reminderSchedule', () => {
  it('stores days before a milestone as a negative offset and after as positive', () => {
    expect(beforeDue.offsetDays).toBe(-3);
    expect(beforeDue.milestone).toBe('DUE_DATE');
    expect(afterDue.offsetDays).toBe(5);
  });

  it('describes the timing in plain Spanish', () => {
    expect(describeNotice(beforeDue)).toBe('3 días antes del vencimiento · App y Mail');
    expect(describeNotice(buildNotice({ messageType: 'AFTER_DUE_REMINDER', days: 1, channels: ['EMAIL'], repetition: { everyDays: 7, until: 'SUSPENSION' } }))).toBe(
      '1 día después del vencimiento · Mail · Se repite cada 7 días hasta la suspensión',
    );
  });

  it('reports no changes for an untouched draft', () => {
    expect(diffSchedules(base, structuredClone(base))).toEqual([]);
  });

  it('classifies added, edited and removed notices', () => {
    const added = buildNotice({ noticeKey: 'c', messageType: 'BEFORE_SUSPENSION_REMINDER', days: 2, channels: ['EMAIL'] });
    const edited = { ...afterDue, offsetDays: 9 };
    const rows = diffSchedules(base, { notices: [edited, added], sendingOverrides: overrides });
    expect(rows.map((row) => [row.key, row.kind])).toEqual([
      ['b', 'edited'],
      ['c', 'added'],
      ['a', 'removed'],
    ]);
  });

  it('treats channel order as irrelevant', () => {
    const reordered = { ...beforeDue, channels: ['EMAIL', 'IN_APP'] };
    expect(diffSchedules(base, { notices: [reordered, afterDue], sendingOverrides: overrides })).toEqual([]);
  });

  it('lists a sending override that stops inheriting', () => {
    const rows = diffSchedules(base, { ...base, sendingOverrides: { ...overrides, dailyEmailCap: 40 } });
    expect(rows).toEqual([{ key: 'dailyEmailCap', label: 'Tope diario de mails', today: null, next: 40, kind: 'setting' }]);
  });

  it('diffs only the global settings that changed', () => {
    expect(diffSettingValues({ a: 1, b: 2 }, { a: 1, b: 3 }, ['a', 'b']).map((row) => row.key)).toEqual(['b']);
  });

  it('flags days out of range, missing channels and duplicated slots', () => {
    const tooFar = buildNotice({ noticeKey: 'x', messageType: 'AFTER_DUE_REMINDER', days: 61, channels: ['EMAIL'] });
    const noChannel = buildNotice({ noticeKey: 'y', messageType: 'BEFORE_DUE_REMINDER', days: 4, channels: [] });
    const duplicate = buildNotice({ noticeKey: 'z', messageType: 'BEFORE_DUE_REMINDER', days: 3, channels: ['EMAIL'] });
    const messages = localViolations({ notices: [beforeDue, duplicate, tooFar, noChannel] });
    expect(messages).toHaveLength(3);
    expect(messages.join(' ')).toMatch(/entre 0 y 60/);
    expect(messages.join(' ')).toMatch(/al menos un canal/);
    expect(messages.join(' ')).toMatch(/mismo día/);
  });

  it('accepts a valid schedule', () => {
    expect(localViolations(base)).toEqual([]);
  });

  it('knows when every sending override inherits', () => {
    expect(inheritsAllOverrides(overrides)).toBe(true);
    expect(inheritsAllOverrides({ ...overrides, dailyEmailCap: 10 })).toBe(false);
  });

  it('picks the latest effective version', () => {
    const versions = [
      { id: 1, status: 'SUPERSEDED', effectiveFrom: '2026-01-01' },
      { id: 2, status: 'EFFECTIVE', effectiveFrom: '2026-03-01' },
      { id: 3, status: 'PROPOSED', effectiveFrom: '2026-12-01' },
    ];
    expect(pickEffective(versions).id).toBe(2);
  });
});
