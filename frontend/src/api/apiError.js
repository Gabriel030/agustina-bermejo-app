export class ApiError extends Error {
  constructor(message, { status, code = null, retryable = false, requestId = null, violations = [] }) {
    super(message);
    this.name = 'ApiError';
    this.status = status;
    this.code = code;
    this.retryable = retryable;
    this.requestId = requestId;
    this.violations = violations;
  }
}
