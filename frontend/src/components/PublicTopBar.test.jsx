import { describe, expect, it, vi } from 'vitest';
import { screen } from '@testing-library/react';
import { renderWithProviders } from '@/test/renderWithProviders';
import { axe } from '@/test/axe';
import { PublicTopBar } from '@/components/PublicTopBar';

const authState = { usuario: null };

vi.mock('@/context/AuthContext', () => ({
  useAuth: () => ({ usuario: authState.usuario }),
}));

function renderBar(route, usuario = null) {
  authState.usuario = usuario;
  return renderWithProviders(<PublicTopBar />, { route });
}

const loginLink = () => screen.queryByRole('link', { name: 'Iniciar sesión' });
const registerLink = () => screen.queryByRole('link', { name: 'Registrarse' });

describe('PublicTopBar', () => {
  it.each(['/legal/terminos', '/legal/privacidad'])('showsLoginAndRegisterOn%s', (route) => {
    renderBar(route);
    expect(loginLink()).toHaveAttribute('href', '/login');
    expect(registerLink()).toHaveAttribute('href', '/registrarse');
  });

  it('showsOnlyLoginOnForgotPassword', () => {
    renderBar('/olvide-mi-contrasena');
    expect(loginLink()).toHaveAttribute('href', '/login');
    expect(registerLink()).not.toBeInTheDocument();
  });

  it.each(['/login', '/registrarse', '/activar', '/baja', '/verificar/ABC', '/no-autorizado'])(
    'showsOnlyTheBrandOn%s',
    (route) => {
      renderBar(route);
      expect(screen.getByRole('link', { name: 'Ir al inicio' })).toBeInTheDocument();
      expect(loginLink()).not.toBeInTheDocument();
      expect(registerLink()).not.toBeInTheDocument();
    },
  );

  it('hidesActionsWhenSignedIn', () => {
    renderBar('/legal/terminos', { id: 1, roles: ['DIRECTOR'] });
    expect(loginLink()).not.toBeInTheDocument();
    expect(registerLink()).not.toBeInTheDocument();
  });

  it('linksBrandToLoginWithoutSession', () => {
    renderBar('/legal/terminos');
    expect(screen.getByRole('link', { name: 'Ir al inicio' })).toHaveAttribute('href', '/login');
  });

  it('linksBrandToRoleHomeWithSession', () => {
    renderBar('/legal/terminos', { id: 1, roles: ['DIRECTOR'] });
    expect(screen.getByRole('link', { name: 'Ir al inicio' })).toHaveAttribute('href', '/director/inicio');
  });

  it('exposesAccountNavigationLandmark', () => {
    renderBar('/legal/terminos');
    expect(screen.getByRole('navigation', { name: 'Cuenta' })).toBeInTheDocument();
  });

  it('hasNoAccessibilityViolations', async () => {
    const { container } = renderBar('/legal/terminos');
    expect(await axe(container)).toHaveNoViolations();
  });
});
