import { loadContract } from './loadContract';

const HTTP_METHODS = ['get', 'post', 'put', 'patch', 'delete'];

let operationIndex;

function escapeRegex(text) {
  return text.replace(/[.*+?^${}()|[\]\\]/g, '\\$&');
}

function compileTemplate(template) {
  const segments = template.split('/').map((segment) =>
    /^\{[^}]+\}$/.test(segment) ? '[^/]+' : escapeRegex(segment),
  );
  return new RegExp(`^${segments.join('/')}/?$`);
}

function buildOperationIndex() {
  const index = [];
  for (const [template, pathItem] of Object.entries(loadContract().paths)) {
    const literalSegments = template.split('/').filter((segment) => !segment.startsWith('{')).length;
    for (const method of HTTP_METHODS) {
      if (pathItem[method]) {
        index.push({
          method,
          template,
          literalSegments,
          pattern: compileTemplate(template),
          operation: pathItem[method],
        });
      }
    }
  }
  index.sort((a, b) => b.literalSegments - a.literalSegments);
  return index;
}

export function matchOperation(method, pathname) {
  operationIndex ??= buildOperationIndex();
  const normalizedMethod = method.toLowerCase();
  return (
    operationIndex.find((entry) => entry.method === normalizedMethod && entry.pattern.test(pathname)) ?? null
  );
}
