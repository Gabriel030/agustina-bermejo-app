import { Navigate } from 'react-router-dom';
import { Login } from '@/pages/Login';
import { Registrarse } from '@/pages/Registrarse';
import { ForgotPassword } from '@/pages/ForgotPassword';
import { VerificacionEmail } from '@/pages/VerificacionEmail';
import { NoAutorizado } from '@/pages/NoAutorizado';
import { MiPerfil } from '@/features/gestion-personas/pages/MiPerfil';
import { LegajoAlumno } from '@/features/gestion-personas/pages/LegajoAlumno';
import { NuevoAlumno } from '@/features/gestion-personas/pages/NuevoAlumno';
import { GestionPersonal } from '@/features/gestion-personas/pages/GestionPersonal';
import { LegajoPersonal } from '@/features/gestion-personas/pages/LegajoPersonal';

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
