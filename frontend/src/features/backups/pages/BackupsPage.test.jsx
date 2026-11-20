import { describe, expect, it, vi } from 'vitest';
import { screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { http, HttpResponse } from 'msw';
import { axe } from '@/test/axe';
import { installFixedClock } from '@/test/fixedClock';
import {
  attentionBackupStatus,
  backupStatusForDirector,
  backupStatusForPlatformAdmin,
  notConfiguredBackupStatus,
  staleBackupStatus,
} from '@/test/fixtures/backupStatus';
import { errorBody } from '@/test/fixtures/errors';
import { takeContractDeviations } from '@/test/contract/withContract';
import { server } from '@/test/mocks/server';
import { renderWithProviders } from '@/test/renderWithProviders';
import { roleRoutesFor } from '@/routes/featureRoutes';
import { BackupsPage } from './BackupsPage';

const STATUS_URL = 'http://localhost:8080/backups/status';
const respondWith = (body) => server.use(http.get(STATUS_URL, () => HttpResponse.json(body)));
const ranDaysBefore = (days) => new Date(Date.parse('2026-11-20T12:00:00-03:00') - days * 86400000).toISOString();

installFixedClock({ now: '2026-11-20T12:00:00-03:00' });

describe('BackupsPage', () => {
  it('showsHealthySummaryForDirector', async () => {
    respondWith(backupStatusForDirector());
    renderWithProviders(<BackupsPage viewer="director" />);

    expect(await screen.findByText(/el último backup terminó hace 2 h/)).toBeInTheDocument();
    expect(screen.getByText('Hasta 2 h')).toBeInTheDocument();
    expect(screen.getByText('Objetivo: 4 h')).toBeInTheDocument();
    expect(screen.getByText('Fallos seguidos')).toBeInTheDocument();
    expect(screen.queryByText(/Avisá a soporte técnico\.$/)).not.toBeInTheDocument();
  });

  it('showsAttentionSummaryAsksForSupportAndListsAlerts', async () => {
    respondWith(attentionBackupStatus());
    renderWithProviders(<BackupsPage viewer="director" />);

    expect(await screen.findByText(/hace 9 h que no hay un backup completo/)).toBeInTheDocument();
    expect(screen.getByText('Avisá a soporte técnico.')).toBeInTheDocument();
    expect(screen.getByText('Supera el objetivo')).toBeInTheDocument();
    expect(screen.getByText('Un backup quedó dañado y no se puede usar')).toBeInTheDocument();
    expect(screen.getByText('Dañada')).toBeInTheDocument();
  });

  it('showsStaleSummaryWithFailedRestoreTestAndCriticalDisk', async () => {
    respondWith(staleBackupStatus());
    renderWithProviders(<BackupsPage viewer="director" />);

    expect(await screen.findByText(/el estado no se actualiza desde el/)).toBeInTheDocument();
    expect(screen.getByText('Fallida')).toBeInTheDocument();
    expect(screen.getByText('Crítico')).toBeInTheDocument();
    expect(screen.getByText('El disco de backups está casi lleno')).toBeInTheDocument();
  });

  it('showsNotConfiguredStateWithoutTabs', async () => {
    respondWith(notConfiguredBackupStatus());
    renderWithProviders(<BackupsPage viewer="director" />);

    expect(await screen.findByText('Todavía no hay backups')).toBeInTheDocument();
    expect(screen.getByText(/los backups todavía no están activos/)).toBeInTheDocument();
    expect(screen.queryByRole('tab')).not.toBeInTheDocument();
  });

  it('showsRetryableErrorWhenTheRequestFails', async () => {
    server.use(http.get(STATUS_URL, () => HttpResponse.json(errorBody('INTERNAL_ERROR', { retryable: true }), { status: 500 })));
    renderWithProviders(<BackupsPage viewer="director" />);

    expect(await screen.findByText('No pudimos cargar el estado de los backups')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Reintentar' })).toBeInTheDocument();
  });

  it('hidesTechnicalDetailsAndCommandsFromDirectorEvenWhenTheResponseCarriesThem', async () => {
    respondWith(backupStatusForPlatformAdmin());
    const user = userEvent.setup();
    renderWithProviders(<BackupsPage viewer="director" />);

    await screen.findByText(/el último backup terminó/);
    expect(screen.queryByText(/\.dump\.age/)).not.toBeInTheDocument();
    expect(screen.queryByText('/var/backups/agustina')).not.toBeInTheDocument();

    await user.click(screen.getByRole('tab', { name: 'Qué se respalda' }));
    expect(screen.queryByText('/var/backups/agustina')).not.toBeInTheDocument();
    expect(screen.queryByText('BACKUP_DIR')).not.toBeInTheDocument();

    await user.click(screen.getByRole('tab', { name: 'Cómo restaurar' }));
    expect(screen.getByText('Esta pantalla no restaura nada.')).toBeInTheDocument();
    expect(screen.getAllByText('A quién avisar')).toHaveLength(1);
    expect(screen.queryByRole('button', { name: /Copiar comandos/ })).not.toBeInTheDocument();
    expect(screen.queryByText(/restore\.sh/)).not.toBeInTheDocument();
    expect(screen.queryByText(/docker compose/)).not.toBeInTheDocument();
  });

  it('rendersNeutralFallbacksForUnknownEnumValues', async () => {
    const base = backupStatusForDirector();
    respondWith(
      backupStatusForDirector({
        tiers: [{ ...base.tiers[0], tier: 'FUTURE_TIER', verification: 'FUTURE_STATE' }],
        lastRestoreTest: { ranAt: '2026-11-19T04:00:00Z', result: 'FUTURE_RESULT', failureCode: null },
        alerts: [{ at: '2026-11-20T08:05:00Z', level: 'FUTURE_LEVEL', code: 'FUTURE_CODE' }],
      }),
    );
    renderWithProviders(<BackupsPage viewer="director" />);

    expect(await screen.findByText('Otro nivel')).toBeInTheDocument();
    expect(screen.getAllByText('Desconocido')).toHaveLength(3);
    expect(screen.getByText('Alerta de backups')).toBeInTheDocument();
    takeContractDeviations();
  });

  it('showsFileNamesDiskPathAndCopyableCommandsToPlatformAdmin', async () => {
    respondWith(backupStatusForPlatformAdmin());
    const user = userEvent.setup();
    renderWithProviders(<BackupsPage viewer="platform_admin" />);

    expect(await screen.findByText('daily-2026-11-20.dump.age')).toBeInTheDocument();
    expect(screen.getByText('/var/backups/agustina')).toBeInTheDocument();

    await user.click(screen.getByRole('tab', { name: 'Cómo restaurar' }));
    expect(screen.getAllByRole('button', { name: /Copiar comandos del paso/ }).length).toBeGreaterThan(0);
    expect(screen.getByRole('link', { name: 'Ver guía completa' })).toHaveAttribute('rel', expect.stringContaining('noopener'));
  });

  it('announcesCopiedCommandInALiveRegion', async () => {
    respondWith(backupStatusForPlatformAdmin());
    const writeText = vi.fn().mockResolvedValue();
    const user = userEvent.setup();
    renderWithProviders(<BackupsPage viewer="platform_admin" />);
    await user.click(await screen.findByRole('tab', { name: 'Cómo restaurar' }));
    Object.defineProperty(navigator, 'clipboard', { value: { writeText }, configurable: true });

    const button = screen.getAllByRole('button', { name: 'Copiar comandos del paso 1' })[0];
    await user.click(button);

    expect(writeText).toHaveBeenCalledWith(expect.stringContaining('docker compose exec backup ls'));
    const region = within(button.closest('div').parentElement).getByRole('status');
    expect(region).toHaveAttribute('aria-live', 'polite');
    expect(region).toHaveTextContent('Comando copiado');
    expect(button).toHaveTextContent('Copiado');
  });

  it('announcesEveryCopyAgainAndStopsItsTimerOnUnmount', async () => {
    respondWith(backupStatusForPlatformAdmin());
    const writeText = vi.fn().mockResolvedValue();
    const user = userEvent.setup();
    const { unmount } = renderWithProviders(<BackupsPage viewer="platform_admin" />);
    await user.click(await screen.findByRole('tab', { name: 'Cómo restaurar' }));
    Object.defineProperty(navigator, 'clipboard', { value: { writeText }, configurable: true });

    const button = screen.getAllByRole('button', { name: 'Copiar comandos del paso 1' })[0];
    await user.click(button);
    const region = within(button.closest('div').parentElement).getByRole('status');
    const firstAnnouncement = region.firstElementChild;
    await user.click(button);

    expect(region.firstElementChild).not.toBe(firstAnnouncement);
    expect(region).toHaveTextContent('Comando copiado');
    expect(writeText).toHaveBeenCalledTimes(2);
    const clear = vi.spyOn(globalThis, 'clearTimeout');
    unmount();
    expect(clear).toHaveBeenCalled();
  });

  it('remindsWhenThereIsNoRestoreTest', async () => {
    respondWith(backupStatusForDirector({ lastRestoreTest: null }));
    renderWithProviders(<BackupsPage viewer="director" />);

    expect(await screen.findByText('Todavía no se hizo una prueba completa de restauración.')).toBeInTheDocument();
  });

  it('staysSilentWhenTheRestoreTestIsThirteenDaysOld', async () => {
    respondWith(backupStatusForDirector({ lastRestoreTest: { ranAt: ranDaysBefore(13), result: 'PASSED', failureCode: null } }));
    renderWithProviders(<BackupsPage viewer="director" />);

    await screen.findByText(/el último backup terminó/);
    expect(screen.queryByText(/sin una prueba completa de restauración/)).not.toBeInTheDocument();
    expect(screen.getByText('Sin alertas')).toBeInTheDocument();
  });

  it('remindsWhenTheRestoreTestIsFifteenDaysOld', async () => {
    respondWith(backupStatusForDirector({ lastRestoreTest: { ranAt: ranDaysBefore(15), result: 'PASSED', failureCode: null } }));
    renderWithProviders(<BackupsPage viewer="director" />);

    expect(await screen.findByText('Pasaron más de 14 días sin una prueba completa de restauración.')).toBeInTheDocument();
    expect(screen.queryByText('Sin alertas')).not.toBeInTheDocument();
  });

  it('hasNoAxeViolationsInAnyTab', async () => {
    respondWith(backupStatusForPlatformAdmin());
    const user = userEvent.setup();
    const { container } = renderWithProviders(<BackupsPage viewer="platform_admin" />);
    await screen.findByText(/el último backup terminó/);
    expect(await axe(container)).toHaveNoViolations();

    await user.click(screen.getByRole('tab', { name: 'Qué se respalda' }));
    expect(await axe(container)).toHaveNoViolations();

    await user.click(screen.getByRole('tab', { name: 'Cómo restaurar' }));
    expect(await axe(container)).toHaveNoViolations();
  });
});

describe('backups routes', () => {
  it.each([
    ['director', true],
    ['platform_admin', true],
    ['administrativo', false],
    ['preceptor', false],
    ['docente', false],
    ['alumno', false],
  ])('exposesTheScreenOnlyToAllowedRoles %s', (role, allowed) => {
    expect(roleRoutesFor(role).some((route) => route.path === 'backups')).toBe(allowed);
  });
});
