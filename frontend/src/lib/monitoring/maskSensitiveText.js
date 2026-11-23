const MAX_TEXT_LENGTH = 2000;

const URL_QUERY_PATTERN = /(https?:\/\/[^\s?#"']+)\?[^\s#"']*/g;
const TOKEN_PARAM_PATTERN = /\b(token|access_token|refresh_token|password|secret|authorization)=[^&\s"']+/gi;
const JWT_PATTERN = /eyJ[A-Za-z0-9_-]+\.[A-Za-z0-9_-]+\.[A-Za-z0-9_-]+/g;
const EMAIL_PATTERN = /[A-Za-z0-9._%+-]+@[A-Za-z0-9-]+(?:\.[A-Za-z0-9-]+)+/g;
const DNI_PATTERN = /\b\d{1,2}(?:\.\d{3}){2}\b|\b\d{7,8}\b/g;

export function maskSensitiveText(value) {
  if (typeof value !== 'string') return value;
  const masked = value
    .replace(URL_QUERY_PATTERN, '$1?[query]')
    .replace(TOKEN_PARAM_PATTERN, '$1=[redactado]')
    .replace(JWT_PATTERN, '[jwt]')
    .replace(EMAIL_PATTERN, '[email]')
    .replace(DNI_PATTERN, '[dni]');
  return masked.length > MAX_TEXT_LENGTH ? `${masked.slice(0, MAX_TEXT_LENGTH - 1)}…` : masked;
}
