import { describe, expect, it, vi } from 'vitest';
import { render, screen, within } from '@testing-library/react';
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
    expect(screen.getByText('DNI — frente')).toBeInTheDocument();
    expect(screen.getByText('DNI — dorso')).toBeInTheDocument();
    expect(screen.getByText('Analítico del secundario')).toBeInTheDocument();
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
    expect(screen.queryByText('DNI — frente')).not.toBeInTheDocument();
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

  // Auditoría 2026-09-04 (calidad de tests): el handler devolvía siempre 201 sin mirar el
  // FormData/query param real. Se espía fetch (no request.formData() dentro del handler de MSW,
  // que cuelga con un File real en este entorno — ver el comentario en registro.integration.test.jsx).
  it('sube un documento y lo refleja como subido en la lista', async () => {
    const fetchSpy = vi.spyOn(globalThis, 'fetch');
    server.use(
      http.get(`${API}/me/program-enrollment-applications`, () =>
        HttpResponse.json([{ id: 1, programName: 'Diseño Gráfico', status: 'PENDING', requestedAt: '2026-03-02T10:00:00' }]),
      ),
      http.post(`${API}/me/program-enrollment-applications/1/documents`, () => new HttpResponse(null, { status: 201 })),
    );
    const user = userEvent.setup();
    renderDetalle('1');

    const dniFrenteLabel = await screen.findByText('DNI — frente');
    // Corrección 2026-09-04 (auditoría de calidad de tests): antes se tomaba el input por
    // posición en el DOM ("es el primero"), frágil ante un reordenamiento de TIPOS_DOCUMENTO que
    // rompería el test en silencio sin que hubiera ningún fallo real de la feature — se ubica acá
    // el input real asociado a la fila "DNI — frente" navegando desde su propio label visible.
    const fileInput = dniFrenteLabel.closest('.border-b').querySelector('input[type="file"]');
    const file = new File(['contenido'], 'dni-frente.pdf', { type: 'application/pdf' });

    await user.upload(fileInput, file);

    expect(await screen.findByText(/dni-frente\.pdf · subido/)).toBeInTheDocument();

    const [url, opts] = fetchSpy.mock.calls.find(
      ([callUrl, callOpts]) => String(callUrl).includes('/documents?type=') && callOpts?.method === 'POST',
    );
    expect(new URL(url).searchParams.get('type')).toBe('DNI_FRENTE');
    expect(opts.body.get('file').name).toBe('dni-frente.pdf');
  });
  it('does not ask an approved application to upload documents', async () => {
    server.use(
      http.get(`${API}/me/program-enrollment-applications`, () =>
        HttpResponse.json([{ id: 7, programName: 'Profesorado de Educación Primaria', status: 'APPROVED', requestedAt: '2026-03-02T10:00:00' }]),
      ),
    );
    renderDetalle('7');

    expect(await screen.findByText(/Solicitud aprobada/)).toBeInTheDocument();
    expect(screen.queryByText(/Subí los 3 documentos/)).not.toBeInTheDocument();
    expect(screen.queryByRole('button', { name: 'Subir archivo' })).not.toBeInTheDocument();
  });

  it('shows the documents the student already uploaded in a previous visit', async () => {
    server.use(
      http.get(`${API}/me/program-enrollment-applications`, () =>
        HttpResponse.json([{ id: 10, programName: 'Diseño Gráfico', status: 'PENDING', requestedAt: '2026-03-02T10:00:00' }]),
      ),
      http.get(`${API}/me/documents`, () =>
        HttpResponse.json([{ id: 1, type: 'DNI_FRENTE', uploadedAt: '2026-03-02T10:05:00' }]),
      ),
    );
    renderDetalle('10');

    const dniFrenteRow = (await screen.findByText('DNI — frente')).closest('.border-b');
    expect(await within(dniFrenteRow).findByText(/Subido el 2 (de )?mar (de )?2026/)).toBeInTheDocument();
    expect(within(dniFrenteRow).getByRole('button', { name: 'Reemplazar' })).toBeInTheDocument();
    const dniDorsoRow = screen.getByText('DNI — dorso').closest('.border-b');
    expect(within(dniDorsoRow).getByText('Sin subir todavía')).toBeInTheDocument();
  });
});
