import { beforeEach, describe, expect, it, vi } from 'vitest';
import { renderHook } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import { useActiveRole } from '@/hooks/useActiveRole';
import { writeLastRole } from '@/lib/activeRole';

let currentUser;

vi.mock('@/context/AuthContext', () => ({
  useAuth: () => ({ usuario: currentUser }),
}));

function activeRoleAt(path) {
  const wrapper = ({ children }) => <MemoryRouter initialEntries={[path]}>{children}</MemoryRouter>;
  return renderHook(() => useActiveRole(), { wrapper }).result.current;
}

beforeEach(() => {
  localStorage.clear();
  currentUser = { id: 7, roles: ['ALUMNO', 'PRECEPTOR', 'DOCENTE'] };
});

describe('useActiveRole', () => {
  it('takes the role from the URL when the person has it', () => {
    writeLastRole(7, 'ALUMNO');

    const result = activeRoleAt('/docente/comisiones');

    expect(result.activeRole).toBe('DOCENTE');
    expect(result.roleBase).toBe('docente');
  });

  it('uses the last used role on shared screens', () => {
    writeLastRole(7, 'ALUMNO');

    expect(activeRoleAt('/perfil').activeRole).toBe('ALUMNO');
  });

  it('uses the fixed order when there is no stored role', () => {
    expect(activeRoleAt('/catalogo').activeRole).toBe('PRECEPTOR');
  });

  it('lists the choices in the fixed order and allows switching', () => {
    const result = activeRoleAt('/perfil');

    expect(result.choices).toEqual(['PRECEPTOR', 'DOCENTE', 'ALUMNO']);
    expect(result.canSwitch).toBe(true);
  });

  it('does not allow switching with a single role', () => {
    currentUser = { id: 7, roles: ['DOCENTE'] };

    const result = activeRoleAt('/perfil');

    expect(result.activeRole).toBe('DOCENTE');
    expect(result.canSwitch).toBe(false);
  });

  it('keeps the platform role out of the choices when there are institute roles', () => {
    currentUser = { id: 7, roles: ['PLATFORM_ADMIN', 'DOCENTE', 'ALUMNO'] };

    expect(activeRoleAt('/perfil').choices).toEqual(['DOCENTE', 'ALUMNO']);
  });
});
