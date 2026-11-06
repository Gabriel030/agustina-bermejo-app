import { lazyPage } from '@/routes/lazyPage';
const DirectorInstitutionSettings = lazyPage(() => import('@/features/institution/pages/DirectorInstitutionSettings'), 'DirectorInstitutionSettings');

export const routes = {
  public: [],
  shared: [],
  role: [{ roles: ['director'], path: 'ajustes-del-instituto', element: <DirectorInstitutionSettings /> }],
};
