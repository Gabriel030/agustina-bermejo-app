import { Login } from '@/features/identity/pages/Login';
import { Registrarse } from '@/features/identity/pages/Registrarse';
import { ForgotPassword } from '@/features/identity/pages/ForgotPassword';
import { VerificacionEmail } from '@/features/identity/pages/VerificacionEmail';
import { NoAutorizado } from '@/pages/NoAutorizado';
import { lazyPage } from '@/routes/lazyPage';
const MiPerfil = lazyPage(() => import('@/features/identity/pages/MiPerfil'), 'MiPerfil');
const LegajoAlumno = lazyPage(() => import('@/features/identity/pages/LegajoAlumno'), 'LegajoAlumno');
const NuevoAlumno = lazyPage(() => import('@/features/identity/pages/NuevoAlumno'), 'NuevoAlumno');
const GestionPersonal = lazyPage(() => import('@/features/identity/pages/GestionPersonal'), 'GestionPersonal');
const LegajoPersonal = lazyPage(() => import('@/features/identity/pages/LegajoPersonal'), 'LegajoPersonal');

export const routes = {
  public: [
    { path: '/login', element: <Login /> },
    { path: '/registrarse', element: <Registrarse /> },
    { path: '/olvide-mi-contrasena', element: <ForgotPassword /> },
    { path: '/verificar-email', element: <VerificacionEmail /> },
    { path: '/no-autorizado', element: <NoAutorizado /> },
  ],
  shared: [{ path: '/perfil', element: <MiPerfil /> }],
  role: [
    { roles: ['administrativo', 'director'], path: 'personal', element: <GestionPersonal /> },
    { roles: ['administrativo', 'director'], path: 'personal/:id', element: <LegajoPersonal /> },
    { roles: ['preceptor', 'administrativo', 'director'], path: 'alumnos/:id', element: <LegajoAlumno /> },
    { roles: ['preceptor', 'administrativo', 'director'], path: 'alumnos/nuevo', element: <NuevoAlumno /> },
  ],
};
