const MAX_TEXT_LENGTH = 2000;

const QUERY_PATTERN = /\?[^\s#"']+/g;
const TOKEN_PARAM_PATTERN = /\b(token|access_token|refresh_token|password|secret|authorization)=[^&\s"']{1,500}/gi;
const JWT_PATTERN = /eyJ[A-Za-z0-9_-]{1,500}\.[A-Za-z0-9_-]{1,500}\.[A-Za-z0-9_-]{1,500}/g;
const EMAIL_PATTERN = /[\p{L}\p{N}._%+-]{1,64}@[\p{L}\p{N}-]{1,255}(?:\.[\p{L}\p{N}-]{1,63}){1,10}/gu;
const PHONE_PATTERN = /\+54(?:[\s-]?\d){8,14}|\b\d{2,4}-\d{4}-\d{4}\b/g;
const DOCUMENT_PATTERN = /\b\d(?:[.\s-]?\d){6,10}\b/g;
const OPAQUE_TOKEN_PATTERN = /\b[A-Za-z0-9_-]{32,}\b/g;
const PATH_ID_PATTERN = /\/\d{1,20}(?=[/\s?#"']|$)/g;

function truncate(text) {
  return text.length > MAX_TEXT_LENGTH ? `${text.slice(0, MAX_TEXT_LENGTH - 1)}…` : text;
}

export function maskSensitiveText(value) {
  if (typeof value !== 'string') return value;
  const masked = truncate(value)
    .replace(QUERY_PATTERN, '?[redactado]')
    .replace(TOKEN_PARAM_PATTERN, '$1=[redactado]')
    .replace(JWT_PATTERN, '[jwt]')
    .replace(EMAIL_PATTERN, '[email]')
    .replace(PHONE_PATTERN, '[telefono]')
    .replace(DOCUMENT_PATTERN, '[dni]')
    .replace(OPAQUE_TOKEN_PATTERN, '[token]')
    .replace(PATH_ID_PATTERN, '/[id]');
  return truncate(masked);
}
