import { describe, expect, it } from 'vitest';
import { screen } from '@testing-library/react';
import { http, HttpResponse } from 'msw';
import { Routes, Route } from 'react-router-dom';
import { server } from '@/test/mocks/server';
import { renderWithProviders } from '@/test/renderWithProviders';
import { AlumnoPagos } from '@/pages/AlumnoPagos';

const API = 'http://localhost:8080';

function renderPagos() {
  return renderWithProviders(
    <Routes>
      <Route path="/alumno/pagos" element={<AlumnoPagos />} />
    </Routes>,
    { route: '/alumno/pagos' },
  );
}

describe('AlumnoPagos', () => {
  it('ofrece pagar la matrícula de ingreso de una solicitud aprobada sin inscripción todavía (D-227)', async () => {
    server.use(
      http.get(`${API}/me/program-enrollment-applications`, () =>
        HttpResponse.json([
          {
            id: 3,
            programName: 'Diseño Gráfico',
            status: 'APPROVED',
            admissionInvoice: { id: 77, total: { amount: '50000.00', currency: 'ARS' }, dueDate: '2026-03-20', paymentStatus: 'PENDING' },
          },
        ]),
      ),
    );
    renderPagos();

    expect(await screen.findByText('Pagá la matrícula de ingreso para confirmar tu inscripción')).toBeInTheDocument();
    expect(screen.getByText(/Diseño Gráfico · \$\s50\.000,00\. Vencimiento:/)).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Pagar matrícula' })).toBeInTheDocument();
  });

  it('no ofrece pagar la matrícula de ingreso cuando ya está paga', async () => {
    server.use(
      http.get(`${API}/me/program-enrollment-applications`, () =>
        HttpResponse.json([
          {
            id: 3,
            programName: 'Diseño Gráfico',
            status: 'APPROVED',
            admissionInvoice: { id: 77, total: { amount: '50000.00', currency: 'ARS' }, dueDate: '2026-03-20', paymentStatus: 'PAID' },
          },
        ]),
      ),
    );
    renderPagos();

    await screen.findByPlaceholderText('Buscar curso o carrera…');
    await new Promise((resolve) => setTimeout(resolve, 50));
    expect(screen.queryByRole('button', { name: 'Pagar matrícula' })).not.toBeInTheDocument();
  });

  it('muestra el error en lugar de callar cuando no puede cargar las solicitudes', async () => {
    server.use(http.get(`${API}/me/program-enrollment-applications`, () => HttpResponse.json({}, { status: 500 })));
    renderPagos();

    expect(await screen.findByText(/No pudimos cargar tus matrículas de ingreso/)).toBeInTheDocument();
  });

  it('con el comprobante en revisión muestra el pago en revisión en lugar de ofrecer pagar de nuevo', async () => {
    server.use(
      http.get(`${API}/me/program-enrollment-applications`, () =>
        HttpResponse.json([
          {
            id: 3,
            programName: 'Diseño Gráfico',
            status: 'APPROVED',
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
    renderPagos();

    expect(await screen.findByText('Pago en revisión')).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: 'Pagar matrícula' })).not.toBeInTheDocument();
  });
});
