import { vi } from 'vitest';
import { describe, expect, it } from 'vitest';
import { screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { http, HttpResponse } from 'msw';
import { axe } from '@/test/axe';
import { server } from '@/test/mocks/server';
import { renderWithProviders } from '@/test/renderWithProviders';
import { OwnWaitlistPosition, WaitlistPositionCard } from './WaitlistPositionCard';
import { describeWaitlistExpiry } from '@/features/enrollment/lib/waitlistPosition';

const API = 'http://localhost:8080';

function entry(overrides = {}) {
  return { kind: 'COURSE', queueId: 1, queueName: 'Cerámica', enrollmentId: 7, position: 3, totalWaiting: 8, expiresOn: null, ...overrides };
}

function isoDaysFromNow(days) {
  const date = new Date();
  date.setDate(date.getDate() + days);
  const pad = (value) => String(value).padStart(2, '0');
  return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())}`;
}

describe('WaitlistPositionCard', () => {
  it('showsPositionAndQueueBarWhenQueueIsShort', () => {
    renderWithProviders(<WaitlistPositionCard entry={entry()} />);
    expect(screen.getByRole('group', { name: 'Lista de espera: Cerámica' })).toBeInTheDocument();
    expect(screen.getByRole('img', { name: 'Lugar 3 de 8' })).toBeInTheDocument();
    expect(screen.getByText('de 8')).toBeInTheDocument();
  });

  it('hidesQueueBarWhenMoreThanTwelveWaiting', () => {
    renderWithProviders(<WaitlistPositionCard entry={entry({ position: 9, totalWaiting: 20 })} />);
    expect(screen.queryByRole('img')).not.toBeInTheDocument();
    expect(screen.getByText('de 20')).toBeInTheDocument();
  });

  it('showsNextPillOnlyForFirstPlace', () => {
    const { rerender } = renderWithProviders(<WaitlistPositionCard entry={entry({ position: 1 })} showName={false} />);
    expect(screen.getByText('Sos el próximo')).toBeInTheDocument();
    rerender(<WaitlistPositionCard entry={entry({ position: 2 })} showName={false} />);
    expect(screen.queryByText('Sos el próximo')).not.toBeInTheDocument();
  });

  it('omitsQueueNameTextWhenHidden', () => {
    renderWithProviders(<WaitlistPositionCard entry={entry()} showName={false} />);
    expect(screen.queryByText('Cerámica')).not.toBeInTheDocument();
  });

  it('marksExpiryAsUrgentFromThreeDaysOrLess', () => {
    expect(describeWaitlistExpiry(isoDaysFromNow(0))).toEqual({ label: 'Vence hoy', urgent: true });
    expect(describeWaitlistExpiry(isoDaysFromNow(1))).toEqual({ label: 'Vence mañana', urgent: true });
    expect(describeWaitlistExpiry(isoDaysFromNow(3))).toEqual({ label: 'Vence en 3 días', urgent: true });
    const calm = describeWaitlistExpiry(isoDaysFromNow(4));
    expect(calm.urgent).toBe(false);
    expect(calm.label).toMatch(/^Vence el \d{2}\/\d{2}\/\d{4}$/);
  });

  it('rendersUrgentExpiryChipWithDangerTone', () => {
    renderWithProviders(<WaitlistPositionCard entry={entry({ expiresOn: isoDaysFromNow(2) })} />);
    expect(screen.getByText('Vence en 2 días').className).toMatch(/danger/);
  });

  it('opensAndClosesExpiryHelpWithEscape', async () => {
    const user = userEvent.setup();
    renderWithProviders(<WaitlistPositionCard entry={entry({ expiresOn: isoDaysFromNow(10) })} />);
    const trigger = screen.getByRole('button', { name: '¿Qué pasa si vence?' });
    expect(trigger).toHaveAttribute('aria-expanded', 'false');
    await user.click(trigger);
    expect(await screen.findByText('Si no hay lugar al cierre, la espera termina y te avisamos.')).toBeInTheDocument();
    expect(trigger).toHaveAttribute('aria-expanded', 'true');
    await user.keyboard('{Escape}');
    expect(screen.queryByText('Si no hay lugar al cierre, la espera termina y te avisamos.')).not.toBeInTheDocument();
  });

  it('hasNoAxeViolations', async () => {
    const { container } = renderWithProviders(<WaitlistPositionCard entry={entry({ expiresOn: isoDaysFromNow(2) })} />);
    expect(await axe(container)).toHaveNoViolations();
  });
});

describe('OwnWaitlistPosition', () => {
  it('rendersNothingWhenListIsEmpty', async () => {
    const { container } = renderWithProviders(<OwnWaitlistPosition kind="COURSE" enrollmentId={7} />);
    await screen.findByRole('status', { name: 'Cargando tu lugar' });
    await vi.waitFor(() => expect(screen.queryByRole('status')).not.toBeInTheDocument());
    expect(container).toBeEmptyDOMElement();
  });

  it('showsOnlyTheMatchingEntry', async () => {
    server.use(http.get(`${API}/me/waitlist-positions`, () => HttpResponse.json([entry({ enrollmentId: 99, queueName: 'Otra' }), entry()])));
    renderWithProviders(<OwnWaitlistPosition kind="COURSE" enrollmentId={7} />);
    const card = await screen.findByRole('group', { name: 'Lista de espera: Cerámica' });
    expect(within(card).getByText('Tu lugar')).toBeInTheDocument();
    expect(screen.queryByRole('group', { name: 'Lista de espera: Otra' })).not.toBeInTheDocument();
  });

  it('offersRetryWhenLoadFails', async () => {
    server.use(http.get(`${API}/me/waitlist-positions`, () => HttpResponse.json({ code: 'INTERNAL_ERROR', requestId: 'req-test-0001', retryable: true, violations: [] }, { status: 500 })));
    renderWithProviders(<OwnWaitlistPosition kind="COURSE" enrollmentId={7} />);
    expect(await screen.findByText('No pudimos cargar tu lugar')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Reintentar' })).toBeInTheDocument();
  });
});
