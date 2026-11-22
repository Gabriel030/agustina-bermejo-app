import { afterEach, describe, expect, it, vi } from 'vitest';
import { screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { http, HttpResponse } from 'msw';
import { Routes, Route } from 'react-router-dom';
import { useQueryClient } from '@tanstack/react-query';
import { server } from '@/test/mocks/server';
import { renderWithProviders } from '@/test/renderWithProviders';
import { reiniciarAvisosMarcados } from '@/features/announcements';
import { NotificationBell } from '@/features/notifications/components/NotificationBell';
import { AlumnoInicio } from '@/features/workspace/pages/AlumnoInicio';
import { installFixedClock } from '@/test/fixedClock';
import { TIPO_NOTIFICACION_LABEL, TONO_NOTIFICACION } from '@/lib/notificaciones';

vi.mock('@/context/AuthContext', () => ({
  useAuth: () => ({ usuario: { nombre: 'Ana', apellido: 'Pérez' } }),
}));

const API = 'http://localhost:8080';

installFixedClock();

const hoursAgo = (hours) => new Date(Date.now() - hours * 60 * 60 * 1000).toISOString();

function unAviso(extra = {}) {
  return {
    id: 1,
    subjectName: 'Análisis Matemático I',
    title: 'Cambio de aula',
    body: 'La clase del jueves se dicta en el Aula 204.',
    category: 'SCHEDULING',
    urgent: false,
    teacherName: 'Ana Docente',
    createdAt: hoursAgo(1),
    updatedAt: hoursAgo(1),
    readAt: null,
    ...extra,
  };
}

function mockearBase(avisos = []) {
  server.use(
    http.get(`${API}/me/enrollment-payment-status`, () => HttpResponse.json([])),
    http.get(`${API}/me/course-enrollments`, () => HttpResponse.json([])),
    http.get(`${API}/me/announcements`, () => HttpResponse.json(avisos)),
  );
}

function montar(route = '/alumno/inicio') {
  return renderWithProviders(
    <Routes>
      <Route path="/alumno/inicio" element={<AlumnoInicio />} />
    </Routes>,
    { route },
  );
}

describe('AlumnoInicio — Novedades', () => {
  it('muestra EmptyState cuando no hay avisos', async () => {
    mockearBase([]);
    montar();
    expect(await screen.findByText('Todavía no hay novedades')).toBeInTheDocument();
  });

  it('renderiza los avisos ordenados por fecha descendente', async () => {
    const avisos = [
      unAviso({ id: 1, title: 'Aviso viejo', createdAt: hoursAgo(2), updatedAt: hoursAgo(2) }),
      unAviso({ id: 2, title: 'Aviso nuevo', createdAt: hoursAgo(1), updatedAt: hoursAgo(1) }),
    ];
    mockearBase(avisos);
    montar();

    const titulos = await screen.findAllByText(/Aviso viejo|Aviso nuevo/);
    const textos = titulos.map((el) => el.textContent);
    expect(textos.indexOf('Aviso nuevo')).toBeLessThan(textos.indexOf('Aviso viejo'));
  });

  it('urgente se muestra con pill pero no se reordena', async () => {
    const avisos = [
      unAviso({ id: 1, title: 'Normal', urgent: false, createdAt: hoursAgo(1), updatedAt: hoursAgo(1) }),
      unAviso({ id: 2, title: 'Urgente', urgent: true, createdAt: hoursAgo(2), updatedAt: hoursAgo(2) }),
    ];
    mockearBase(avisos);
    montar();

    await screen.findByText('Normal');
    expect(screen.getByText('Urgente', { selector: 'span' })).toBeInTheDocument();

    const titulos = screen.getAllByText(/Normal|Urgente/i).filter((el) => el.tagName !== 'SPAN');
    expect(titulos[0].textContent).toBe('Normal');
  });

  it('muestra "editado" cuando updatedAt supera createdAt en más de 1 minuto', async () => {
    const updatedAt = new Date(Date.now() - 30 * 60 * 1000).toISOString();
    const createdAt = new Date(Date.now() - 60 * 60 * 1000).toISOString();
    mockearBase([unAviso({ id: 1, createdAt, updatedAt })]);
    montar();

    expect(await screen.findByText(/editado/)).toBeInTheDocument();
  });

  it('writesEditedOnDateWhenEditedMoreThanAWeekAgo', async () => {
    mockearBase([unAviso({ id: 1, createdAt: hoursAgo(24 * 20), updatedAt: hoursAgo(24 * 10) })]);
    montar();

    expect(await screen.findByText(/editado el \d{2}\/\d{2}\/\d{4}/)).toBeInTheDocument();
  });

  it('writesEditedAgoWhenEditedRecently', async () => {
    mockearBase([unAviso({ id: 1, createdAt: hoursAgo(5), updatedAt: hoursAgo(2) })]);
    montar();

    expect(await screen.findByText('editado hace 2 h')).toBeInTheDocument();
  });

  it('no muestra "editado" cuando updatedAt === createdAt', async () => {
    mockearBase([unAviso({ id: 1, createdAt: hoursAgo(1), updatedAt: hoursAgo(1) })]);
    montar();

    await screen.findByText('Cambio de aula');
    expect(screen.queryByText(/editado/)).not.toBeInTheDocument();
  });

  it('el alumno no ve el segmento ni el conteo de destinatarios', async () => {
    mockearBase([unAviso({ id: 1 })]);
    montar();

    await screen.findByText('Cambio de aula');
    expect(screen.queryByText(/destinatario/i)).not.toBeInTheDocument();
    expect(screen.queryByText(/segmento/i)).not.toBeInTheDocument();
  });

  it('muestra "Ver todos" cuando hay más de 3 avisos y cambia a "Mostrar menos avisos"', async () => {
    const avisos = Array.from({ length: 5 }, (_, i) =>
      unAviso({ id: i + 1, title: `Aviso ${i + 1}`, createdAt: new Date(Date.now() - i * 3600_000).toISOString(), updatedAt: new Date(Date.now() - i * 3600_000).toISOString() }),
    );
    mockearBase(avisos);
    const user = userEvent.setup();
    montar();

    const boton = await screen.findByRole('button', { name: /Ver todos/ });
    expect(boton).toBeInTheDocument();

    await user.click(boton);
    expect(screen.getByRole('button', { name: 'Mostrar menos avisos' })).toBeInTheDocument();
  });

  it('expandir la lista no colapsa los avisos individuales ya expandidos', async () => {
    const avisos = Array.from({ length: 4 }, (_, i) =>
      unAviso({ id: i + 1, title: `Aviso ${i + 1}`, createdAt: new Date(Date.now() - i * 3600_000).toISOString(), updatedAt: new Date(Date.now() - i * 3600_000).toISOString() }),
    );
    mockearBase(avisos);
    const user = userEvent.setup();
    montar();

    await screen.findByText('Aviso 1');
    await user.click(screen.getByRole('button', { name: /Ver todos/ }));
    expect(screen.getByText('Aviso 4')).toBeInTheDocument();
  });
});

function PollNow() {
  const queryClient = useQueryClient();
  return (
    <button type="button" onClick={() => queryClient.invalidateQueries({ queryKey: ['me', 'announcements'] })}>
      poll
    </button>
  );
}

describe('AlumnoInicio — deep link del aviso', () => {
  function manyAnnouncements(count) {
    return Array.from({ length: count }, (_, i) =>
      unAviso({ id: i + 1, title: `Aviso ${i + 1}`, createdAt: hoursAgo(i + 1), updatedAt: hoursAgo(i + 1) }),
    );
  }

  it('expandsAndHighlightsTheAnnouncementFromTheUrl', async () => {
    reiniciarAvisosMarcados();
    mockearBase(manyAnnouncements(2));
    server.use(http.post(`${API}/me/announcements/:id/read`, () => new HttpResponse(null, { status: 204 })));
    montar('/alumno/inicio?aviso=2');

    const title = await screen.findByRole('button', { name: /Aviso 2/ });
    expect(title).toHaveAttribute('aria-expanded', 'true');
    expect(screen.getByRole('button', { name: /Aviso 1/ })).toHaveAttribute('aria-expanded', 'false');
    expect(title.closest('[data-aviso-id]')).toHaveAttribute('aria-current', 'true');
    expect(screen.getByRole('button', { name: /Aviso 1/ }).closest('[data-aviso-id]')).not.toHaveAttribute('aria-current');
  });

  it('movesFocusToTheHighlightedAnnouncementWhenArrivingByDeepLink', async () => {
    reiniciarAvisosMarcados();
    mockearBase(manyAnnouncements(2));
    server.use(http.post(`${API}/me/announcements/:id/read`, () => new HttpResponse(null, { status: 204 })));
    montar('/alumno/inicio?aviso=2');

    const title = await screen.findByRole('button', { name: /Aviso 2/ });
    await waitFor(() => expect(title.closest('[data-aviso-id]')).toHaveFocus());
  });

  it('doesNotScrollAgainWhenPollingBringsANewAnnouncement', async () => {
    reiniciarAvisosMarcados();
    let avisos = manyAnnouncements(2);
    server.use(
      http.get(`${API}/me/enrollment-payment-status`, () => HttpResponse.json([])),
      http.get(`${API}/me/course-enrollments`, () => HttpResponse.json([])),
      http.get(`${API}/me/announcements`, () => HttpResponse.json(avisos)),
      http.post(`${API}/me/announcements/:id/read`, () => new HttpResponse(null, { status: 204 })),
    );
    const scroll = vi.spyOn(Element.prototype, 'scrollIntoView');
    renderWithProviders(
      <Routes>
        <Route path="/alumno/inicio" element={<><PollNow /><AlumnoInicio /></>} />
      </Routes>,
      { route: '/alumno/inicio?aviso=2' },
    );
    await screen.findByRole('button', { name: /Aviso 2/ });
    await waitFor(() => expect(scroll).toHaveBeenCalledTimes(1));

    avisos = [...avisos, unAviso({ id: 9, title: 'Aviso nuevo', createdAt: hoursAgo(9), updatedAt: hoursAgo(9) })];
    await userEvent.click(screen.getByRole('button', { name: 'poll' }));
    await screen.findByRole('button', { name: /Aviso nuevo/ });
    expect(scroll).toHaveBeenCalledTimes(1);
    scroll.mockRestore();
  });

  it.each(['1e0', '0x2', ' 2 ', '1.5', '0', '-1', 'abc', '', '99999999999999999999'])(
    'treatsTheInvalidParameter%sAsUnavailable',
    async (value) => {
      mockearBase(manyAnnouncements(3));
      montar(`/alumno/inicio?aviso=${encodeURIComponent(value)}`);

      expect(await screen.findByText('Ese aviso ya no está disponible.')).toBeInTheDocument();
      screen.getAllByRole('button', { name: /Aviso \d/ }).forEach((button) => expect(button).toHaveAttribute('aria-expanded', 'false'));
    },
  );

  it('treatsARepeatedParameterAsUnavailable', async () => {
    mockearBase(manyAnnouncements(3));
    montar('/alumno/inicio?aviso=1&aviso=2');

    expect(await screen.findByText('Ese aviso ya no está disponible.')).toBeInTheDocument();
  });

  it('doesNotShowTheUnavailableMessageWhileTheListIsRefetching', async () => {
    reiniciarAvisosMarcados();
    const avisos = manyAnnouncements(2);
    let releaseRefetch;
    const gate = new Promise((resolve) => {
      releaseRefetch = resolve;
    });
    let calls = 0;
    server.use(
      http.get(`${API}/me/enrollment-payment-status`, () => HttpResponse.json([])),
      http.get(`${API}/me/course-enrollments`, () => HttpResponse.json([])),
      http.get(`${API}/me/announcements`, async () => {
        calls += 1;
        if (calls > 1) await gate;
        return HttpResponse.json(avisos);
      }),
    );
    renderWithProviders(
      <Routes>
        <Route path="/alumno/inicio" element={<><PollNow /><AlumnoInicio /></>} />
      </Routes>,
      { route: '/alumno/inicio?aviso=9' },
    );
    expect(await screen.findByText('Ese aviso ya no está disponible.')).toBeInTheDocument();

    await userEvent.click(screen.getByRole('button', { name: 'poll' }));
    await waitFor(() => expect(screen.queryByText('Ese aviso ya no está disponible.')).not.toBeInTheDocument());
    releaseRefetch();
    expect(await screen.findByText('Ese aviso ya no está disponible.')).toBeInTheDocument();
  });

  it('collapsesTheListWhenTappingShowLessWithADeepLinkBeyondTheThird', async () => {
    reiniciarAvisosMarcados();
    mockearBase(manyAnnouncements(5));
    server.use(http.post(`${API}/me/announcements/:id/read`, () => new HttpResponse(null, { status: 204 })));
    const user = userEvent.setup();
    montar('/alumno/inicio?aviso=5');

    await screen.findByRole('button', { name: /Aviso 5/ });
    await user.click(screen.getByRole('button', { name: 'Mostrar menos avisos' }));

    expect(screen.queryByRole('button', { name: /Aviso 5/ })).not.toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Ver todos (5)' })).toBeInTheDocument();
  });

  it('collapsesTheListAfterShowAllWhenTheDeepLinkIsWithinTheFirstThree', async () => {
    reiniciarAvisosMarcados();
    mockearBase(manyAnnouncements(5));
    server.use(http.post(`${API}/me/announcements/:id/read`, () => new HttpResponse(null, { status: 204 })));
    const user = userEvent.setup();
    montar('/alumno/inicio?aviso=1');

    await screen.findByRole('button', { name: /Aviso 1/ });
    await user.click(screen.getByRole('button', { name: /Ver todos/ }));
    expect(screen.getByRole('button', { name: /Aviso 5/ })).toBeInTheDocument();
    await user.click(screen.getByRole('button', { name: 'Mostrar menos avisos' }));
    expect(screen.queryByRole('button', { name: /Aviso 5/ })).not.toBeInTheDocument();
  });

  it('revealsAnAnnouncementHiddenBehindShowAll', async () => {
    reiniciarAvisosMarcados();
    mockearBase(manyAnnouncements(5));
    server.use(http.post(`${API}/me/announcements/:id/read`, () => new HttpResponse(null, { status: 204 })));
    montar('/alumno/inicio?aviso=5');

    expect(await screen.findByRole('button', { name: /Aviso 5/ })).toHaveAttribute('aria-expanded', 'true');
    expect(screen.getByRole('button', { name: 'Mostrar menos avisos' })).toBeInTheDocument();
  });

  it('showsAClearMessageWhenTheAnnouncementIsNotAvailable', async () => {
    mockearBase(manyAnnouncements(2));
    montar('/alumno/inicio?aviso=999');

    expect(await screen.findByText('Ese aviso ya no está disponible.')).toBeInTheDocument();
    expect(screen.getByText('Aviso 1')).toBeInTheDocument();
  });

  it('doesNotShowTheMessageWhenTheAnnouncementExists', async () => {
    reiniciarAvisosMarcados();
    mockearBase(manyAnnouncements(2));
    server.use(http.post(`${API}/me/announcements/:id/read`, () => new HttpResponse(null, { status: 204 })));
    montar('/alumno/inicio?aviso=1');

    await screen.findByRole('button', { name: /Aviso 1/ });
    expect(screen.queryByText('Ese aviso ya no está disponible.')).not.toBeInTheDocument();
  });
});

describe('AlumnoInicio — estado de lectura único con la campanita', () => {
  it('marksTheRelatedNotificationReadAndLowersTheBellCountWhenReadingFromNews', async () => {
    reiniciarAvisosMarcados();
    const notification = {
      id: 11,
      type: 'ANNOUNCEMENT_PUBLISHED',
      message: 'Nuevo aviso en Análisis Matemático I',
      read: false,
      relatedEntityType: 'Announcement',
      relatedEntityId: 7,
      createdAt: '2026-11-05T10:00:00',
    };
    const readRequests = [];
    const unreadCountRequests = [];
    mockearBase([unAviso({ id: 7, title: 'Sin abrir' })]);
    server.use(
      http.get(`${API}/me/notifications/unread-count`, () => {
        unreadCountRequests.push(true);
        return HttpResponse.json({ count: notification.read ? 0 : 1 });
      }),
      http.get(`${API}/me/notifications`, () =>
        HttpResponse.json({ items: [notification], page: 0, size: 10, totalElements: 1, totalPages: 1 }),
      ),
      http.post(`${API}/me/announcements/:id/read`, ({ params }) => {
        readRequests.push(params.id);
        if (Number(params.id) === notification.relatedEntityId) notification.read = true;
        return new HttpResponse(null, { status: 204 });
      }),
    );
    renderWithProviders(
      <Routes>
        <Route path="/alumno/inicio" element={<><NotificationBell /><AlumnoInicio /></>} />
      </Routes>,
      { route: '/alumno/inicio' },
    );
    expect(await screen.findByRole('button', { name: 'Notificaciones, 1 sin leer' })).toBeInTheDocument();
    const requestsBeforeReading = unreadCountRequests.length;

    await userEvent.click(await screen.findByRole('button', { name: /Sin abrir/ }));

    expect(await screen.findByRole('button', { name: 'Notificaciones' })).toBeInTheDocument();
    expect(readRequests).toEqual(['7']);
    expect(unreadCountRequests.length).toBeGreaterThan(requestsBeforeReading);
  });
});

describe('AlumnoInicio — cuerpo de los avisos', () => {
  afterEach(() => {
    delete HTMLElement.prototype.scrollHeight;
    delete HTMLElement.prototype.clientHeight;
  });

  it('hidesSeeMoreWhenTheBodyFitsInSixLines', async () => {
    mockearBase([unAviso({ id: 1 })]);
    montar();

    await screen.findByText('Cambio de aula');
    expect(screen.queryByRole('button', { name: 'Ver más' })).not.toBeInTheDocument();
  });

  function mockLongBodyMeasures() {
    Object.defineProperty(HTMLElement.prototype, 'scrollHeight', { configurable: true, get: () => 300 });
    Object.defineProperty(HTMLElement.prototype, 'clientHeight', {
      configurable: true,
      get() {
        return this.className.includes('line-clamp-6') ? 120 : 300;
      },
    });
    const original = window.getComputedStyle.bind(window);
    return vi.spyOn(window, 'getComputedStyle').mockImplementation((element, pseudo) => {
      const real = original(element, pseudo);
      return new Proxy(real, {
        get: (target, property) => {
          if (property === 'lineHeight') return '20px';
          const value = target[property];
          return typeof value === 'function' ? value.bind(target) : value;
        },
      });
    });
  }

  it('offersSeeMoreForLongBodiesAndKeepsSeeLessAfterExpanding', async () => {
    reiniciarAvisosMarcados();
    const computed = mockLongBodyMeasures();
    mockearBase([unAviso({ id: 1, title: 'Largo' }), unAviso({ id: 2, title: 'Otro', createdAt: hoursAgo(2), updatedAt: hoursAgo(2) })]);
    server.use(http.post(`${API}/me/announcements/:id/read`, () => new HttpResponse(null, { status: 204 })));
    const user = userEvent.setup();
    montar();

    const [firstSeeMore] = await screen.findAllByRole('button', { name: 'Ver más' });
    await user.click(firstSeeMore);
    expect(await screen.findByRole('button', { name: 'Ver menos' })).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Largo' })).toHaveAttribute('aria-expanded', 'true');
    expect(screen.getByRole('button', { name: 'Otro' })).toHaveAttribute('aria-expanded', 'false');

    await user.click(screen.getByRole('button', { name: 'Otro' }));
    expect(screen.getByRole('button', { name: 'Largo' })).toHaveAttribute('aria-expanded', 'true');
    expect(screen.getByRole('button', { name: 'Otro' })).toHaveAttribute('aria-expanded', 'true');
    computed.mockRestore();
  });

  it('showsSeeLessForALongAnnouncementOpenedByDeepLink', async () => {
    reiniciarAvisosMarcados();
    const computed = mockLongBodyMeasures();
    mockearBase([unAviso({ id: 1, title: 'Largo' })]);
    server.use(http.post(`${API}/me/announcements/:id/read`, () => new HttpResponse(null, { status: 204 })));
    montar('/alumno/inicio?aviso=1');

    expect(await screen.findByRole('button', { name: 'Ver menos' })).toBeInTheDocument();
    computed.mockRestore();
  });

  it('rendersTheTimeMetaAsATimeElementWithTheDate', async () => {
    const createdAt = hoursAgo(1);
    mockearBase([unAviso({ id: 1, createdAt, updatedAt: createdAt })]);
    montar();

    const meta = await screen.findByText('hace 1 h');
    expect(meta.tagName).toBe('TIME');
    expect(meta).toHaveAttribute('dateTime', createdAt);
  });
});

describe('AlumnoInicio — cursos', () => {
  it('un alumno solo en lista de espera no ve cursos en curso sino su lugar en la espera', async () => {
    mockearBase([]);
    server.use(http.get(`${API}/me/course-enrollments`, () => HttpResponse.json([{ id: 1, status: 'WAITLISTED' }])));
    montar();

    expect(await screen.findByText('Estás en lista de espera de 1 curso')).toBeInTheDocument();
    expect(screen.queryByText(/en curso/)).not.toBeInTheDocument();
  });
});

describe('Contrato NotificationType — todo tipo tiene label y tono', () => {
  const TIPOS_BACKEND = [
    'SECTION_EVAL_CONFIG_PROPOSED',
    'SECTION_EVAL_CONFIG_APPROVED',
    'SECTION_EVAL_CONFIG_REJECTED',
    'SECTION_EVAL_CONFIG_REMINDER',
    'ATTENDANCE_CHANGE_REQUEST_APPROVED',
    'ATTENDANCE_CHANGE_REQUEST_REJECTED',
    'GRADUATION_REQUEST_APPROVED',
    'GRADUATION_REQUEST_REJECTED',
    'DIRECTOR_APPROVAL_REQUEST_APPROVED',
    'DIRECTOR_APPROVAL_REQUEST_REJECTED',
    'COURSE_WAITLIST_EXPIRED',
    'SUSPENSION_LIFTED',
    'PAYMENT_RECEIPT_APPROVED',
    'PAYMENT_RECEIPT_REJECTED',
    'ANNOUNCEMENT_PUBLISHED',
    'ANNOUNCEMENT_UPDATED',
  ];

  it('cada NotificationType tiene label en TIPO_NOTIFICACION_LABEL', () => {
    for (const tipo of TIPOS_BACKEND) {
      expect(TIPO_NOTIFICACION_LABEL[tipo], `${tipo} sin label`).toBeDefined();
    }
  });

  it('cada NotificationType tiene tono en TONO_NOTIFICACION', () => {
    for (const tipo of TIPOS_BACKEND) {
      expect(TONO_NOTIFICACION[tipo], `${tipo} sin tono`).toBeDefined();
    }
  });
});

describe('AlumnoInicio — matrícula de ingreso (D-227)', () => {
  const solicitudAprobada = {
    id: 3,
    programName: 'Diseño Gráfico',
    status: 'APPROVED',
    admissionInvoice: { id: 77, total: { amount: '50000.00', currency: 'ARS' }, dueDate: '2099-03-20', paymentStatus: 'PENDING' },
  };

  it('muestra la card de la matrícula impaga y no dice que no hay saldo pendiente', async () => {
    mockearBase([]);
    server.use(
      http.get(`${API}/me/program-enrollment-applications`, () => HttpResponse.json([solicitudAprobada])),
    );
    montar();

    expect(await screen.findByText('Pagá la matrícula de ingreso para confirmar tu inscripción')).toBeInTheDocument();
    expect(await screen.findByText('Matrícula de ingreso pendiente')).toBeInTheDocument();
    expect(screen.queryByText('Pagala para confirmar tu inscripción.')).not.toBeInTheDocument();
    expect(screen.queryByText('No tenés saldo pendiente.')).not.toBeInTheDocument();
  });

  it('sin matrículas impagas sigue diciendo que no hay saldo pendiente', async () => {
    mockearBase([]);
    server.use(
      http.get(`${API}/me/program-enrollment-applications`, () => HttpResponse.json([])),
    );
    montar();

    expect(await screen.findByText('No tenés saldo pendiente.')).toBeInTheDocument();
    expect(screen.queryByText('Pagá la matrícula de ingreso para confirmar tu inscripción')).not.toBeInTheDocument();
  });
});

describe('AlumnoInicio — acuse de lectura', () => {
  it('marksAnnouncementReadWhenOpenedOnlyOnce', async () => {
    reiniciarAvisosMarcados();
    const marcados = [];
    mockearBase([unAviso({ id: 7, title: 'Sin abrir' })]);
    server.use(
      http.post(`${API}/me/announcements/:id/read`, ({ params }) => {
        marcados.push(params.id);
        return new HttpResponse(null, { status: 204 });
      }),
    );
    montar();
    const titulo = await screen.findByRole('button', { name: /Sin abrir/ });
    expect(screen.getByText('1 sin leer')).toBeInTheDocument();
    await userEvent.click(titulo);
    await userEvent.click(titulo);
    await userEvent.click(titulo);
    await waitFor(() => expect(screen.queryByText('1 sin leer')).not.toBeInTheDocument());
    expect(marcados).toEqual(['7']);
  });

  it('doesNotMarkAlreadyReadAnnouncements', async () => {
    reiniciarAvisosMarcados();
    const marcados = [];
    mockearBase([unAviso({ id: 8, title: 'Ya leído', readAt: hoursAgo(1) })]);
    server.use(
      http.post(`${API}/me/announcements/:id/read`, ({ params }) => {
        marcados.push(params.id);
        return new HttpResponse(null, { status: 204 });
      }),
    );
    montar();
    await userEvent.click(await screen.findByRole('button', { name: /Ya leído/ }));
    expect(screen.queryByText(/sin leer/)).not.toBeInTheDocument();
    expect(marcados).toEqual([]);
  });

  it('keepsReadingWhenMarkingFailsSilently', async () => {
    reiniciarAvisosMarcados();
    mockearBase([unAviso({ id: 9, title: 'Falla en silencio' })]);
    server.use(http.post(`${API}/me/announcements/:id/read`, () => new HttpResponse(null, { status: 500 })));
    montar();
    const titulo = await screen.findByRole('button', { name: /Falla en silencio/ });
    await userEvent.click(titulo);
    expect(titulo).toHaveAttribute('aria-expanded', 'true');
    expect(screen.getByText('La clase del jueves se dicta en el Aula 204.')).toBeInTheDocument();
  });
});
