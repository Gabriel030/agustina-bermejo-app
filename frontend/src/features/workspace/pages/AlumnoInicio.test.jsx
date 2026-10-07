import { describe, expect, it, vi } from 'vitest';
import { screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { http, HttpResponse } from 'msw';
import { Routes, Route } from 'react-router-dom';
import { server } from '@/test/mocks/server';
import { renderWithProviders } from '@/test/renderWithProviders';
import { AlumnoInicio } from '@/features/workspace/pages/AlumnoInicio';
import { TIPO_NOTIFICACION_LABEL, TONO_NOTIFICACION } from '@/lib/notificaciones';

vi.mock('@/context/AuthContext', () => ({
  useAuth: () => ({ usuario: { nombre: 'Ana', apellido: 'Pérez' } }),
}));

const API = 'http://localhost:8080';

const AHORA = new Date().toISOString();
const HACE_1H = new Date(Date.now() - 60 * 60 * 1000).toISOString();
const HACE_2H = new Date(Date.now() - 2 * 60 * 60 * 1000).toISOString();

function unAviso(extra = {}) {
  return {
    id: 1,
    subjectName: 'Análisis Matemático I',
    title: 'Cambio de aula',
    body: 'La clase del jueves se dicta en el Aula 204.',
    category: 'SCHEDULING',
    urgent: false,
    teacherName: 'Ana Docente',
    createdAt: HACE_1H,
    updatedAt: HACE_1H,
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
      unAviso({ id: 1, title: 'Aviso viejo', createdAt: HACE_2H, updatedAt: HACE_2H }),
      unAviso({ id: 2, title: 'Aviso nuevo', createdAt: HACE_1H, updatedAt: HACE_1H }),
    ];
    mockearBase(avisos);
    montar();

    const titulos = await screen.findAllByText(/Aviso viejo|Aviso nuevo/);
    const textos = titulos.map((el) => el.textContent);
    expect(textos.indexOf('Aviso nuevo')).toBeLessThan(textos.indexOf('Aviso viejo'));
  });

  it('urgente se muestra con pill pero no se reordena', async () => {
    const avisos = [
      unAviso({ id: 1, title: 'Normal', urgent: false, createdAt: HACE_1H, updatedAt: HACE_1H }),
      unAviso({ id: 2, title: 'Urgente', urgent: true, createdAt: HACE_2H, updatedAt: HACE_2H }),
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

  it('no muestra "editado" cuando updatedAt === createdAt', async () => {
    mockearBase([unAviso({ id: 1, createdAt: HACE_1H, updatedAt: HACE_1H })]);
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
