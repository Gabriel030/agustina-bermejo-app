import { createContext, useCallback, useContext, useEffect, useMemo, useState } from 'react';
import { apiFetch, setAccessToken, setSessionExpiredHandler } from '@/api/client';

const AuthContext = createContext(null);

export function AuthProvider({ children }) {
  const [usuario, setUsuario] = useState(null);
  const [cargando, setCargando] = useState(true);

  const limpiarSesion = useCallback(() => {
    setAccessToken(null);
    localStorage.removeItem('refreshToken');
    setUsuario(null);
  }, []);

  useEffect(() => {
    setSessionExpiredHandler(limpiarSesion);
  }, [limpiarSesion]);

  // Al montar: si hay refreshToken guardado, se intenta retomar la sesión antes de
  // mostrar cualquier pantalla protegida (ver decisión 4 de la spec).
  useEffect(() => {
    async function restaurarSesion() {
      const refreshToken = localStorage.getItem('refreshToken');
      if (!refreshToken) {
        setCargando(false);
        return;
      }
      try {
        const tokens = await apiFetch('/auth/refresh', {
          method: 'POST',
          body: JSON.stringify({ refreshToken }),
        });
        setAccessToken(tokens.accessToken);
        localStorage.setItem('refreshToken', tokens.refreshToken);
        const perfil = await apiFetch('/me');
        setUsuario(perfil);
      } catch {
        limpiarSesion();
      } finally {
        setCargando(false);
      }
    }
    restaurarSesion();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  const login = useCallback(async (username, password) => {
    const tokens = await apiFetch('/auth/login', {
      method: 'POST',
      body: JSON.stringify({ username, password }),
    });
    setAccessToken(tokens.accessToken);
    localStorage.setItem('refreshToken', tokens.refreshToken);
    const perfil = await apiFetch('/me');
    setUsuario(perfil);
    return perfil;
  }, []);

  // Mismo patrón que login(): guarda tokens y le pide el perfil a /me. A
  // diferencia de login, la persona queda sin roles hasta su primera
  // aprobación (ver ProgramEnrollmentApplicationService / CourseEnrollmentService).
  const register = useCallback(async ({ nombre, apellido, email, username, password, institutionId }) => {
    const tokens = await apiFetch('/auth/register', {
      method: 'POST',
      body: JSON.stringify({ nombre, apellido, email, username, password, institutionId }),
    });
    setAccessToken(tokens.accessToken);
    localStorage.setItem('refreshToken', tokens.refreshToken);
    const perfil = await apiFetch('/me');
    setUsuario(perfil);
    return perfil;
  }, []);

  // Re-hidrata `usuario` sin pasar por login — lo necesita Catálogo después de
  // una inscripción a curso, que puede otorgar el rol ALUMNO en el momento
  // (ver PersonRoleGranter) y el usuario no debería tener que desloguearse
  // para que el resto de la app vea su rol nuevo.
  const refrescarPerfil = useCallback(async () => {
    const perfil = await apiFetch('/me');
    setUsuario(perfil);
    return perfil;
  }, []);

  const logout = useCallback(async () => {
    const refreshToken = localStorage.getItem('refreshToken');
    limpiarSesion();
    if (refreshToken) {
      try {
        await apiFetch('/auth/logout', { method: 'POST', body: JSON.stringify({ refreshToken }) });
      } catch {
        // El logout local ya se aplicó; si la llamada al backend falla (red, token ya
        // vencido) no hay nada más que el usuario pueda hacer desde acá.
      }
    }
  }, [limpiarSesion]);

  const value = useMemo(
    () => ({ usuario, roles: usuario?.roles ?? [], login, register, refrescarPerfil, logout, cargando }),
    [usuario, login, register, refrescarPerfil, logout, cargando],
  );

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
}

export function useAuth() {
  const context = useContext(AuthContext);
  if (!context) throw new Error('useAuth debe usarse dentro de <AuthProvider>');
  return context;
}
