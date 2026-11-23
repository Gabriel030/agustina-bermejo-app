const stripFrontendPrefix = (path) => path.replace(/^frontend\//, '');

export function renamedKeysFrom(nameStatusOutput) {
  const renames = {};
  for (const line of nameStatusOutput.split('\n')) {
    const [status, from, to] = line.split('\t');
    if (!status?.startsWith('R') || !from || !to) continue;
    renames[stripFrontendPrefix(to)] = stripFrontendPrefix(from);
  }
  return renames;
}

export function inheritRenamedBaseline(base, renames) {
  const inherited = structuredClone(base);
  for (const [current, previous] of Object.entries(renames)) {
    if (!(current in inherited) && previous in base) inherited[current] = base[previous];
  }
  return inherited;
}

export function grownEntries(current, expectedBase) {
  return Object.entries(current).flatMap(([file, perKey]) =>
    Object.entries(perKey)
      .filter(([key, count]) => count > (expectedBase[file]?.[key] ?? 0))
      .map(([key, count]) => `${file} ${key} ${count} > ${expectedBase[file]?.[key] ?? 0}`),
  );
}
