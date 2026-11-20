import { lazyPage } from '@/routes/lazyPage';
const BackupsPage = lazyPage(() => import('@/features/backups/pages/BackupsPage'), 'BackupsPage');

export const routes = {
  public: [],
  shared: [],
  role: [
    { roles: ['director'], path: 'backups', element: <BackupsPage viewer="director" /> },
    { roles: ['platform_admin'], path: 'backups', element: <BackupsPage viewer="platform_admin" /> },
  ],
};
