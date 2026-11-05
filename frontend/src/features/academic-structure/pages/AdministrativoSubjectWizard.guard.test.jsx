import { describe, expect, it, vi } from 'vitest';
import { screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { http, HttpResponse } from 'msw';
import { Routes, Route, useLocation } from 'react-router-dom';
import { server } from '@/test/mocks/server';
import { renderWithProviders } from '@/test/renderWithProviders';
import { AdministrativoSubjectWizard } from './AdministrativoSubjectWizard';

vi.setConfig({ testTimeout: 20000 });

const API = 'http://localhost:8080';

function Bank() {
  return <span data-testid="bank">{useLocation().search}</span>;
}

function mount(route) {
  return renderWithProviders(
    <Routes>
      <Route path="/administrativo/gestion-academica/nueva" element={<AdministrativoSubjectWizard />} />
      <Route path="/administrativo/gestion-academica/:id" element={<AdministrativoSubjectWizard />} />
      <Route path="/administrativo/gestion-academica" element={<Bank />} />
    </Routes>,
    { route },
  );
}

describe('AdministrativoSubjectWizard — exit protection', () => {
  it('leaves a new untouched subject without asking', async () => {
    const user = userEvent.setup();
    mount('/administrativo/gestion-academica/nueva');

    await user.click(screen.getByRole('button', { name: /Volver al banco/ }));

    expect(await screen.findByTestId('bank')).toHaveTextContent('');
  });

  it('asks before leaving with edits and returns to the tab of the subject status', async () => {
    server.use(
      http.get(`${API}/subjects/5`, () =>
        HttpResponse.json({
          id: 5,
          code: 'ANAT',
          name: 'Anatomía',
          description: null,
          weeklyHours: 4,
          weeklyHoursLab: null,
          rejectionReason: null,
          gradeMode: 'NUMERIC',
          maxFinalExamAttempts: 3,
          status: 'DRAFT',
        }),
      ),
    );
    const user = userEvent.setup();
    mount('/administrativo/gestion-academica/5');

    await user.type(await screen.findByDisplayValue('Anatomía'), ' I');
    await user.click(screen.getByRole('button', { name: /Volver al banco/ }));
    expect(await screen.findByRole('alertdialog', { name: '¿Salir sin guardar?' })).toBeInTheDocument();

    await user.click(screen.getByRole('button', { name: 'Salir sin guardar' }));
    expect(await screen.findByTestId('bank')).toHaveTextContent('?section=materias&tab=DRAFT');
  });
});
