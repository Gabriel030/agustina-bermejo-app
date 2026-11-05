import { AlumnoLayout } from '@/layouts/AlumnoLayout';
import { DocenteLayout } from '@/layouts/DocenteLayout';
import { PreceptorLayout } from '@/layouts/PreceptorLayout';
import { AdministrativoLayout } from '@/layouts/AdministrativoLayout';
import { DirectorLayout } from '@/layouts/DirectorLayout';
import { PlatformLayout } from '@/layouts/PlatformLayout';

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
