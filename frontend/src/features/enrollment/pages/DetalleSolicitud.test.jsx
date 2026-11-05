import { describe, expect, it, vi } from 'vitest';
import { render, screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { http, HttpResponse } from 'msw';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { MemoryRouter, Route, Routes } from 'react-router-dom';
import { server } from '@/test/mocks/server';
import { errorBody } from '@/test/fixtures/errors';
import { DetalleSolicitud } from '@/features/enrollment/pages/DetalleSolicitud';

const API = 'http://localhost:8080';

function renderDetalle(id = '1') {
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  return render(
    <QueryClientProvider client={queryClient}>
      <MemoryRouter initialEntries={[`/mis-solicitudes/${id}`]}>
        <Routes>
          <Route path="/mis-solicitudes/:id" element={<DetalleSolicitud />} />
          <Route path="/catalogo/carreras/:id" element={<div>Ficha de la carrera</div>} />
          <Route path="/catalogo" element={<div>Bandeja de mis solicitudes</div>} />
        </Routes>
      </MemoryRouter>
    </QueryClientProvider>,
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
  it('replaceWarnsThatTheFileReplacesTheOneInTheRecordAndCancelDoesNotOpenThePicker', async () => {
    server.use(
      http.get(`${API}/me/program-enrollment-applications`, () =>
        HttpResponse.json([{ id: 1, programName: 'Diseño Gráfico', status: 'PENDING', requestedAt: '2026-03-02T10:00:00' }]),
      ),
      http.get(`${API}/me/documents`, () => HttpResponse.json([{ id: 1, type: 'DNI_FRENTE', uploadedAt: '2026-03-02T10:05:00' }])),
    );
    const user = userEvent.setup();
    renderDetalle('1');
    const pickerSpy = vi.spyOn(HTMLInputElement.prototype, 'click');

    await user.click(await screen.findByRole('button', { name: 'Reemplazar DNI — frente' }));

    const dialog = await screen.findByRole('dialog');
    expect(within(dialog).getByText(/se guarda en tu legajo y reemplaza al anterior/)).toBeInTheDocument();
    expect(pickerSpy).not.toHaveBeenCalled();
    await user.click(within(dialog).getByRole('button', { name: 'Cancelar' }));
    expect(screen.queryByRole('dialog')).not.toBeInTheDocument();
    expect(pickerSpy).not.toHaveBeenCalled();

    await user.click(screen.getByRole('button', { name: 'Reemplazar DNI — frente' }));
    await user.click(within(await screen.findByRole('dialog')).getByRole('button', { name: 'Elegir archivo nuevo' }));
    await waitFor(() => expect(pickerSpy).toHaveBeenCalledTimes(1));
    pickerSpy.mockRestore();
  });

  it('firstUploadOfADocumentDoesNotAskForConfirmation', async () => {
    server.use(
      http.get(`${API}/me/program-enrollment-applications`, () =>
        HttpResponse.json([{ id: 1, programName: 'Diseño Gráfico', status: 'PENDING', requestedAt: '2026-03-02T10:00:00' }]),
      ),
    );
    const user = userEvent.setup();
    renderDetalle('1');

    await user.click(await screen.findByRole('button', { name: 'Subir archivo de DNI — frente' }));

    expect(screen.queryByRole('dialog')).not.toBeInTheDocument();
  });

  it('lockedApplicationErrorExplainsThatDocumentsCannotChangeAnymore', async () => {
    server.use(
      http.get(`${API}/me/program-enrollment-applications`, () =>
        HttpResponse.json([{ id: 1, programName: 'Diseño Gráfico', status: 'PENDING', requestedAt: '2026-03-02T10:00:00' }]),
      ),
      http.post(`${API}/me/program-enrollment-applications/1/documents`, () =>
        HttpResponse.json(errorBody('APPLICATION_DOCUMENTS_LOCKED'), { status: 409 }),
      ),
    );
    const user = userEvent.setup();
    renderDetalle('1');
    const row = (await screen.findByText('DNI — frente')).closest('.border-b');

    await user.upload(row.querySelector('input[type="file"]'), new File(['x'], 'dni.pdf', { type: 'application/pdf' }));

    expect(await screen.findByRole('alert')).toHaveTextContent('Recargá la página para ver su estado');
    expect(screen.queryByText(/dni\.pdf · subido/)).not.toBeInTheDocument();
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
    expect(within(dniFrenteRow).getByRole('button', { name: 'Reemplazar DNI — frente' })).toBeInTheDocument();
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
          { id: 2, programId: 9, programName: 'Administración de Empresas', status: 'REJECTED', requestedAt: '2026-01-10T10:00:00', rejectionReason: 'Falta analítico' },
        ]),
      ),
    );
    const user = userEvent.setup();
    renderDetalle('2');

    expect(await screen.findByText('Enviada')).toBeInTheDocument();
    expect(screen.getAllByText('Rechazada').length).toBeGreaterThan(0);
    expect(screen.getByText('Podés volver a solicitar el ingreso a Administración de Empresas cuando corrijas lo indicado.')).toBeInTheDocument();
    await user.click(screen.getByRole('button', { name: 'Solicitar de nuevo' }));
    expect(await screen.findByText('Ficha de la carrera')).toBeInTheDocument();
  });

  it('una solicitud vencida lo explica, ofrece solicitar de nuevo y no muestra la revisión ni la subida de documentos', async () => {
    server.use(
      http.get(`${API}/me/program-enrollment-applications`, () =>
        HttpResponse.json([
          { id: 3, programId: 9, programName: 'Administración de Empresas', status: 'EXPIRED', requestedAt: '2026-01-10T10:00:00' },
        ]),
      ),
    );
    const user = userEvent.setup();
    renderDetalle('3');

    expect(await screen.findByText('Solicitud vencida.')).toBeInTheDocument();
    expect(screen.getAllByText('Vencida').length).toBeGreaterThan(0);
    expect(screen.queryByText(/Tu solicitud está en revisión/)).not.toBeInTheDocument();
    expect(screen.queryByText('Documentos de tu solicitud')).not.toBeInTheDocument();
    await user.click(screen.getByRole('button', { name: 'Solicitar de nuevo' }));
    expect(await screen.findByText('Ficha de la carrera')).toBeInTheDocument();
  });

  describe('error handling and file validation', () => {
    const pending = (extra = {}) =>
      http.get(`${API}/me/program-enrollment-applications`, () =>
        HttpResponse.json([{ id: 1, programName: 'Diseño Gráfico', status: 'PENDING', requestedAt: '2026-03-02T10:00:00', ...extra }]),
      );

    async function dniFrontInput() {
      const row = (await screen.findByText('DNI — frente')).closest('.border-b');
      return row.querySelector('input[type="file"]');
    }

    it('limitsFileInputsToSupportedTypes', async () => {
      server.use(pending());
      renderDetalle('1');

      expect((await dniFrontInput()).getAttribute('accept')).toContain('application/pdf');
    });

    it('rejectsFileOverSizeLimitWithoutCallingTheServer', async () => {
      const fetchSpy = vi.spyOn(globalThis, 'fetch');
      fetchSpy.mockClear();
      server.use(pending());
      const user = userEvent.setup();
      renderDetalle('1');
      const input = await dniFrontInput();
      const bigFile = new File([new Uint8Array(6 * 1024 * 1024)], 'dni.pdf', { type: 'application/pdf' });

      await user.upload(input, bigFile);

      expect(await screen.findByRole('alert')).toHaveTextContent('El archivo pesa 6 MB; el máximo es 5 MB.');
      expect(fetchSpy.mock.calls.some(([url, opts]) => String(url).includes('/documents?type=') && opts?.method === 'POST')).toBe(false);
    });

    it('rejectsUnsupportedFileTypeLocally', async () => {
      server.use(pending());
      const user = userEvent.setup({ applyAccept: false });
      renderDetalle('1');
      const input = await dniFrontInput();

      await user.upload(input, new File(['x'], 'dni.gif', { type: 'image/gif' }));

      expect(await screen.findByRole('alert')).toHaveTextContent('El archivo tiene que ser un PDF, JPG o PNG.');
    });

    it('showsTypeErrorFromBackend', async () => {
      server.use(
        pending(),
        http.post(`${API}/me/program-enrollment-applications/1/documents`, () =>
          HttpResponse.json(errorBody('UNSUPPORTED_FILE_TYPE'), { status: 400 }),
        ),
      );
      const user = userEvent.setup();
      renderDetalle('1');

      await user.upload(await dniFrontInput(), new File(['x'], 'dni.pdf', { type: 'application/pdf' }));

      expect(await screen.findByRole('alert')).toHaveTextContent('El archivo tiene que ser un PDF, JPG o PNG.');
    });

    it('showsSizeErrorFromBackend', async () => {
      server.use(
        pending(),
        http.post(`${API}/me/program-enrollment-applications/1/documents`, () =>
          HttpResponse.json(errorBody('MAX_UPLOAD_SIZE_EXCEEDED'), { status: 400 }),
        ),
      );
      const user = userEvent.setup();
      renderDetalle('1');

      await user.upload(await dniFrontInput(), new File(['x'], 'dni.pdf', { type: 'application/pdf' }));

      expect(await screen.findByRole('alert')).toHaveTextContent('El archivo pesa más de 5 MB. Subí uno más liviano.');
    });

    it('showsGenericUploadErrorForServerFailure', async () => {
      server.use(
        pending(),
        http.post(`${API}/me/program-enrollment-applications/1/documents`, () => new HttpResponse(null, { status: 500 })),
      );
      const user = userEvent.setup();
      renderDetalle('1');

      await user.upload(await dniFrontInput(), new File(['x'], 'dni.pdf', { type: 'application/pdf' }));

      expect(await screen.findByRole('alert')).toHaveTextContent('No pudimos subir el archivo. Probá de nuevo.');
    });

    it('announcesSuccessfulUploadToAssistiveTechnology', async () => {
      server.use(
        pending(),
        http.post(`${API}/me/program-enrollment-applications/1/documents`, () => new HttpResponse(null, { status: 201 })),
      );
      const user = userEvent.setup();
      renderDetalle('1');

      await user.upload(await dniFrontInput(), new File(['x'], 'dni.pdf', { type: 'application/pdf' }));

      expect(await screen.findByRole('status')).toHaveTextContent('DNI — frente: dni.pdf subido');
    });

    it('retryReloadsApplication', async () => {
      let attempts = 0;
      server.use(
        http.get(`${API}/me/program-enrollment-applications`, () => {
          attempts += 1;
          return attempts === 1
            ? new HttpResponse(null, { status: 500 })
            : HttpResponse.json([{ id: 1, programName: 'Diseño Gráfico', status: 'PENDING', requestedAt: '2026-03-02T10:00:00' }]);
        }),
      );
      const user = userEvent.setup();
      renderDetalle('1');

      expect(await screen.findByText('No pudimos cargar la solicitud')).toBeInTheDocument();
      await user.click(screen.getByRole('button', { name: 'Reintentar' }));

      expect(await screen.findByRole('heading', { name: 'Diseño Gráfico' })).toBeInTheDocument();
    });

    it('documentsErrorDoesNotClaimNotUploaded', async () => {
      server.use(pending(), http.get(`${API}/me/documents`, () => new HttpResponse(null, { status: 500 })));
      renderDetalle('1');

      expect(await screen.findByText('No pudimos ver los documentos que ya subiste.')).toBeInTheDocument();
      expect(screen.queryByText('Sin subir todavía')).not.toBeInTheDocument();
      expect(screen.getAllByText('No pudimos verificar si ya lo subiste')).toHaveLength(3);
    });

    it('doesNotShowAnotherStudentsApplicationWhenIdIsNotInOwnList', async () => {
      server.use(
        http.get(`${API}/me/program-enrollment-applications`, () =>
          HttpResponse.json([{ id: 2, programName: 'Carrera ajena', status: 'PENDING', requestedAt: '2026-03-02T10:00:00' }]),
        ),
      );
      renderDetalle('1');

      expect(await screen.findByText('Solicitud no encontrada')).toBeInTheDocument();
      expect(screen.queryByText('Carrera ajena')).not.toBeInTheDocument();
    });

    it('explainsConsequenceOfOverdueEnrollmentFee', async () => {
      server.use(
        pending({
          status: 'APPROVED',
          admissionInvoice: { id: 5, total: { amount: '1000.00', currency: 'ARS' }, paymentStatus: 'OVERDUE', dueDate: '2026-03-01', receiptStatus: null },
        }),
      );
      renderDetalle('1');

      expect(await screen.findByText('La matrícula está vencida.')).toBeInTheDocument();
      expect(screen.getByText(/la solicitud vence y la factura se anula/)).toBeInTheDocument();
      expect(screen.getByRole('button', { name: 'Pagar matrícula' })).toBeInTheDocument();
    });

    it('showsFallbackWhenRejectionHasNoReason', async () => {
      server.use(pending({ status: 'REJECTED', rejectionReason: '  ' }));
      renderDetalle('1');

      expect(await screen.findByText(/No se indicó un motivo; consultá con Secretaría\./)).toBeInTheDocument();
    });

    it('backLinkGoesStraightToMyApplicationsTab', async () => {
      server.use(pending());
      renderDetalle('1');

      const link = await screen.findByRole('link', { name: /Mis solicitudes/ });
      expect(link).toHaveAttribute('href', '/catalogo?tab=mis-solicitudes');
    });
  });
});
