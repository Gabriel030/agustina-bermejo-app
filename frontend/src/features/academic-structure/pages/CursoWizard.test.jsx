import { describe, expect, it } from 'vitest';
import { fireEvent, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { http, HttpResponse } from 'msw';
import { server } from '@/test/mocks/server';
import { renderWithProviders } from '@/test/renderWithProviders';
import { CursoWizard } from './CursoWizard';

const API = 'http://localhost:8080';

function mockTerms() {
  server.use(http.get(`${API}/terms`, () => HttpResponse.json([{ id: 4, year: 2027, description: 'Anual' }])));
}

async function completeCourseFirstStep(user) {
  await user.type(screen.getByPlaceholderText('Ej. Curso de Excel Avanzado'), 'Excel avanzado');
  await screen.findByRole('option', { name: '2027 — Anual' });
  await user.selectOptions(screen.getAllByRole('combobox')[0], '4');
  await user.type(screen.getByPlaceholderText('Ej. 30'), '20');
  await user.click(screen.getByRole('button', { name: 'Continuar' }));
}

describe('CursoWizard', () => {
  it('marks the current step and lets the user go back to a visited step', async () => {
    const user = userEvent.setup();
    mockTerms();
    renderWithProviders(<CursoWizard />);

    expect(screen.getByRole('button', { name: 'Datos básicos' })).toHaveAttribute('aria-current', 'step');

    await completeCourseFirstStep(user);

    expect(await screen.findByRole('button', { name: 'Detalle' })).toHaveAttribute('aria-current', 'step');
    const firstStep = screen.getByRole('button', { name: 'Datos básicos' });
    expect(firstStep).toBeEnabled();

    await user.click(firstStep);

    expect(screen.getByPlaceholderText('Ej. Curso de Excel Avanzado')).toHaveValue('Excel avanzado');
  });

  it('requires the duration in months before creating the course', async () => {
    let posted = false;
    server.use(
      http.post(`${API}/courses`, () => {
        posted = true;
        return HttpResponse.json({ id: 1 }, { status: 201 });
      }),
    );
    const user = userEvent.setup();
    mockTerms();
    renderWithProviders(<CursoWizard />);
    await completeCourseFirstStep(user);

    await user.click(await screen.findByRole('button', { name: 'Crear curso' }));

    expect(await screen.findByText('La duración en meses es obligatoria y tiene que ser al menos 1.')).toBeInTheDocument();
    expect(posted).toBe(false);
  });

  it('rejects a registration window that closes before it opens', async () => {
    let posted = false;
    server.use(
      http.post(`${API}/courses`, () => {
        posted = true;
        return HttpResponse.json({ id: 1 }, { status: 201 });
      }),
    );
    const user = userEvent.setup();
    mockTerms();
    const { container } = renderWithProviders(<CursoWizard />);
    await completeCourseFirstStep(user);

    await user.type(await screen.findByPlaceholderText('Ej. 3'), '3');
    const [start, end] = container.querySelectorAll('input[type="datetime-local"]');
    fireEvent.change(start, { target: { value: '2027-02-10T00:00' } });
    fireEvent.change(end, { target: { value: '2027-01-10T00:00' } });
    await user.click(screen.getByRole('button', { name: 'Crear curso' }));

    expect(await screen.findByText('El cierre de inscripción no puede ser anterior a la apertura.')).toBeInTheDocument();
    expect(posted).toBe(false);
  });
});
