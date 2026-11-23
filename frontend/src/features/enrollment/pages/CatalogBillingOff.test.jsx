import { describe, expect, it } from 'vitest';
import { screen, waitFor } from '@testing-library/react';
import { http, HttpResponse } from 'msw';
import { Routes, Route } from 'react-router-dom';
import { server } from '@/test/mocks/server';
import { renderWithProviders } from '@/test/renderWithProviders';
import { DetalleCarrera } from '@/features/enrollment/pages/DetalleCarrera';
import { DetalleCurso } from '@/features/enrollment/pages/DetalleCurso';
import { BankAccountDetails } from '../components/BankAccountDetails';

const API = 'http://localhost:8080';

function modulesWithBilling(mode) {
  return http.get(`${API}/me/modules`, () =>
    HttpResponse.json({
      billing: { mode },
      fiscalInvoicing: { enabled: false },
      analytics: { enabled: false },
      paymentMethods: [],
      billingNotifications: { enabled: false },
      externalBilling: { enabled: false },
    }),
  );
}

function renderCourse() {
  return renderWithProviders(
    <Routes>
      <Route path="/catalogo/cursos/:id" element={<DetalleCurso />} />
    </Routes>,
    { route: '/catalogo/cursos/1', withAuth: true },
  );
}

function renderProgram() {
  return renderWithProviders(
    <Routes>
      <Route path="/catalogo/carreras/:id" element={<DetalleCarrera />} />
    </Routes>,
    { route: '/catalogo/carreras/1', withAuth: true },
  );
}

describe('catalog with billing off', () => {
  it('does not render course prices nor request them', async () => {
    let pricingRequests = 0;
    server.use(
      modulesWithBilling('OFF'),
      http.get(`${API}/courses/:id/pricing`, () => {
        pricingRequests += 1;
        return HttpResponse.json({ courseId: 1, cuotaMensual: { amount: '8000.00', currency: 'ARS' } });
      }),
    );
    renderCourse();

    expect(await screen.findByText('Curso de prueba')).toBeInTheDocument();
    await waitFor(() => expect(screen.queryByText('Cuota mensual')).not.toBeInTheDocument());
    expect(screen.queryByText('$ 8.000,00')).not.toBeInTheDocument();
    expect(pricingRequests).toBe(0);
  });

  it('does not render program enrollment fees nor monthly fee', async () => {
    server.use(modulesWithBilling('OFF'));
    renderProgram();

    expect(await screen.findByText('Plan de estudio')).toBeInTheDocument();
    expect(screen.queryByText('Cuota mensual')).not.toBeInTheDocument();
    expect(screen.queryByText(/Matrícula/)).not.toBeInTheDocument();
  });

  it('does not render the bank account nor request it', async () => {
    let configRequests = 0;
    server.use(
      modulesWithBilling('OFF'),
      http.get(`${API}/payment-config`, () => {
        configRequests += 1;
        return HttpResponse.json({ bankAlias: 'ALIAS.PRUEBA', bankCbu: '0170099220000067797370', accountHolder: 'Instituto', qrImageUrl: null, mpEnabled: false });
      }),
    );
    renderWithProviders(<BankAccountDetails />);

    await new Promise((resolve) => setTimeout(resolve, 150));
    expect(screen.queryByText('Datos para la transferencia')).not.toBeInTheDocument();
    expect(screen.queryByText('0170099220000067797370')).not.toBeInTheDocument();
    expect(configRequests).toBe(0);
  });
});

describe('catalog with billing on', () => {
  it('renders the course monthly fee', async () => {
    server.use(modulesWithBilling('FULL'));
    renderCourse();

    expect(await screen.findByText('$ 8.000,00')).toBeInTheDocument();
  });

  it('renders the bank account details', async () => {
    server.use(
      modulesWithBilling('MIRROR'),
      http.get(`${API}/payment-config`, () =>
        HttpResponse.json({ bankAlias: 'ALIAS.PRUEBA', bankCbu: '0170099220000067797370', accountHolder: 'Instituto', qrImageUrl: null, mpEnabled: false }),
      ),
    );
    renderWithProviders(<BankAccountDetails />);

    expect(await screen.findByText('0170099220000067797370')).toBeInTheDocument();
  });
});
