import { describe, expect, it, vi } from 'vitest';
import { render, screen } from '@testing-library/react';
import { axe } from '@/test/axe';
import { FilaAviso } from './FilaAviso';

function unAviso(extra = {}) {
  return {
    id: 10,
    sectionType: 'PROGRAM',
    sectionId: 1,
    title: 'Cambio de aula',
    body: 'Aula 204.',
    category: 'SCHEDULING',
    urgent: false,
    expiresAt: null,
    audienceLabel: 'Toda la comisión',
    recipientCount: 25,
    readCount: 18,
    readTracked: true,
    status: 'SENT',
    scheduledFor: null,
    sentAt: '2026-09-23T10:00:00',
    createdBy: 'Ana Docente',
    createdAt: '2026-09-23T10:00:00',
    updatedAt: null,
    ...extra,
  };
}

function montar(aviso) {
  return render(
    <table>
      <tbody>
        <FilaAviso aviso={aviso} scrollerRef={null} soloLectura onVerDestinatarios={vi.fn()} />
      </tbody>
    </table>,
  );
}

describe('FilaAviso — acuse de lectura', () => {
  it('showsReadCounters', () => {
    montar(unAviso());
    expect(screen.getByText('18 de 25')).toBeInTheDocument();
    expect(screen.getByRole('progressbar')).toHaveAttribute('aria-valuenow', '72');
  });

  it('showsNoRecordForAnnouncementsBeforeReadTracking', () => {
    montar(unAviso({ readTracked: false, readCount: 0 }));
    expect(screen.getByText('Sin registro')).toBeInTheDocument();
    expect(screen.queryByRole('progressbar')).not.toBeInTheDocument();
  });

  it('showsNotSentForScheduledAnnouncements', () => {
    montar(unAviso({ status: 'SCHEDULED', recipientCount: 0, readCount: 0 }));
    expect(screen.getByText('Sin enviar')).toBeInTheDocument();
  });

  it('handlesZeroRecipientsWithoutDividingByZero', () => {
    montar(unAviso({ recipientCount: 0, readCount: 0 }));
    expect(screen.getByText('0 de 0')).toBeInTheDocument();
    expect(screen.getByRole('progressbar')).toHaveAttribute('aria-valuenow', '0');
  });

  it('hasNoAxeViolations', async () => {
    const { container } = montar(unAviso());
    expect(await axe(container)).toHaveNoViolations();
  });
});
