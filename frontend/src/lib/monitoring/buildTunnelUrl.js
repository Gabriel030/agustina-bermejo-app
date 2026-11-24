export function buildTunnelUrl(apiBaseUrl) {
  return `${apiBaseUrl.replace(/\/+$/, '')}/public/monitoring/tunnel`;
}
