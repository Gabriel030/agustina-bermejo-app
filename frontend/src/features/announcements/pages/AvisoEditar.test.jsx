import { beforeEach, describe, expect, it } from 'vitest';
import { screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { Toaster, toast } from 'sonner';
import { http, HttpResponse } from 'msw';
import { Route, Routes } from 'react-router-dom';
import { server } from '@/test/mocks/server';
import { renderWithProviders } from '@/test/renderWithProviders';
import { AvisoEditar } from './AvisoEditar';

const API = 'http://localhost:8080';
const SECTION_ID = 49;

describe('AvisoEditar', () => {
  beforeEach(() => {
    toast.dismiss();
  });

  it('un aviso enviado a un segmento muestra el total de la comisión, no la cantidad de destinatarios', async () => {
    server.use(
      http.get(`${API}/subject-sections/${SECTION_ID}/announcements`, () =>
        HttpResponse.json([
          {
            id: 10,
            title: 'Riesgo de quedar libre',
            body: 'Pasá por preceptoría.',
            category: 'REMINDER',
            urgent: true,
            expiresAt: null,
            audienceLabel: 'Con 17 o más inasistencias',
            recipientCount: 25,
            readCount: 0,
            readTracked: true,
            status: 'SENT',
            sentAt: '2026-10-03T10:00:00',
            createdAt: '2026-10-03T10:00:00',
          },
        ]),
      ),
      http.get(`${API}/announcements/10/recipients`, () => HttpResponse.json([{ studentId: 1, file: 'A-1', name: 'Lucía Pérez', readAt: null }])),
      http.post(`${API}/subject-sections/${SECTION_ID}/audience-preview`, () =>
        HttpResponse.json({ count: 4, totalInSection: 4, label: 'Toda la comisión', hasUngradedInstances: false, students: [] }),
      ),
    );

    renderWithProviders(
      <Routes>
        <Route path="/docente/mis-comisiones/:sectionId/avisos/:avisoId/editar" element={<AvisoEditar />} />
      </Routes>,
      { route: `/docente/mis-comisiones/${SECTION_ID}/avisos/10/editar` },
    );

    expect(await screen.findByText('Lo ve 1 de los 4 alumnos de la comisión.')).toBeInTheDocument();
  });

  function aviso(extra) {
    return {
      id: 10,
      title: 'Aviso',
      body: 'Texto',
      category: 'REMINDER',
      urgent: false,
      expiresAt: null,
      audienceLabel: 'Toda la comisión',
      recipientCount: 25,
      readCount: 0,
      readTracked: true,
      status: 'SENT',
      sentAt: '2026-10-03T10:00:00',
      scheduledFor: null,
      createdAt: '2026-10-03T10:00:00',
      ...extra,
    };
  }

  function montar(avisos, { avisoId = 10, onPut } = {}) {
    server.use(
      http.get(`${API}/subject-sections/${SECTION_ID}/announcements`, () => HttpResponse.json(avisos)),
      http.get(`${API}/announcements/10/recipients`, () => HttpResponse.json([{ studentId: 1, file: 'A-1', name: 'Lucía Pérez', readAt: null }])),
      http.post(`${API}/subject-sections/${SECTION_ID}/audience-preview`, () =>
        HttpResponse.json({ count: 4, totalInSection: 4, label: 'Toda la comisión', hasUngradedInstances: false, students: [] }),
      ),
      http.get(`${API}/subject-sections/${SECTION_ID}/audience-segments`, () => HttpResponse.json([])),
      http.put(`${API}/announcements/10`, onPut ?? (() => HttpResponse.json({ id: 10 }))),
    );
    return renderWithProviders(
      <>
        <Toaster />
        <Routes>
          <Route path="/docente/mis-comisiones/:sectionId/avisos/:avisoId/editar" element={<AvisoEditar />} />
          <Route path="/docente/mis-comisiones/:sectionId" element={<div>Volvió a la comisión</div>} />
        </Routes>
      </>,
      { route: `/docente/mis-comisiones/${SECTION_ID}/avisos/${avisoId}/editar` },
    );
  }

  it('con un id inexistente muestra aviso no encontrado y permite volver', async () => {
    const user = userEvent.setup();
    montar([aviso()], { avisoId: 999 });

    expect(await screen.findByText('No encontramos este aviso')).toBeInTheDocument();
    await user.click(screen.getByRole('button', { name: 'Volver a los avisos' }));
    expect(await screen.findByText('Volvió a la comisión')).toBeInTheDocument();
  });

  it('editar un aviso programado deshabilita Ahora y explica cómo enviarlo ya', async () => {
    const user = userEvent.setup();
    montar([aviso({ status: 'SCHEDULED', sentAt: null, scheduledFor: '2099-01-01T10:00:00' })]);

    await user.click(await screen.findByRole('button', { name: 'Cambiar fechas' }));
    expect(screen.getByRole('radio', { name: 'Ahora' })).toBeDisabled();
    expect(screen.getByText(/Para enviarlo ahora, creá un aviso nuevo/)).toBeInTheDocument();
  });

  const NOTIFY_LABEL = 'Avisar a los alumnos de este cambio';

  function savedAnnouncement() {
    return {
      id: 10,
      sectionId: SECTION_ID,
      sectionType: 'PROGRAM',
      title: 'Aviso',
      body: 'Texto',
      category: 'REMINDER',
      urgent: false,
      expiresAt: null,
      audienceLabel: 'Toda la comisión',
      recipientCount: 25,
      readCount: 0,
      readTracked: true,
      status: 'SENT',
      scheduledFor: null,
      sentAt: '2026-10-03T10:00:00',
      createdAt: '2026-10-03T10:00:00',
      updatedAt: '2026-11-04T10:00:00',
      createdBy: 'Docente Demo',
    };
  }

  it('editar un aviso enviado no notifica por defecto y guarda sin confirmación', async () => {
    const bodies = [];
    const user = userEvent.setup();
    montar([aviso()], {
      onPut: async ({ request }) => {
        bodies.push(await request.json());
        return HttpResponse.json(savedAnnouncement());
      },
    });

    expect(await screen.findByRole('checkbox', { name: NOTIFY_LABEL })).not.toBeChecked();
    expect(screen.queryByText(/vuelven a recibir/)).not.toBeInTheDocument();
    expect(screen.getByText(/Nadie recibe una notificación/)).toBeInTheDocument();
    await user.click(screen.getByRole('button', { name: 'Guardar cambios' }));

    expect(await screen.findByText('Volvió a la comisión')).toBeInTheDocument();
    expect(bodies).toHaveLength(1);
    expect(bodies[0].notify).toBe(false);
  });

  it('con la casilla marcada pide confirmar la nueva notificación y no guarda sin confirmar', async () => {
    let guardados = 0;
    const user = userEvent.setup();
    montar([aviso()], {
      onPut: () => {
        guardados += 1;
        return HttpResponse.json(savedAnnouncement());
      },
    });

    await user.click(await screen.findByRole('checkbox', { name: NOTIFY_LABEL }));
    expect(screen.getByText(/vuelven a recibir/)).toBeInTheDocument();
    await user.click(screen.getByRole('button', { name: 'Guardar y avisar' }));
    expect(await screen.findByText(/Al guardar se volverá a notificar a todos los destinatarios/)).toBeInTheDocument();
    expect(guardados).toBe(0);
  });

  it('confirmar la notificación guarda el aviso con notify verdadero', async () => {
    const bodies = [];
    const user = userEvent.setup();
    montar([aviso()], {
      onPut: async ({ request }) => {
        bodies.push(await request.json());
        return HttpResponse.json(savedAnnouncement());
      },
    });

    await user.click(await screen.findByRole('checkbox', { name: NOTIFY_LABEL }));
    await user.click(screen.getByRole('button', { name: 'Guardar y avisar' }));
    const dialog = await screen.findByRole('dialog');
    await user.click(within(dialog).getByRole('button', { name: 'Guardar y avisar' }));

    expect(await screen.findByText('Volvió a la comisión')).toBeInTheDocument();
    expect(bodies[0].notify).toBe(true);
  });

  it('seguir editando en la confirmación no guarda nada', async () => {
    let guardados = 0;
    const user = userEvent.setup();
    montar([aviso()], {
      onPut: () => {
        guardados += 1;
        return HttpResponse.json(savedAnnouncement());
      },
    });

    await user.click(await screen.findByRole('checkbox', { name: NOTIFY_LABEL }));
    await user.click(screen.getByRole('button', { name: 'Guardar y avisar' }));
    await user.click(await screen.findByRole('button', { name: 'Seguir editando' }));
    expect(screen.queryByText(/Al guardar se volverá a notificar/)).not.toBeInTheDocument();
    expect(guardados).toBe(0);
  });

  it.each([
    ['el título', async (user) => user.type(screen.getByLabelText(/^Título/), ' nuevo')],
    ['la urgencia', async (user) => user.click(screen.getByRole('button', { name: 'Marcar como urgente' }))],
  ])('cambiar %s tilda la casilla sola y el botón pasa a Guardar y avisar', async (motivo, cambiar) => {
    const user = userEvent.setup();
    montar([aviso()]);

    const casilla = await screen.findByRole('checkbox', { name: NOTIFY_LABEL });
    expect(casilla).not.toBeChecked();
    await cambiar(user);

    expect(casilla).toBeChecked();
    expect(screen.getByText(`Se tildó solo porque cambiaste ${motivo}.`)).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Guardar y avisar' })).toBeInTheDocument();
    expect(screen.getByText(/vuelven a recibir/)).toBeInTheDocument();
  });

  it('cambiar solo el mensaje no tilda la casilla', async () => {
    const user = userEvent.setup();
    montar([aviso()]);

    const casilla = await screen.findByRole('checkbox', { name: NOTIFY_LABEL });
    await user.type(screen.getByLabelText(/^Mensaje/), ' más');

    expect(casilla).not.toBeChecked();
    expect(screen.getByRole('button', { name: 'Guardar cambios' })).toBeInTheDocument();
  });

  it('si el docente destilda a mano después de un cambio, se respeta aunque siga habiendo cambios', async () => {
    const bodies = [];
    const user = userEvent.setup();
    montar([aviso()], {
      onPut: async ({ request }) => {
        bodies.push(await request.json());
        return HttpResponse.json(savedAnnouncement());
      },
    });

    const casilla = await screen.findByRole('checkbox', { name: NOTIFY_LABEL });
    await user.type(screen.getByLabelText(/^Título/), ' nuevo');
    await user.click(casilla);
    expect(casilla).not.toBeChecked();
    expect(screen.getByText('Elegiste no avisar a mano.')).toBeInTheDocument();
    await user.type(screen.getByLabelText(/^Título/), ' otra vez');
    expect(casilla).not.toBeChecked();

    await user.click(screen.getByRole('button', { name: 'Guardar cambios' }));
    expect(await screen.findByText('Volvió a la comisión')).toBeInTheDocument();
    expect(bodies[0].notify).toBe(false);
  });

  it('un aviso programado no muestra la casilla ni cambia el botón', async () => {
    const user = userEvent.setup();
    montar([aviso({ status: 'SCHEDULED', sentAt: null, scheduledFor: '2099-01-01T10:00:00' })]);

    await screen.findByRole('button', { name: 'Cambiar fechas' });
    await user.type(screen.getByLabelText(/^Título/), ' nuevo');
    expect(screen.queryByRole('checkbox', { name: NOTIFY_LABEL })).not.toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Guardar cambios' })).toBeInTheDocument();
  });
});

describe('AvisoEditar — load failure', () => {
  it('shows the error state with a way back and reloads the announcement on retry', async () => {
    let calls = 0;
    server.use(
      http.get(`${API}/subject-sections/${SECTION_ID}/announcements`, () => {
        calls += 1;
        return new HttpResponse(null, { status: 500 });
      }),
    );
    const user = userEvent.setup();
    renderWithProviders(
      <Routes>
        <Route path="/docente/mis-comisiones/:sectionId/avisos/:avisoId/editar" element={<AvisoEditar />} />
      </Routes>,
      { route: `/docente/mis-comisiones/${SECTION_ID}/avisos/10/editar` },
    );

    expect(await screen.findByRole('alert')).toHaveTextContent('No pudimos cargar el aviso');
    expect(screen.getByRole('link', { name: /Volver a los avisos/ })).toBeInTheDocument();
    await user.click(screen.getByRole('button', { name: 'Reintentar' }));

    await waitFor(() => expect(calls).toBe(2));
  });
});
