import { Navigate } from 'react-router-dom';
import { lazyPage } from '@/routes/lazyPage';
const PrimerDirector = lazyPage(() => import('@/features/platform/pages/PrimerDirector'), 'PrimerDirector');

export const routes = {
  public: [],
  shared: [],
  role: [
    { roles: ['platform_admin'], path: 'inicio', element: <Navigate to="/platform_admin/primer-director" replace /> },
    { roles: ['platform_admin'], path: 'primer-director', element: <PrimerDirector /> },
  ],
};
