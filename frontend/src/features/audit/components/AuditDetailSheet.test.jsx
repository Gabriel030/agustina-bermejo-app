import { describe, expect, it } from 'vitest';
import { screen } from '@testing-library/react';
import { renderWithProviders } from '@/test/renderWithProviders';
import { AuditDetailSheet } from './AuditDetailSheet';

const SENSITIVE_EVENT = {
  id: 1,
  action: 'CERTIFICATE_SIGNATORY_SIGNATURE_UPLOADED',
  actorPersonId: 7,
  actorName: 'Marcela Quiroga',
  actorRole: 'DIRECTOR',
  entityType: 'CERTIFICATE_SIGNATORY',
  entityId: 3,
  beforeValue: '{"users":[{"password":"abc","name":"Ana"}],"tokens":[{"apiKey":"abc"}]}',
  afterValue: '{"users":[{"password":"def","name":"Ana"}],"tokens":[{"apiKey":"abc"}]}',
  reason: null,
  occurredAt: '2026-11-17T15:42:00',
};

function renderedAttributeValues() {
  return [...document.querySelectorAll('*')].flatMap((element) => [...element.attributes].map((attribute) => attribute.value));
}

describe('AuditDetailSheet', () => {
  it('shows the changed fields with sensitive values masked', () => {
    renderWithProviders(<AuditDetailSheet event={SENSITIVE_EVENT} onClose={() => undefined} />);

    expect(screen.getByText('1 campo cambió')).toBeInTheDocument();
    expect(screen.getByText('users.0.password')).toBeInTheDocument();
    expect(screen.getAllByText('••••••••').length).toBeGreaterThan(0);
  });

  it('never renders a sensitive value in text or attributes', () => {
    renderWithProviders(<AuditDetailSheet event={SENSITIVE_EVENT} onClose={() => undefined} />);

    const text = document.body.textContent;
    expect(text).not.toContain('abc');
    expect(text).not.toContain('def');
    const attributes = renderedAttributeValues().join(' ');
    expect(attributes).not.toContain('abc');
    expect(attributes).not.toContain('def');
  });
});
