import { describe, expect, it } from 'vitest';
import { server } from './server';
import {
  activationTokenStatusHandlers,
  emptyInvitationWavesHandlers,
  invitationRecipientsWithoutPersonalDataHandlers,
  invitationWaveErrorHandlers,
} from './invitationWaveHandlers';

const API = 'http://localhost:8080';
const json = { 'Content-Type': 'application/json' };

async function call(method, path, body) {
  const response = await fetch(`${API}${path}`, {
    method,
    headers: json,
    body: body === undefined ? undefined : JSON.stringify(body),
  });
  const text = await response.text();
  const isJson = response.headers.get('content-type')?.includes('json');
  return { status: response.status, headers: response.headers, body: text && isJson ? JSON.parse(text) : text };
}

const newWave = { name: 'Oleada', importBatchId: 7, batchSize: 50, intervalMinutes: 30, dailyCap: 150 };

describe('invitation wave default handlers', () => {
  it('waveEndpointsAnswerAgainstTheContract', async () => {
    const created = await call('POST', '/invitation-waves', newWave);
    expect(created.status).toBe(201);
    expect(created.body.excludedByReason).toHaveProperty('NO_EMAIL');
    expect((await call('GET', '/invitation-waves?page=0&size=25')).body.items).toHaveLength(3);
    expect((await call('GET', '/invitation-waves/2')).body.status).toBe('RUNNING');
    for (const [action, status] of [['start', 'RUNNING'], ['pause', 'PAUSED'], ['resume', 'RUNNING'], ['cancel', 'CANCELLED']]) {
      expect((await call('POST', `/invitation-waves/2/${action}`)).body.status).toBe(status);
    }
  });

  it('supportEndpointsAnswerAgainstTheContract', async () => {
    expect((await call('GET', '/invitation-waves/settings')).body.sendWindow.zone).toBeTruthy();
    expect((await call('GET', '/invitation-waves/import-batches')).body).toHaveLength(2);
    const report = await call('GET', '/invitation-waves/2/report.csv');
    expect(report.headers.get('content-disposition')).toContain('attachment');
    expect(report.body.startsWith('﻿personId,status')).toBe(true);
  });

  it('recipientsCarryNameAndEmailWhenTheCallerMayReadStudents', async () => {
    const { body } = await call('GET', '/invitation-waves/2/recipients?status=SENT&page=0&size=50');
    expect(body.items[0].displayName).toBe('Ana Pérez');
    expect(body.items.some((entry) => entry.email === null)).toBe(true);
  });

  it('activationTokenStatusAnswersTheThreeStates', async () => {
    const valid = await call('POST', '/auth/activation-token/status', { token: 'abc' });
    expect(valid.body).toMatchObject({ status: 'VALID', username: 'ana.perez' });
    server.use(activationTokenStatusHandlers.expired);
    const expired = await call('POST', '/auth/activation-token/status', { token: 'abc' });
    expect(expired.body.username).toBeNull();
    expect(expired.body.institution).not.toBeNull();
    server.use(activationTokenStatusHandlers.invalid);
    const invalid = await call('POST', '/auth/activation-token/status', { token: 'abc' });
    expect(invalid.body).toEqual({ status: 'INVALID', username: null, institution: null });
  });
});

describe('invitation wave state handlers', () => {
  it('emptyStateAnswersWithoutItemsOrBatches', async () => {
    server.use(...emptyInvitationWavesHandlers);
    expect((await call('GET', '/invitation-waves')).body.items).toEqual([]);
    expect((await call('GET', '/invitation-waves/import-batches')).body).toEqual([]);
    expect((await call('GET', '/invitation-waves/2/recipients')).body.totalElements).toBe(0);
  });

  it('withoutReadStudentsPermissionNameAndEmailAreNull', async () => {
    server.use(...invitationRecipientsWithoutPersonalDataHandlers);
    const { body } = await call('GET', '/invitation-waves/2/recipients');
    expect(body.items.every((entry) => entry.displayName === null && entry.email === null)).toBe(true);
  });

  it('errorStateAnswersWithDomainErrorCodes', async () => {
    server.use(...invitationWaveErrorHandlers);
    expect((await call('POST', '/invitation-waves', newWave)).body.code).toBe('INVITATION_WAVE_WITHOUT_RECIPIENTS');
    expect((await call('GET', '/invitation-waves/9')).status).toBe(404);
    expect((await call('POST', '/invitation-waves/9/start')).status).toBe(409);
    expect((await call('GET', '/invitation-waves')).status).toBe(403);
  });
});
