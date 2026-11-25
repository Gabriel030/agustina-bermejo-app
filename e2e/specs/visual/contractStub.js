import { readFileSync } from 'node:fs';

const contract = JSON.parse(readFileSync(new URL('../../../contracts/openapi.json', import.meta.url), 'utf8'));

const NOTIFICATIONS = [
  { id: 1, type: 'ANNOUNCEMENT_PUBLISHED', message: 'Aviso uno', read: false, createdAt: '2026-01-01T10:00:00Z' },
  { id: 2, type: 'ANNOUNCEMENT_PUBLISHED', message: 'Aviso dos', read: true, createdAt: '2026-01-01T09:00:00Z' },
];

const templates = Object.keys(contract.paths).map((template) => ({
  template,
  pattern: new RegExp(`^${template.replace(/\{[^}]+\}/g, '[^/]+')}$`),
}));

function resolve(schema) {
  return schema.$ref ? resolve(contract.components.schemas[schema.$ref.split('/').pop()]) : schema;
}

function generate(rawSchema, depth = 0) {
  const resolved = resolve(rawSchema);
  const schema = Array.isArray(resolved.type) ? { ...resolved, type: resolved.type.find((type) => type !== 'null') } : resolved;
  if (schema.enum) return schema.enum[0];
  if (schema.allOf) return Object.assign({}, ...schema.allOf.map((part) => generate(part, depth)).filter((value) => value && typeof value === 'object'));
  if (schema.oneOf || schema.anyOf) return generate((schema.oneOf ?? schema.anyOf).find((option) => option.type !== 'null'), depth);
  if (schema.type === 'array') return depth > 4 ? [] : [generate(schema.items, depth + 1)];
  if (schema.type === 'object' || schema.properties) {
    return Object.fromEntries(Object.entries(schema.properties ?? {}).map(([key, value]) => [key, generate(value, depth + 1)]));
  }
  if (schema.type === 'integer' || schema.type === 'number') return 1;
  if (schema.type === 'boolean') return false;
  if (schema.format === 'date-time') return '2026-01-01T00:00:00Z';
  if (schema.format === 'date') return '2026-01-01';
  if (schema.pattern === '^-?\\d+(\\.\\d+)?$') return '1.00';
  return 'x';
}

function generatedResponse(pathname) {
  const match = templates.find(({ pattern }) => pattern.test(pathname));
  const schema = match && contract.paths[match.template].get?.responses?.['200']?.content?.['application/json']?.schema;
  return schema ? generate(schema) : [];
}

export function contractExample(template, patch = {}) {
  const schema = contract.paths[template].get.responses['200'].content['application/json'].schema;
  const base = generate(schema);
  return Array.isArray(base) ? base.map((item) => ({ ...item, ...patch })) : { ...base, ...patch };
}

export function contractPage(template, patches) {
  const schema = resolve(contract.paths[template].get.responses['200'].content['application/json'].schema);
  const itemSchema = schema.type === 'array' ? schema.items : schema.properties.items.items;
  const items = patches.map((patch) => ({ ...generate(itemSchema), ...patch }));
  return schema.type === 'array' ? items : { ...generate(schema), items, totalElements: items.length, totalPages: 1 };
}

export async function stubContractApi(page, { roles, overrides = {} }) {
  await page.route('http://localhost:8080/**', (route) => {
    const request = route.request();
    const { pathname } = new URL(request.url());
    if (pathname === '/auth/refresh') {
      return route.fulfill({ json: { accessToken: 'stub-access', refreshToken: 'stub-refresh' } });
    }
    if (pathname === '/me') {
      return route.fulfill({
        json: { id: 1, nombre: 'Ana', apellido: 'Perez', email: 'ana@example.com', roles, hasAvatar: false },
      });
    }
    if (pathname === '/me/modules') return route.fulfill({ json: { billing: { mode: 'ON' } } });
    if (pathname === '/me/notifications') {
      return route.fulfill({ json: { items: NOTIFICATIONS, page: 0, size: 10, totalElements: NOTIFICATIONS.length } });
    }
    if (pathname === '/me/notifications/unread-count') return route.fulfill({ json: { count: 1 } });
    if (pathname === '/me/legal-acceptances/pending') return route.fulfill({ json: { documents: [] } });
    if (pathname === '/me/contact-consents') return route.fulfill({ json: [] });
    const override = overrides[pathname];
    if (override === null) return route.fulfill({ status: 404, json: { code: 'NOT_FOUND' } });
    if (override !== undefined) {
      return route.fulfill({ json: typeof override === 'function' ? override(new URL(request.url()), request) : override });
    }
    if (request.method() !== 'GET') return route.fulfill({ json: {} });
    return route.fulfill({ json: generatedResponse(pathname) });
  });
}
