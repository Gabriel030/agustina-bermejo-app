import { describe, expect, it } from 'vitest';
import { screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { http, HttpResponse } from 'msw';
import { Routes, Route } from 'react-router-dom';
import { server } from '@/test/mocks/server';
import { renderWithProviders } from '@/test/renderWithProviders';
import { DocenteComisionDetalle } from './DocenteComisionDetalle';

const API = 'http://localhost:8080';
const SECTION_ID = 7;

const UN_ANIO_ADELANTE = new Date(Date.now() + 365 * 24 * 60 * 60 * 1000).toISOString();
const UN_ANIO_ATRAS = new Date(Date.now() - 365 * 24 * 60 * 60 * 1000).toISOString();

function unaConfig(extra = {}) {
  return {
    id: 1,
    subjectSectionId: SECTION_ID,
    subjectName: 'Análisis Matemático I',
    sectionName: 'C1',
    status: 'APPROVED',
    tpCount: 0,
    examCountExpected: 2,
    termStartDate: UN_ANIO_ATRAS,
    termEndDate: UN_ANIO_ADELANTE,
    instructors: [],
    canEdit: true,
    ...extra,
  };
}

function unAviso(extra = {}) {
  return {
    id: 10,
    sectionType: 'PROGRAM',
    sectionId: SECTION_ID,
    title: 'Cambio de aula del jueves',
    body: 'La clase del jueves se dicta en el Aula 204.',
    category: 'SCHEDULING',
    urgent: false,
    expiresAt: null,
    audienceLabel: 'Toda la comisión',
    status: 'SENT',
    scheduledFor: null,
    sentAt: '2026-09-23T10:00:00',
    createdBy: 'Ana Docente',
    createdAt: '2026-09-23T10:00:00',
    updatedAt: null,
    ...extra,
  };
}

function montar(route = `/docente/mis-comisiones/${SECTION_ID}`) {
  return renderWithProviders(
    <Routes>
      <Route path="/docente/mis-comisiones/:sectionId" element={<DocenteComisionDetalle />} />
    </Routes>,
    { route },
  );
}

function mockearConfig(config = unaConfig()) {
  server.use(
    http.get(`${API}/me/section-evaluation-configs/${SECTION_ID}`, () => HttpResponse.json(config)),
    http.get(`${API}/holidays`, () => HttpResponse.json([])),
  );
}

function mockearAvisos(avisos) {
  server.use(http.get(`${API}/subject-sections/${SECTION_ID}/announcements`, () => HttpResponse.json(avisos)));
}

describe('DocenteComisionDetalle', () => {
  it('sin query param abre la pestaña Evaluación', async () => {
    mockearConfig();
    mockearAvisos([]);
    montar();

    expect(await screen.findByRole('heading', { name: 'Análisis Matemático I · C1' })).toBeInTheDocument();
    expect(screen.getByRole('tab', { name: 'Evaluación' })).toHaveAttribute('data-state', 'active');
    expect(screen.queryByRole('table')).not.toBeInTheDocument();
  });

  it('con ?tab=avisos muestra la tabla de avisos', async () => {
    mockearConfig();
    mockearAvisos([unAviso()]);
    montar(`/docente/mis-comisiones/${SECTION_ID}?tab=avisos`);

    expect(await screen.findByText('Cambio de aula del jueves')).toBeInTheDocument();
    expect(screen.getByRole('table')).toBeInTheDocument();
  });

  it('un curso suelto no renderiza la barra de pestañas', async () => {
    server.use(
      http.get(`${API}/me/teaching-sections`, () =>
        HttpResponse.json([{ id: SECTION_ID, type: 'COURSE', subjectName: 'Illustrator', sectionName: 'Turno noche' }]),
      ),
      http.get(`${API}/course-sections/${SECTION_ID}/announcements`, () => HttpResponse.json([unAviso()])),
    );
    montar(`/docente/mis-comisiones/${SECTION_ID}?type=COURSE`);

    expect(await screen.findByRole('heading', { name: 'Illustrator · Turno noche' })).toBeInTheDocument();
    expect(screen.queryByRole('tab')).not.toBeInTheDocument();
  });

  it('una comisión cerrada no ofrece acciones sobre los avisos', async () => {
    mockearConfig(unaConfig({ termEndDate: UN_ANIO_ATRAS }));
    mockearAvisos([unAviso()]);
    const user = userEvent.setup();
    montar(`/docente/mis-comisiones/${SECTION_ID}?tab=avisos`);

    await user.click(await screen.findByText('Cambio de aula del jueves'));

    expect(screen.queryByRole('button', { name: 'Nuevo aviso' })).not.toBeInTheDocument();
    expect(screen.queryByRole('button', { name: 'Editar' })).not.toBeInTheDocument();
    expect(screen.queryByRole('button', { name: 'Eliminar' })).not.toBeInTheDocument();
  });

  it('tocar la fila despliega el mensaje del aviso', async () => {
    mockearConfig();
    mockearAvisos([unAviso()]);
    const user = userEvent.setup();
    montar(`/docente/mis-comisiones/${SECTION_ID}?tab=avisos`);

    await user.click(await screen.findByText('Cambio de aula del jueves'));

    expect(screen.getByText('Mensaje')).toBeInTheDocument();
    expect(screen.getByText('La clase del jueves se dicta en el Aula 204.')).toBeInTheDocument();
  });

  it('un aviso programado avisa que el segmento se calcula al enviarlo', async () => {
    mockearConfig();
    mockearAvisos([unAviso({ status: 'SCHEDULED', sentAt: null, scheduledFor: '2026-12-01T08:00:00' })]);
    const user = userEvent.setup();
    montar(`/docente/mis-comisiones/${SECTION_ID}?tab=avisos`);

    await user.click(await screen.findByText('Cambio de aula del jueves'));

    expect(screen.getByText(/se calcula en el momento del envío/)).toBeInTheDocument();
  });

  it('tocar el pill de audiencia abre los destinatarios y no despliega la fila', async () => {
    mockearConfig();
    mockearAvisos([unAviso({ audienceLabel: 'Desaprobaron 2° parcial' })]);
    server.use(
      http.get(`${API}/announcements/10/recipients`, () =>
        HttpResponse.json([{ studentId: 1, file: 'A-1042', name: 'Valentina Suárez' }]),
      ),
    );
    const user = userEvent.setup();
    montar(`/docente/mis-comisiones/${SECTION_ID}?tab=avisos`);

    await user.click(await screen.findByRole('button', { name: 'Ver destinatarios: Desaprobaron 2° parcial' }));

    const dialogo = await screen.findByRole('dialog');
    expect(within(dialogo).getByText('A-1042')).toBeInTheDocument();
    expect(within(dialogo).getByText('Valentina Suárez')).toBeInTheDocument();
    expect(screen.queryByText('Mensaje')).not.toBeInTheDocument();
  });

  it('con error de la query muestra el card de error y no el estado vacío', async () => {
    mockearConfig();
    server.use(
      http.get(`${API}/subject-sections/${SECTION_ID}/announcements`, () => new HttpResponse(null, { status: 500 })),
    );
    montar(`/docente/mis-comisiones/${SECTION_ID}?tab=avisos`);

    expect(await screen.findByText('No pudimos cargar los avisos')).toBeInTheDocument();
    expect(screen.queryByText(/Todavía no publicaste avisos/)).not.toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Reintentar' })).toBeInTheDocument();
  });

  it('una comisión de un ciclo anterior sin configuración muestra la ficha en vez de una pantalla en blanco', async () => {
    server.use(
      http.get(`${API}/me/section-evaluation-configs/${SECTION_ID}`, () => new HttpResponse(null, { status: 404 })),
      http.get(`${API}/me/teaching-sections`, () =>
        HttpResponse.json([
          { id: SECTION_ID, type: 'PROGRAM', subjectName: 'Pedagogía', sectionName: 'Comisión A', termStartDate: '2024-03-04T00:00:00', termEndDate: '2024-12-13T00:00:00' },
        ]),
      ),
    );
    montar();

    expect(await screen.findByRole('heading', { name: 'Pedagogía · Comisión A' })).toBeInTheDocument();
    expect(screen.getByText('Esta comisión no tiene configuración de evaluación')).toBeInTheDocument();
  });

  it('una comisión que no es del docente o no existe muestra un estado de error', async () => {
    server.use(
      http.get(`${API}/me/section-evaluation-configs/${SECTION_ID}`, () => new HttpResponse(null, { status: 403 })),
      http.get(`${API}/me/teaching-sections`, () => HttpResponse.json([])),
    );
    montar();

    expect(await screen.findByText('No encontramos esta comisión')).toBeInTheDocument();
    expect(screen.getByRole('link', { name: 'Volver a Mis comisiones' })).toBeInTheDocument();
  });

  it('un borrador con el plazo vencido no se puede enviar y explica por qué', async () => {
    mockearConfig(unaConfig({ status: 'DRAFT', canEdit: true, canSubmit: false, hasFinal: true }));
    montar();

    expect(await screen.findByText(/Venció el plazo para enviarla/)).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Enviar a aprobación' })).toBeDisabled();
  });

  it('una configuración rechazada muestra el motivo y se puede reenviar', async () => {
    mockearConfig(
      unaConfig({
        status: 'REJECTED',
        rejectionReason: 'El 2do llamado cae fuera del ciclo',
        canEdit: true,
        canSubmit: true,
        hasFinal: true,
        tpCount: 1,
        tp1DueDate: '2026-04-24',
        exam1Date: '2026-05-15',
        exam2Date: '2026-09-11',
        finalCall1Date: '2026-12-02',
        finalCall2Date: '2026-12-10',
        termEndDate: '2026-12-11T00:00:00',
      }),
    );
    montar();

    expect(await screen.findByText(/El 2do llamado cae fuera del ciclo/)).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Reenviar a aprobación' })).toBeEnabled();
  });

  it('un error de fecha del backend se explica con su motivo', async () => {
    mockearConfig(unaConfig({ status: 'DRAFT', canEdit: true, canSubmit: true, hasFinal: true }));
    server.use(
      http.put(`${API}/me/section-evaluation-configs/${SECTION_ID}`, () =>
        HttpResponse.json({ code: 'EVALUATION_DATE_ON_HOLIDAY' }, { status: 400 }),
      ),
    );
    const user = userEvent.setup();
    montar();

    await user.click(await screen.findByRole('button', { name: 'Guardar borrador' }));

    expect(await screen.findByText(/Una de las fechas cae en un feriado/)).toBeInTheDocument();
  });

  it('marca la fecha que cae después del cierre del cuatrimestre', async () => {
    mockearConfig(
      unaConfig({
        status: 'DRAFT',
        canEdit: true,
        canSubmit: true,
        hasFinal: true,
        termStartDate: '2026-03-02T00:00:00',
        termEndDate: '2026-12-11T00:00:00',
        finalCall2Date: '2026-12-16',
      }),
    );
    montar();

    expect(await screen.findByText('Fuera del cuatrimestre')).toBeInTheDocument();
  });

  it('si el esquema no tiene final no pide llamados a final', async () => {
    mockearConfig(unaConfig({ status: 'DRAFT', canEdit: true, canSubmit: true, hasFinal: false }));
    montar();

    expect(await screen.findByText('Parciales')).toBeInTheDocument();
    expect(screen.queryByText('1er llamado a final')).not.toBeInTheDocument();
  });

  it('el diálogo de borrado nombra el aviso y enfoca la salida segura', async () => {
    mockearConfig();
    mockearAvisos([unAviso()]);
    const user = userEvent.setup();
    montar(`/docente/mis-comisiones/${SECTION_ID}?tab=avisos`);

    await user.click(await screen.findByText('Cambio de aula del jueves'));
    await user.click(screen.getByRole('button', { name: 'Eliminar' }));

    const dialogo = await screen.findByRole('dialog');
    expect(within(dialogo).getByText('Eliminar “Cambio de aula del jueves”')).toBeInTheDocument();
    expect(within(dialogo).getByRole('button', { name: 'Volver' })).toHaveFocus();
  });
});
