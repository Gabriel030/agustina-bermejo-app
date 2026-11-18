import { lazyPage } from '@/routes/lazyPage';
const BroadcastListPage = lazyPage(() => import('@/features/broadcasts/pages/BroadcastListPage'), 'BroadcastListPage');
const BroadcastDetailPage = lazyPage(() => import('@/features/broadcasts/pages/BroadcastDetailPage'), 'BroadcastDetailPage');

export const routes = {
  public: [],
  shared: [],
  role: [
    { roles: ['director', 'administrativo'], path: 'comunicaciones', element: <BroadcastListPage /> },
    { roles: ['director', 'administrativo'], path: 'comunicaciones/:broadcastId', element: <BroadcastDetailPage /> },
  ],
};
