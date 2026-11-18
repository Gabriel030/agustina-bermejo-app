import { describe, expect, it, vi } from 'vitest';
import { screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { http, HttpResponse } from 'msw';
import { server } from '@/test/mocks/server';
import { renderWithAppCache } from '@/test/renderWithAppCache';
import { recordApiRequests } from '@/test/recordApiRequests';
import { countOf, settledRequests } from '@/test/apiRequestCount';
import { DiscountsPage } from './DiscountsPage';
import { API, CATALOG, mockOffers } from './discountFixtures';

const auth = vi.hoisted(() => ({ roles: ['DIRECTOR'] }));
vi.mock('@/context/AuthContext', () => ({
  useAuth: () => ({ usuario: { nombre: 'Ana', apellido: 'Pérez', roles: auth.roles } }),
}));

vi.setConfig({ testTimeout: 20000 });

function mountDiscounts(route) {
  server.use(http.get(`${API}/discount-definitions`, () => HttpResponse.json(CATALOG)));
  mockOffers();
  const recorder = recordApiRequests();
  renderWithAppCache(<DiscountsPage />, { route });
  return recorder;
}

describe.each([['DIRECTOR'], ['ADMINISTRATIVO']])('DiscountsPage requests as %s', (role) => {
  const roleBase = role.toLowerCase();

  it(`e31 does not request programs or courses on the default tab`, async () => {
    auth.roles = [role];
    const recorder = mountDiscounts(`/${roleBase}/descuentos`);
    await screen.findByRole('heading', { name: 'Becas y pago anual' });
    const requests = await settledRequests(recorder);
    expect(countOf(requests, 'GET /discount-definitions')).toBe(1);
    expect(requests.some((request) => request.startsWith('GET /programs'))).toBe(false);
    expect(requests.some((request) => request.startsWith('GET /courses'))).toBe(false);
    recorder.stop();
  });

  it(`e31 requests programs and courses once when the promotions tab opens`, async () => {
    auth.roles = [role];
    const recorder = mountDiscounts(`/${roleBase}/descuentos`);
    await userEvent.click(await screen.findByRole('tab', { name: 'Promociones de cohorte' }));
    await screen.findByRole('heading', { name: 'Promociones de cohorte' });
    const requests = await settledRequests(recorder);
    expect(requests.filter((request) => request.startsWith('GET /programs'))).toHaveLength(1);
    expect(requests.filter((request) => request.startsWith('GET /courses'))).toHaveLength(1);
    expect(countOf(requests, 'GET /discount-definitions')).toBe(1);
    recorder.stop();
  });
});
