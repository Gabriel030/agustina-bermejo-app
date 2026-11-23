import { mkdirSync, writeFileSync } from 'node:fs';
import { dirname } from 'node:path';
import { afterAll, afterEach, beforeEach, describe, expect, it } from 'vitest';
import { render, waitFor } from '@testing-library/react';
import { http, HttpResponse } from 'msw';
import { QueryClientProvider } from '@tanstack/react-query';
import { MemoryRouter } from 'react-router-dom';
import { GoogleOAuthProvider } from '@react-oauth/google';
import { server } from '@/test/mocks/server';
import { recordApiRequests } from '@/test/recordApiRequests';
import { settledRequests } from '@/test/apiRequestCount';
import { profileFixture } from '@/test/fixtures/profile';
import { contractList } from '@/test/fixtures/fromContract';
import { AuthProvider } from '@/context/AuthContext';
import { ThemeProvider } from '@/context/ThemeContext';
import { AppRouter } from '@/routes/AppRouter';
import { createQueryClient } from '@/api/queryClient';

const API = 'http://localhost:8080';

const DIRECTOR_SCREENS = [
  'financiero', 'alumnos', 'listas-de-espera', 'periodos', 'oferta-academica', 'oferta-academica/calendario',
  'gestion-academica', 'aprobaciones', 'cierres-de-comision', 'mesas-de-examen', 'esquemas-evaluacion',
  'financiero/anios-lectivos', 'facturacion', 'precios', 'descuentos', 'corridas-de-facturacion',
  'constancias', 'personal', 'importacion-inicial', 'ajustes-del-instituto',
];

const EMPTY_LISTS = [
  '/authorization-requests',
  '/bank-transfer-receipts',
  '/billing-runs',
  '/collection-reminders/contact-list',
  '/commission-closures',
  '/data-imports',
  '/director-approval-requests',
  '/discount-definitions',
  '/evaluation-schemes',
  '/exam-boards',
  '/prices',
  '/section-evaluation-configs',
  '/staff',
];

const emptyMetric = (panel) => ({ panel, available: true, partial: false, months: [], series: [] });

const SCREEN_ROWS_FILE = globalThis.process.env.PERF_SCREENS_OUT;
const screenRows = {};

describe('director screens request profile', () => {
  let tracker;
  let queryClient;

  beforeEach(() => {
    localStorage.setItem('sessionHint', '1');
    server.use(
      http.get(`${API}/me`, () => HttpResponse.json(profileFixture({ roles: ['DIRECTOR'] }))),
      ...EMPTY_LISTS.map((path) => http.get(`${API}${path}`, () => HttpResponse.json(contractList(path, [])))),
      http.get(`${API}/financial-dashboard`, () => HttpResponse.json({ studentsInDebt: 0, studentsUpToDate: 0, totalsByCurrency: [] })),
      http.get(`${API}/financial-dashboard/debtors/page`, () => HttpResponse.json({ items: [], page: 0, size: 25, totalElements: 0 })),
      http.get(`${API}/billing/metrics/first-month`, () => HttpResponse.json({ month: null })),
      http.get(`${API}/billing/metrics/:panel`, ({ params }) => HttpResponse.json(emptyMetric(params.panel))),
      http.get(`${API}/notification-metrics/collection-reminders`, () => HttpResponse.json(emptyMetric('collection-reminders'))),
    );
    tracker = recordApiRequests();
    queryClient = createQueryClient();
  });

  afterEach(() => {
    tracker.stop();
    localStorage.clear();
  });

  it.each(DIRECTOR_SCREENS)('%s makes no duplicated request', { timeout: 120000 }, async (screen) => {
    render(
      <GoogleOAuthProvider clientId="test-client-id">
        <ThemeProvider>
          <QueryClientProvider client={queryClient}>
            <MemoryRouter initialEntries={[`/director/${screen}`]}>
              <AuthProvider>
                <AppRouter />
              </AuthProvider>
            </MemoryRouter>
          </QueryClientProvider>
        </ThemeProvider>
      </GoogleOAuthProvider>,
    );
    await waitFor(() => expect(tracker.requests.includes('GET /me/notifications/unread-count')).toBe(true), { timeout: 60000 });
    await settledRequests(tracker, { quietMs: 1500, timeout: 30000 });
    screenRows[`screen:/director/${screen}`] = {
      requests: tracker.requests.length,
      duplicates: tracker.duplicates().length,
    };
    expect(tracker.duplicates()).toEqual([]);
  });

  afterAll(() => {
    if (!SCREEN_ROWS_FILE) return;
    mkdirSync(dirname(SCREEN_ROWS_FILE), { recursive: true });
    writeFileSync(SCREEN_ROWS_FILE, JSON.stringify(screenRows, null, 2));
  });
});
