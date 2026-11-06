import { describe, expect, it } from 'vitest';
import { render, screen, within } from '@testing-library/react';
import { ChangesTable } from './ChangesTable';

describe('ChangesTable', () => {
  it('renders today, next and difference for each row, including non money values', () => {
    render(
      <ChangesTable
        rows={[
          { key: 'days', label: 'Plazo', today: '5 días', next: '8 días', difference: '+3 días' },
          { key: 'flag', label: 'Email', today: 'No', next: 'Sí', difference: 'Se activa', unchanged: false },
          { key: 'same', label: 'Otro', today: '1 día', next: '1 día', difference: 'Sin cambios', unchanged: true },
        ]}
      />,
    );
    const days = screen.getByRole('group', { name: 'Plazo' });
    expect(within(days).getByText('5 días')).toBeInTheDocument();
    expect(within(days).getByText('8 días')).toBeInTheDocument();
    expect(within(days).getByText('+3 días')).toBeInTheDocument();
    expect(within(screen.getByRole('group', { name: 'Email' })).getByText('Se activa')).toBeInTheDocument();
    expect(within(screen.getByRole('group', { name: 'Otro' })).getByText('Sin cambios')).toBeInTheDocument();
  });

  it('shows a dash when a side has no value', () => {
    render(<ChangesTable rows={[{ key: 'x', label: 'Aviso', today: null, next: 'Nuevo', difference: 'Aviso nuevo' }]} />);
    expect(within(screen.getByRole('group', { name: 'Aviso' })).getByText('—')).toBeInTheDocument();
  });
});
