import { loadContract } from '@/test/contract/loadContract';

function resolve(schema, contract) {
  return schema.$ref ? resolve(contract.components.schemas[schema.$ref.split('/').pop()], contract) : schema;
}

function generate(rawSchema, contract) {
  const resolved = resolve(rawSchema, contract);
  const schema = Array.isArray(resolved.type) ? { ...resolved, type: resolved.type.find((type) => type !== 'null') } : resolved;
  if (schema.enum) return schema.enum[0];
  if (schema.oneOf || schema.anyOf) return generate((schema.oneOf ?? schema.anyOf).find((option) => option.type !== 'null'), contract);
  if (schema.type === 'array') return [generate(schema.items, contract)];
  if (schema.type === 'object' || schema.properties) {
    return Object.fromEntries(Object.entries(schema.properties ?? {}).map(([key, value]) => [key, generate(value, contract)]));
  }
  if (schema.type === 'integer' || schema.type === 'number') return 1;
  if (schema.type === 'boolean') return false;
  if (schema.format === 'date-time') return '2026-01-01T00:00:00Z';
  if (schema.format === 'date') return '2026-01-01';
  return 'x';
}

function responseSchema(path) {
  const contract = loadContract();
  return { contract, schema: contract.paths[path].get.responses['200'].content['application/json'].schema };
}

export function contractList(path, patches) {
  const { contract, schema } = responseSchema(path);
  const base = resolve(schema, contract);
  const itemSchema = base.type === 'array' ? base.items : base.properties.items.items;
  const items = patches.map((patch) => ({ ...generate(itemSchema, contract), ...patch }));
  if (base.type === 'array') return items;
  return { ...generate(schema, contract), items, totalElements: items.length };
}

export function contractObject(path, patch = {}) {
  const { contract, schema } = responseSchema(path);
  return { ...generate(schema, contract), ...patch };
}
