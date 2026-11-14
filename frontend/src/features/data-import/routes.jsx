import { lazyPage } from '@/routes/lazyPage';
const DataImportListPage = lazyPage(() => import('@/features/data-import/pages/DataImportListPage'), 'DataImportListPage');
const DataImportNewPage = lazyPage(() => import('@/features/data-import/pages/DataImportNewPage'), 'DataImportNewPage');
const DataImportDetailPage = lazyPage(() => import('@/features/data-import/pages/DataImportDetailPage'), 'DataImportDetailPage');

export const routes = {
  public: [],
  shared: [],
  role: [
    { roles: ['director'], path: 'importacion-inicial', element: <DataImportListPage /> },
    { roles: ['director'], path: 'importacion-inicial/nueva', element: <DataImportNewPage /> },
    { roles: ['director'], path: 'importacion-inicial/:id', element: <DataImportDetailPage /> },
  ],
};
