const isApiRequest = (id, request, { api, ignored }) => request.end === null && request.url.startsWith(api) && !ignored?.has(id);

export function activeRequests(requests, { api, ignored = null, hungMs, now = Date.now() }) {
  return [...requests.entries()].filter(([id, request]) => isApiRequest(id, request, { api, ignored }) && now - request.seenAt < hungMs);
}

export function stalledRequests(requests, { api, ignored = null, hungMs, now = Date.now() }) {
  return [...requests.entries()]
    .filter(([id, request]) => isApiRequest(id, request, { api, ignored }) && now - request.seenAt >= hungMs)
    .map(([, request]) => `${request.method} ${request.url.replace(api, '')}`);
}
