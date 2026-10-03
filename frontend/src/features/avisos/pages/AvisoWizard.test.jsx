import { describe, expect, it } from 'vitest';
import { screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { http, HttpResponse } from 'msw';
import { Routes, Route } from 'react-router-dom';
import { server } from '@/test/mocks/server';
import { renderWithProviders } from '@/test/renderWithProviders';
import { AvisoWizard } from './AvisoWizard';

const API = 'http://localhost:8080';
const SECTION_ID = 7;

const SEGMENTOS = [
  { key: 'ALL', label: 'Toda la comisión', count: 23, needsInstances: false, needsThreshold: false, instanceCount: null },
  { key: 'EXAM_FAILED', label: 'Desaprobaron parciales…', count: null, needsInstances: true, needsThreshold: false, instanceCount: 2 },
  { key: 'ABSENCES', label: 'Con N o más inasistencias…', count: null, needsInstances: false, needsThreshold: true, instanceCount: null },
];

function preview(extra = {}) {
  return {
    count: 8,
    totalInSection: 23,
    label: 'Toda la comisión',
    hasUngradedInstances: false,
    students: [{ studentId: 1, file: 'A-1042', name: 'Valentina Suárez', examScores: {}, tpScores: {}, finalScores: {}, absences: 0 }],
    ...extra,
  };
}

function mockear({ previewBody = preview(), onPost } = {}) {
  server.use(
    http.get(`${API}/subject-sections/${SECTION_ID}/audience-segments`, () => HttpResponse.json(SEGMENTOS)),
    http.post(`${API}/subject-sections/${SECTION_ID}/audience-preview`, () => HttpResponse.json(previewBody)),
    http.post(`${API}/subject-sections/${SECTION_ID}/announcements`, onPost ?? (() => HttpResponse.json({ id: 1 }, { status: 201 }))),
  );
}

function montar(route = `/docente/mis-comisiones/${SECTION_ID}/avisos/nuevo`) {
  return renderWithProviders(
    <Routes>
      <Route path="/docente/mis-comisiones/:sectionId/avisos/nuevo" element={<AvisoWizard />} />
      <Route path="/docente/mis-comisiones/:sectionId" element={<div>Volvió a la comisión</div>} />
    </Routes>,
    { route },
  );
}

async function irAMensaje(user) {
  await screen.findByText('Toda la comisión');
  await user.click(await screen.findByRole('button', { name: 'Siguiente' }));
}

describe('AvisoWizard', () => {
  it('arranca en Destinatarios con toda la comisión y su conteo', async () => {
    mockear();
    montar();

    expect(await screen.findByRole('tab', { name: /Destinatarios/ }).catch(() => null)).toBeNull();
    expect(await screen.findByText('Destinatarios')).toBeInTheDocument();
    expect(await screen.findByText('8')).toBeInTheDocument();
    expect(screen.getByText('de 23 alumnos de la comisión')).toBeInTheDocument();
  });

  it('un segmento con parámetros muestra los chips de instancias y el cuantificador', async () => {
    mockear();
    const user = userEvent.setup();
    montar();

    await user.click(await screen.findByRole('combobox'));
    await user.click(await screen.findByRole('option', { name: 'Desaprobaron parciales…' }));

    expect(await screen.findByRole('button', { name: '1° parcial' })).toBeInTheDocument();
    expect(screen.getByRole('button', { name: '2° parcial' })).toBeInTheDocument();
    expect(screen.getByRole('radio', { name: 'alguno de ellos' })).toHaveAttribute('aria-checked', 'true');
    expect(screen.getByRole('radio', { name: 'todos ellos' })).toBeInTheDocument();
  });

  it('un segmento que no alcanza a nadie bloquea el avance y lo explica', async () => {
    mockear({ previewBody: preview({ count: 0, students: [] }) });
    montar();

    expect(await screen.findByText(/Ningún alumno cumple esta condición hoy/)).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Siguiente' })).toBeDisabled();
  });

  it('publicar está deshabilitado sin título o sin mensaje', async () => {
    mockear();
    const user = userEvent.setup();
    montar();
    await irAMensaje(user);

    expect(await screen.findByRole('button', { name: 'Publicar aviso' })).toBeDisabled();

    await user.type(screen.getByLabelText('Título'), 'Cambio de aula');
    expect(screen.getByRole('button', { name: 'Publicar aviso' })).toBeDisabled();

    await user.type(screen.getByLabelText('Mensaje'), 'Nos vemos en la 204.');
    expect(screen.getByRole('button', { name: 'Publicar aviso' })).toBeEnabled();
  });

  it('el hint de privacidad aparece solo cuando la audiencia es un subconjunto', async () => {
    mockear();
    const user = userEvent.setup();
    montar();
    await irAMensaje(user);

    expect(screen.queryByText(/No hace falta aclarar a quién le hablás/)).not.toBeInTheDocument();
  });

  it('programar el envío cambia el CTA y avisa que el grupo se calcula al enviarse', async () => {
    mockear();
    const user = userEvent.setup();
    montar();

    await user.click(await screen.findByRole('combobox'));
    await user.click(await screen.findByRole('option', { name: 'Desaprobaron parciales…' }));
    await user.click(screen.getByRole('button', { name: 'Siguiente' }));

    await user.click(await screen.findByRole('button', { name: 'Cambiar fechas' }));
    await user.click(screen.getByRole('radio', { name: 'Programar…' }));

    expect(await screen.findByRole('button', { name: 'Programar envío' })).toBeInTheDocument();
    expect(screen.getByText(/Cuando salga, solo lo van a ver los alumnos que cumplan esa condición/)).toBeInTheDocument();
  });

  it('si la publicación falla, el formulario queda abierto con el texto intacto', async () => {
    mockear({ onPost: () => new HttpResponse(null, { status: 500 }) });
    const user = userEvent.setup();
    montar();
    await irAMensaje(user);

    await user.type(await screen.findByLabelText('Título'), 'Cambio de aula');
    await user.type(screen.getByLabelText('Mensaje'), 'Nos vemos en la 204.');
    await user.click(screen.getByRole('button', { name: 'Publicar aviso' }));

    expect(await screen.findByDisplayValue('Cambio de aula')).toBeInTheDocument();
    expect(screen.getByDisplayValue('Nos vemos en la 204.')).toBeInTheDocument();
    expect(screen.queryByText('Volvió a la comisión')).not.toBeInTheDocument();
  });

  it('un curso suelto no tiene paso de destinatarios ni selector', async () => {
    server.use(
      http.post(`${API}/course-sections/${SECTION_ID}/announcements`, () => HttpResponse.json({ id: 1 }, { status: 201 })),
    );
    montar(`/docente/mis-comisiones/${SECTION_ID}/avisos/nuevo?type=COURSE`);

    expect(await screen.findByText('Todo el curso')).toBeInTheDocument();
    expect(screen.queryByRole('combobox', { name: /A quiénes/ })).not.toBeInTheDocument();
    expect(screen.getByLabelText('Título')).toBeInTheDocument();
  });

  it('salir con texto escrito pide confirmación en vez de descartarlo en silencio', async () => {
    mockear();
    const user = userEvent.setup();
    montar();
    await irAMensaje(user);

    await user.type(await screen.findByLabelText('Título'), 'Algo a medio escribir');
    await user.click(screen.getByRole('button', { name: 'Cancelar' }));

    const dialogo = await screen.findByRole('dialog');
    expect(within(dialogo).getByText('Salir sin publicar')).toBeInTheDocument();
    expect(within(dialogo).getByRole('button', { name: 'Seguir escribiendo' })).toHaveFocus();
  });

  it('en una comisión ajena bloquea el asistente en vez de mostrarlo vacío', async () => {
    server.use(
      http.get(`${API}/subject-sections/${SECTION_ID}/audience-segments`, () => new HttpResponse(null, { status: 403 })),
      http.post(`${API}/subject-sections/${SECTION_ID}/audience-preview`, () => new HttpResponse(null, { status: 403 })),
    );
    montar();

    expect(await screen.findByText('No podés publicar avisos en esta comisión')).toBeInTheDocument();
    expect(screen.queryByText('Destinatarios')).not.toBeInTheDocument();
  });
});
