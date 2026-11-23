import { beforeEach, describe, expect, it, vi } from 'vitest';
import { render, screen } from '@testing-library/react';
import { MemoryRouter, Route, Routes } from 'react-router-dom';
import { ProtectedRoute } from '@/routes/ProtectedRoute';
import { roleRoutesFor } from '@/routes/featureRoutes';
import { menuItemsFor, pagesFor } from '@/routes/roleMenu';
import { writeLastRole } from '@/lib/activeRole';
import { manageableStaffRoles } from '@/features/identity/staffRoles';

let auth;

vi.mock('@/context/AuthContext', () => ({
  useAuth: () => auth,
}));

function sessionWithRoles(roles) {
  auth = { usuario: { id: 9, roles }, roles };
}

const DENIED_TO_SUBDIRECTOR = 'constancias-por-excepcion';
const DENIED_LABEL = 'Emitir por excepción';

function labelsOf(role) {
  return menuItemsFor(role).map((item) => item.label);
}

function renderAdministrativeUrl(path, roles) {
  sessionWithRoles(roles);
  writeLastRole(9, roles[0]);
  return render(
    <MemoryRouter initialEntries={[path]}>
      <Routes>
        <Route path="/administrativo/*" element={<ProtectedRoute rol="ADMINISTRATIVO"><div>Pantalla administrativa</div></ProtectedRoute>} />
        <Route path="/subdirector/*" element={<div>Pantalla de subdirección</div>} />
        <Route path="/no-autorizado" element={<div>No autorizado</div>} />
      </Routes>
    </MemoryRouter>,
  );
}

beforeEach(() => {
  localStorage.clear();
});

describe('SUBDIRECTOR inherits ADMINISTRATIVO', () => {
  it('shows the same menu as administrativo plus Roles y permisos', () => {
    expect(labelsOf('subdirector')).toEqual([...labelsOf('administrativo').filter((label) => label !== DENIED_LABEL), 'Roles y permisos']);
  });

  it('keeps Emitir por excepción out of the subdirector menu, routes and pages', () => {
    expect(labelsOf('administrativo')).toContain(DENIED_LABEL);
    expect(labelsOf('director')).toContain(DENIED_LABEL);
    expect(labelsOf('subdirector')).not.toContain(DENIED_LABEL);
    expect(roleRoutesFor('administrativo').map((route) => route.path)).toContain(DENIED_TO_SUBDIRECTOR);
    expect(roleRoutesFor('director').map((route) => route.path)).toContain(DENIED_TO_SUBDIRECTOR);
    expect(roleRoutesFor('subdirector').map((route) => route.path)).not.toContain(DENIED_TO_SUBDIRECTOR);
    expect(pagesFor('subdirector')).not.toContain(DENIED_TO_SUBDIRECTOR);
  });

  it('gets every screen that administrativo gets, and the permissions screen', () => {
    const subdirectorPaths = roleRoutesFor('subdirector').map((route) => route.path);
    const administrativoPaths = roleRoutesFor('administrativo').map((route) => route.path).filter((path) => path !== DENIED_TO_SUBDIRECTOR && path !== 'constancias/excepciones/:id');
    expect(subdirectorPaths).toEqual(expect.arrayContaining(administrativoPaths));
    expect(subdirectorPaths).toContain('permisos');
  });

  it('does not give administrativo the permissions screen', () => {
    expect(roleRoutesFor('administrativo').map((route) => route.path)).not.toContain('permisos');
  });

  it('keeps the subdirector pages in the layout pages list', () => {
    expect(pagesFor('subdirector')).toEqual(expect.arrayContaining(pagesFor('administrativo').filter((slug) => slug !== DENIED_TO_SUBDIRECTOR)));
  });

  it('sends a subdirector who opens an administrativo link to the same screen under /subdirector', () => {
    renderAdministrativeUrl('/administrativo/alumnos?page=2', ['SUBDIRECTOR']);

    expect(screen.getByText('Pantalla de subdirección')).toBeInTheDocument();
    expect(screen.queryByText('Pantalla administrativa')).not.toBeInTheDocument();
  });

  it('still refuses administrativo screens to a person with neither role', () => {
    renderAdministrativeUrl('/administrativo/alumnos', ['DOCENTE']);

    expect(screen.getByText('No autorizado')).toBeInTheDocument();
  });

  it('lets an administrativo in without redirect', () => {
    renderAdministrativeUrl('/administrativo/alumnos', ['ADMINISTRATIVO']);

    expect(screen.getByText('Pantalla administrativa')).toBeInTheDocument();
  });
});

describe('who can be assigned in the staff form', () => {
  it('lets the Director create administrativo and subdirector', () => {
    expect(manageableStaffRoles(['DIRECTOR'])).toEqual(['ADMINISTRATIVO', 'SUBDIRECTOR']);
  });

  it('lets a subdirector create only docente and preceptor, never subdirector or director', () => {
    const assignable = manageableStaffRoles(['SUBDIRECTOR']);
    expect(assignable).toEqual(['DOCENTE', 'PRECEPTOR']);
    expect(assignable).not.toContain('SUBDIRECTOR');
    expect(assignable).not.toContain('DIRECTOR');
  });

  it('gives an administrativo the same staff assignments as a subdirector', () => {
    expect(manageableStaffRoles(['ADMINISTRATIVO'])).toEqual(manageableStaffRoles(['SUBDIRECTOR']));
  });
});
