import baseline from './unhandled-baseline.json';

const recorded = new Map();

const normalizePath = (pathname) => pathname.replace(/\/\d+(?=\/|$)/g, '/:id');

export const recordUnhandledRequest = (request) => {
  const key = `${request.method} ${normalizePath(new URL(request.url).pathname)}`;
  recorded.set(key, (recorded.get(key) ?? 0) + 1);
  return key;
};

export const takeUnhandledExcess = (testPath) => {
  const file = testPath.slice(testPath.indexOf('/src/') + 1);
  const allowed = baseline[file] ?? {};
  const excess = [...recorded.entries()]
    .filter(([key]) => !(key in allowed))
    .map(([key, count]) => `${key} x${count}`);
  recorded.clear();
  return excess;
};
