import { describe, expect, it, vi } from 'vitest';
import { screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { http, HttpResponse } from 'msw';
import { axe } from '@/test/axe';
import { server } from '@/test/mocks/server';
import { renderWithProviders } from '@/test/renderWithProviders';
import { BroadcastWizard } from './BroadcastWizard';

const API = 'http://localhost:8080';

vi.mock('@/context/AuthContext', () => ({ useAuth: () => ({ usuario: { id: 7 }, roles: ['DIRECTOR'] }) }));

describe('BroadcastWizard accessibility', () => {
  it('audienceStepHasNoAxeViolations', async () => {
    server.use(
      http.get('*/broadcasts/segment-catalog', () => HttpResponse.json([{ key: 'PROGRAM', parameters: [] }])),
      http.post('*/broadcasts/audience-preview', () =>
        HttpResponse.json({
          total: 2,
          included: 1,
          excludedByReason: { OPTED_OUT: 1 },
          sample: [
            { personId: 1, fullName: 'Ana Paz', addressMasked: 'an••@gmail.com', status: 'INCLUDED', exclusionReason: null },
            { personId: 2, fullName: 'Luis Sosa', addressMasked: 'lu••@gmail.com', status: 'EXCLUDED', exclusionReason: 'OPTED_OUT' },
          ],
        }),
      ),
      http.get(`${API}/programs`, () => HttpResponse.json([])),
      http.get(`${API}/subjects`, () => HttpResponse.json([])),
    );
    const { container } = renderWithProviders(<BroadcastWizard />);
    await userEvent.type(await screen.findByLabelText('Nombre interno'), 'Mesas');
    await userEvent.click(screen.getByRole('button', { name: 'Siguiente' }));
    await screen.findByText('Reciben');

    expect(await axe(container)).toHaveNoViolations();
  });

  it('messageStepAndConfirmDialogHaveNoAxeViolations', async () => {
    server.use(
      http.get('*/broadcasts/segment-catalog', () => HttpResponse.json([{ key: 'PROGRAM', parameters: [] }])),
      http.post('*/broadcasts/audience-preview', () =>
        HttpResponse.json({
          total: 1,
          included: 1,
          excludedByReason: {},
          sample: [{ personId: 1, fullName: 'Ana Paz', addressMasked: 'an••@gmail.com', status: 'INCLUDED', exclusionReason: null }],
        }),
      ),
      http.get(`${API}/message-templates`, () => HttpResponse.json([])),
      http.post(`${API}/message-templates/render-preview`, () => HttpResponse.json({ subject: 'Hola', body: 'Hola Ana' })),
      http.get(`${API}/programs`, () => HttpResponse.json([])),
      http.get(`${API}/subjects`, () => HttpResponse.json([])),
    );
    const { container } = renderWithProviders(<BroadcastWizard />);
    await userEvent.type(await screen.findByLabelText('Nombre interno'), 'Mesas');
    await userEvent.click(screen.getByRole('button', { name: 'Siguiente' }));
    await screen.findByText('Reciben');
    await waitFor(() => expect(screen.getByRole('button', { name: 'Siguiente' })).toBeEnabled());
    await userEvent.click(screen.getByRole('button', { name: 'Siguiente' }));
    await userEvent.type(await screen.findByLabelText('Asunto'), 'Hola');
    await userEvent.type(screen.getByLabelText('Mensaje'), 'Hola nombre');

    expect(await axe(container)).toHaveNoViolations();

    await userEvent.click(screen.getByRole('button', { name: 'Siguiente' }));
    await userEvent.click(await screen.findByRole('button', { name: 'Confirmar envío' }));
    const dialog = await screen.findByRole('dialog');

    expect(await axe(dialog)).toHaveNoViolations();
  });
});
