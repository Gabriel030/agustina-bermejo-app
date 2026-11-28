const FRONT_MATTER = /^---\r?\n([\s\S]*?)\r?\n---\r?\n?([\s\S]*)$/;

function unquote(value) {
  return value.replace(/^["']|["']$/g, '');
}

function parseValue(raw) {
  const value = raw.trim();
  if (value.startsWith('[') && value.endsWith(']')) {
    return value
      .slice(1, -1)
      .split(',')
      .map((item) => unquote(item.trim()))
      .filter(Boolean);
  }
  return unquote(value);
}

export function parseFrontMatter(source) {
  const match = FRONT_MATTER.exec(source);
  if (!match) return { data: {}, body: source };
  const data = {};
  match[1].split(/\r?\n/).forEach((line) => {
    const separator = line.indexOf(':');
    if (separator < 1) return;
    data[line.slice(0, separator).trim()] = parseValue(line.slice(separator + 1));
  });
  return { data, body: match[2] };
}
