import { beforeEach, describe, expect, it, vi } from 'vitest';
import { render, screen } from '@testing-library/react';
import { MemoryRouter, Route, Routes } from 'react-router-dom';
import { toast } from 'sonner';
import { ProtectedRoute } from '@/routes/ProtectedRoute';
import { readLastRole, writeLastRole } from '@/lib/activeRole';

let auth;

vi.mock('@/context/AuthContext', () => ({
  useAuth: () => auth,
}));

vi.mock('sonner', () => ({ toast: vi.fn() }));

function renderAt(path, rol) {
  return render(
    <MemoryRouter initialEntries={[path]}>
      <Routes>
        <Route
          path={path}
          element={
            <ProtectedRoute rol={rol}>
              <div>Pantalla protegida</div>
            </ProtectedRoute>
          }
        />
        <Route path="/login" element={<div>Login</div>} />
        <Route path="/no-autorizado" element={<div>No autorizado</div>} />
        <Route path="/elegir-rol" element={<div>Elegir rol</div>} />
        <Route path="/:role/inicio" element={<div>Inicio de otro rol</div>} />
      </Routes>
    </MemoryRouter>,
  );
}

function sessionWithRoles(roles) {
  auth = { usuario: { id: 4, roles }, roles };
}

beforeEach(() => {
  localStorage.clear();
  vi.mocked(toast).mockClear();
});

describe('ProtectedRoute', () => {
  it('lets in a person who has the role, even when it is not the active one', () => {
    sessionWithRoles(['DOCENTE', 'PRECEPTOR']);
    writeLastRole(4, 'DOCENTE');

    renderAt('/preceptor/inicio', 'PRECEPTOR');

    expect(screen.getByText('Pantalla protegida')).toBeInTheDocument();
  });

  it('sends a person without a session to the login', () => {
    auth = { usuario: null, roles: [] };

    renderAt('/docente/inicio', 'DOCENTE');

    expect(screen.getByText('Login')).toBeInTheDocument();
  });

  it('sends to not-authorized a person who never had the role', () => {
    sessionWithRoles(['DOCENTE']);
    writeLastRole(4, 'DOCENTE');

    renderAt('/director/inicio', 'DIRECTOR');

    expect(screen.getByText('No autorizado')).toBeInTheDocument();
  });

  it('moves to the remaining role with a notice when the active role was taken away', () => {
    sessionWithRoles(['DOCENTE']);
    writeLastRole(4, 'PRECEPTOR');

    renderAt('/preceptor/inicio', 'PRECEPTOR');

    expect(screen.getByText('Inicio de otro rol')).toBeInTheDocument();
    expect(readLastRole(4)).toBe('DOCENTE');
    expect(toast).toHaveBeenCalledWith('Ya no tenés el rol Preceptor. Ahora estás como Docente.');
  });

  it('asks which role to continue with when several remain after losing the active one', () => {
    sessionWithRoles(['DOCENTE', 'ALUMNO']);
    writeLastRole(4, 'PRECEPTOR');

    renderAt('/preceptor/inicio', 'PRECEPTOR');

    expect(screen.getByText('Elegir rol')).toBeInTheDocument();
    expect(readLastRole(4)).toBeNull();
    expect(toast).toHaveBeenCalledWith('Ya no tenés el rol Preceptor. Elegí con cuál seguir.');
  });
});
