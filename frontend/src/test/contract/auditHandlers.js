import { findContractDeviation } from './withContract';

function sampleUrl(handler) {
  return handler.info.path.replace(/:[A-Za-z]+/g, '1');
}

export async function auditHandlers(handlers, { apiBase }) {
  const deviations = [];
  for (const handler of handlers) {
    const path = handler.info?.path;
    if (typeof path !== 'string' || !path.startsWith(apiBase)) {
      continue;
    }
    const request = new Request(sampleUrl(handler), { method: handler.info.method });
    const result = await handler.run({ request, requestId: 'audit' });
    if (result?.response) {
      const deviation = await findContractDeviation(request, result.response);
      if (deviation) {
        deviations.push(deviation);
      }
    }
  }
  return deviations;
}
