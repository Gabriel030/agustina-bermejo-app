import { describe, expect, it } from 'vitest';
import { screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { http, HttpResponse } from 'msw';
import { Route, Routes } from 'react-router-dom';
import { server } from '@/test/mocks/server';
import { renderWithProviders } from '@/test/renderWithProviders';
import { installFixedClock } from '@/test/fixedClock';
import { createDeferred } from '@/test/deferred';
import { errorBody } from '@/test/fixtures/errors';
import { annualTerms } from '@/test/fixtures/annualTerms';
import { AnnualTermsPage } from './AnnualTermsPage';

const API = 'http://localhost:8080';

function mount(route) {
  return renderWithProviders(
    <Routes>
      <Route path="/terminos-pago-anual" element={<AnnualTermsPage />} />
      <Route path="/terminos-pago-anual/:versionNumber" element={<AnnualTermsPage />} />
    </Routes>,
    { route },
  );
}

describe('AnnualTermsPage', () => {
  installFixedClock();

  it('showsCurrentVersionWithPillDateAndSections', async () => {
    mount('/terminos-pago-anual');
    expect(await screen.findByText('Vigente')).toBeInTheDocument();
    expect(screen.getByText('Versión v1')).toBeInTheDocument();
    expect(screen.getByText(/05\/01\/2027/)).toBeInTheDocument();
    expect(screen.getByRole('heading', { name: 'No devolución' })).toBeInTheDocument();
    expect(screen.getByRole('heading', { name: 'Mi aceptación' })).toBeInTheDocument();
    expect(screen.queryByText('Esta versión ya no rige.')).not.toBeInTheDocument();
  });

  it('showsLoadingSkeletonWhileTermsAreLoading', async () => {
    const gate = createDeferred();
    server.use(
      http.get(`${API}/annual-upfront-terms/current`, async () => {
        await gate.promise;
        return HttpResponse.json(annualTerms());
      }),
    );
    mount('/terminos-pago-anual');
    expect(screen.getByLabelText('Cargando los términos')).toBeInTheDocument();
    gate.resolve();
    expect(await screen.findByText('Vigente')).toBeInTheDocument();
  });

  it('showsReplacedVersionWithNoticeAndLinkToCurrent', async () => {
    server.use(
      http.get(`${API}/annual-upfront-terms/versions/1`, () => HttpResponse.json(annualTerms({ current: false }))),
    );
    mount('/terminos-pago-anual/1');
    expect(await screen.findByText('Reemplazada')).toBeInTheDocument();
    expect(screen.getByText('Esta versión ya no rige.')).toBeInTheDocument();
    expect(screen.getByRole('link', { name: 'Ver la versión vigente' })).toHaveAttribute('href', '/terminos-pago-anual');
  });

  it('retriesAfterLoadError', async () => {
    const user = userEvent.setup();
    server.use(
      http.get(`${API}/annual-upfront-terms/current`, () => HttpResponse.json(errorBody('INTERNAL_ERROR'), { status: 500 }), {
        once: true,
      }),
    );
    mount('/terminos-pago-anual');
    await user.click(await screen.findByRole('button', { name: 'Reintentar' }));
    expect(await screen.findByText('Vigente')).toBeInTheDocument();
  });
});
