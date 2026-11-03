export function errorBody(code, { retryable = false, requestId = 'req-test-0001', violations = [] } = {}) {
  return { code, requestId, retryable, violations };
}
