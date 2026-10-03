import { describe, expect, it } from 'vitest';
import { http, HttpResponse } from 'msw';
import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter } from 'react-router-dom';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { GoogleOAuthProvider } from '@react-oauth/google';
import { server } from '@/test/mocks/server';
import { AuthProvider } from '@/context/AuthContext';
import { ThemeProvider } from '@/context/ThemeContext';
import { AppRouter } from '@/routes/AppRouter';

const API = 'http://localhost:8080';

function renderApp(initialPath) {
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  return render(
    <GoogleOAuthProvider clientId="test-client-id">
      <ThemeProvider>
        <QueryClientProvider client={queryClient}>
          <MemoryRouter initialEntries={[initialPath]}>
            <AuthProvider>
              <AppRouter />
            </AuthProvider>
          </MemoryRouter>
        </QueryClientProvider>
      </ThemeProvider>
    </GoogleOAuthProvider>,
  );
}

describe('AppRouter', () => {
  it('does not treat /director/alumnos/nuevo as the record of a student called "nuevo"', async () => {
    const requestedStudents = [];
    server.use(
      http.get(`${API}/me`, () =>
        HttpResponse.json({ id: 2, nombre: 'Dora', apellido: 'Directora', email: 'dora@demo.test', roles: ['DIRECTOR'] }),
      ),
      http.get(`${API}/students/:id`, ({ params }) => {
        requestedStudents.push(params.id);
        return new HttpResponse(null, { status: 400 });
      }),
    );
    renderApp('/director/alumnos/nuevo');

    const user = userEvent.setup();
    await user.type(await screen.findByPlaceholderText('DNI o email'), 'director.demo');
    await user.type(screen.getByPlaceholderText('••••••••'), 'Test1234');
    await user.click(screen.getByRole('button', { name: /ingresar/i }));

    expect(await screen.findByText('No autorizado')).toBeInTheDocument();
    expect(requestedStudents).not.toContain('nuevo');
  });
});
