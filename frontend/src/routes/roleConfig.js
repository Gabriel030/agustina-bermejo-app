import { lazyPage } from '@/routes/lazyPage';
const AlumnoLayout = lazyPage(() => import('@/layouts/AlumnoLayout'), 'AlumnoLayout');
const DocenteLayout = lazyPage(() => import('@/layouts/DocenteLayout'), 'DocenteLayout');
const PreceptorLayout = lazyPage(() => import('@/layouts/PreceptorLayout'), 'PreceptorLayout');
const AdministrativoLayout = lazyPage(() => import('@/layouts/AdministrativoLayout'), 'AdministrativoLayout');
const DirectorLayout = lazyPage(() => import('@/layouts/DirectorLayout'), 'DirectorLayout');
const PlatformLayout = lazyPage(() => import('@/layouts/PlatformLayout'), 'PlatformLayout');

import { pagesFor } from '@/routes/roleMenu';

export const ROLE_CONFIG = {
  alumno: {
    Layout: AlumnoLayout,
    pages: pagesFor('alumno'),
  },
  docente: {
    Layout: DocenteLayout,
    pages: pagesFor('docente'),
  },
  preceptor: {
    Layout: PreceptorLayout,
    pages: pagesFor('preceptor'),
  },
  administrativo: {
    Layout: AdministrativoLayout,
    pages: pagesFor('administrativo'),
  },
  director: {
    Layout: DirectorLayout,
    pages: pagesFor('director'),
  },
  platform_admin: {
    Layout: PlatformLayout,
    pages: pagesFor('platform_admin'),
  },
};
