import { describe, expect, it } from 'vitest';
import { screen } from '@testing-library/react';
import { http, HttpResponse } from 'msw';
import { Route, Routes } from 'react-router-dom';
import { server } from '@/test/mocks/server';
import { renderWithProviders } from '@/test/renderWithProviders';
import { DirectorRevisarConfiguracion } from './DirectorRevisarConfiguracion';

globalThis.process.env.TZ ='America/Argentina/Buenos_Aires';

const API = 'http://localhost:8080';

function montar(sectionId) {
  return renderWithProviders(
    <Routes>
      <Route path="/director/revisar-configuracion/:sectionId" element={<DirectorRevisarConfiguracion />} />
    </Routes>,
    { route: `/director/revisar-configuracion/${sectionId}` },
  );
}

describe('DirectorRevisarConfiguracion', () => {
  it('muestra las fechas de la propuesta en el mismo día que cargó el docente', async () => {
    server.use(
      http.get(`${API}/section-evaluation-configs/42`, () =>
        HttpResponse.json({
          subjectSectionId: 42,
          subjectName: 'Sujeto y Educación',
          sectionName: 'Comisión A',
          status: 'PENDING_APPROVAL',
          tpCount: 1,
          tp1DueDate: '2026-04-24',
          exam1Date: '2026-05-15',
          exam2Date: '2026-09-11',
          finalCall1Date: '2026-12-02',
          finalCall2Date: '2026-12-16',
          examCountExpected: 2,
          hasFinal: true,
          termStartDate: '2026-03-02T00:00:00',
          termEndDate: '2026-12-11T00:00:00',
          instructors: [],
        }),
      ),
      http.get(`${API}/holidays`, () => HttpResponse.json([])),
    );

    montar(42);

    expect(await screen.findByDisplayValue(/^24 de abr/)).toBeInTheDocument();
    expect(screen.getByDisplayValue(/^15 de may/)).toBeInTheDocument();
  });

  it('una configuración inexistente muestra un estado de error en vez de cargar para siempre', async () => {
    server.use(
      http.get(`${API}/section-evaluation-configs/99999`, () => new HttpResponse(null, { status: 404 })),
      http.get(`${API}/holidays`, () => HttpResponse.json([])),
    );

    montar(99999);

    expect(await screen.findByText('No encontramos esta configuración')).toBeInTheDocument();
    expect(screen.getByRole('link', { name: 'Volver a la bandeja' })).toBeInTheDocument();
  });
});
