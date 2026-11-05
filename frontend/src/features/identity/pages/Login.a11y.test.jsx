import { describe, expect, it } from 'vitest';
import { render } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import { GoogleOAuthProvider } from '@react-oauth/google';
import { axe } from '@/test/axe';
import { AuthProvider } from '@/context/AuthContext';
import { Login } from '@/features/identity/pages/Login';

describe('Login accessibility', () => {
  it('loginFormHasNoAxeViolations', async () => {
    const { container } = render(
      <GoogleOAuthProvider clientId="test-client-id">
        <MemoryRouter initialEntries={['/login']}>
          <AuthProvider>
            <Login />
          </AuthProvider>
        </MemoryRouter>
      </GoogleOAuthProvider>,
    );

    expect(await axe(container)).toHaveNoViolations();
  });
});
