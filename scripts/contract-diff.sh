#!/usr/bin/env bash
set -euo pipefail

if [ "$#" -ne 2 ]; then
  echo "uso: $0 <ref-base> <ref-nueva>" >&2
  exit 0
fi

TMP_DIR="$(mktemp -d)"
trap 'rm -rf "${TMP_DIR}"' EXIT

for pair in "base:$1" "new:$2"; do
  name="${pair%%:*}"
  ref="${pair#*:}"
  if ! git show "${ref}:contracts/openapi.json" > "${TMP_DIR}/${name}.json" 2>/dev/null; then
    echo "no se pudo leer contracts/openapi.json en la ref '${ref}'" >&2
    exit 0
  fi
done

node - "${TMP_DIR}/base.json" "${TMP_DIR}/new.json" "$1" "$2" <<'NODE'
const fs = require("fs");
const [baseFile, newFile, baseRef, newRef] = process.argv.slice(2);
const base = JSON.parse(fs.readFileSync(baseFile, "utf8"));
const next = JSON.parse(fs.readFileSync(newFile, "utf8"));
const METHODS = ["get", "put", "post", "delete", "options", "head", "patch", "trace"];
const findings = [];

const report = (direction, message) => findings.push({ direction, message });

const schemaDirection = (name) => {
  if (/(Request|Command|Input)$/.test(name)) return "REQUEST";
  if (/Response$/.test(name)) return "RESPONSE";
  return "GENERAL";
};

const refName = (schema) =>
  schema && typeof schema.$ref === "string" ? schema.$ref.split("/").pop() : null;

const typeSet = (schema) => {
  const types = new Set();
  if (!schema) return types;
  const raw = schema.type;
  if (Array.isArray(raw)) raw.forEach((t) => types.add(t));
  else if (raw) types.add(raw);
  if (schema.nullable === true) types.add("null");
  for (const key of ["oneOf", "anyOf"]) {
    for (const option of schema[key] || []) {
      if (option && option.type === "null") types.add("null");
    }
  }
  return types;
};

const fmt = (set) => [...set].sort().join("|") || "(sin tipo)";

const compareSchema = (a, b, path, direction, depth) => {
  if (!a || !b || depth > 12) return;
  const refA = refName(a);
  const refB = refName(b);
  if (refA || refB) {
    if (refA !== refB) {
      report(direction, `${path}: cambia la referencia de ${refA || "inline"} a ${refB || "inline"}`);
    }
    return;
  }

  const typesA = typeSet(a);
  const typesB = typeSet(b);
  const nonNullA = new Set([...typesA].filter((t) => t !== "null"));
  const nonNullB = new Set([...typesB].filter((t) => t !== "null"));
  if (fmt(nonNullA) !== fmt(nonNullB) && nonNullA.size > 0) {
    report(direction, `${path}: cambia el tipo de ${fmt(nonNullA)} a ${fmt(nonNullB)}`);
  }
  if (!typesA.has("null") && typesB.has("null")) {
    report(direction, `${path}: pasa a nullable`);
  }
  if (a.format !== b.format && a.format !== undefined) {
    report(direction, `${path}: cambia el formato de ${a.format} a ${b.format ?? "(ninguno)"}`);
  }

  if (Array.isArray(a.enum) && Array.isArray(b.enum)) {
    for (const value of a.enum) {
      if (!b.enum.includes(value)) {
        report(direction, `${path}: se remueve el valor de enum ${JSON.stringify(value)}`);
      }
    }
  }

  if (typeof b.maxLength === "number" && (typeof a.maxLength !== "number" || b.maxLength < a.maxLength)) {
    report(direction, `${path}: maxLength baja de ${a.maxLength ?? "(sin límite)"} a ${b.maxLength}`);
  }

  const propsA = a.properties || {};
  const propsB = b.properties || {};
  for (const key of Object.keys(propsA)) {
    if (!(key in propsB)) {
      report(direction, `${path}: se remueve la propiedad ${key}`);
    } else {
      compareSchema(propsA[key], propsB[key], `${path}.${key}`, direction, depth + 1);
    }
  }
  const requiredA = new Set(a.required || []);
  for (const key of b.required || []) {
    if (!requiredA.has(key)) {
      report(direction, `${path}: nuevo required ${key}`);
    }
  }

  if (a.items && b.items) {
    compareSchema(a.items, b.items, `${path}[]`, direction, depth + 1);
  }
  if (a.additionalProperties && typeof a.additionalProperties === "object" &&
      b.additionalProperties && typeof b.additionalProperties === "object") {
    compareSchema(a.additionalProperties, b.additionalProperties, `${path}{}`, direction, depth + 1);
  }
  for (const key of ["allOf"]) {
    const listA = a[key] || [];
    const listB = b[key] || [];
    listA.forEach((item, index) => {
      if (listB[index]) compareSchema(item, listB[index], `${path}.${key}[${index}]`, direction, depth + 1);
    });
  }
};

