import { describe, expect, it } from 'vitest';
import { screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { Routes, Route, useLocation } from 'react-router-dom';
import { http, HttpResponse } from 'msw';
import { server } from '@/test/mocks/server';
import { renderWithProviders } from '@/test/renderWithProviders';
import { NotificationBell } from '@/features/notifications/components/NotificationBell';

const API = 'http://localhost:8080';

describe('NotificationBell', () => {
  it('has an accessible name that includes the unread count', async () => {
    server.use(
      http.get(`${API}/me/notifications`, () =>
        HttpResponse.json([
          { id: 1, type: 'GRADE_POSTED', read: false, createdAt: '2026-10-01T10:00:00' },
          { id: 2, type: 'GRADE_POSTED', read: false, createdAt: '2026-10-01T11:00:00' },
          { id: 3, type: 'GRADE_POSTED', read: true, createdAt: '2026-10-01T12:00:00' },
        ]),
      ),
    );
    renderWithProviders(<NotificationBell />);

    expect(await screen.findByRole('button', { name: 'Notificaciones, 2 sin leer' })).toBeInTheDocument();
  });

  it('is named just Notificaciones when everything is read', async () => {
    server.use(http.get(`${API}/me/notifications`, () => HttpResponse.json([])));
    renderWithProviders(<NotificationBell />);

    expect(await screen.findByRole('button', { name: 'Notificaciones' })).toBeInTheDocument();
  });

  it('abre el detalle de la solicitud al tocar la notificación de solicitud aprobada', async () => {
    const user = userEvent.setup();
    function Destino() {
      return <div>{useLocation().pathname}</div>;
    }
    server.use(
      http.get(`${API}/me/notifications`, () =>
        HttpResponse.json([
          {
            id: 9,
            type: 'PROGRAM_APPLICATION_APPROVED',
            message: 'Aprobamos tu solicitud para Diseño Gráfico. Pagá la matrícula de ingreso para confirmar tu inscripción.',
            read: false,
            relatedEntityType: 'ProgramEnrollmentApplication',
            relatedEntityId: 42,
            createdAt: '2026-10-01T10:00:00',
          },
        ]),
      ),
      http.post(`${API}/me/notifications/9/read`, () => HttpResponse.json({})),
      http.patch(`${API}/me/notifications/9/read`, () => HttpResponse.json({})),
      http.put(`${API}/me/notifications/9/read`, () => HttpResponse.json({})),
    );
    renderWithProviders(
      <Routes>
        <Route path="/" element={<NotificationBell />} />
        <Route path="*" element={<Destino />} />
      </Routes>,
    );

    await user.click(await screen.findByRole('button', { name: 'Notificaciones, 1 sin leer' }));
    await user.click(await screen.findByText('Solicitud aprobada'));

    expect(await screen.findByText('/mis-solicitudes/42')).toBeInTheDocument();
  });
});
