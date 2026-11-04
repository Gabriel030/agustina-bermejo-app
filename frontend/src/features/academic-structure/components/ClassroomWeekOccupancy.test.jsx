import { beforeEach, describe, expect, it, vi } from 'vitest';
import { render, screen } from '@testing-library/react';

const occupancy = vi.hoisted(() => ({ current: null }));

vi.mock('@/features/academic-structure/hooks/useClassrooms', () => ({
  useClassroomOccupancy: () => occupancy.current,
}));

import { ClassroomWeekOccupancy } from './ClassroomWeekOccupancy';

const slot = (overrides) => ({
  dayOfWeek: 'MONDAY',
  startTime: '08:00',
  endTime: '10:00',
  overlapsInDates: true,
  section: { id: 9, kind: 'COURSE', name: 'Inglés I', sectionName: 'A', code: 'ING1-2027C1-01' },
  term: { id: 7, name: 'Ciclo 2027', startDate: '2027-03-01T00:00:00Z', endDate: '2027-12-01T00:00:00Z' },
  ...overrides,
});

function renderWeek(slots, proposal) {
  occupancy.current = { data: { slots }, isLoading: false, isError: false };
  render(
    <ClassroomWeekOccupancy classroomId={1} classroomName="Aula 1" termId="7" proposal={proposal} onBack={() => {}} />,
  );
}

describe('ClassroomWeekOccupancy', () => {
  beforeEach(() => {
    occupancy.current = null;
  });

  it('flags a slot that overlaps the proposal in the same day and period', () => {
    renderWeek([slot({})], { dayOfWeek: 'MONDAY', startTime: '09:00', endTime: '11:00' });
    expect(screen.getByText('Choca con tu franja')).toBeInTheDocument();
  });

  it('does not flag a slot from another period or another day', () => {
    renderWeek(
      [slot({ overlapsInDates: false }), slot({ dayOfWeek: 'TUESDAY' })],
      { dayOfWeek: 'MONDAY', startTime: '09:00', endTime: '11:00' },
    );
    expect(screen.queryByText('Choca con tu franja')).not.toBeInTheDocument();
    expect(screen.getByText('Otro período')).toBeInTheDocument();
    expect(screen.getByText(/Ciclo 2027 \(01\/03\/2027 al 01\/12\/2027\)/)).toBeInTheDocument();
  });

  it('does not flag slots that only touch the proposal', () => {
    renderWeek([slot({})], { dayOfWeek: 'MONDAY', startTime: '10:00', endTime: '11:00' });
    expect(screen.queryByText('Choca con tu franja')).not.toBeInTheDocument();
  });
});