const jsonSchemaOf = (content) => {
  if (!content) return null;
  const media = content["application/json"] || content["*/*"] || Object.values(content)[0];
  return media ? media.schema : null;
};

const schemasA = (base.components && base.components.schemas) || {};
const schemasB = (next.components && next.components.schemas) || {};
for (const name of Object.keys(schemasA)) {
  const direction = schemaDirection(name);
  if (!(name in schemasB)) {
    report(direction, `esquema ${name}: se remueve`);
    continue;
  }
  compareSchema(schemasA[name], schemasB[name], `esquema ${name}`, direction, 0);
}

const parameterKey = (p) => `${p.in}:${p.name}`;
const resolveParam = (doc, p) => {
  const name = refName(p);
  if (!name) return p;
  return (doc.components && doc.components.parameters && doc.components.parameters[name]) || p;
};

const pathsA = base.paths || {};
const pathsB = next.paths || {};
for (const path of Object.keys(pathsA)) {
  if (!(path in pathsB)) {
    report("GENERAL", `path ${path}: se remueve`);
    continue;
  }
  const sharedParamsA = (pathsA[path].parameters || []).map((p) => resolveParam(base, p));
  const sharedParamsB = (pathsB[path].parameters || []).map((p) => resolveParam(next, p));
  for (const method of METHODS) {
    const opA = pathsA[path][method];
    const opB = pathsB[path][method];
    const label = `${method.toUpperCase()} ${path}`;
    if (!opA) continue;
    if (!opB) {
      report("GENERAL", `operación ${label}: se remueve`);
      continue;
    }

    const paramsA = new Map([...sharedParamsA, ...(opA.parameters || []).map((p) => resolveParam(base, p))]
      .map((p) => [parameterKey(p), p]));
    const paramsB = new Map([...sharedParamsB, ...(opB.parameters || []).map((p) => resolveParam(next, p))]
      .map((p) => [parameterKey(p), p]));
    for (const [key, paramB] of paramsB) {
      const paramA = paramsA.get(key);
      if (!paramA && paramB.required === true) {
        report("REQUEST", `${label}: nuevo parámetro requerido ${key}`);
      } else if (paramA && paramA.required !== true && paramB.required === true) {
        report("REQUEST", `${label}: el parámetro ${key} pasa a requerido`);
      }
      if (paramA) {
        compareSchema(paramA.schema, paramB.schema, `${label} parámetro ${key}`, "REQUEST", 0);
      }
    }
    for (const [key] of paramsA) {
      if (!paramsB.has(key)) {
        report("REQUEST", `${label}: se remueve el parámetro ${key}`);
      }
    }

    const bodyA = opA.requestBody;
    const bodyB = opB.requestBody;
    if (bodyB) {
      if (!bodyA && bodyB.required === true) {
        report("REQUEST", `${label}: nuevo request body requerido`);
      } else if (bodyA && bodyA.required !== true && bodyB.required === true) {
        report("REQUEST", `${label}: el request body pasa a requerido`);
      }
      if (bodyA) {
        compareSchema(jsonSchemaOf(bodyA.content), jsonSchemaOf(bodyB.content), `${label} request body`, "REQUEST", 0);
      }
    }

    for (const status of Object.keys(opA.responses || {})) {
      const respA = opA.responses[status];
      const respB = (opB.responses || {})[status];
      if (!respB) {
        report("RESPONSE", `${label}: se remueve la respuesta ${status}`);
        continue;
      }
      compareSchema(jsonSchemaOf(respA.content), jsonSchemaOf(respB.content), `${label} respuesta ${status}`, "RESPONSE", 0);
    }
  }
}

const order = { REQUEST: 0, RESPONSE: 1, GENERAL: 2 };
findings.sort((x, y) => order[x.direction] - order[y.direction] || x.message.localeCompare(y.message));
const unique = [...new Map(findings.map((f) => [`${f.direction}|${f.message}`, f])).values()];

console.log(`Comparando contracts/openapi.json: ${baseRef} -> ${newRef}`);
for (const f of unique) {
  console.log(`[${f.direction}] ${f.message}`);
}
const count = (d) => unique.filter((f) => f.direction === d).length;
console.log(`Resumen: ${unique.length} hallazgos potencialmente rompedores (${count("REQUEST")} [REQUEST], ${count("RESPONSE")} [RESPONSE], ${count("GENERAL")} [GENERAL]).`);
if (unique.length === 0) {
  console.log("Diff aditivo: sin cambios potencialmente rompedores.");
}
NODE
