import { useState } from 'react';
import { describe, expect, it } from 'vitest';
import { screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { renderWithProviders } from '@/test/renderWithProviders';
import { PreceptorMultiSelect } from './PreceptorMultiSelect';

function Harness({ initial = [], error }) {
  const [value, setValue] = useState(initial);
  return (
    <>
      <PreceptorMultiSelect label="Responsable" value={value} onChange={setValue} required error={error} />
      <output data-testid="value">{value.join(',')}</output>
    </>
  );
}

describe('PreceptorMultiSelect', () => {
  it('adds a preceptor from the searchable list and shows it as a chip', async () => {
    const user = userEvent.setup();
    renderWithProviders(<Harness />);

    await user.click(screen.getByRole('button', { name: 'Buscar preceptor' }));
    await user.click(await screen.findByRole('option', { name: /Marcela Quiroga/ }));

    expect(screen.getByTestId('value')).toHaveTextContent('31');
    expect(screen.getByRole('button', { name: 'Quitar a Marcela Quiroga' })).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Agregar preceptor' })).toBeInTheDocument();
  });

  it('filters the list while searching and tells when nothing matches', async () => {
    const user = userEvent.setup();
    renderWithProviders(<Harness />);

    await user.click(screen.getByRole('button', { name: 'Buscar preceptor' }));
    await screen.findByRole('option', { name: /Marcela Quiroga/ });
    await user.type(screen.getByRole('textbox', { name: 'Buscar preceptor' }), 'ferre');

    expect(screen.getAllByRole('option')).toHaveLength(1);
    expect(screen.getByRole('option', { name: /Julián Ferreyra/ })).toBeInTheDocument();

    await user.clear(screen.getByRole('textbox', { name: 'Buscar preceptor' }));
    await user.type(screen.getByRole('textbox', { name: 'Buscar preceptor' }), 'zzz');
    expect(screen.getByText('Sin resultados')).toBeInTheDocument();
  });

  it('removes a preceptor with the chip button', async () => {
    const user = userEvent.setup();
    renderWithProviders(<Harness initial={[31, 32]} />);

    await user.click(await screen.findByRole('button', { name: 'Quitar a Marcela Quiroga' }));

    expect(screen.getByTestId('value')).toHaveTextContent('32');
    expect(screen.queryByRole('button', { name: 'Quitar a Marcela Quiroga' })).not.toBeInTheDocument();
  });

  it('collapses more than four chips into a "+N más" button that expands them', async () => {
    const user = userEvent.setup();
    renderWithProviders(<Harness initial={[31, 32, 33, 34, 35, 36]} />);

    const group = screen.getByRole('group', { name: /Responsable/ });
    expect(await within(group).findByRole('button', { name: /Quitar a Marcela Quiroga/ })).toBeInTheDocument();
    expect(within(group).getAllByRole('button', { name: /^Quitar a/ })).toHaveLength(3);

    await user.click(within(group).getByRole('button', { name: '+3 más' }));

    expect(within(group).getAllByRole('button', { name: /^Quitar a/ })).toHaveLength(6);
    expect(within(group).getByRole('button', { name: 'Ver menos' })).toBeInTheDocument();
  });

  it('shows the validation error under the field', () => {
    renderWithProviders(<Harness error="Elegí al menos un preceptor." />);

    expect(screen.getByRole('alert')).toHaveTextContent('Elegí al menos un preceptor.');
  });
});
