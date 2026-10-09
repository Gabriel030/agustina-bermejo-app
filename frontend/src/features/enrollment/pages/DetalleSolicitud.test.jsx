import { describe, expect, it, vi } from 'vitest';
import { render, screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { http, HttpResponse } from 'msw';
import { MemoryRouter, Route, Routes } from 'react-router-dom';
import { server } from '@/test/mocks/server';
import { DetalleSolicitud } from '@/features/enrollment/pages/DetalleSolicitud';

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
    expect(screen.getAllByText('En revisión')).toHaveLength(2);
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

  it('muestra la matrícula de ingreso a pagar cuando la solicitud está aprobada y la factura impaga (D-227)', async () => {
    server.use(
      http.get(`${API}/me/program-enrollment-applications`, () =>
        HttpResponse.json([
          {
            id: 3,
            programName: 'Diseño Gráfico',
            status: 'APPROVED',
            requestedAt: '2026-03-02T10:00:00',
            admissionInvoice: { id: 77, total: { amount: '50000.00', currency: 'ARS' }, dueDate: '2026-03-20', paymentStatus: 'PENDING' },
          },
        ]),
      ),
    );
    renderDetalle('3');

    expect(await screen.findByText('Matrícula de ingreso')).toBeInTheDocument();
    expect(screen.getByText('$ 50.000,00')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Pagar matrícula' })).toBeInTheDocument();
  });

  it('confirma la inscripción cuando la matrícula de ingreso ya está paga (D-227)', async () => {
    server.use(
      http.get(`${API}/me/program-enrollment-applications`, () =>
        HttpResponse.json([
          {
            id: 4,
            programName: 'Diseño Gráfico',
            status: 'APPROVED',
            requestedAt: '2026-03-02T10:00:00',
            programEnrollmentId: 15,
            admissionInvoice: { id: 78, total: { amount: '50000.00', currency: 'ARS' }, dueDate: '2026-03-20', paymentStatus: 'PAID' },
          },
        ]),
      ),
    );
    renderDetalle('4');

    expect(await screen.findByText(/tu inscripción a la carrera está confirmada/)).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: 'Pagar matrícula' })).not.toBeInTheDocument();
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

  it('en estado pendiente explica que no se cobró nada y que la factura llega al aprobarla', async () => {
    server.use(
      http.get(`${API}/me/program-enrollment-applications`, () =>
        HttpResponse.json([{ id: 1, programName: 'Diseño Gráfico', status: 'PENDING', requestedAt: '2026-03-02T10:00:00' }]),
      ),
    );
    renderDetalle('1');

    expect(await screen.findByText(/No se te cobró nada: si la aprueban, vas a recibir la factura de la matrícula de ingreso/)).toBeInTheDocument();
    expect(screen.queryByText(/Subí los 3 documentos/)).not.toBeInTheDocument();
  });

  it('aprobada sin factura (solicitud previa a D-227) remite a Secretaría sin prometer una factura inminente', async () => {
    server.use(
      http.get(`${API}/me/program-enrollment-applications`, () =>
        HttpResponse.json([{ id: 1, programName: 'Diseño Gráfico', status: 'APPROVED', requestedAt: '2026-03-02T10:00:00', admissionInvoice: null }]),
      ),
    );
    renderDetalle('1');

    expect(await screen.findByText(/Consultá con Secretaría el pago de la matrícula de ingreso\./)).toBeInTheDocument();
    expect(screen.queryByText(/Estamos generando la factura/)).not.toBeInTheDocument();
  });

  it('aprobada con la matrícula impaga promete la confirmación cuando se confirme el pago y muestra el badge de pago pendiente', async () => {
    server.use(
      http.get(`${API}/me/program-enrollment-applications`, () =>
        HttpResponse.json([
          {
            id: 1,
            programName: 'Diseño Gráfico',
            status: 'APPROVED',
            requestedAt: '2026-03-02T10:00:00',
            admissionInvoice: { id: 77, total: { amount: '50000.00', currency: 'ARS' }, dueDate: '2099-03-20', paymentStatus: 'PENDING' },
          },
        ]),
      ),
    );
    renderDetalle('1');

    expect(await screen.findByText('Pagá la matrícula')).toBeInTheDocument();
    expect(screen.getByText(/Cuando confirmemos el pago, tu inscripción a la carrera queda confirmada\./)).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Pagar matrícula' })).toBeInTheDocument();
  });

  it('con el comprobante en revisión no ofrece pagar de nuevo', async () => {
    server.use(
      http.get(`${API}/me/program-enrollment-applications`, () =>
        HttpResponse.json([
          {
            id: 1,
            programName: 'Diseño Gráfico',
            status: 'APPROVED',
            requestedAt: '2026-03-02T10:00:00',
            admissionInvoice: {
              id: 77,
              total: { amount: '50000.00', currency: 'ARS' },
              dueDate: '2099-03-20',
              paymentStatus: 'PENDING',
              receiptStatus: 'PENDING_REVIEW',
              receiptUploadedAt: '2026-10-02T10:00:00',
            },
          },
        ]),
      ),
    );
    renderDetalle('1');

    expect((await screen.findAllByText('Pago en revisión')).length).toBeGreaterThan(0);
    expect(screen.getByRole('button', { name: 'Ver estado del pago' })).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: 'Pagar matrícula' })).not.toBeInTheDocument();
    expect(screen.getByText(/Subiste el comprobante el .*; cuando lo aprobemos/)).toBeInTheDocument();
  });

  it('aprobada con la matrícula pagada y la inscripción creada figura como inscripto', async () => {
    server.use(
      http.get(`${API}/me/program-enrollment-applications`, () =>
        HttpResponse.json([
          {
            id: 1,
            programName: 'Diseño Gráfico',
            status: 'APPROVED',
            requestedAt: '2026-03-02T10:00:00',
            programEnrollmentId: 15,
            admissionInvoice: { id: 77, total: { amount: '50000.00', currency: 'ARS' }, dueDate: '2026-03-20', paymentStatus: 'PAID' },
          },
        ]),
      ),
    );
    renderDetalle('1');

    expect(await screen.findAllByText('Inscripto')).toHaveLength(2);
    expect(screen.getByText(/tu inscripción a la carrera está confirmada/)).toBeInTheDocument();
  });

  it('aprobada con la matrícula pagada pero sin inscripción todavía dice que se está confirmando', async () => {
    server.use(
      http.get(`${API}/me/program-enrollment-applications`, () =>
        HttpResponse.json([
          {
            id: 1,
            programName: 'Diseño Gráfico',
            status: 'APPROVED',
            requestedAt: '2026-03-02T10:00:00',
            programEnrollmentId: null,
            admissionInvoice: { id: 77, total: { amount: '50000.00', currency: 'ARS' }, dueDate: '2026-03-20', paymentStatus: 'PAID' },
          },
        ]),
      ),
    );
    renderDetalle('1');

    expect(await screen.findByText('Confirmando inscripción')).toBeInTheDocument();
    expect(screen.getByText(/Estamos confirmando tu inscripción en Diseño Gráfico\./)).toBeInTheDocument();
    expect(screen.getAllByText('Inscripto')).toHaveLength(1);
    expect(screen.queryByText(/tu inscripción a la carrera está confirmada/)).not.toBeInTheDocument();
  });

  it('muestra el avance de la solicitud en el stepper y una salida desde el rechazo', async () => {
    server.use(
      http.get(`${API}/me/program-enrollment-applications`, () =>
        HttpResponse.json([
          { id: 2, programName: 'Administración de Empresas', status: 'REJECTED', requestedAt: '2026-01-10T10:00:00', rejectionReason: 'Falta analítico' },
        ]),
      ),
    );
    const user = userEvent.setup();
    renderDetalle('2');

    expect(await screen.findByText('Solicitud enviada')).toBeInTheDocument();
    expect(screen.getAllByText('Rechazada').length).toBeGreaterThan(0);
    expect(screen.getByRole('button', { name: 'Ver el catálogo' })).toBeInTheDocument();
    await user.click(screen.getByRole('button', { name: 'Ver el catálogo' }));
  });
});
