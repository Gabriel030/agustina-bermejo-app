const HEADING = /^(#{2,3})\s+(.*)$/;
const UNORDERED_ITEM = /^[-*]\s+(.*)$/;
const ORDERED_ITEM = /^\d+[.)]\s+(.*)$/;
const TABLE_ROW = /^\|(.*)\|\s*$/;
const TABLE_SEPARATOR = /^\|[\s:|-]+\|\s*$/;
const INLINE = /\*\*(.+?)\*\*|`([^`]+)`|\[([^\]]+)\]\(([^)\s]+)\)/g;

export function slugifyHeading(text) {
  return text
    .normalize('NFD')
    .replace(/[̀-ͯ]/g, '')
    .toLowerCase()
    .replace(/[^a-z0-9]+/g, '-')
    .replace(/^-|-$/g, '');
}

export function isSafeLink(href) {
  return /^(https?:\/\/|mailto:|\/(?!\/)|#)/i.test(href);
}

export function parseInline(text) {
  const tokens = [];
  let cursor = 0;
  for (const match of text.matchAll(INLINE)) {
    if (match.index > cursor) tokens.push({ type: 'text', value: text.slice(cursor, match.index) });
    if (match[1] !== undefined) tokens.push({ type: 'strong', value: match[1] });
    else if (match[2] !== undefined) tokens.push({ type: 'code', value: match[2] });
    else if (isSafeLink(match[4])) tokens.push({ type: 'link', value: match[3], href: match[4] });
    else tokens.push({ type: 'text', value: match[3] });
    cursor = match.index + match[0].length;
  }
  if (cursor < text.length) tokens.push({ type: 'text', value: text.slice(cursor) });
  return tokens;
}

function splitCells(line) {
  return line
    .trim()
    .replace(/^\||\|$/g, '')
    .split('|')
    .map((cell) => cell.trim());
}

function isTableStart(lines, index) {
  return TABLE_ROW.test(lines[index].trim()) && TABLE_SEPARATOR.test((lines[index + 1] ?? '').trim());
}

function startsBlock(lines, index) {
  const line = lines[index].trim();
  return HEADING.test(line) || UNORDERED_ITEM.test(line) || ORDERED_ITEM.test(line) || isTableStart(lines, index);
}

function readList(lines, start, pattern, type) {
  const items = [];
  let index = start;
  while (index < lines.length && pattern.test(lines[index].trim())) {
    items.push(lines[index].trim().match(pattern)[1]);
    index += 1;
  }
  return { block: { type, items }, next: index };
}

function readTable(lines, start) {
  const header = splitCells(lines[start]);
  const rows = [];
  let index = start + 2;
  while (index < lines.length && TABLE_ROW.test(lines[index].trim())) {
    rows.push(splitCells(lines[index]));
    index += 1;
  }
  return { block: { type: 'table', header, rows }, next: index };
}

function readParagraph(lines, start) {
  const parts = [];
  let index = start;
  while (index < lines.length && lines[index].trim() !== '' && (index === start || !startsBlock(lines, index))) {
    parts.push(lines[index].trim());
    index += 1;
  }
  return { block: { type: 'paragraph', text: parts.join(' ') }, next: index };
}

function readBlock(lines, index) {
  const line = lines[index].trim();
  const heading = HEADING.exec(line);
  if (heading) {
    const text = heading[2].trim();
    return { block: { type: 'heading', level: heading[1].length, text, id: slugifyHeading(text) }, next: index + 1 };
  }
  if (UNORDERED_ITEM.test(line)) return readList(lines, index, UNORDERED_ITEM, 'unordered');
  if (ORDERED_ITEM.test(line)) return readList(lines, index, ORDERED_ITEM, 'ordered');
  if (isTableStart(lines, index)) return readTable(lines, index);
  return readParagraph(lines, index);
}

export function parseMarkdown(source) {
  const lines = source.split(/\r?\n/);
  const blocks = [];
  let index = 0;
  while (index < lines.length) {
    if (lines[index].trim() === '') {
      index += 1;
      continue;
    }
    const { block, next } = readBlock(lines, index);
    blocks.push(block);
    index = next;
  }
  return blocks;
}

function inlineToPlain(text) {
  return parseInline(text)
    .map((token) => token.value)
    .join('');
}

export function plainText(blocks) {
  return blocks
    .map((block) => {
      if (block.type === 'heading' || block.type === 'paragraph') return inlineToPlain(block.text);
      if (block.type === 'table') return [...block.header, ...block.rows.flat()].map(inlineToPlain).join(' ');
      return block.items.map(inlineToPlain).join(' ');
    })
    .join('\n');
}
