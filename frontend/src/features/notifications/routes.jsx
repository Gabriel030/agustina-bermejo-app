import { lazyPage } from '@/routes/lazyPage';
const BroadcastWizard = lazyPage(() => import('@/features/broadcasts/pages/BroadcastWizard'), 'BroadcastWizard');

export const routes = {
  public: [],
  shared: [],
  role: [{ roles: ['administrativo', 'director'], path: 'comunicaciones/nuevo', element: <BroadcastWizard /> }],
};
