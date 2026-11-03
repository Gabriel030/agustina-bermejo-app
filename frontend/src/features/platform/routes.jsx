import { Navigate } from 'react-router-dom';
import { PrimerDirector } from '@/features/platform/pages/PrimerDirector';

export const routes = {
  public: [],
  shared: [],
  role: [
    { roles: ['platform_admin'], path: 'inicio', element: <Navigate to="/platform_admin/primer-director" replace /> },
    { roles: ['platform_admin'], path: 'primer-director', element: <PrimerDirector /> },
  ],
};
