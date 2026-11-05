import { describe, expect, it } from 'vitest';
import { screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { http, HttpResponse } from 'msw';
import { axe } from '@/test/axe';
import { server } from '@/test/mocks/server';
import { renderWithProviders } from '@/test/renderWithProviders';
import { PrimerDirector } from './PrimerDirector';

const API = 'http://localhost:8080';
const STATUS_URL = `${API}/platform/provisioning/first-director/status`;
const PROVISION_URL = `${API}/platform/provisioning/first-director`;

const noDirector = () => http.get(STATUS_URL, () => HttpResponse.json({ directorExists: false, institutionName: null }));

async function fillForm(user) {
  await user.type(await screen.findByLabelText('Nombre de la institución'), 'Instituto Nuevo Horizonte');
  await user.type(screen.getByLabelText('Nombre'), 'Carolina');
  await user.type(screen.getByLabelText('Apellido'), 'Ledesma');
  await user.type(screen.getByLabelText('Email'), 'carolina.ledesma@nuevohorizonte.edu.ar');
  await user.type(screen.getByLabelText('Número de documento'), '28441907');
  await user.type(screen.getByLabelText('Motivo'), 'Alta inicial del instituto contratado');
}

describe('PrimerDirector accessibility', () => {
  it('formWithErrorsHasNoAxeViolations', async () => {
    server.use(noDirector());
    const user = userEvent.setup();
    const { container } = renderWithProviders(<PrimerDirector />);

    await user.click(await screen.findByRole('button', { name: 'Crear Director' }));

    expect(await axe(container)).toHaveNoViolations();
  });

  it('openConfirmationDialogHasNoAxeViolations', async () => {
    server.use(noDirector());
    const user = userEvent.setup();
    renderWithProviders(<PrimerDirector />);
    await fillForm(user);

    await user.click(screen.getByRole('button', { name: 'Crear Director' }));
    const dialog = await screen.findByRole('dialog');

    expect(await axe(dialog)).toHaveNoViolations();
  });

  it('successViewHasNoAxeViolations', async () => {
    server.use(
      noDirector(),
      http.post(PROVISION_URL, () => HttpResponse.json({ institutionId: 1, institutionCreated: true, directorPersonId: 7, directorEmail: 'carolina.ledesma@nuevohorizonte.edu.ar' }, { status: 201 })),
    );
    const user = userEvent.setup();
    const { container } = renderWithProviders(<PrimerDirector />);
    await fillForm(user);
    await user.click(screen.getByRole('button', { name: 'Crear Director' }));
    await user.click(await screen.findByRole('button', { name: 'Sí, crear Director' }));
    await screen.findByText('Director creado');

    expect(await axe(container)).toHaveNoViolations();
  });
});
