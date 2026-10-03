import { describe, expect, it } from 'vitest';
import { screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { http, HttpResponse } from 'msw';
import { server } from '@/test/mocks/server';
import { renderWithProviders } from '@/test/renderWithProviders';
import { DirectorEsquemasEvaluacion } from './DirectorEsquemasEvaluacion';

const API = 'http://localhost:8080';

const ESQUEMAS = [
  { id: 1, name: 'Promocional', examCountExpected: 2, hasFinal: true, minRegularGrade: 4, minApprovalGrade: 6, promotionMinGrade: 8, aggregationMethod: 'SIMPLE_AVERAGE', inUse: true },
  { id: 5, name: 'Reglamento borrador', examCountExpected: 3, hasFinal: true, minRegularGrade: 4, minApprovalGrade: 6, promotionMinGrade: null, aggregationMethod: 'SIMPLE_AVERAGE', inUse: false },
];

function mockearEsquemas() {
  server.use(http.get(`${API}/evaluation-schemes`, () => HttpResponse.json(ESQUEMAS)));
}

describe('DirectorEsquemasEvaluacion', () => {
  it('crea el esquema con el método de promedio explícito', async () => {
    mockearEsquemas();
    let enviado;
    server.use(
      http.post(`${API}/evaluation-schemes`, async ({ request }) => {
        enviado = await request.json();
        return HttpResponse.json({ id: 9, ...enviado, inUse: false }, { status: 201 });
      }),
    );
    const user = userEvent.setup();
    renderWithProviders(<DirectorEsquemasEvaluacion />);

    await user.type(await screen.findByPlaceholderText('Ej: Reglamento 2027...'), 'Reglamento 2027');
    await user.click(screen.getByRole('button', { name: 'Crear esquema' }));

    await waitFor(() => expect(enviado).toBeDefined());
    expect(enviado.aggregationMethod).toBe('SIMPLE_AVERAGE');
  });

  it('no deja crear un esquema con notas fuera de 0 a 10', async () => {
    mockearEsquemas();
    const user = userEvent.setup();
    renderWithProviders(<DirectorEsquemasEvaluacion />);

    await user.type(await screen.findByPlaceholderText('Ej: Reglamento 2027...'), 'Inválido');
    const aprobar = screen.getByLabelText('Nota para aprobar el final');
    await user.clear(aprobar);
    await user.type(aprobar, '15');

    expect(screen.getByText('Las notas van de 0 a 10.')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Crear esquema' })).toBeDisabled();
  });

  it('no deja una nota de promoción menor que la de aprobación', async () => {
    mockearEsquemas();
    const user = userEvent.setup();
    renderWithProviders(<DirectorEsquemasEvaluacion />);

    await user.type(await screen.findByPlaceholderText('Ej: Reglamento 2027...'), 'Promo baja');
    await user.click(within(screen.getByText('¿Es promocional?').parentElement).getByRole('button', { name: 'Sí' }));
    await user.type(screen.getByPlaceholderText('Nota de corte'), '3');

    expect(screen.getByText('La nota de promoción no puede ser menor que la de aprobación ni que la de regularidad.')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Crear esquema' })).toBeDisabled();
  });

  it('un esquema editable se puede editar y se guarda con PUT', async () => {
    mockearEsquemas();
    let enviado;
    server.use(
      http.put(`${API}/evaluation-schemes/5`, async ({ request }) => {
        enviado = await request.json();
        return HttpResponse.json({ id: 5, ...enviado, inUse: false });
      }),
    );
    const user = userEvent.setup();
    renderWithProviders(<DirectorEsquemasEvaluacion />);

    await user.click(await screen.findByRole('button', { name: 'Editar Reglamento borrador' }));
    expect(screen.getByText('Editar esquema')).toBeInTheDocument();
    const nombre = screen.getByDisplayValue('Reglamento borrador');
    await user.clear(nombre);
    await user.type(nombre, 'Reglamento 2027');
    await user.click(screen.getByRole('button', { name: 'Guardar cambios' }));

    await screen.findByText('Nuevo esquema');
    expect(enviado.name).toBe('Reglamento 2027');
    expect(enviado.examCountExpected).toBe(3);
    expect(screen.queryByRole('button', { name: 'Editar Promocional' })).not.toBeInTheDocument();
  });
});
