import { describe, expect, it } from 'vitest';
import {
  canDecideRequest,
  canWithdrawRequest,
  describeStatusChange,
  detailPath,
  displayStatusKey,
  dueInfo,
  inboxPath,
  requestActionErrorMessage,
  sumRefundAmount,
} from './authorizationRequests';

const today = new Date(2026, 9, 25);

describe('authorizationRequests', () => {
  it('shows an approved request that could not be applied as its own status', () => {
    expect(displayStatusKey({ status: 'APPROVED', resumeOutcome: 'REJECTED_ON_RESUME' })).toBe('APPROVED_NOT_APPLIED');
    expect(displayStatusKey({ status: 'APPROVED', resumeOutcome: 'PROCESSED' })).toBe('APPROVED');
  });

  it('describes the due date, with null meaning it does not expire', () => {
    expect(dueInfo(null, today)).toMatchObject({ key: 'none', label: 'No vence' });
    expect(dueInfo('2026-10-25', today)).toMatchObject({ key: 'today', tone: 'warning' });
    expect(dueInfo('2026-10-26', today)).toMatchObject({ label: 'En 1 día', tone: 'warning' });
    expect(dueInfo('2026-10-30', today)).toMatchObject({ label: 'En 5 días', tone: 'neutral' });
  });

  it('adds up only pending refund amounts', () => {
    const requests = [
      { operationType: 'ISSUE_REFUND', amount: { amount: '100.10', currency: 'ARS' } },
      { operationType: 'ISSUE_REFUND', amount: { amount: '50.20', currency: 'ARS' } },
      { operationType: 'CANCEL_INVOICE', amount: { amount: '999.00', currency: 'ARS' } },
    ];
    expect(sumRefundAmount(requests)).toEqual({ amount: '150.30', currency: 'ARS' });
    expect(sumRefundAmount([requests[2]])).toBeNull();
  });

  it('only lets a director decide a pending request that someone else made', () => {
    const request = { status: 'PENDING', requestedBy: { id: 5 } };
    expect(canDecideRequest(request, { id: 1, roles: ['DIRECTOR'] })).toBe(true);
    expect(canDecideRequest(request, { id: 5, roles: ['DIRECTOR'] })).toBe(false);
    expect(canDecideRequest(request, { id: 1, roles: ['ADMINISTRATIVO'] })).toBe(false);
    expect(canDecideRequest({ ...request, status: 'APPROVED' }, { id: 1, roles: ['DIRECTOR'] })).toBe(false);
  });

  it('only lets the requester withdraw while pending', () => {
    const request = { status: 'PENDING', requestedBy: { id: 5 } };
    expect(canWithdrawRequest(request, { id: 5, roles: ['ADMINISTRATIVO'] })).toBe(true);
    expect(canWithdrawRequest(request, { id: 6, roles: ['ADMINISTRATIVO'] })).toBe(false);
    expect(canWithdrawRequest({ ...request, status: 'REJECTED' }, { id: 5, roles: ['ADMINISTRATIVO'] })).toBe(false);
  });

  it('builds the paths of each role', () => {
    expect(inboxPath('director')).toBe('/director/aprobaciones/facturacion');
    expect(inboxPath('administrativo')).toBe('/administrativo/aprobaciones');
    expect(detailPath('director', 9)).toBe('/director/aprobaciones/facturacion/9');
    expect(detailPath('administrativo', 9)).toBe('/administrativo/aprobaciones/9');
  });

  it('describes a status change with its actor', () => {
    expect(describeStatusChange({ event: 'APPROVE', actorName: 'Ana Díaz' })).toEqual({ label: 'Aprobada por Ana Díaz', tone: 'success' });
    expect(describeStatusChange({ event: 'EXPIRE', actorName: null, actorProcess: 'job' }).label).toBe('Expiró sin decisión');
  });

  it('explains 403, 404 and 409 errors by status and code', () => {
    expect(requestActionErrorMessage({ status: 403 }, { action: 'approve', own: true })).toMatch(/hiciste vos/);
    expect(requestActionErrorMessage({ status: 403 }, { action: 'approve' })).toMatch(/Solo la dirección/);
    expect(requestActionErrorMessage({ status: 403 }, { action: 'withdraw' })).toMatch(/quien hizo el pedido/);
    expect(requestActionErrorMessage({ status: 404 }, { action: 'reject' })).toMatch(/ya no existe/);
    expect(requestActionErrorMessage({ status: 409, code: 'ILLEGAL_AUTHORIZATION_REQUEST_TRANSITION' }, { action: 'approve' })).toMatch(/ya no está pendiente/);
    expect(requestActionErrorMessage({ status: 409, code: 'OTHER' }, { action: 'approve' })).toMatch(/cambió su estado/);
  });
});
