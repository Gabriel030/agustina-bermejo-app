import { describe, expect, it } from 'vitest';
import { screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { http, HttpResponse } from 'msw';
import { Routes, Route, useLocation } from 'react-router-dom';
import { server } from '@/test/mocks/server';
import { renderWithProviders } from '@/test/renderWithProviders';
import { AdmissionInvoicePendingCard } from '@/features/billing/components/AdmissionInvoicePendingCard';

const API = 'http://localhost:8080';

function enDias(dias) {
  const fecha = new Date();
  fecha.setDate(fecha.getDate() + dias);
  const mes = String(fecha.getMonth() + 1).padStart(2, '0');
  const dia = String(fecha.getDate()).padStart(2, '0');
  return `${fecha.getFullYear()}-${mes}-${dia}`;
}

function solicitud(id, programName, factura, status = 'APPROVED') {
  return {
    id,
    programName,
    status,
    admissionInvoice: {
      id: id * 10,
      total: { amount: '50000.00', currency: 'ARS' },
      dueDate: enDias(5),
      paymentStatus: 'PENDING',
      ...factura,
    },
  };
}

function Destino() {
  return <div>{useLocation().pathname}</div>;
}

function montar(solicitudes) {
  server.use(http.get(`${API}/me/program-enrollment-applications`, () => HttpResponse.json(solicitudes)));
  return renderWithProviders(
    <Routes>
      <Route path="/" element={<AdmissionInvoicePendingCard />} />
      <Route path="*" element={<Destino />} />
    </Routes>,
  );
}

describe('AdmissionInvoicePendingCard', () => {
  it('muestra un esqueleto mientras carga', () => {
    server.use(http.get(`${API}/me/program-enrollment-applications`, () => new Promise(() => {})));
    renderWithProviders(<AdmissionInvoicePendingCard />);
    expect(document.querySelector('.skeleton-shimmer')).not.toBeNull();
  });

  it('muestra un error si no puede cargar las solicitudes', async () => {
    server.use(http.get(`${API}/me/program-enrollment-applications`, () => HttpResponse.json({}, { status: 500 })));
    renderWithProviders(<AdmissionInvoicePendingCard />);
    expect(await screen.findByText(/No pudimos cargar tus matrículas de ingreso/)).toBeInTheDocument();
  });

  it('no muestra nada cuando no hay matrículas impagas', async () => {
    const { container } = montar([solicitud(3, 'Diseño Gráfico', { paymentStatus: 'PAID' }), solicitud(4, 'Otra', null, 'PENDING')]);
    await new Promise((resolve) => setTimeout(resolve, 60));
    expect(container).toBeEmptyDOMElement();
  });

  it('muestra el título, el monto, el vencimiento y los días que quedan', async () => {
    montar([solicitud(3, 'Diseño Gráfico', {})]);

    expect(await screen.findByText('Pagá la matrícula de ingreso para confirmar tu inscripción')).toBeInTheDocument();
    expect(screen.getByText(/Diseño Gráfico · \$\s50\.000,00\. Vencimiento: .* \(te quedan 5 días\)\./)).toBeInTheDocument();
  });

  it('avisa que la matrícula venció pero todavía se puede pagar (D-229)', async () => {
    montar([solicitud(3, 'Diseño Gráfico', { paymentStatus: 'OVERDUE', dueDate: enDias(-3) })]);

    expect(await screen.findByText(/La matrícula venció el .*Todavía podés pagarla para confirmar tu inscripción mientras la admisión siga abierta\./)).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Pagar matrícula' })).toBeInTheDocument();
  });

  it('con varias solicitudes impagas ordena por vencimiento y deja un solo botón principal', async () => {
    montar([
      solicitud(4, 'Carrera tardía', { dueDate: enDias(9) }),
      solicitud(3, 'Carrera próxima', { dueDate: enDias(2) }),
    ]);

    const botones = await screen.findAllByRole('button', { name: 'Pagar matrícula' });
    expect(botones).toHaveLength(2);
    expect(screen.getAllByText(/Vencimiento:/)[0]).toHaveTextContent('Carrera próxima');
    expect(botones[0]).toHaveAttribute('data-variant', 'gradient');
    expect(botones[1]).toHaveAttribute('data-variant', 'outline');
  });

  it('el botón lleva a elegir cómo pagar la factura de la matrícula', async () => {
    const user = userEvent.setup();
    montar([solicitud(3, 'Diseño Gráfico', {})]);

    await user.click(await screen.findByRole('button', { name: 'Pagar matrícula' }));

    expect(await screen.findByText('/alumno/pagos/30/seleccion')).toBeInTheDocument();
  });

  it('con un comprobante en revisión reemplaza el botón por el estado del pago', async () => {
    const user = userEvent.setup();
    montar([solicitud(3, 'Diseño Gráfico', { receiptStatus: 'PENDING_REVIEW', receiptUploadedAt: '2026-10-02T10:00:00' })]);

    expect(await screen.findByText('Pago en revisión')).toBeInTheDocument();
    expect(screen.getByText(/Subiste el comprobante el .*; cuando lo aprobemos tu inscripción queda confirmada\./)).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: 'Pagar matrícula' })).not.toBeInTheDocument();

    await user.click(screen.getByRole('button', { name: 'Ver estado del pago' }));
    expect(await screen.findByText('/alumno/pagos/30/transferencia')).toBeInTheDocument();
  });
});
