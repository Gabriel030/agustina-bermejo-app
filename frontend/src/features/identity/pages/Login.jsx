import { useEffect, useState } from 'react';
import { Link, useLocation, useNavigate } from 'react-router-dom';
import { motion } from 'motion/react';
import { GoogleLogin } from '@react-oauth/google';
import { GlassCard } from '@/components/GlassCard';
import { MeshBackground } from '@/components/MeshBackground';
import { InstitutionBrand } from '@/components/InstitutionBrand';
import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';
import { PasswordInput } from '@/components/ui/password-input';
import { useAuth } from '@/context/AuthContext';
import { apiFetch, RateLimitedError } from '@/api/client';

// Épica 1 Fase 2: el botón de Google ya no es decorativo (era un <div
// aria-disabled> con tooltip "Próximamente"). Cuenta no encontrada (404 de
// POST /auth/google) muestra una confirmación inline en vez de un error —
// pedido explícito del usuario, mismo patrón que Google/Microsoft/la mayoría
// de los SaaS ("no encontramos tu cuenta, ¿querés crearla?").
export function Login() {
  const [username, setUsername] = useState('');
  const [password, setPassword] = useState('');
  const [error, setError] = useState(null);
  const [enviando, setEnviando] = useState(false);
  const [googleIdTokenPendiente, setGoogleIdTokenPendiente] = useState(null);
  const [institutionId, setInstitutionId] = useState(null);
  const [registrandoConGoogle, setRegistrandoConGoogle] = useState(false);
  const { usuario, login, loginWithGoogle, registerWithGoogle } = useAuth();
  const navigate = useNavigate();
  const location = useLocation();

  useEffect(() => {
    apiFetch('/institutions')
      .then((instituciones) => {
        if (instituciones?.length) setInstitutionId(instituciones[0].id);
      })
      .catch(() => {});
  }, []);

  // Única fuente de verdad para la navegación post-login: cubre tanto "recién
  // logueado" como "ya logueado y entró a /login a mano". AppRouter no
  // duplica este redirect — dos navegaciones compitiendo por el mismo cambio
  // de `usuario` es una carrera real (una puede pisar el `from` de la otra).
  useEffect(() => {
    if (!usuario) return;
    // Un alumno recién registrado (local o vía Google) no tiene rol todavía —
    // hasta su primera aprobación (ver RegistrationService) — mismo fallback
    // que ya usa Registrarse.jsx, acá faltaba y armaba "/undefined/inicio".
    const from = location.state?.from;
    const destino = from?.pathname
      ? `${from.pathname}${from.search ?? ''}${from.hash ?? ''}`
      : usuario.roles.length ? `/${usuario.roles[0].toLowerCase()}/inicio` : '/catalogo';
    navigate(destino, { replace: true });
  }, [usuario, location.state, navigate]);

  async function handleSubmit(event) {
    event.preventDefault();
    setError(null);
    setEnviando(true);
    try {
      await login(username, password);
    } catch (err) {
      setError(
        err instanceof RateLimitedError
          ? 'Demasiados intentos, esperá un momento antes de reintentar.'
          : 'Usuario o contraseña incorrectos.',
      );
    } finally {
      setEnviando(false);
    }
  }

  async function handleGoogleSuccess(credentialResponse) {
    setError(null);
    setGoogleIdTokenPendiente(null);
    const idToken = credentialResponse.credential;
    try {
      await loginWithGoogle(idToken);
    } catch (err) {
      if (err.status === 404) {
        setGoogleIdTokenPendiente(idToken);
      } else if (err instanceof RateLimitedError) {
        setError('Demasiados intentos, esperá un momento antes de reintentar.');
      } else {
        setError('No pudimos iniciar sesión con Google. Probá de nuevo.');
      }
    }
  }

  async function handleRegistrarseConGoogle() {
    setError(null);
    setRegistrandoConGoogle(true);
    try {
      await registerWithGoogle(googleIdTokenPendiente, institutionId);
    } catch {
      setError('No pudimos crear tu cuenta. Probá de nuevo.');
    } finally {
      setRegistrandoConGoogle(false);
    }
  }

  return (
    <div className="flex min-h-screen items-center justify-center px-4">
      <MeshBackground />
      <GlassCard className="w-full max-w-[436px] p-10 pb-9">
        <div className="mb-7">
          <InstitutionBrand subtitulo="Ingresá a tu cuenta" />
        </div>

        <form onSubmit={handleSubmit} className="flex flex-col gap-3.5">
          <div>
            <div className="mb-1.5 text-xs font-semibold text-ink-soft">Usuario</div>
            <Input
              autoFocus
              value={username}
              onChange={(e) => setUsername(e.target.value)}
              placeholder="DNI o email"
              disabled={enviando}
            />
          </div>
          <div>
            <div className="mb-1.5 text-xs font-semibold text-ink-soft">Contraseña</div>
            <PasswordInput
              value={password}
              onChange={(e) => setPassword(e.target.value)}
              placeholder="••••••••"
              disabled={enviando}
            />
          </div>

          <div className="-mt-1 flex justify-end">
            <Link to="/olvide-mi-contrasena" className="text-xs font-semibold text-brand hover:text-accent-2">
              ¿Olvidaste tu contraseña?
            </Link>
          </div>

          {error && <div className="rounded-lg bg-danger-bg px-3 py-2 text-xs font-medium text-danger">{error}</div>}

          <motion.div whileHover={{ y: -2, scale: 1.01 }} whileTap={{ scale: 0.98 }}>
            <Button
              type="submit"
              variant="gradient"
              disabled={enviando}
              className="w-full rounded-2xl py-6 text-[14.5px] font-bold"
            >
              {enviando ? 'Ingresando…' : 'Ingresar'}
            </Button>
          </motion.div>

          <div className="my-2 flex items-center gap-3">
            <div className="h-px flex-1 bg-border" />
            <div className="text-xs font-medium text-ink-faint">o continuá con</div>
            <div className="h-px flex-1 bg-border" />
          </div>

          {googleIdTokenPendiente ? (
            <div className="flex flex-col gap-2.5 rounded-2xl border border-border bg-accent-soft/40 p-3.5 text-center">
              <div className="text-[13px] font-medium text-ink">
                No encontramos una cuenta con ese email. ¿Querés crear una cuenta nueva?
              </div>
              <div className="flex gap-2">
                <Button
                  type="button"
                  variant="outline"
                  className="flex-1"
                  disabled={registrandoConGoogle}
                  onClick={() => setGoogleIdTokenPendiente(null)}
                >
                  Cancelar
                </Button>
                <Button
                  type="button"
                  variant="gradient"
                  className="flex-1"
                  disabled={registrandoConGoogle || !institutionId}
                  onClick={handleRegistrarseConGoogle}
                >
                  {registrandoConGoogle ? 'Creando…' : 'Sí, registrarme'}
                </Button>
              </div>
            </div>
          ) : (
            <div className="flex justify-center">
              <GoogleLogin onSuccess={handleGoogleSuccess} onError={() => setError('No pudimos iniciar sesión con Google. Probá de nuevo.')} width="316" />
            </div>
          )}
        </form>

        <div className="mt-6 text-center text-xs text-ink-faint">
          ¿No tenés cuenta?{' '}
          <Link to="/registrarse" className="font-semibold text-brand hover:text-accent-2">
            Registrate
          </Link>
        </div>
        <div className="mt-2 text-center text-xs text-ink-faint">
          ¿Problemas para ingresar? Consultá en Secretaría.
        </div>
      </GlassCard>
    </div>
  );
}
