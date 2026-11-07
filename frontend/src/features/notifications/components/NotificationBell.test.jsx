import { describe, expect, it, vi } from 'vitest';
import { screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter, Routes, Route, useLocation } from 'react-router-dom';
import { render } from '@testing-library/react';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { http, HttpResponse } from 'msw';
import { server } from '@/test/mocks/server';
import { renderWithProviders } from '@/test/renderWithProviders';
import { NotificationBell } from '@/features/notifications/components/NotificationBell';
import { TIPO_NOTIFICACION_LABEL } from '@/lib/notificaciones';

const API = 'http://localhost:8080';

describe('NotificationBell', () => {
  it('has an accessible name that includes the unread count', async () => {
    server.use(
      http.get(`${API}/me/notifications`, () =>
        HttpResponse.json([
          { id: 1, type: 'PAYMENT_RECEIPT_APPROVED', read: false, createdAt: '2026-10-01T10:00:00' },
          { id: 2, type: 'PAYMENT_RECEIPT_APPROVED', read: false, createdAt: '2026-10-01T11:00:00' },
          { id: 3, type: 'PAYMENT_RECEIPT_APPROVED', read: true, createdAt: '2026-10-01T12:00:00' },
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
      http.post(`${API}/me/notifications/9/read`, () =>
        HttpResponse.json({ id: 9, type: 'GENERAL', message: 'Aviso', read: true, createdAt: '2026-01-01T00:00:00', relatedEntityId: 1, relatedEntityType: 'INVOICE' }),
      ),
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

  it('shows an error state with retry instead of the empty message when loading fails', async () => {
    const user = userEvent.setup();
    let calls = 0;
    server.use(
      http.get(`${API}/me/notifications`, () => {
        calls += 1;
        if (calls === 1) return HttpResponse.json({ message: 'boom' }, { status: 500 });
        return HttpResponse.json([{ id: 1, type: 'PAYMENT_RECEIPT_APPROVED', message: 'Listo', read: false, createdAt: '2026-10-01T10:00:00' }]);
      }),
    );
    renderWithProviders(<NotificationBell />);

    await user.click(await screen.findByRole('button', { name: 'Notificaciones, no pudimos cargarlas' }));
    expect(await screen.findByText('No pudimos cargar tus notificaciones.')).toBeInTheDocument();
    expect(screen.queryByText('Nada por ahora')).not.toBeInTheDocument();
    expect(screen.getByTestId('bell-error-indicator')).toBeInTheDocument();

    await user.click(screen.getByRole('button', { name: 'Reintentar' }));
    expect(await screen.findByText('Pago acreditado')).toBeInTheDocument();
  });

  it('navigates to the draft invoices of the run when tapping DRAFT_INVOICES_AWAITING_REVIEW', async () => {
    const user = userEvent.setup();
    function Destino() {
      const location = useLocation();
      return <div>{location.pathname}</div>;
    }
    server.use(
      http.get(`${API}/me/notifications`, () =>
        HttpResponse.json([
          { id: 41, type: 'DRAFT_INVOICES_AWAITING_REVIEW', message: 'Quedan 3 borradores.', read: true, relatedEntityType: 'MonthlyInvoiceRun', relatedEntityId: 7, createdAt: '2026-11-01T10:00:00' },
        ]),
      ),
    );
    renderWithProviders(
      <Routes>
        <Route path="/director/inicio" element={<NotificationBell />} />
        <Route path="*" element={<Destino />} />
      </Routes>,
      { route: '/director/inicio' },
    );

    await user.click(await screen.findByRole('button', { name: 'Notificaciones' }));
    await user.click(await screen.findByText('Facturas por revisar'));

    expect(await screen.findByText('/director/emision-mensual/7/borradores')).toBeInTheDocument();
  });

  it.each([
    ['GRADES_PUBLISHED', 'Section'],
    ['GRADE_CORRECTED', 'GradeItem'],
  ])('navigates to the student grades tab when tapping %s', async (type, entityType) => {
    const user = userEvent.setup();
    function Destino() {
      const location = useLocation();
      return <div>{`${location.pathname}${location.search}`}</div>;
    }
    server.use(
      http.get(`${API}/me/notifications`, () =>
        HttpResponse.json([
          { id: 31, type, message: 'Hay novedades en tus notas.', read: true, relatedEntityType: entityType, relatedEntityId: 5, createdAt: '2026-11-01T10:00:00' },
        ]),
      ),
    );
    renderWithProviders(
      <Routes>
        <Route path="/" element={<NotificationBell />} />
        <Route path="*" element={<Destino />} />
      </Routes>,
    );

    await user.click(await screen.findByRole('button', { name: 'Notificaciones' }));
    await user.click(await screen.findByText(TIPO_NOTIFICACION_LABEL[type]));

    expect(await screen.findByText('/alumno/mis-carreras?tab=notas')).toBeInTheDocument();
  });

  it.each(['ATTENDANCE_CHANGE_REQUEST_APPROVED', 'ATTENDANCE_CHANGE_REQUEST_REJECTED'])(
    'navigates to the teacher requests tab when tapping %s',
    async (type) => {
      const user = userEvent.setup();
      function Destino() {
        const location = useLocation();
        return <div>{`${location.pathname}${location.search}`}</div>;
      }
      server.use(
        http.get(`${API}/me/notifications`, () =>
          HttpResponse.json([
            { id: 32, type, message: 'Respondieron tu pedido.', read: true, relatedEntityType: 'AttendanceChangeRequest', relatedEntityId: 40, createdAt: '2026-11-01T10:00:00' },
          ]),
        ),
      );
      renderWithProviders(
        <Routes>
          <Route path="/" element={<NotificationBell />} />
          <Route path="*" element={<Destino />} />
        </Routes>,
      );

      await user.click(await screen.findByRole('button', { name: 'Notificaciones' }));
      await user.click(await screen.findByText(TIPO_NOTIFICACION_LABEL[type]));

      expect(await screen.findByText('/docente/tomar-asistencia?tab=pedidos')).toBeInTheDocument();
    },
  );

  it.each([
    'GRADUATION_REQUEST_CREATED',
    'GRADUATION_REQUEST_PENDING_REVIEW',
    'GRADUATION_REQUEST_VERIFIED',
    'GRADUATION_REQUEST_DEBT_BLOCKED',
    'GRADUATION_REQUEST_APPROVED',
    'GRADUATION_REQUEST_REJECTED',
  ])('navigates to the student graduation screen when tapping %s', async (type) => {
    const user = userEvent.setup();
    function Destino() {
      return <div>{useLocation().pathname}</div>;
    }
    server.use(
      http.get(`${API}/me/notifications`, () =>
        HttpResponse.json([
          {
            id: 21,
            type,
            message: 'Tu solicitud de egreso está detenida por una deuda pendiente de $15000 (2 cuotas).',
            read: true,
            relatedEntityType: 'GraduationRequest',
            relatedEntityId: 7,
            createdAt: '2026-10-01T10:00:00',
          },
        ]),
      ),
    );
    renderWithProviders(
      <Routes>
        <Route path="/" element={<NotificationBell />} />
        <Route path="*" element={<Destino />} />
      </Routes>,
    );

    await user.click(await screen.findByRole('button', { name: 'Notificaciones' }));
    await user.click(await screen.findByText(TIPO_NOTIFICACION_LABEL[type]));

    expect(await screen.findByText('/alumno/mis-carreras')).toBeInTheDocument();
  });

  it('navigates to the announcement on the student home when tapping an announcement', async () => {
    const user = userEvent.setup();
    function Destino() {
      return <div>{useLocation().pathname}</div>;
    }
    server.use(
      http.get(`${API}/me/notifications`, () =>
        HttpResponse.json([
          {
            id: 22,
            type: 'ANNOUNCEMENT_PUBLISHED',
            message: 'Nuevo aviso sobre la inscripción.',
            read: true,
            relatedEntityType: 'Announcement',
            relatedEntityId: 5,
            createdAt: '2026-10-01T10:00:00',
          },
        ]),
      ),
    );
    renderWithProviders(
      <Routes>
        <Route path="/" element={<NotificationBell />} />
        <Route path="*" element={<Destino />} />
      </Routes>,
    );

    await user.click(await screen.findByRole('button', { name: 'Notificaciones' }));
    await user.click(await screen.findByText(TIPO_NOTIFICACION_LABEL.ANNOUNCEMENT_PUBLISHED));

    expect(await screen.findByText('/alumno/inicio')).toBeInTheDocument();
  });

  it('shows a Spanish generic label and screen reader text for unknown unread types', async () => {
    const user = userEvent.setup();
    server.use(
      http.get(`${API}/me/notifications`, () =>
        HttpResponse.json([{ id: 1, type: 'SOME_FUTURE_TYPE', message: 'Algo pasó', read: false, createdAt: '2026-10-01T10:00:00' }]),
      ),
    );
    renderWithProviders(<NotificationBell />);

    await user.click(await screen.findByRole('button', { name: 'Notificaciones, 1 sin leer' }));
    expect(await screen.findByText('Notificación')).toBeInTheDocument();
    expect(screen.queryByText('SOME_FUTURE_TYPE')).not.toBeInTheDocument();
    expect(screen.getByText('Sin leer')).toBeInTheDocument();
  });

  it('keeps the list visible when a refetch fails after a good load', async () => {
    const user = userEvent.setup();
    let calls = 0;
    server.use(
      http.get(`${API}/me/notifications`, () => {
        calls += 1;
        if (calls === 1) return HttpResponse.json([{ id: 1, type: 'PAYMENT_RECEIPT_APPROVED', message: 'Listo', read: false, createdAt: '2026-10-01T10:00:00' }]);
        return HttpResponse.json({ message: 'boom' }, { status: 500 });
      }),
    );
    const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } });
    render(
      <QueryClientProvider client={queryClient}>
        <MemoryRouter>
          <NotificationBell />
        </MemoryRouter>
      </QueryClientProvider>,
    );

    await user.click(await screen.findByRole('button', { name: 'Notificaciones, 1 sin leer' }));
    await screen.findByText('Pago acreditado');
    await queryClient.refetchQueries({ queryKey: ['me', 'notifications'] });

    expect(screen.getByText('Pago acreditado')).toBeInTheDocument();
    expect(screen.queryByText('No pudimos cargar tus notificaciones.')).not.toBeInTheDocument();
  });
});

describe('NotificationBell offline stamp', () => {
  it('showsLastUpdateStampWhileOffline', async () => {
    server.use(
      http.get(`${API}/me/notifications`, () =>
        HttpResponse.json([{ id: 1, type: 'PAYMENT_RECEIPT_APPROVED', read: false, createdAt: '2026-10-01T10:00:00' }]),
      ),
    )
    const onLine = vi.spyOn(window.navigator, 'onLine', 'get').mockReturnValue(true)
    renderWithProviders(<NotificationBell />)
    const bell = await screen.findByRole('button', { name: 'Notificaciones, 1 sin leer' })
    onLine.mockReturnValue(false)
    window.dispatchEvent(new Event('offline'))
    await userEvent.click(bell)

    expect(await screen.findByText(/Actualizado (recién|hace)/)).toBeInTheDocument()
    onLine.mockRestore()
  })
})
