import { maskSensitiveText } from './maskSensitiveText';

const REQUEST_ID_PATTERN = /^[0-9a-f-]{36}$/;

function withoutQueryOrFragment(value) {
  return typeof value === 'string' ? value.split(/[?#]/)[0] : value;
}

function scrubFrame(frame) {
  return {
    filename: maskSensitiveText(withoutQueryOrFragment(frame.filename)),
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

function scrubTags(tags) {
  const kept = {};
  if (typeof tags?.scope === 'string') kept.scope = maskSensitiveText(tags.scope);
  if (typeof tags?.request_id === 'string' && REQUEST_ID_PATTERN.test(tags.request_id)) kept.request_id = tags.request_id;
  return Object.keys(kept).length ? kept : undefined;
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
    tags: scrubTags(event.tags),
    exception: event.exception?.values ? { values: event.exception.values.map(scrubException) } : undefined,
  };
}
