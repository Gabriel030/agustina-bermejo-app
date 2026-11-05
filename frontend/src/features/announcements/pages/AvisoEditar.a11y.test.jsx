import { describe, expect, it } from 'vitest';
import { screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { http, HttpResponse } from 'msw';
import { Route, Routes } from 'react-router-dom';
import { axe } from '@/test/axe';
import { server } from '@/test/mocks/server';
import { renderWithProviders } from '@/test/renderWithProviders';
import { AvisoEditar } from './AvisoEditar';

const API = 'http://localhost:8080';

describe('AvisoEditar accessibility', () => {
  it('editFormWithAutoCheckedNotifyBoxHasNoAxeViolations', async () => {
    server.use(
      http.get(`${API}/subject-sections/49/announcements`, () =>
        HttpResponse.json([
          {
            id: 10,
            title: 'Aviso',
            body: 'Texto',
            category: 'REMINDER',
            urgent: false,
            expiresAt: null,
            audienceLabel: 'Toda la comisión',
            status: 'SENT',
            sentAt: '2026-10-03T10:00:00',
            scheduledFor: null,
            createdAt: '2026-10-03T10:00:00',
          },
        ]),
      ),
      http.get(`${API}/announcements/10/recipients`, () => HttpResponse.json([{ studentId: 1, file: 'A-1', name: 'Lucía Pérez' }])),
      http.post(`${API}/subject-sections/49/audience-preview`, () =>
        HttpResponse.json({ count: 4, totalInSection: 4, label: 'Toda la comisión', hasUngradedInstances: false, students: [] }),
      ),
    );
    const user = userEvent.setup();
    const { container } = renderWithProviders(
      <Routes>
        <Route path="/docente/mis-comisiones/:sectionId/avisos/:avisoId/editar" element={<AvisoEditar />} />
      </Routes>,
      { route: '/docente/mis-comisiones/49/avisos/10/editar' },
    );

    await user.type(await screen.findByLabelText(/^Título/), ' nuevo');

    expect(screen.getByRole('checkbox', { name: 'Avisar a los alumnos de este cambio' })).toBeChecked();
    expect(await axe(container)).toHaveNoViolations();
  });
});
