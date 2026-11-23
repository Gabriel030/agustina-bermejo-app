import { render, screen } from '@testing-library/react';
import { describe, expect, it, vi } from 'vitest';
import { ExamCertificatesTable } from '@/features/evaluation/components/ExamCertificatesTable';

function renderTable(candidates) {
  return render(
    <ExamCertificatesTable
      candidates={candidates}
      selected={new Set()}
      busy={false}
      canRevoke
      downloadingId={null}
      onToggle={vi.fn()}
      onRevoke={vi.fn()}
      onDownload={vi.fn()}
    />,
  );
}

describe('ExamCertificatesTable', () => {
  it('shows a generic name for a candidate without name and never null', () => {
    renderTable([{ registrationId: 1, studentId: 2, studentName: null, maskedDocument: '***123', availability: 'AVAILABLE', certificateId: null }]);

    expect(screen.getByRole('checkbox', { name: 'Seleccionar a Alumno sin nombre' })).toBeInTheDocument();
    expect(document.body).not.toHaveTextContent(/null|undefined/);
  });

  it('offers no PDF nor revoke for a revoked candidate with null issuedAt', () => {
    renderTable([{ registrationId: 1, studentId: 2, studentName: null, maskedDocument: '***123', availability: 'REVOKED', certificateId: 5, issuedAt: null, revokedAt: null }]);

    expect(screen.queryByRole('button', { name: /PDF|Revocar constancia/ })).not.toBeInTheDocument();
    expect(document.body).not.toHaveTextContent(/null|undefined/);
  });
});
