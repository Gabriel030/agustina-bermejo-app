import { validateAgainstContract } from './validateAgainstContract';
import baseline from './contract-baseline.json';

const WRAPPED = Symbol('contractWrapped');
const recorded = [];

async function readJsonBody(message) {
  if (!(message.headers.get('content-type') ?? '').includes('application/json')) {
    return { hasBody: false };
  }
  const text = await message.text();
  if (!text) {
    return { hasBody: false };
  }
  try {
    return { hasBody: true, body: JSON.parse(text) };
  } catch {
    return { hasBody: false };
  }
}

export async function findContractDeviation(request, response) {
  const url = new URL(request.url);
  const requestPart = await readJsonBody(request.clone());
  const responsePart = await readJsonBody(response.clone());
  const { key, violations } = validateAgainstContract({
    method: request.method,
    pathname: url.pathname,
    request: requestPart,
    response: { status: response.status, ...responsePart },
  });
  return violations.length > 0 ? { key, violations } : null;
}

export function withContract(handler, { apiBase }) {
  const path = handler.info?.path;
  if (handler[WRAPPED] || typeof path !== 'string' || !path.startsWith(apiBase)) {
    return handler;
  }
  const original = handler.resolver;
  handler.resolver = async (info) => {
    const response = await original(info);
    if (response instanceof Response) {
      const deviation = await findContractDeviation(info.request, response);
      if (deviation && !(deviation.key in baseline)) {
        recorded.push(deviation);
      }
    }
    return response;
  };
  handler[WRAPPED] = true;
  return handler;
}

export function takeContractDeviations() {
  return recorded.splice(0, recorded.length);
}

export function formatContractDeviations(deviations) {
  return deviations.map(({ key, violations }) => `${key}\n  - ${violations.join('\n  - ')}`).join('\n');
}
