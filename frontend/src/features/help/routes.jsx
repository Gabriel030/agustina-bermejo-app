import { lazyPage } from '@/routes/lazyPage';
const HelpPage = lazyPage(() => import('@/features/help/pages/HelpPage'), 'HelpPage');

export const routes = {
  public: [],
  shared: [{ path: '/ayuda', element: <HelpPage /> }],
  role: [],
};
