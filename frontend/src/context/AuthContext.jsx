import { createContext, useCallback, useContext, useEffect, useMemo, useState } from 'react';
import { QueryClientContext } from '@tanstack/react-query';
import { apiFetch, setAccessToken, setSessionExpiredHandler } from '@/api/client';
import { claimOfflineCache, purgeOfflineData } from '@/api/offlinePersistence';

const AuthContext = createContext(null);

const RESTORE_RETRY_DELAYS_MS = [2000, 5000, 15000, 30000, 60000];

function isSessionRejected(error) {
  return error?.status === 401 || error?.status === 403 || error?.name === 'AuthError';
}

let storedSessionRestoration = null;

export function restoreStoredSession() {
  if (!storedSessionRestoration) {
    storedSessionRestoration = loadStoredSession().finally(() => {
      storedSessionRestoration = null;
    });
  }
  return storedSessionRestoration;
}

async function loadStoredSession() {
  const refreshToken = localStorage.getItem('refreshToken');
  if (!refreshToken) return null;
  const tokens = await apiFetch('/auth/refresh', {
    method: 'POST',
    body: JSON.stringify({ refreshToken }),
  });
  setAccessToken(tokens.accessToken);
  localStorage.setItem('refreshToken', tokens.refreshToken);
  return apiFetch('/me');
}

export function AuthProvider({ children }) {
  const [usuario, setUsuario] = useState(null);
  const [cargando, setCargando] = useState(true);
  const [loggedOut, setLoggedOut] = useState(false);
  const [sessionPending, setSessionPending] = useState(false);
  const [restoreAttempts, setRestoreAttempts] = useState(0);
  const queryClient = useContext(QueryClientContext);

  const limpiarSesion = useCallback(() => {
    setAccessToken(null);
    localStorage.removeItem('refreshToken');
    setUsuario(null);
    purgeOfflineData(queryClient);
  }, [queryClient]);

  const beginSession = useCallback(
    async (tokens) => {
      await purgeOfflineData(queryClient);
      setAccessToken(tokens.accessToken);
      localStorage.setItem('refreshToken', tokens.refreshToken);
      const perfil = await apiFetch('/me');
      await claimOfflineCache(perfil.id, queryClient);
      setUsuario(perfil);
      setLoggedOut(false);
      return perfil;
    },
    [queryClient],
  );

  useEffect(() => {
    setSessionExpiredHandler(limpiarSesion);
  }, [limpiarSesion]);

  const attemptRestore = useCallback(async () => {
    try {
      const perfil = await restoreStoredSession();
      if (perfil) {
        await claimOfflineCache(perfil.id, queryClient);
        setUsuario(perfil);
      }
      setSessionPending(false);
      setRestoreAttempts(0);
    } catch (error) {
      if (isSessionRejected(error)) {
        setSessionPending(false);
        limpiarSesion();
      } else {
        setSessionPending(true);
        setRestoreAttempts((attempts) => attempts + 1);
      }
    } finally {
      setCargando(false);
    }
  }, [queryClient, limpiarSesion]);

  useEffect(() => {
    if (!localStorage.getItem('refreshToken')) {
      setCargando(false);
      return;
    }
    attemptRestore();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  useEffect(() => {
    if (!sessionPending || restoreAttempts === 0 || restoreAttempts > RESTORE_RETRY_DELAYS_MS.length) return undefined;
    const timer = setTimeout(attemptRestore, RESTORE_RETRY_DELAYS_MS[restoreAttempts - 1]);
    return () => clearTimeout(timer);
  }, [sessionPending, restoreAttempts, attemptRestore]);

  useEffect(() => {
    if (!sessionPending) return undefined;
    const retryWhenVisible = () => {
      if (document.visibilityState === 'visible') attemptRestore();
    };
    window.addEventListener('online', attemptRestore);
    document.addEventListener('visibilitychange', retryWhenVisible);
    return () => {
      window.removeEventListener('online', attemptRestore);
      document.removeEventListener('visibilitychange', retryWhenVisible);
    };
  }, [sessionPending, attemptRestore]);

  const login = useCallback(async (username, password) => {
    const tokens = await apiFetch('/auth/login', {
      method: 'POST',
      body: JSON.stringify({ username, password }),
    });
    return beginSession(tokens);
  }, [beginSession]);

  // Mismo patrón que login(): guarda tokens y le pide el perfil a /me. A
  // diferencia de login, la persona queda sin roles hasta su primera
  // aprobación (ver ProgramEnrollmentApplicationService / CourseEnrollmentService).
  const register = useCallback(async ({ nombre, apellido, email, username, password, institutionId }) => {
    const tokens = await apiFetch('/auth/register', {
      method: 'POST',
      body: JSON.stringify({ nombre, apellido, email, username, password, institutionId }),
    });
    return beginSession(tokens);
  }, [beginSession]);

  // Épica 1 Fase 2: mismo patrón que login()/register() — la diferencia entre las dos es solo
  // qué endpoint golpean, el manejo de tokens/perfil es idéntico.
  const loginWithGoogle = useCallback(async (idToken) => {
    const tokens = await apiFetch('/auth/google', {
      method: 'POST',
      body: JSON.stringify({ idToken }),
    });
    return beginSession(tokens);
  }, [beginSession]);

  const registerWithGoogle = useCallback(async (idToken, institutionId) => {
    const tokens = await apiFetch('/auth/google/register', {
      method: 'POST',
      body: JSON.stringify({ idToken, institutionId }),
    });
    return beginSession(tokens);
  }, [beginSession]);

  // Re-hidrata `usuario` sin pasar por login — lo necesita Catálogo después de
  // una inscripción a curso, que puede otorgar el rol ALUMNO en el momento
  // (ver PersonRoleGranter) y el usuario no debería tener que desloguearse
  // para que el resto de la app vea su rol nuevo.
  const refrescarPerfil = useCallback(async () => {
    const perfil = await apiFetch('/me');
    setUsuario(perfil);
    setLoggedOut(false);
    return perfil;
  }, []);

  const logout = useCallback(async () => {
    const refreshToken = localStorage.getItem('refreshToken');
    setLoggedOut(true);
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
    () => ({ usuario, roles: usuario?.roles ?? [], login, register, loginWithGoogle, registerWithGoogle, refrescarPerfil, logout, cargando, loggedOut, sessionPending, retrySession: attemptRestore }),
    [usuario, login, register, loginWithGoogle, registerWithGoogle, refrescarPerfil, logout, cargando, loggedOut, sessionPending, attemptRestore],
  );

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
}

export function useAuth() {
  const context = useContext(AuthContext);
  if (!context) throw new Error('useAuth debe usarse dentro de <AuthProvider>');
  return context;
}
