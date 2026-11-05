let reporter = (payload) => console.error('client_error', payload);

export function setClientErrorReporter(next) {
  reporter = next;
}

export function reportClientError(error, context = {}) {
  try {
    reporter({ name: error?.name, message: error?.message, stack: error?.stack, requestId: error?.requestId ?? null, ...context });
  } catch {
    return;
  }
}
