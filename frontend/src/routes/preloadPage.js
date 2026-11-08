import { matchPath } from 'react-router-dom';
import { roleRoutesFor } from '@/routes/featureRoutes';
import { ROLE_CONFIG } from '@/routes/roleConfig';

function startPreload(element) {
  const preload = element?.type?.preload;
  if (typeof preload !== 'function') return null;
  return Promise.resolve(preload()).catch(() => null);
}

export function preloadRolePage(role, pathname) {
  if (!role || !pathname) return null;
  const prefix = `/${role}/`;
  if (!pathname.startsWith(prefix)) return null;
  const remainder = `/${pathname.slice(prefix.length).replace(/\/+$/, '')}`;
  const route = roleRoutesFor(role).find((candidate) => matchPath({ path: `/${candidate.path}`, end: true }, remainder));
  return route ? startPreload(route.element) : null;
}

export function preloadPageForPath(pathname) {
  const role = pathname?.split('/')[1];
  ROLE_CONFIG[role]?.Layout?.preload?.();
  return preloadRolePage(role, pathname);
}
