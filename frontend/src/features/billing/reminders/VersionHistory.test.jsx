import { afterEach, describe, expect, it, vi } from 'vitest';
import { render, screen } from '@testing-library/react';
import { VersionHistory } from './VersionHistory';

const versions = [
  { id: 1, status: 'EFFECTIVE', effectiveFrom: '2026-10-01', proposedByName: 'Pérez, Ana', decidedByName: 'Gómez, Luis', changeReason: 'Ajuste de plazos' },
  { id: 2, status: 'SUPERSEDED', effectiveFrom: '2026-05-01', proposedByName: null, decidedByName: null, changeReason: null },
];

afterEach(() => {
  vi.restoreAllMocks();
});

describe('VersionHistory', () => {
  it('shows each version as a card with its status and reason on mobile', () => {
    vi.spyOn(window, 'matchMedia').mockImplementation(() => ({ matches: true, addEventListener() {}, removeEventListener() {} }));
    render(<VersionHistory versions={versions} />);

    expect(screen.queryByRole('table')).not.toBeInTheDocument();
    expect(screen.getByText('Vigente')).toBeInTheDocument();
    expect(screen.getByText('Reemplazada')).toBeInTheDocument();
    expect(screen.getByText('Ajuste de plazos')).toBeInTheDocument();
  });

  it('keeps the table on wide screens', () => {
    render(<VersionHistory versions={versions} />);

    expect(screen.getByRole('table')).toBeInTheDocument();
  });
});
