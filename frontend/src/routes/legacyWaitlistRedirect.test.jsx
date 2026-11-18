import { describe, expect, it } from 'vitest';
import { screen } from '@testing-library/react';
import { Route, Routes } from 'react-router-dom';
import { renderWithProviders } from '@/test/renderWithProviders';
import { roleRoutesFor } from '@/routes/featureRoutes';

function renderRole(role, route) {
  return renderWithProviders(
    <Routes>
      <Route path={`/${role}`}>
        {roleRoutesFor(role).map(({ path, element }) => {
          const isTarget = path.startsWith('inscripciones/listas-de-espera');
          return <Route key={path} path={path} element={isTarget ? <div>{`Destino ${path}`}</div> : element} />;
        })}
      </Route>
    </Routes>,
    { route },
  );
}

describe('legacy waitlist paths', () => {
  it.each(['preceptor', 'administrativo', 'director'])('redirects the old list path to the new one for %s', async (role) => {
    renderRole(role, `/${role}/listas-de-espera`);

    expect(await screen.findByText('Destino inscripciones/listas-de-espera')).toBeInTheDocument();
  });

  it('redirects the old detail path keeping kind and id', async () => {
    renderRole('preceptor', '/preceptor/listas-de-espera/PROGRAM_SECTION/7');

    expect(await screen.findByText('Destino inscripciones/listas-de-espera/:kind/:id')).toBeInTheDocument();
  });
});
