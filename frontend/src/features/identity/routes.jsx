import { Navigate } from 'react-router-dom';
import { Login } from '@/features/identity/pages/Login';
import { Registrarse } from '@/features/identity/pages/Registrarse';
import { ForgotPassword } from '@/features/identity/pages/ForgotPassword';
import { VerificacionEmail } from '@/features/identity/pages/VerificacionEmail';
import { NoAutorizado } from '@/pages/NoAutorizado';
import { MiPerfil } from '@/features/identity/pages/MiPerfil';
import { LegajoAlumno } from '@/features/identity/pages/LegajoAlumno';
import { NuevoAlumno } from '@/features/identity/pages/NuevoAlumno';
import { GestionPersonal } from '@/features/identity/pages/GestionPersonal';
import { LegajoPersonal } from '@/features/identity/pages/LegajoPersonal';

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
    { roles: ['preceptor', 'administrativo'], path: 'alumnos/nuevo', element: <NuevoAlumno /> },
    { roles: ['director'], path: 'alumnos/nuevo', element: <Navigate to="/no-autorizado" replace /> },
  ],
};
