import { readdirSync, readFileSync, statSync } from 'node:fs';
import { basename, join, relative, sep } from 'node:path';
import { fileURLToPath } from 'node:url';

export const RAW_TAGS = ['button', 'table', 'input', 'select', 'textarea'];

export const RAW_EQUIVALENTS = {
  button: ['Button'],
  table: ['Table', 'DataTable'],
  input: ['Input'],
  select: ['Select', 'NativeSelect'],
  textarea: ['Textarea'],
};

const SOURCE_FILE = /\.(jsx|js)$/;
const TEST_FILE = /\.(test|spec)\.(jsx|js)$/;
const EXPORT_NAMED_LIST = /export\s*\{([^}]*)\}/g;
const EXPORT_DECLARATION = /export\s+(?:default\s+)?(?:async\s+)?(?:function|const|class)\s+([A-Z]\w*[a-z]\w*)/g;
const IMPORT_STATEMENT = /import\s+([^'";]*?)\s+from\s+['"]([^'"]+)['"]/g;

function walk(dir) {
  return readdirSync(dir).flatMap((entry) => {
    const full = join(dir, entry);
    if (statSync(full).isDirectory()) return walk(full);
    return SOURCE_FILE.test(entry) ? [full] : [];
  });
}

function normalize(path) {
  return path.split(sep).join('/');
}

function exportedComponents(source) {
  const names = new Set();
  for (const match of source.matchAll(EXPORT_DECLARATION)) names.add(match[1]);
  for (const match of source.matchAll(EXPORT_NAMED_LIST)) {
    match[1]
      .split(',')
      .map((part) => part.trim().split(/\s+as\s+/).pop())
      .filter((name) => /^[A-Z]\w*$/.test(name) && /[a-z]/.test(name))
      .forEach((name) => names.add(name));
  }
  return [...names];
}

function importedNames(clause) {
  const names = [];
  const braces = clause.match(/\{([^}]*)\}/);
  if (braces) {
    braces[1]
      .split(',')
      .map((part) => part.trim())
      .filter(Boolean)
      .forEach((part) => {
        const [imported, local] = part.split(/\s+as\s+/);
        names.push({ imported: imported.trim(), local: (local ?? imported).trim() });
      });
  }
  const defaultName = clause.replace(/\{[^}]*\}/, '').replace(',', '').trim();
  if (defaultName && /^\w+$/.test(defaultName)) names.push({ imported: 'default', local: defaultName });
  return names;
}

function countJsx(source, name) {
  return [...source.matchAll(new RegExp(`<${name}(?=[\\s/>])`, 'g'))].length;
}

function countRaw(source, tag) {
  return [...source.matchAll(new RegExp(`<${tag}(?![\\w-])`, 'g'))].length;
}

export function collectUsage(srcDir) {
  const files = walk(srcDir)
    .map((full) => ({ full, rel: normalize(relative(srcDir, full)) }))
    .filter(({ rel }) => !TEST_FILE.test(rel));

  const definitions = new Map();
  for (const { full, rel } of files) {
    const isShared = rel.startsWith('components/');
    if (!isShared) continue;
    const base = basename(rel).replace(SOURCE_FILE, '');
    for (const name of exportedComponents(readFileSync(full, 'utf8'))) {
      definitions.set(`${rel}::${name}`, {
        name,
        module: rel,
        base,
        group: rel.startsWith('components/ui/') ? 'ui' : 'components',
        files: 0,
        uses: 0,
      });
    }
  }

  const importedAnywhere = new Set();
  const raw = Object.fromEntries(RAW_TAGS.map((tag) => [tag, { total: 0, files: {} }]));

  for (const { full, rel } of files) {
    const source = readFileSync(full, 'utf8');
    const inUi = rel.startsWith('components/ui/');

    if (!inUi) {
      for (const tag of RAW_TAGS) {
        const count = countRaw(source, tag);
        if (count > 0) {
          raw[tag].total += count;
          raw[tag].files[rel] = count;
        }
      }
    }

    for (const match of source.matchAll(IMPORT_STATEMENT)) {
      const [, clause, specifier] = match;
      const specBase = basename(specifier);
      const isComponentSpec = specifier.includes('components/') || specifier.startsWith('.');
      if (!isComponentSpec) continue;
      for (const { imported, local } of importedNames(clause)) {
        importedAnywhere.add(imported === 'default' ? local : imported);
        const candidates = [...definitions.values()].filter(
          (definition) =>
            definition.base === specBase &&
            definition.module !== rel &&
            (imported === 'default' ? true : definition.name === imported) &&
            (imported !== 'default' || definition.name === local),
        );
        for (const definition of candidates) {
          definition.files += 1;
          definition.uses += countJsx(source, local);
        }
      }
    }
  }

  const components = [...definitions.values()]
    .filter((definition) => definition.files > 0 || definition.uses > 0)
    .sort((a, b) => b.files - a.files || b.uses - a.uses || a.name.localeCompare(b.name))
    .map(({ name, module, group, files: fileCount, uses }) => ({ name, module, group, files: fileCount, uses }));

  const unused = [...definitions.values()]
    .filter((definition) => definition.files === 0 && definition.uses === 0 && !importedAnywhere.has(definition.name))
    .map((definition) => definition.name)
    .filter((name, index, all) => all.indexOf(name) === index)
    .sort();

  const rawSummary = Object.fromEntries(
    RAW_TAGS.map((tag) => [
      tag,
      {
        occurrences: raw[tag].total,
        files: Object.keys(raw[tag].files).length,
        equivalents: RAW_EQUIVALENTS[tag],
        byFile: raw[tag].files,
      },
    ]),
  );

  return { components, unused, raw: rawSummary };
}

export function formatTable(report) {
  const rows = report.components.map((c) => [c.name, c.group, String(c.files), String(c.uses)]);
  const header = ['Componente', 'Origen', 'Archivos', 'Usos'];
  const widths = header.map((title, index) => Math.max(title.length, ...rows.map((row) => row[index].length)));
  const line = (cells) => cells.map((cell, index) => cell.padEnd(widths[index])).join('  ');
  const componentLines = [line(header), line(widths.map((width) => '-'.repeat(width))), ...rows.map(line)];

  const rawHeader = ['Elemento crudo', 'Archivos', 'Ocurrencias', 'Usar en su lugar'];
  const rawRows = RAW_TAGS.map((tag) => [
    `<${tag}>`,
    String(report.raw[tag].files),
    String(report.raw[tag].occurrences),
    report.raw[tag].equivalents.join(' / '),
  ]);
  const rawWidths = rawHeader.map((title, index) => Math.max(title.length, ...rawRows.map((row) => row[index].length)));
  const rawLine = (cells) => cells.map((cell, index) => cell.padEnd(rawWidths[index])).join('  ');

  return [
    ...componentLines,
    '',
    rawLine(rawHeader),
    rawLine(rawWidths.map((width) => '-'.repeat(width))),
    ...rawRows.map(rawLine),
    '',
    `Componentes compartidos sin usos: ${report.unused.length ? report.unused.join(', ') : 'ninguno'}`,
  ].join('\n');
}

const invokedDirectly = process.argv[1] && fileURLToPath(import.meta.url) === process.argv[1];

if (invokedDirectly) {
  const args = process.argv.slice(2);
  const dirArg = args.find((arg) => !arg.startsWith('--'));
  const srcDir = dirArg ?? fileURLToPath(new URL('../src/', import.meta.url));
  const report = collectUsage(srcDir);
  console.log(args.includes('--json') ? JSON.stringify(report, null, 2) : formatTable(report));
}
