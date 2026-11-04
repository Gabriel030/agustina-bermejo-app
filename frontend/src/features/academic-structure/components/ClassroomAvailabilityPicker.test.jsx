import { describe, expect, it, vi } from 'vitest';
import { render, screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { ClassroomAvailabilityPicker } from './ClassroomAvailabilityPicker';

const occupant = (overlapsInDates) => ({
  dayOfWeek: 'MONDAY',
  startTime: '08:00',
  endTime: '10:00',
  overlapsInDates,
  section: { id: 9, kind: 'COURSE', name: 'Inglés I', sectionName: 'A', code: 'ING1-2027C1-01' },
  term: { id: 7, name: 'Ciclo 2027', startDate: '2027-03-01T00:00:00Z', endDate: '2027-12-01T00:00:00Z' },
});

const classrooms = [
  { id: 1, name: 'Aula 1', capacity: 30, available: false, overlapsInDates: true, occupant: occupant(true) },
  { id: 2, name: 'Aula 2', capacity: 25, available: true, overlapsInDates: false, occupant: occupant(false) },
  { id: 3, name: 'Aula 3', capacity: 10, available: true, capacityShortfall: 5 },
  { id: 4, name: 'Aula 4', capacity: 40, available: true },
];

function renderPicker(props = {}) {
  const handlers = { onChoose: vi.fn(), onViewWeek: vi.fn() };
  render(<ClassroomAvailabilityPicker classrooms={classrooms} seats={15} {...handlers} {...props} />);
  return handlers;
}

describe('ClassroomAvailabilityPicker', () => {
  it('lists free classrooms first and names who occupies the others', () => {
    renderPicker();
    const names = screen.getAllByRole('listitem').map((item) => item.querySelector('label').textContent);
    expect(names.map((n) => n.match(/^Aula \d/)[0])).toEqual(['Aula 4', 'Aula 2', 'Aula 1', 'Aula 3']);
    const occupied = screen.getByText('Aula 1').closest('li');
    expect(within(occupied).getByText('Ocupada')).toBeInTheDocument();
    expect(within(occupied).getByText(/ING1-2027C1-01 · Inglés I · Ciclo 2027 \(01\/03\/2027 al 01\/12\/2027\)/)).toBeInTheDocument();
    expect(within(screen.getByText('Aula 2').closest('li')).getByText('Libre en tu período')).toBeInTheDocument();
    expect(screen.getByText('Faltan 5 lugares para el cupo.')).toBeInTheDocument();
  });

  it('keeps occupied classrooms selectable', async () => {
    const user = userEvent.setup();
    const { onChoose } = renderPicker();
    await user.click(screen.getByRole('radio', { name: /Aula 1/ }));
    expect(onChoose).toHaveBeenCalledWith(classrooms[0]);
  });

  it('asks for the week of a classroom', async () => {
    const user = userEvent.setup();
    const { onViewWeek } = renderPicker();
    await user.click(within(screen.getByText('Aula 4').closest('li')).getByRole('button', { name: /Ver la semana/ }));
    expect(onViewWeek).toHaveBeenCalledWith(classrooms[3]);
  });
});
