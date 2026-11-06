import { describe, expect, it, vi } from 'vitest';
import { screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { http, HttpResponse } from 'msw';
import { server } from '@/test/mocks/server';
import { renderWithProviders } from '@/test/renderWithProviders';
import { DirectorInstitutionSettings } from './DirectorInstitutionSettings';

const API = 'http://localhost:8080';
const URL = `${API}/institution/academic-settings`;

const toast = vi.hoisted(() => ({ success: vi.fn(), error: vi.fn() }));
vi.mock('sonner', async (importOriginal) => ({ ...(await importOriginal()), toast }));

const SAVED = { justificationDeadlineBusinessDays: 5, evaluationConfigDueDays: 30, announcementEmailEnabled: false };

async function openScreen() {
  server.use(http.get(URL, () => HttpResponse.json(SAVED)));
  const user = userEvent.setup();
  renderWithProviders(<DirectorInstitutionSettings />);
  const justification = await screen.findByLabelText('Plazo para justificar inasistencias');
  return { user, justification };
}

describe('DirectorInstitutionSettings', () => {
  it('shows the three settings with their ranges and no changes bar actions enabled', async () => {
    await openScreen();
    expect(screen.getByLabelText('Plazo para configurar la evaluación')).toHaveValue('30');
    expect(screen.getByText('Entre 0 y 60')).toBeInTheDocument();
    expect(screen.getByText('Entre 1 y 120')).toBeInTheDocument();
    expect(screen.getByRole('switch', { name: 'Avisos no urgentes por email' })).not.toBeChecked();
    expect(screen.getByRole('button', { name: 'Revisar cambios' })).toBeDisabled();
    expect(screen.getByRole('button', { name: 'Descartar' })).toBeDisabled();
  });

  it('counts unsaved changes and discards them', async () => {
    const { user, justification } = await openScreen();
    await user.clear(justification);
    await user.type(justification, '7');
    await user.click(screen.getByRole('switch', { name: 'Avisos no urgentes por email' }));
    expect(screen.getByText('2 cambios sin guardar')).toBeInTheDocument();
    await user.click(screen.getByRole('button', { name: 'Descartar' }));
    expect(justification).toHaveValue('5');
    expect(screen.getByRole('switch', { name: 'Avisos no urgentes por email' })).not.toBeChecked();
    expect(screen.getByText('Sin cambios por guardar')).toBeInTheDocument();
  });

  it('shows the range error only after typing and blocks the review without clamping the value', async () => {
    const { user, justification } = await openScreen();
    expect(screen.queryByRole('alert')).not.toBeInTheDocument();
    await user.clear(justification);
    await user.type(justification, '99');
    expect(screen.getByRole('alert')).toHaveTextContent('Entre 0 y 60');
    expect(justification).toHaveValue('99');
    expect(screen.getByRole('button', { name: 'Revisar cambios' })).toBeDisabled();
  });

  it('rejects empty and non integer values', async () => {
    const { user, justification } = await openScreen();
    await user.clear(justification);
    expect(screen.getByRole('alert')).toHaveTextContent('Ingresá un número');
    await user.type(justification, '2.5');
    expect(screen.getByRole('alert')).toHaveTextContent('Solo números enteros');
  });

  it('rejects zero for the evaluation deadline', async () => {
    const { user } = await openScreen();
    const evaluation = screen.getByLabelText('Plazo para configurar la evaluación');
    await user.clear(evaluation);
    await user.type(evaluation, '0');
    expect(screen.getByRole('alert')).toHaveTextContent('Entre 1 y 120');
  });

  it('reviews the diff and saves the exact payload, then confirms with a toast', async () => {
    const { user, justification } = await openScreen();
    let sent;
    server.use(
      http.put(URL, async ({ request }) => {
        sent = await request.json();
        return HttpResponse.json(sent);
      }),
      http.get(URL, () => HttpResponse.json(sent ?? SAVED)),
    );
    await user.clear(justification);
    await user.type(justification, '8');
    await user.click(screen.getByRole('switch', { name: 'Avisos no urgentes por email' }));
    await user.click(screen.getByRole('button', { name: 'Revisar cambios' }));

    const deadline = screen.getByRole('group', { name: 'Plazo para justificar inasistencias' });
    expect(within(deadline).getByText('5 días hábiles')).toBeInTheDocument();
    expect(within(deadline).getByText('8 días hábiles')).toBeInTheDocument();
    expect(within(deadline).getByText('+3 días')).toBeInTheDocument();
    const email = screen.getByRole('group', { name: 'Avisos no urgentes por email' });
    expect(within(email).getByText('Se activa')).toBeInTheDocument();
    expect(within(screen.getByRole('group', { name: 'Plazo para configurar la evaluación' })).getByText('Sin cambios')).toBeInTheDocument();

    await user.click(screen.getByRole('button', { name: 'Confirmar y guardar' }));
    await waitFor(() => expect(toast.success).toHaveBeenCalledWith('Ajustes guardados'));
    expect(sent).toEqual({ justificationDeadlineBusinessDays: 8, evaluationConfigDueDays: 30, announcementEmailEnabled: true });
    expect(await screen.findByText('Sin cambios por guardar')).toBeInTheDocument();
    expect(screen.getByLabelText('Plazo para justificar inasistencias')).toHaveValue('8');
  });

  it('goes back from the confirmation keeping the draft', async () => {
    const { user, justification } = await openScreen();
    await user.clear(justification);
    await user.type(justification, '9');
    await user.click(screen.getByRole('button', { name: 'Revisar cambios' }));
    await user.click(screen.getByRole('button', { name: 'Volver' }));
    expect(screen.getByLabelText('Plazo para justificar inasistencias')).toHaveValue('9');
  });

  it('keeps the draft and reports the failure when saving fails', async () => {
    const { user, justification } = await openScreen();
    server.use(http.put(URL, () => new HttpResponse(null, { status: 500 })));
    await user.clear(justification);
    await user.type(justification, '8');
    await user.click(screen.getByRole('button', { name: 'Revisar cambios' }));
    await user.click(screen.getByRole('button', { name: 'Confirmar y guardar' }));
    await waitFor(() => expect(toast.error).toHaveBeenCalled());
    expect(screen.getByRole('button', { name: 'Confirmar y guardar' })).toBeEnabled();
  });

  it('shows an error state and retries the load', async () => {
    let calls = 0;
    server.use(
      http.get(URL, () => {
        calls += 1;
        return calls === 1 ? new HttpResponse(null, { status: 500 }) : HttpResponse.json(SAVED);
      }),
    );
    const user = userEvent.setup();
    renderWithProviders(<DirectorInstitutionSettings />);
    await user.click(await screen.findByRole('button', { name: 'Reintentar' }));
    expect(await screen.findByLabelText('Plazo para justificar inasistencias')).toHaveValue('5');
  });
});
