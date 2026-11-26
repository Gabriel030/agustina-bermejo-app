import { readdirSync, readFileSync, existsSync } from 'node:fs';
import { join } from 'node:path';

const ROUTE_PATTERN = /roles:\s*\[([^\]]*)\][^\n]*?path:\s*'([^']+)'/g;

export function extractRoleRoutes(featuresDir) {
  const byRole = {};
  for (const feature of readdirSync(featuresDir)) {
    const file = join(featuresDir, feature, 'routes.jsx');
    if (!existsSync(file)) continue;
    for (const match of readFileSync(file, 'utf8').matchAll(ROUTE_PATTERN)) {
      const roleNames = match[1].split(',').map((name) => name.trim().replace(/'/g, '')).filter(Boolean);
      for (const role of roleNames) (byRole[role] ??= new Set()).add(match[2]);
    }
  }
  return byRole;
}

export function findCoverageGaps(routesByRole, roles, excludedRoles) {
  const byKey = Object.fromEntries(Object.values(roles).map((role) => [role.key, role]));
  const gaps = [];
  for (const [roleKey, routes] of Object.entries(routesByRole)) {
    if (excludedRoles[roleKey]) continue;
    const role = byKey[roleKey];
    if (!role) {
      gaps.push({ role: roleKey, route: '*', problem: 'rol sin configuración en screens.mjs ni motivo de exclusión' });
      continue;
    }
    const listed = new Set([...role.screens, ...role.details.map((detail) => detail.route)]);
    for (const route of routes) {
      if (route.includes(':')) continue;
      if (!listed.has(route)) gaps.push({ role: roleKey, route, problem: 'ruta sin listar en screens.mjs' });
    }
    for (const screen of role.screens) {
      if (screen !== 'inicio' && !routes.has(screen)) gaps.push({ role: roleKey, route: screen, problem: 'pantalla listada que no existe en las rutas' });
    }
    for (const detail of role.details) {
      if (!routes.has(detail.route)) gaps.push({ role: roleKey, route: detail.route, problem: 'detalle que no existe en las rutas' });
    }
  }
  return gaps;
}
