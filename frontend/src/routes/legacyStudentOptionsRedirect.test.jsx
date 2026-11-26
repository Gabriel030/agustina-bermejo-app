import { describe, expect, it } from 'vitest';
import { screen } from '@testing-library/react';
import { Route, Routes } from 'react-router-dom';
import { renderWithProviders } from '@/test/renderWithProviders';
import { roleRoutesFor } from '@/routes/featureRoutes';

describe('legacy student options path', () => {
  it('redirects /alumno/opciones to the profile screen', async () => {
    renderWithProviders(
      <Routes>
        <Route path="/alumno">
          {roleRoutesFor('alumno').map(({ path, element }) => (
            <Route key={path} path={path} element={element} />
          ))}
        </Route>
        <Route path="/perfil" element={<div>Pantalla de perfil</div>} />
      </Routes>,
      { route: '/alumno/opciones' },
    );

    expect(await screen.findByText('Pantalla de perfil')).toBeInTheDocument();
  });
});
