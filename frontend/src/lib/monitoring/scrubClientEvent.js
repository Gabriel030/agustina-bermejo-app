import { maskSensitiveText } from './maskSensitiveText';

function scrubFrame(frame) {
  return {
    filename: maskSensitiveText(frame.filename),
    function: frame.function,
    lineno: frame.lineno,
    colno: frame.colno,
    in_app: frame.in_app,
  };
}

function scrubException(value) {
  return {
    type: maskSensitiveText(value.type),
    value: maskSensitiveText(value.value),
    stacktrace: value.stacktrace?.frames ? { frames: value.stacktrace.frames.map(scrubFrame) } : undefined,
  };
}

export function scrubClientEvent(event) {
  if (event.type) return null;
  return {
    event_id: event.event_id,
    timestamp: event.timestamp,
    platform: event.platform,
    level: event.level,
    environment: event.environment,
    release: event.release,
    message: maskSensitiveText(event.message),
    exception: event.exception?.values ? { values: event.exception.values.map(scrubException) } : undefined,
  };
}
