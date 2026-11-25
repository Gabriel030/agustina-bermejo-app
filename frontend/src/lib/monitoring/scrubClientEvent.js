import { maskSensitiveText } from './maskSensitiveText';

const REQUEST_ID_PATTERN = /^[0-9a-f-]{36}$/;
const OMITTED_MESSAGE = '[mensaje omitido]';
const KNOWN_SCOPES = new Set(['root', 'screen', 'monitoring-start', 'window-error', 'unhandledrejection', 'service-worker']);

const IDENTIFIER = String.raw`[A-Za-z_$][\w$]{0,63}`;
const IDENTIFIER_PATH = String.raw`${IDENTIFIER}(?:\.${IDENTIFIER}){0,5}`;

const KNOWN_MESSAGE_FORMS = [
  new RegExp(String.raw`^Cannot read properties of (?:undefined|null) \(reading '${IDENTIFIER}'\)$`),
  new RegExp(String.raw`^Cannot set properties of (?:undefined|null) \(setting '${IDENTIFIER}'\)$`),
  new RegExp(String.raw`^${IDENTIFIER_PATH} is not a function$`),
  new RegExp(String.raw`^${IDENTIFIER_PATH} is not a constructor$`),
  new RegExp(String.raw`^${IDENTIFIER_PATH} is not iterable$`),
  new RegExp(String.raw`^${IDENTIFIER_PATH} is not defined$`),
  new RegExp(String.raw`^(?:undefined|null) is not an object \(evaluating '${IDENTIFIER_PATH}'\)$`),
  /^ApiError status=(?:\d{3}|sin-estado) code=(?:[A-Z][A-Z0-9_]{0,63}|sin-codigo)$/,
  /^Failed to fetch$/,
  /^Load failed$/,
  /^NetworkError when attempting to fetch resource\.$/,
  /^Maximum call stack size exceeded$/,
  /^Script error\.?$/,
];

function keepKnownMessage(text) {
  if (typeof text !== 'string') return text;
  return KNOWN_MESSAGE_FORMS.some((form) => form.test(text)) ? text : OMITTED_MESSAGE;
}

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
    value: keepKnownMessage(value.value),
    stacktrace: value.stacktrace?.frames ? { frames: value.stacktrace.frames.map(scrubFrame) } : undefined,
  };
}

function scrubTags(tags) {
  const kept = {};
  if (KNOWN_SCOPES.has(tags?.scope)) kept.scope = tags.scope;
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
    message: keepKnownMessage(event.message),
    tags: scrubTags(event.tags),
    exception: event.exception?.values ? { values: event.exception.values.map(scrubException) } : undefined,
  };
}
