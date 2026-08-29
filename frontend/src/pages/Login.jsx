import { useEffect, useState } from 'react';
import { useLocation, useNavigate } from 'react-router-dom';
import { motion } from 'motion/react';
import { GlassCard } from '@/components/GlassCard';
import { MeshBackground } from '@/components/MeshBackground';
import { InstitutionBrand } from '@/components/InstitutionBrand';
import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';
import { PasswordInput } from '@/components/ui/password-input';
import { useAuth } from '@/context/AuthContext';
import { RateLimitedError } from '@/api/client';

// Mapeo 1:1 a Login.dc.html — el botón de Google queda deshabilitado con
// tooltip "Próximamente" (decisión 7 de la spec: el backend hoy solo
// soporta AuthProvider.LOCAL, Google OAuth es la Fase 2 de esta épica).
export function Login() {
  const [username, setUsername] = useState('');
  const [password, setPassword] = useState('');
  const [error, setError] = useState(null);
  const [enviando, setEnviando] = useState(false);
  const { usuario, login } = useAuth();
  const navigate = useNavigate();
  const location = useLocation();

  // Única fuente de verdad para la navegación post-login: cubre tanto "recién
  // logueado" como "ya logueado y entró a /login a mano". AppRouter no
  // duplica este redirect — dos navegaciones compitiendo por el mismo cambio
  // de `usuario` es una carrera real (una puede pisar el `from` de la otra).
  useEffect(() => {
    if (!usuario) return;
    const destino = location.state?.from?.pathname ?? `/${usuario.roles[0]?.toLowerCase()}/inicio`;
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

  return (
    <div className="flex min-h-screen items-center justify-center">
      <MeshBackground />
      <GlassCard className="w-[436px] p-10 pb-9">
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
            <a href="/olvide-mi-contrasena" className="text-xs font-semibold text-accent hover:text-accent-2">
              ¿Olvidaste tu contraseña?
            </a>
          </div>

          {error && <div className="rounded-lg bg-danger-bg px-3 py-2 text-xs font-medium text-danger">{error}</div>}

          <motion.div whileHover={{ y: -2, scale: 1.01 }} whileTap={{ scale: 0.98 }}>
            <Button
              type="submit"
              disabled={enviando}
              className="w-full rounded-2xl bg-gradient-to-br from-accent to-accent-2 py-6 text-[14.5px] font-bold text-white"
            >
              {enviando ? 'Ingresando…' : 'Ingresar'}
            </Button>
          </motion.div>

          <div className="my-2 flex items-center gap-3">
            <div className="h-px flex-1 bg-border" />
            <div className="text-xs font-medium text-ink-faint">o continuá con</div>
            <div className="h-px flex-1 bg-border" />
          </div>

          <div
            title="Próximamente"
            aria-disabled="true"
            className="flex cursor-not-allowed items-center justify-center gap-2.5 rounded-2xl border border-border bg-surface/60 py-3 text-sm font-semibold text-ink-faint opacity-70"
          >
            <GoogleIcon />
            Continuar con Google
          </div>
        </form>

        <div className="mt-6 text-center text-xs text-ink-faint">
          ¿No tenés cuenta?{' '}
          <a href="/registrarse" className="font-semibold text-accent hover:text-accent-2">
            Registrate
          </a>
        </div>
        <div className="mt-2 text-center text-xs text-ink-faint">
          ¿Problemas para ingresar? Consultá en Secretaría.
        </div>
      </GlassCard>
    </div>
  );
}

function GoogleIcon() {
  return (
    <svg width="18" height="18" viewBox="0 0 48 48" aria-hidden="true">
      <path fill="#FFC107" d="M43.6 20.5H42V20H24v8h11.3c-1.6 4.7-6.1 8-11.3 8-6.6 0-12-5.4-12-12s5.4-12 12-12c3.1 0 5.9 1.2 8 3.1l5.7-5.7C34.6 6.1 29.6 4 24 4 12.9 4 4 12.9 4 24s8.9 20 20 20 20-8.9 20-20c0-1.3-.1-2.7-.4-3.5z" />
      <path fill="#FF3D00" d="M6.3 14.7l6.6 4.8C14.5 15.1 18.9 12 24 12c3.1 0 5.9 1.2 8 3.1l5.7-5.7C34.6 6.1 29.6 4 24 4 16.3 4 9.7 8.3 6.3 14.7z" />
      <path fill="#4CAF50" d="M24 44c5.5 0 10.5-2.1 14.3-5.6l-6.6-5.6C29.6 34.7 26.9 36 24 36c-5.2 0-9.6-3.3-11.3-7.9l-6.5 5C9.6 39.6 16.3 44 24 44z" />
      <path fill="#1976D2" d="M43.6 20.5H42V20H24v8h11.3c-.8 2.3-2.2 4.3-4.1 5.8l6.6 5.6C39.9 37.1 44 31 44 24c0-1.3-.1-2.7-.4-3.5z" />
    </svg>
  );
}
