import Ajv2020 from 'ajv/dist/2020';
import addFormats from 'ajv-formats';
import { loadContract } from './loadContract';
import { matchOperation } from './matchOperation';

const CONTRACT_ID = 'https://contract.test/openapi';
const JSON_CONTENT_TYPE = 'application/json';
const NUMERIC_FORMATS = ['int32', 'int64', 'float', 'double'];

let lenientAjv;
let strictAjv;

function forbidUndeclaredProperties(node) {
  if (Array.isArray(node)) {
    return node.map(forbidUndeclaredProperties);
  }
  if (node && typeof node === 'object') {
    const copy = Object.fromEntries(
      Object.entries(node).map(([key, value]) => [key, forbidUndeclaredProperties(value)]),
    );
    if (copy.properties && copy.additionalProperties === undefined) {
      copy.additionalProperties = false;
    }
    return copy;
  }
  return node;
}

function createAjv(components) {
  const ajv = new Ajv2020({ strict: false, allErrors: true });
  addFormats(ajv);
  NUMERIC_FORMATS.forEach((format) => ajv.addFormat(format, true));
  ajv.addSchema({ $id: CONTRACT_ID, components });
  return ajv;
}

function ajvFor(forbidUndeclared) {
  const { components } = loadContract();
  if (forbidUndeclared) {
    strictAjv ??= createAjv(forbidUndeclaredProperties(components));
    return strictAjv;
  }
  lenientAjv ??= createAjv(components);
  return lenientAjv;
}

function rewriteRefs(node) {
  if (Array.isArray(node)) {
    return node.map(rewriteRefs);
  }
  if (node && typeof node === 'object') {
    return Object.fromEntries(
      Object.entries(node).map(([key, value]) => [
        key,
        key === '$ref' && typeof value === 'string' ? `${CONTRACT_ID}${value}` : rewriteRefs(value),
      ]),
    );
  }
  return node;
}

function describeErrors(errors) {
  return errors.map((error) => `${error.instancePath || '/'} ${error.message}`).join('; ');
}

const validators = new Map();

function validatorFor(cacheKey, schema, forbidUndeclared) {
  if (!validators.has(cacheKey)) {
    validators.set(cacheKey, ajvFor(forbidUndeclared).compile(rewriteRefs(schema)));
  }
  return validators.get(cacheKey);
}

export function warmUpContract() {
  loadContract();
  ajvFor(false);
  ajvFor(true);
  matchOperation('GET', '/');
}

function checkBody(cacheKey, schema, body, label, forbidUndeclared) {
  const validate = validatorFor(cacheKey, schema, forbidUndeclared);
  return validate(body) ? [] : [`${label}: ${describeErrors(validate.errors)}`];
}

function jsonSchemaOf(content) {
  return content?.[JSON_CONTENT_TYPE]?.schema ?? null;
}

export function validateAgainstContract({ method, pathname, request, response }) {
  const match = matchOperation(method, pathname);
  if (!match) {
    return { key: `${method.toUpperCase()} ${pathname}`, violations: ['operation not found in the contract'] };
  }
  const key = `${method.toUpperCase()} ${match.template}`;
  const violations = [];
  const requestSchema = jsonSchemaOf(match.operation.requestBody?.content);
  if (request?.hasBody && requestSchema) {
    violations.push(...checkBody(`${key}|request`, requestSchema, request.body, 'request body', false));
  }
  if (response) {
    const declared = match.operation.responses?.[String(response.status)];
    if (!declared && response.status < 400) {
      violations.push(`status ${response.status} is not declared in the contract`);
    }
    const responseSchema = declared ? jsonSchemaOf(declared.content) : null;
    if (responseSchema && response.hasBody) {
      violations.push(...checkBody(`${key}|${response.status}`, responseSchema, response.body, `response ${response.status} body`, true));
    }
  }
  return { key, violations };
}
