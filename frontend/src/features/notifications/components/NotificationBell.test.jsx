import { describe, expect, it } from 'vitest';
import { screen } from '@testing-library/react';
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
});
