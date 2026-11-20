import { describe, expect, it } from 'vitest';
import { API_BASE } from './server';
import { validateAgainstContract } from '../contract/validateAgainstContract';
import {
  attentionBackupStatus,
  backupStatusForDirector,
  backupStatusForPlatformAdmin,
  notConfiguredBackupStatus,
  staleBackupStatus,
} from '../fixtures/backupStatus';

const fixtures = {
  platformAdmin: backupStatusForPlatformAdmin(),
  director: backupStatusForDirector(),
  attention: attentionBackupStatus(),
  stale: staleBackupStatus(),
  notConfigured: notConfiguredBackupStatus(),
};

describe('backup status fixtures', () => {
  it.each(Object.entries(fixtures))('%s fixture matches the contract', (_, body) => {
    const { violations } = validateAgainstContract({
      method: 'GET',
      pathname: '/backups/status',
      request: { hasBody: false },
      response: { status: 200, hasBody: true, body },
    });

    expect(violations).toEqual([]);
  });

  it('directorFixtureHidesTechnicalFields', () => {
    const director = backupStatusForDirector();

    expect(director.disk.path).toBeNull();
    expect(director.tiers.every((tier) => tier.fileNames === null)).toBe(true);
  });

  it('platformAdminFixtureShowsTechnicalFields', () => {
    const platformAdmin = backupStatusForPlatformAdmin();

    expect(platformAdmin.disk.path).not.toBeNull();
    expect(platformAdmin.tiers.every((tier) => Array.isArray(tier.fileNames))).toBe(true);
  });

  it('defaultHandlerServesTheCompleteOkStatus', async () => {
    const response = await fetch(`${API_BASE}/backups/status`);
    const body = await response.json();

    expect(response.status).toBe(200);
    expect(body.state).toBe('OK');
    expect(body.tiers).toHaveLength(5);
    expect(body.lastRestoreTest.result).toBe('PASSED');
    expect(body.alerts).toEqual([]);
  });
});
