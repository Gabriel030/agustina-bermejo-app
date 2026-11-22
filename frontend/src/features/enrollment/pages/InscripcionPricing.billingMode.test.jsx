import { describe, expect, it } from 'vitest';
import { waitFor } from '@testing-library/react';
import { http, HttpResponse } from 'msw';
import { Routes, Route } from 'react-router-dom';
import { server } from '@/test/mocks/server';
import { renderWithProviders } from '@/test/renderWithProviders';
import { InscripcionCurso } from '@/features/enrollment/pages/InscripcionCurso';
import { InscripcionCarrera } from '@/features/enrollment/pages/InscripcionCarrera';

const API = 'http://localhost:8080';

function modulesWithBilling(mode, served) {
  return http.get(`${API}/me/modules`, () => {
    served.count += 1;
    return HttpResponse.json({
      billing: { mode },
      fiscalInvoicing: { enabled: false },
      analytics: { enabled: false },
      paymentMethods: [],
      billingNotifications: { enabled: false },
      externalBilling: { enabled: false },
    });
  });
}

function trackPricingRequests() {
  const requests = [];
  server.use(
    http.get(`${API}/courses/:id/pricing`, ({ request }) => {
      requests.push(request.url);
      return HttpResponse.json({ courseId: 1, cuotaMensual: { amount: '8000.00', currency: 'ARS' } });
    }),
    http.get(`${API}/programs/:id/pricing`, ({ request }) => {
      requests.push(request.url);
      return HttpResponse.json({ programId: 1, matriculaCohorte1: { amount: '5000.00', currency: 'ARS' }, matriculaCohorte2: { amount: '5000.00', currency: 'ARS' }, cuotaMensual: { amount: '8000.00', currency: 'ARS' } });
    }),
  );
  return requests;
}

function renderCourseEnrollment() {
  return renderWithProviders(
    <Routes>
      <Route path="/catalogo/cursos/:id/inscribirme" element={<InscripcionCurso />} />
    </Routes>,
    { route: '/catalogo/cursos/1/inscribirme', withAuth: true },
  );
}

function renderProgramEnrollment() {
  return renderWithProviders(
    <Routes>
      <Route path="/catalogo/carreras/:id/inscribirme" element={<InscripcionCarrera />} />
    </Routes>,
    { route: '/catalogo/carreras/1/inscribirme', withAuth: true },
  );
}

describe('inscripción: precios según modo de facturación', () => {
  it('con facturación apagada la inscripción a curso no pide los precios', async () => {
    const served = { count: 0 };
    server.use(modulesWithBilling('OFF', served));
    const requests = trackPricingRequests();
    renderCourseEnrollment();

    await waitFor(() => expect(served.count).toBeGreaterThan(0));
    await new Promise((resolve) => setTimeout(resolve, 200));
    expect(requests).toEqual([]);
  });

  it('con facturación encendida la inscripción a curso pide los precios', async () => {
    const served = { count: 0 };
    server.use(modulesWithBilling('FULL', served));
    const requests = trackPricingRequests();
    renderCourseEnrollment();

    await waitFor(() => expect(requests.length).toBeGreaterThan(0));
  });

  it('con facturación apagada la inscripción a carrera no pide los precios', async () => {
    const served = { count: 0 };
    server.use(modulesWithBilling('OFF', served));
    const requests = trackPricingRequests();
    renderProgramEnrollment();

    await waitFor(() => expect(served.count).toBeGreaterThan(0));
    await new Promise((resolve) => setTimeout(resolve, 200));
    expect(requests).toEqual([]);
  });

  it('con facturación encendida la inscripción a carrera pide los precios', async () => {
    const served = { count: 0 };
    server.use(modulesWithBilling('FULL', served));
    const requests = trackPricingRequests();
    renderProgramEnrollment();

    await waitFor(() => expect(requests.length).toBeGreaterThan(0));
  });
});
