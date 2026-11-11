import { describe, expect, it, vi } from 'vitest';
import { screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { http, HttpResponse } from 'msw';
import { Route, Routes } from 'react-router-dom';
import { errorBody } from '@/test/fixtures/errors';
import { server } from '@/test/mocks/server';
import { renderWithProviders } from '@/test/renderWithProviders';
import { DirectorOfertaCicloDetalle } from '@/features/academic-structure/pages/DirectorOfertaCicloDetalle';

const API = 'http://localhost:8080';

const cycle = {
  id: 7,
  status: 'PENDING_APPROVAL',
  termName: '1.er cuatrimestre 2027',
  programId: 3,
  programName: 'Tecnicatura en Administración',
  submittedByName: 'Marina Fernández',
  submittedAt: '2026-11-03T10:00:00',
  decidedByName: null,
  decidedAt: null,
  rejectionReason: null,
  sectionCount: 2,
};

const sections = [
  {
    id: 11,
    name: 'A',
    code: 'A',
    subjectName: 'Contabilidad I',
    capacity: 35,
    shift: 'EVENING',
    status: 'ACTIVE',
    pendingApproval: true,
    instructors: [{ name: 'Pablo Ledesma', role: 'LEAD' }],
    slots: [{ dayOfWeek: 'MONDAY', startTime: '18:00:00', endTime: '20:00:00', classroomId: 3, classroomName: 'Aula 3' }],
  },
  {
    id: 12,
    name: 'B',
    code: 'B',
    subjectName: 'Matemática',
    capacity: 30,
    shift: 'MORNING',
    status: 'ACTIVE',
    pendingApproval: true,
    instructors: [{ name: 'Irene Campos', role: 'LEAD' }],
    slots: [{ dayOfWeek: 'TUESDAY', startTime: '08:00:00', endTime: '10:00:00', classroomId: 2, classroomName: 'Aula 2' }],
  },
];

const conflict = {
  type: 'CLASSROOM',
  dayOfWeek: 'MONDAY',
  startTime: '19:00:00',
  endTime: '20:00:00',
  classroomId: 3,
  classroomName: 'Aula 3',
  instructorId: null,
  instructorName: null,
  ownSection: { sectionId: 11, sectionName: 'A', subjectName: 'Contabilidad I', cycleId: 7 },
  otherSection: { sectionId: 99, sectionName: 'A', subjectName: 'Derecho Laboral', cycleId: 7 },
};

function useBackend({ cycleBody = cycle, conflicts = { hasConflicts: false, conflicts: [] } } = {}) {
  server.use(
    http.get(`${API}/academic-offering-cycles/7`, () => HttpResponse.json(cycleBody)),
    http.get(`${API}/academic-offering-cycles/7/sections`, () => HttpResponse.json(sections)),
    http.get(`${API}/academic-offering-cycles/7/conflicts`, () => HttpResponse.json(conflicts)),
  );
}

function renderDetail() {
  return renderWithProviders(
    <Routes>
      <Route path="/director/aprobaciones/oferta-academica/:id" element={<DirectorOfertaCicloDetalle />} />
      <Route path="/director/aprobaciones/oferta-academica" element={<div>Bandeja de oferta</div>} />
    </Routes>,
    { route: '/director/aprobaciones/oferta-academica/7' },
  );
}

describe('DirectorOfertaCicloDetalle', () => {
  it('shows the sections and who submitted the cycle', async () => {
    useBackend();
    renderDetail();

    expect(await screen.findByText('Contabilidad I')).toBeInTheDocument();
    expect(screen.getByText('Matemática')).toBeInTheDocument();
    expect(screen.getByText(/Enviado por Marina Fernández/)).toBeInTheDocument();
    expect(screen.getByText('Lun 18:00–20:00 · Aula 3')).toBeInTheDocument();
    expect(screen.queryByRole('alert')).not.toBeInTheDocument();
  });

  it('warns about conflicts before approving and marks the conflicted section', async () => {
    useBackend({ conflicts: { hasConflicts: true, conflicts: [conflict] } });
    renderDetail();

    expect(await screen.findByText('Hay 1 cruce para revisar antes de aprobar')).toBeInTheDocument();
    expect(screen.getByText('Aula 3 · Lun de 19:00 a 20:00')).toBeInTheDocument();
    expect(screen.getByText('Contabilidad I · A y Derecho Laboral · A usan la misma aula a la vez.')).toBeInTheDocument();
    expect(screen.getByText('Cruce')).toBeInTheDocument();
  });

  it('asks for confirmation and approves with an Idempotency-Key', async () => {
    useBackend();
    const keys = [];
    server.use(
      http.post(`${API}/academic-offering-cycles/7/approve`, ({ request }) => {
        keys.push(request.headers.get('Idempotency-Key'));
        return new HttpResponse(null, { status: 204 });
      }),
    );
    const user = userEvent.setup();
    renderDetail();

    await user.click(await screen.findByRole('button', { name: 'Aprobar ciclo' }));
    expect(keys).toEqual([]);
    expect(screen.getByText('¿Aprobar el ciclo?')).toBeInTheDocument();
    await user.click(screen.getByRole('button', { name: 'Confirmar aprobación' }));

    expect(await screen.findByText('Bandeja de oferta')).toBeInTheDocument();
    expect(keys).toHaveLength(1);
    expect(keys[0]).toBeTruthy();
  });

  it('reuses the same Idempotency-Key when the approval is retried after a failure', async () => {
    useBackend();
    const keys = [];
    let attempts = 0;
    server.use(
      http.post(`${API}/academic-offering-cycles/7/approve`, ({ request }) => {
        keys.push(request.headers.get('Idempotency-Key'));
        attempts += 1;
        return attempts === 1 ? new HttpResponse(null, { status: 400 }) : new HttpResponse(null, { status: 204 });
      }),
    );
    const user = userEvent.setup();
    renderDetail();

    await user.click(await screen.findByRole('button', { name: 'Aprobar ciclo' }));
    await user.click(screen.getByRole('button', { name: 'Confirmar aprobación' }));
    await waitFor(() => expect(keys).toHaveLength(1));
    await user.click(await screen.findByRole('button', { name: 'Confirmar aprobación' }));

    expect(await screen.findByText('Bandeja de oferta')).toBeInTheDocument();
    expect(keys[1]).toBe(keys[0]);
  });

  it('requires a reason of at least 10 characters to reject and sends it', async () => {
    useBackend();
    const bodies = [];
    server.use(
      http.post(`${API}/academic-offering-cycles/7/reject`, async ({ request }) => {
        bodies.push(await request.json());
        return new HttpResponse(null, { status: 204 });
      }),
    );
    const user = userEvent.setup();
    renderDetail();

    await user.click(await screen.findByRole('button', { name: 'Rechazar' }));
    await user.type(screen.getByPlaceholderText('Qué hay que corregir'), 'corto');
    await user.click(screen.getByRole('button', { name: 'Rechazar ciclo' }));
    expect(await screen.findByText('El motivo debe tener al menos 10 caracteres.')).toBeInTheDocument();
    expect(bodies).toEqual([]);

    await user.type(screen.getByPlaceholderText('Qué hay que corregir'), ' y más detalle');
    await user.click(screen.getByRole('button', { name: 'Rechazar ciclo' }));

    expect(await screen.findByText('Bandeja de oferta')).toBeInTheDocument();
    expect(bodies).toEqual([{ reason: 'corto y más detalle' }]);
  });

  it('shows that someone else already decided when the server answers 409', async () => {
    useBackend();
    server.use(
      http.post(`${API}/academic-offering-cycles/7/approve`, () =>
        HttpResponse.json(errorBody('CYCLE_NOT_PENDING_APPROVAL'), { status: 409 }),
      ),
    );
    const user = userEvent.setup();
    renderDetail();

    await user.click(await screen.findByRole('button', { name: 'Aprobar ciclo' }));
    await user.click(screen.getByRole('button', { name: 'Confirmar aprobación' }));

    expect(await screen.findByText('Otra persona de dirección ya decidió este trámite')).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: 'Aprobar ciclo' })).not.toBeInTheDocument();
  });

  it('hides the decision bar when the cycle is no longer pending', async () => {
    useBackend({ cycleBody: { ...cycle, status: 'REJECTED', decidedByName: 'Carolina Paz', decidedAt: '2026-11-04T10:00:00', rejectionReason: 'Faltan aulas' } });
    renderDetail();

    expect(await screen.findByText('Este ciclo ya no está pendiente')).toBeInTheDocument();
    expect(screen.getByText(/Faltan aulas/)).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: 'Aprobar ciclo' })).not.toBeInTheDocument();
  });

  it('keeps approval available but warns when conflicts cannot be checked', async () => {
    useBackend();
    server.use(http.get(`${API}/academic-offering-cycles/7/conflicts`, () => new HttpResponse(null, { status: 500 })));
    vi.spyOn(console, 'error').mockImplementation(() => {});
    renderDetail();

    expect(await screen.findByText('No pudimos comprobar los cruces de aulas y docentes.')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Aprobar ciclo' })).toBeEnabled();
  });
});
