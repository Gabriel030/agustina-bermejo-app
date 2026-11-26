import { groupMenuItems } from '@/routes/roleMenu';

export function isPathWithin(to, pathname) {
  return pathname === to || pathname.startsWith(`${to}/`);
}

function linkNode(item) {
  return { type: 'link', id: item.to, item };
}

function groupNode(group) {
  if (group.sections) {
    return {
      type: 'group',
      id: group.id,
      label: group.label,
      children: group.sections.map((section) => ({
        type: 'group',
        id: `${group.id}/${section.id}`,
        label: section.label,
        showCount: true,
        children: section.items.map(linkNode),
      })),
    };
  }
  return { type: 'group', id: group.id, label: group.label, children: group.items.map(linkNode) };
}

export function buildDrawerEntries(items, groups) {
  if (!groups) return items.map(linkNode);
  return groups.flatMap((group) => (group.label ? [groupNode(group)] : group.items.map(linkNode)));
}

export function collectLinks(entries) {
  return entries.flatMap((entry) => (entry.type === 'link' ? [entry] : collectLinks(entry.children)));
}

export function findCurrentTarget(entries, pathname) {
  const matches = collectLinks(entries).filter((link) => isPathWithin(link.item.to, pathname));
  return matches.sort((a, b) => b.item.to.length - a.item.to.length)[0]?.item.to ?? null;
}

export function containsTarget(entry, target) {
  if (target == null) return false;
  if (entry.type === 'link') return entry.item.to === target;
  return entry.children.some((child) => containsTarget(child, target));
}

export function countLinks(entry) {
  return collectLinks(entry.children).length;
}

export function resolveLevelNodes(entries, path) {
  const nodes = [];
  let current = entries;
  for (const id of path) {
    const node = current.find((entry) => entry.type === 'group' && entry.id === id);
    if (!node) break;
    nodes.push(node);
    current = node.children;
  }
  return nodes;
}

export function buildTopNavEntries(items, groups) {
  if (!groups) return items.map((item) => ({ type: 'link', kind: 'link', id: item.to, item }));
  return groups.flatMap((group) => {
    const own = group.items;
    if (own.length === 0) return [];
    if (group.panel === 'catalog') return [{ type: 'catalog', kind: 'panel', id: group.id, item: own[0] }];
    if (!group.label) return own.map((item) => ({ type: 'link', kind: 'link', id: item.to, item }));
    const sections = group.sections;
    return [{ type: 'group', kind: 'menu', id: group.id, label: group.label, wide: Boolean(sections), items: own, sections }];
  });
}

export function buildNavModel(role, items) {
  const groups = groupMenuItems(role, items);
  return { items, groups, entries: buildTopNavEntries(items, groups) };
}
