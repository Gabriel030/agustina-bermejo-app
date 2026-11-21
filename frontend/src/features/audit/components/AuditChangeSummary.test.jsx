import { describe, expect, it } from 'vitest';
import { screen } from '@testing-library/react';
import { renderWithProviders } from '@/test/renderWithProviders';
import { AuditChangeSummary } from './AuditChangeSummary';

describe('AuditChangeSummary', () => {
  it('says there are no changes when the count is zero', () => {
    renderWithProviders(<AuditChangeSummary changes={{ count: 0, changes: [] }} />);

    expect(screen.getByText('Sin cambios en los campos')).toBeInTheDocument();
  });

  it('uses the singular for one changed field', () => {
    renderWithProviders(<AuditChangeSummary changes={{ count: 1, changes: [{ field: 'status', before: 'ACTIVE', after: 'WITHDRAWN' }] }} />);

    expect(screen.getByText('1 campo cambió')).toBeInTheDocument();
  });

  it('uses the plural for two or more changed fields', () => {
    const changes = [
      { field: 'status', before: 'ACTIVE', after: 'WITHDRAWN' },
      { field: 'reason', before: '—', after: 'Baja voluntaria' },
    ];
    renderWithProviders(<AuditChangeSummary changes={{ count: 2, changes }} />);

    expect(screen.getByText('2 campos cambiaron')).toBeInTheDocument();
    expect(screen.getByText('Baja voluntaria')).toBeInTheDocument();
  });
});
