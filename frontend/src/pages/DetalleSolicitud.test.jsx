import { describe, expect, it } from 'vitest';
import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { http, HttpResponse } from 'msw';
import { MemoryRouter, Route, Routes } from 'react-router-dom';
import { server } from '@/test/mocks/server';
import { DetalleSolicitud } from '@/pages/DetalleSolicitud';

const API = 'http://localhost:8080';

function renderDetalle(id = '1') {
  return render(
    <MemoryRouter initialEntries={[`/mis-solicitudes/${id}`]}>
      <Routes>
        <Route path="/mis-solicitudes/:id" element={<DetalleSolicitud />} />
        <Route path="/mis-solicitudes" element={<div>Bandeja de mis solicitudes</div>} />
      </Routes>
    </MemoryRouter>,
  );
}

describe('DetalleSolicitud', () => {
  it('muestra carrera, estado y los 3 slots de documentación cuando está pendiente', async () => {
    server.use(
      http.get(`${API}/me/program-enrollment-applications`, () =>
        HttpResponse.json([{ id: 1, programName: 'Diseño Gráfico', status: 'PENDING', requestedAt: '2026-03-02T10:00:00' }]),
      ),
    );
    renderDetalle('1');

    expect(await screen.findByText('Diseño Gráfico')).toBeInTheDocument();
    expect(screen.getByText('Pendiente')).toBeInTheDocument();
    expect(screen.getByText('DNI')).toBeInTheDocument();
    expect(screen.getByText('Analítico de secundario')).toBeInTheDocument();
    expect(screen.getByText('Foto')).toBeInTheDocument();
  });

  it('muestra el motivo de rechazo sin la lista de documentos cuando está rechazada', async () => {
    server.use(
      http.get(`${API}/me/program-enrollment-applications`, () =>
        HttpResponse.json([
          {
            id: 2,
            programName: 'Administración de Empresas',
            status: 'REJECTED',
            requestedAt: '2026-01-10T10:00:00',
            rejectionReason: 'Documentación ilegible',
          },
        ]),
      ),
    );
    renderDetalle('2');

    expect(await screen.findByText(/Documentación ilegible/)).toBeInTheDocument();
    expect(screen.queryByText('DNI')).not.toBeInTheDocument();
  });

  it('muestra "no encontrada" para un id que no está en la lista propia', async () => {
    server.use(
      http.get(`${API}/me/program-enrollment-applications`, () =>
        HttpResponse.json([{ id: 1, programName: 'Diseño Gráfico', status: 'PENDING', requestedAt: '2026-03-02T10:00:00' }]),
      ),
    );
    renderDetalle('999');

    expect(await screen.findByText('Solicitud no encontrada')).toBeInTheDocument();
  });

  it('sube un documento y lo refleja como subido en la lista', async () => {
    server.use(
      http.get(`${API}/me/program-enrollment-applications`, () =>
        HttpResponse.json([{ id: 1, programName: 'Diseño Gráfico', status: 'PENDING', requestedAt: '2026-03-02T10:00:00' }]),
      ),
      http.post(`${API}/me/program-enrollment-applications/1/documents`, () => new HttpResponse(null, { status: 201 })),
    );
    const user = userEvent.setup();
    renderDetalle('1');

    await screen.findByText('DNI');
    // DNI es el primer TIPOS_DOCUMENTO — mismo orden que los inputs en el DOM.
    const [fileInput] = document.querySelectorAll('input[type="file"]');
    const file = new File(['contenido'], 'dni-frente.pdf', { type: 'application/pdf' });

    await user.upload(fileInput, file);

    expect(await screen.findByText(/dni-frente\.pdf · subido/)).toBeInTheDocument();
  });
});
