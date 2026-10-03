import { beforeEach, describe, expect, it, vi } from 'vitest';
import { screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { http, HttpResponse } from 'msw';
import { Routes, Route } from 'react-router-dom';
import { server } from '@/test/mocks/server';
import { renderWithProviders } from '@/test/renderWithProviders';
import { AdministrativoSubjectWizard } from './AdministrativoSubjectWizard';

const toastMock = vi.hoisted(() => ({ success: vi.fn(), error: vi.fn() }));
vi.mock('sonner', async (importOriginal) => ({ ...(await importOriginal()), toast: toastMock }));

const API = 'http://localhost:8080';

vi.setConfig({ testTimeout: 20000 });

function subject(extra = {}) {
  return {
    id: 5,
    code: 'ANAT',
    name: 'Anatomía',
    description: null,
    weeklyHours: 4,
    weeklyHoursLab: null,
    gradeMode: 'NUMERIC',
    maxFinalExamAttempts: 3,
    status: 'ACTIVE',
    rejectionReason: null,
    ...extra,
  };
}

function mount(route) {
  return renderWithProviders(
    <Routes>
      <Route path="/administrativo/gestion-academica/nueva" element={<AdministrativoSubjectWizard />} />
      <Route path="/administrativo/gestion-academica/:id" element={<AdministrativoSubjectWizard />} />
    </Routes>,
    { route },
  );
}

async function goToTeachingStep(user) {
  await user.click(await screen.findByRole('button', { name: /Siguiente/ }));
  await user.click(await screen.findByRole('button', { name: /Siguiente/ }));
}

describe('AdministrativoSubjectWizard', () => {
  beforeEach(() => {
    toastMock.success.mockClear();
    toastMock.error.mockClear();
  });

  it('keeps an active subject active after saving changes instead of offering to send it for approval', async () => {
    server.use(
      http.get(`${API}/subjects/5`, () => HttpResponse.json(subject())),
      http.put(`${API}/subjects/5`, () => HttpResponse.json(subject({ maxFinalExamAttempts: 4 }))),
    );
    const user = userEvent.setup();
    mount('/administrativo/gestion-academica/5');

    await goToTeachingStep(user);
    await user.click(screen.getByRole('button', { name: 'Guardar cambios' }));

    expect(await screen.findByText('Materia activa')).toBeInTheDocument();
    expect(screen.queryByText('Borrador guardado')).not.toBeInTheDocument();
    expect(screen.queryByRole('button', { name: /Enviar a aprobación/ })).not.toBeInTheDocument();
  });

  it('updates the draft it already saved when the user goes back and saves again', async () => {
    const posts = [];
    const puts = [];
    server.use(
      http.post(`${API}/subjects`, async ({ request }) => {
        posts.push(await request.json());
        return HttpResponse.json(subject({ id: 9, code: 'QGA', name: 'Química', status: 'DRAFT' }), { status: 201 });
      }),
      http.put(`${API}/subjects/9`, async ({ request }) => {
        const body = await request.json();
        puts.push(body);
        return HttpResponse.json(subject({ id: 9, code: body.code, name: body.name, status: 'DRAFT' }));
      }),
    );
    const user = userEvent.setup();
    mount('/administrativo/gestion-academica/nueva');

    await user.type(screen.getByPlaceholderText('MAT1'), 'QGA');
    await user.type(screen.getByPlaceholderText('Matemática I'), 'Química');
    await user.type(screen.getByPlaceholderText('3'), '4');
    await goToTeachingStep(user);
    await user.click(screen.getByRole('combobox'));
    await user.click(await screen.findByRole('option', { name: 'Numérica (1–10)' }));
    await user.click(screen.getByRole('button', { name: 'Guardar borrador' }));
    await screen.findByText('Borrador guardado');

    for (let i = 0; i < 3; i += 1) {
      await user.click(screen.getByRole('button', { name: /Anterior/ }));
    }
    const codeInput = screen.getByPlaceholderText('MAT1');
    await user.clear(codeInput);
    await user.type(codeInput, 'QHA');
    await goToTeachingStep(user);
    await user.click(screen.getByRole('button', { name: 'Guardar borrador' }));

    await waitFor(() => expect(puts).toHaveLength(1));
    expect(posts).toHaveLength(1);
    expect(puts[0].code).toBe('QHA');
  });

  it('locks the code of an approved subject because it can no longer change', async () => {
    server.use(http.get(`${API}/subjects/5`, () => HttpResponse.json(subject())));
    mount('/administrativo/gestion-academica/5');

    expect(await screen.findByDisplayValue('ANAT')).toBeDisabled();
  });

  it('shows the reason given by the director on a rejected subject', async () => {
    server.use(
      http.get(`${API}/subjects/5`, () =>
        HttpResponse.json(subject({ status: 'REJECTED', rejectionReason: 'Falta la bibliografía obligatoria' })),
      ),
    );
    mount('/administrativo/gestion-academica/5');

    expect(await screen.findByText('Falta la bibliografía obligatoria')).toBeInTheDocument();
    expect(screen.getByText(/Dirección rechazó esta materia/)).toBeInTheDocument();
  });

  it('explains a duplicate code or name instead of a generic error', async () => {
    server.use(
      http.post(`${API}/subjects`, () => HttpResponse.json({ code: 'SUBJECT_ALREADY_EXISTS' }, { status: 409 })),
    );
    const user = userEvent.setup();
    mount('/administrativo/gestion-academica/nueva');

    await user.type(screen.getByPlaceholderText('MAT1'), 'S113');
    await user.type(screen.getByPlaceholderText('Matemática I'), 'Repetida');
    await user.type(screen.getByPlaceholderText('3'), '4');
    await goToTeachingStep(user);
    await user.click(screen.getByRole('combobox'));
    await user.click(await screen.findByRole('option', { name: 'Numérica (1–10)' }));
    await user.click(screen.getByRole('button', { name: 'Guardar borrador' }));

    await waitFor(() =>
      expect(toastMock.error).toHaveBeenCalledWith('Ya existe una materia con ese código o ese nombre.'),
    );
  });
});
