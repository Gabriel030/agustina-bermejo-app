import { execFileSync } from 'node:child_process';
import { describe, expect, it } from 'vitest';
import { http, HttpResponse } from 'msw';
import { handlers } from '../mocks/handlers';
import { API_BASE } from '../mocks/server';
import baseline from './contract-baseline.json';
import { auditHandlers } from './auditHandlers';
import { takeContractDeviations, withContract } from './withContract';

const API = API_BASE;
const validPerson = {
  id: 1,
  nombre: 'Ana',
  apellido: 'Paz',
  email: 'ana@example.com',
  dni: '30111222',
  documentType: 'DNI',
  legajo: 'L-1',
  sexo: 'F',
  telefono: '1155550000',
  street: 'Calle',
  number: '1',
  floor: '',
  city: 'CABA',
  state: 'CABA',
  country: 'Argentina',
  postalCode: '1000',
  hasAvatar: false,
  roles: ['ALUMNO'],
};

async function exchange(handler, { method = 'GET', path, body }) {
  takeContractDeviations();
  const wrapped = withContract(handler, { apiBase: API });
  const request = new Request(`${API}${path}`, {
    method,
    headers: { 'Content-Type': 'application/json' },
    body: body === undefined ? undefined : JSON.stringify(body),
  });
  await wrapped.run({ request, requestId: 'test' });
  return takeContractDeviations();
}

describe('msw contract validation', () => {
  it('validResponseProducesNoDeviation', async () => {
    const handler = http.get(`${API}/me`, () => HttpResponse.json(validPerson));
    expect(await exchange(handler, { path: '/me' })).toEqual([]);
  });

  it('mockWithUndeclaredFieldFailsContractValidation', async () => {
    const handler = http.get(`${API}/me`, () => HttpResponse.json({ ...validPerson, invented: 'x' }));
    const deviations = await exchange(handler, { path: '/me' });
    expect(deviations).toHaveLength(1);
    expect(deviations[0].key).toBe('GET /me');
    expect(deviations[0].violations[0]).toContain('must NOT have additional properties');
  });

  it('mockWithMissingRequiredFieldFailsContractValidation', async () => {
    const incomplete = { ...validPerson };
    delete incomplete.legajo;
    const handler = http.get(`${API}/me`, () => HttpResponse.json(incomplete));
    const deviations = await exchange(handler, { path: '/me' });
    expect(deviations[0].violations[0]).toContain("required property 'legajo'");
  });

  it('mockForUnknownOperationFailsContractValidation', async () => {
    const handler = http.get(`${API}/nothing-here/:id`, () => HttpResponse.json({}));
    const deviations = await exchange(handler, { path: '/nothing-here/7' });
    expect(deviations).toEqual([
      { key: 'GET /nothing-here/7', violations: ['operation not found in the contract'] },
    ]);
  });

  it('mockWithUndeclaredStatusFailsContractValidation', async () => {
    const handler = http.post(`${API}/auth/logout`, () => new HttpResponse(null, { status: 200 }));
    const deviations = await exchange(handler, { method: 'POST', path: '/auth/logout' });
    expect(deviations[0].violations).toEqual(['status 200 is not declared in the contract']);
  });

  it('requestBodyNotMatchingSchemaFailsContractValidation', async () => {
    const handler = http.post(`${API}/auth/login`, () =>
      HttpResponse.json({ accessToken: 'a', refreshToken: 'r', expiresIn: 900 }),
    );
    const deviations = await exchange(handler, {
      method: 'POST',
      path: '/auth/login',
      body: { username: 'ana' },
    });
    expect(deviations[0].violations[0]).toContain("request body");
    expect(deviations[0].violations[0]).toContain("required property 'password'");
  });

  it('pathParametersAreMatchedAgainstTheContractTemplate', async () => {
    const handler = http.get(`${API}/programs/:id/cohorts`, () => HttpResponse.json([]));
    expect(await exchange(handler, { path: '/programs/42/cohorts' })).toEqual([]);
  });

  it('errorResponsesAreNotValidated', async () => {
    const handler = http.get(`${API}/me`, () => new HttpResponse(null, { status: 401 }));
    expect(await exchange(handler, { path: '/me' })).toEqual([]);
  });

  it('baselinedDeviationDoesNotFailButNewOneDoes', async () => {
    const baselinedKey = 'GET /payment-config';
    expect(baselinedKey in baseline).toBe(true);
    const baselined = http.get(`${API}/payment-config`, () => HttpResponse.json({ invented: 1 }));
    expect(await exchange(baselined, { path: '/payment-config' })).toEqual([]);
    const fresh = http.get(`${API}/me`, () => HttpResponse.json({ invented: 1 }));
    expect(await exchange(fresh, { path: '/me' })).toHaveLength(1);
  });
});

describe('default handlers baseline', () => {
  it('everyDefaultHandlerDeviationIsListedInTheBaseline', async () => {
    const deviations = await auditHandlers(handlers, { apiBase: API });
    const unlisted = deviations.map((deviation) => deviation.key).filter((key) => !(key in baseline));
    expect(unlisted).toEqual([]);
  });

  it('baselineHasNoEntryThatNoLongerDeviates', async () => {
    const deviations = await auditHandlers(handlers, { apiBase: API });
    const current = new Set(deviations.map((deviation) => deviation.key));
    const stale = Object.keys(baseline).filter(
      (key) => baseline[key].source === 'default-handlers' && !current.has(key),
    );
    expect(stale).toEqual([]);
  });

  it('baselineCanOnlyShrink', () => {
    let baseVersion;
    try {
      const raw = execFileSync(
        'git',
        ['show', 'origin/develop:frontend/src/test/contract/contract-baseline.json'],
        { encoding: 'utf8', stdio: ['ignore', 'pipe', 'ignore'] },
      );
      baseVersion = JSON.parse(raw);
    } catch {
      return;
    }
    const added = Object.keys(baseline).filter((key) => !(key in baseVersion));
    expect(added).toEqual([]);
  });
});
