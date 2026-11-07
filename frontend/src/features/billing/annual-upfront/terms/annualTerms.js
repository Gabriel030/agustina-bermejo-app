export const TERMS_MIN_LENGTH = 50;
export const TERMS_MAX_LENGTH = 20000;

export const TERMS_PATH = '/terminos-pago-anual';

export function termsPathOf(versionNumber) {
  return `${TERMS_PATH}/${versionNumber}`;
}

export function termsBlocksOf(text) {
  return String(text ?? '')
    .split(/\n\s*\n/)
    .map((block) => block.trim())
    .filter(Boolean)
    .map((block) => {
      const heading = block.match(/^##\s+(.+?)(?:\n([\s\S]*))?$/);
      if (!heading) return { type: 'paragraph', text: block };
      return { type: 'heading', text: heading[1].trim(), body: heading[2]?.trim() ?? '' };
    });
}

export function isTermsTextValid(text) {
  const length = text.trim().length;
  return length >= TERMS_MIN_LENGTH && length <= TERMS_MAX_LENGTH;
}
