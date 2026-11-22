import { lazyPage } from '@/routes/lazyPage';
const PermissionsPage = lazyPage(() => import('@/features/permissions/pages/PermissionsPage'), 'PermissionsPage');

export const routes = {
  public: [],
  shared: [],
  role: [{ roles: ['director', 'subdirector'], path: 'permisos', element: <PermissionsPage /> }],
};
