import { routes as identity } from '@/features/identity/routes';
import { routes as academicStructure } from '@/features/academic-structure/routes';
import { routes as enrollment } from '@/features/enrollment/routes';
import { routes as attendance } from '@/features/attendance/routes';
import { routes as evaluation } from '@/features/evaluation/routes';
import { routes as billing } from '@/features/billing/routes';
import { routes as graduation } from '@/features/graduation/routes';
import { routes as announcements } from '@/features/announcements/routes';
import { routes as notifications } from '@/features/notifications/routes';
import { routes as workspace } from '@/features/workspace/routes';

export const FEATURE_ROUTES = {
  identity,
  'academic-structure': academicStructure,
  enrollment,
  attendance,
  evaluation,
  billing,
  graduation,
  announcements,
  notifications,
  workspace,
};

const collect = (kind) => Object.values(FEATURE_ROUTES).flatMap((featureRoutes) => featureRoutes[kind]);

export const PUBLIC_ROUTES = collect('public');
export const SHARED_ROUTES = collect('shared');
export const ROLE_ROUTES = collect('role');

export function roleRoutesFor(role) {
  return ROLE_ROUTES.filter((route) => route.roles.includes(role));
}
