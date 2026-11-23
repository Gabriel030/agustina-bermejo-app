import { describe, expect, it, vi } from 'vitest';
import { render, screen } from '@testing-library/react';
import { PermissionCell } from './PermissionCell';

function renderCell(permission) {
  render(<PermissionCell permission={permission} role="PRECEPTOR" granted={false} changed={false} editable onToggle={vi.fn()} />);
}

describe('PermissionCell', () => {
  it('shows a permission fixed to the director without a checkbox', () => {
    renderCell({ code: 'billing.decide_authorization_request', description: 'Decidir solicitudes', fixedToDirector: true, sensitive: true });
    expect(screen.queryByRole('checkbox')).toBeNull();
    expect(screen.getByRole('img', { name: /exclusivo del Director/ })).toBeInTheDocument();
  });

  it('keeps the checkbox for a delegable permission', () => {
    renderCell({ code: 'announcement.create', description: 'Crear avisos', fixedToDirector: false, sensitive: false });
    expect(screen.getByRole('checkbox')).toBeInTheDocument();
  });
});
