import { describe, expect, it } from 'vitest';
import { studentUpcomingEvents } from '@/features/workspace/lib/studentUpcomingEvents';

const NOW = new Date(2026, 10, 21, 15, 0);

function event(overrides) {
  return { id: 'e', type: 'CLASS', date: '2026-11-21', startTime: '18:00', endTime: '20:00', title: 'Clase', place: null, status: 'SCHEDULED', ...overrides };
}

describe('studentUpcomingEvents', () => {
  it('dropsATodayClassThatAlreadyEnded', () => {
    const ended = event({ id: 'ended', startTime: '10:00', endTime: '12:00' });
    const upcoming = event({ id: 'upcoming' });
    expect(studentUpcomingEvents([ended, upcoming], NOW).map((item) => item.id)).toEqual(['upcoming']);
  });

  it('keepsATodayClassThatIsStillRunning', () => {
    const running = event({ id: 'running', startTime: '14:00', endTime: '16:00' });
    expect(studentUpcomingEvents([running], NOW)).toHaveLength(1);
  });

  it('keepsAllDayEventsOfToday', () => {
    const allDay = event({ id: 'holiday', type: 'HOLIDAY', startTime: null, endTime: null });
    expect(studentUpcomingEvents([allDay], NOW)).toHaveLength(1);
  });

  it('keepsEndedLookingEventsOfFutureDays', () => {
    const tomorrow = event({ id: 'tomorrow', date: '2026-11-22', startTime: '08:00', endTime: '09:00' });
    expect(studentUpcomingEvents([tomorrow], NOW)).toHaveLength(1);
  });
});
