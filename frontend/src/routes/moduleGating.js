import { FEATURE_ROUTES, withSubdirectorInheritance } from '@/routes/featureRoutes';

export const BILLING_MODULE = 'billing';

export function isBillingPath(role, path) {
  return withSubdirectorInheritance(FEATURE_ROUTES.billing.role).some((route) => route.path === path && route.roles.includes(role));
}

export function isModuleEnabled(modules, moduleCode) {
  return moduleCode === BILLING_MODULE ? modules.billingEnabled : true;
}

export function moduleOfPath(role, path) {
  return isBillingPath(role, path) ? BILLING_MODULE : null;
}
