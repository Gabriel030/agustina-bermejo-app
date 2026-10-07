import { describe, expect, it } from 'vitest';
import { screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { http, HttpResponse } from 'msw';
import { server } from '@/test/mocks/server';
import { renderWithProviders } from '@/test/renderWithProviders';
import { PanelCorrelativas } from './PanelCorrelativas';

const API = 'http://localhost:8080';

const PLAN = [
  { id: 134, subjectCode: 'S001', subjectName: 'Anatomía', curricularYear: 1, status: 'ACTIVE' },
  { id: 135, subjectCode: 'S003', subjectName: 'Fisiología', curricularYear: 2, status: 'ACTIVE' },
  { id: 136, subjectCode: 'S004', subjectName: 'Bioquímica', curricularYear: 1, status: 'ACTIVE' },
];

function mockPanel({ onPrerequisite, onCorequisite } = {}) {
  server.use(
    http.get(`${API}/programs/9/subjects`, () => HttpResponse.json(PLAN)),
    http.get(`${API}/program-subjects/135/prerequisites`, () => HttpResponse.json([])),
    http.get(`${API}/program-subjects/135/corequisites`, () => HttpResponse.json([])),
    http.post(`${API}/program-subjects/135/prerequisites`, onPrerequisite ?? (() => HttpResponse.json({ id: 1 }, { status: 201 }))),
    http.post(`${API}/program-subjects/135/corequisites`, onCorequisite ?? (() => HttpResponse.json({ id: 1 }, { status: 201 }))),
  );
}

function mount() {
  return renderWithProviders(
    <PanelCorrelativas open onClose={() => {}} programId="9" programSubjectId={135} subjectName="Fisiología" />,
  );
}

describe('PanelCorrelativas', () => {
  it('picks the prerequisite from the other subjects of the plan instead of typing an internal id', async () => {
    let body = null;
    mockPanel({
      onPrerequisite: async ({ request }) => {
        body = await request.json();
        return HttpResponse.json({ id: 1 }, { status: 201 });
      },
    });
    const user = userEvent.setup();
    mount();

    await user.click(await screen.findByRole('button', { name: '+ Agregar correlativa' }));
    expect(screen.queryByPlaceholderText(/ID de la relación/)).not.toBeInTheDocument();
    expect(screen.queryByRole('button', { name: /Fisiología/ })).not.toBeInTheDocument();

    await user.type(screen.getByPlaceholderText('Buscar materia del plan…'), 'anat');
    expect(screen.queryByRole('button', { name: /Bioquímica/ })).not.toBeInTheDocument();
    await user.click(screen.getByRole('button', { name: /Anatomía/ }));
    await user.click(screen.getByRole('button', { name: 'Agregar' }));

    await waitFor(() => expect(body).toEqual({ prerequisiteProgramSubjectId: 134, requirementType: 'APPROVED' }));
  });

  it('explains why the backend rejected the prerequisite', async () => {
    mockPanel({ onPrerequisite: () => HttpResponse.json({ code: 'CIRCULAR_PREREQUISITE' }, { status: 400 }) });
    const user = userEvent.setup();
    mount();

    await user.click(await screen.findByRole('button', { name: '+ Agregar correlativa' }));
    await user.click(await screen.findByRole('button', { name: /Anatomía/ }));
    await user.click(screen.getByRole('button', { name: 'Agregar' }));

    expect(await screen.findByText('Esa correlativa armaría un ciclo con otra que ya existe.')).toBeInTheDocument();
  });

  it('picks the corequisite from the plan too', async () => {
    let body = null;
    mockPanel({
      onCorequisite: async ({ request }) => {
        body = await request.json();
        return HttpResponse.json({ id: 1 }, { status: 201 });
      },
    });
    const user = userEvent.setup();
    mount();

    await user.click(await screen.findByRole('button', { name: '+ Agregar co-requisito' }));
    await user.click(await screen.findByRole('button', { name: /Bioquímica/ }));
    await user.click(screen.getByRole('button', { name: 'Agregar' }));

    await waitFor(() => expect(body).toEqual({ corequisiteProgramSubjectId: 136 }));
  });
});
