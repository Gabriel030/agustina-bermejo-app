import { useEffect, useState } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { motion } from 'motion/react';
import { GlassCard } from '@/components/GlassCard';
import { MeshBackground } from '@/components/MeshBackground';
import { InstitutionBrand } from '@/components/InstitutionBrand';
import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';
import { PasswordInput } from '@/components/ui/password-input';
import { apiFetch, RateLimitedError } from '@/api/client';

const PASSWORD_MIN_LENGTH = 8;
const PASSWORD_TIENE_MAYUSCULA_Y_DIGITO = /(?=.*[A-Z])(?=.*\d)/;
const SEGUNDOS_PARA_REENVIAR = 60;

// Un solo flujo, sin cambiar de pantalla entre pedir el email y poner el código —
// pedido explícito del usuario ("no se por q esta en dos pantallas, tiene que ser
// mas corto el flujo"): al mandar el email, la MISMA pantalla pasa a mostrar el
// mensaje de éxito + el campo de código + el cronómetro de reenvío, sin navegar a
// ningún lado. Recién al verificar el código correctamente se pasa a la pantalla
// de elegir la contraseña nueva (eso sí es un paso distinto, confirmado por el
// usuario: "si pongo el código... ahí voy a la pantalla para poner las 2
// contraseñas").
export function ForgotPassword() {
  const [email, setEmail] = useState('');
  const [codigoPedido, setCodigoPedido] = useState(false);
  const [code, setCode] = useState('');
  const [resetToken, setResetToken] = useState(null);
  const [segundosParaReenviar, setSegundosParaReenviar] = useState(SEGUNDOS_PARA_REENVIAR);
  const [error, setError] = useState(null);
  const [enviando, setEnviando] = useState(false);

  useEffect(() => {
    if (!codigoPedido || segundosParaReenviar <= 0) return;
    const id = setInterval(() => setSegundosParaReenviar((s) => s - 1), 1000);
    return () => clearInterval(id);
  }, [codigoPedido, segundosParaReenviar]);

  async function handlePedirCodigo(event) {
    event.preventDefault();
    setError(null);
    setEnviando(true);
    try {
      await apiFetch('/auth/forgot-password', { method: 'POST', body: JSON.stringify({ email }) });
      setCodigoPedido(true);
    } catch (err) {
      setError(err instanceof RateLimitedError ? 'Demasiados intentos, esperá un momento antes de reintentar.' : 'Algo salió mal. Probá de nuevo.');
    } finally {
      setEnviando(false);
    }
  }

  async function handleVerificarCodigo(event) {
    event.preventDefault();
    setError(null);

    if (!/^\d{6}$/.test(code)) {
      setError('El código tiene que tener 6 dígitos.');
      return;
    }

    setEnviando(true);
    try {
      const { resetToken: token } = await apiFetch('/auth/verify-reset-code', {
        method: 'POST',
        body: JSON.stringify({ email, code }),
      });
      setResetToken(token);
    } catch (err) {
      setError(
        err instanceof RateLimitedError
          ? 'Demasiados intentos, esperá un momento antes de reintentar.'
          : 'Código incorrecto o vencido. Pedí uno nuevo si hace falta.',
      );
    } finally {
      setEnviando(false);
    }
  }

  async function handleReenviarCodigo() {
    setError(null);
    setEnviando(true);
    try {
      await apiFetch('/auth/forgot-password', { method: 'POST', body: JSON.stringify({ email }) });
      setSegundosParaReenviar(SEGUNDOS_PARA_REENVIAR);
      setCode('');
    } catch {
      setError('No pudimos reenviar el código. Probá de nuevo en un momento.');
    } finally {
      setEnviando(false);
    }
  }

  const titulo = resetToken ? 'Elegí tu nueva contraseña' : 'Recuperar contraseña';
  const subtitulo = resetToken ? undefined : codigoPedido ? `Te mandamos un código a ${email}` : 'Te vamos a mandar un código para elegir una nueva';

  return (
    <div className="flex min-h-screen items-center justify-center px-4 py-8">
      <MeshBackground />
      <GlassCard className="w-full max-w-[436px] p-6 pb-7 sm:p-10 sm:pb-9">
        <div className="mb-7">
          <InstitutionBrand titulo={titulo} subtitulo={subtitulo} />
        </div>

        {resetToken ? (
          <NuevaPasswordForm resetToken={resetToken} />
        ) : !codigoPedido ? (
          <form onSubmit={handlePedirCodigo} className="flex flex-col gap-3.5">
            <div>
              <div className="mb-1.5 text-xs font-semibold text-ink-soft">Email</div>
              <Input
                autoFocus
                type="email"
                placeholder="tu@email.com"
                value={email}
                onChange={(e) => setEmail(e.target.value)}
                required
                disabled={enviando}
              />
            </div>

            {error && <div className="rounded-lg bg-danger-bg px-3 py-2 text-xs font-medium text-danger">{error}</div>}

            <motion.div whileHover={{ y: -2, scale: 1.01 }} whileTap={{ scale: 0.98 }}>
              <Button
                type="submit"
                disabled={enviando}
                variant="gradient"
                className="w-full rounded-2xl py-6 text-[14.5px] font-bold"
              >
                {enviando ? 'Enviando…' : 'Enviar código de recuperación'}
              </Button>
            </motion.div>

            <div className="mt-2 text-center text-xs text-ink-faint">
              <Link to="/login" className="font-semibold text-brand hover:text-accent-2">
                ← Volver a ingresar
              </Link>
            </div>
          </form>
        ) : (
          <form onSubmit={handleVerificarCodigo} className="flex flex-col gap-3.5">
            <div className="rounded-lg bg-success-bg px-3 py-2 text-xs font-medium text-success">
              Si existe una cuenta con ese email, te mandamos un código.
            </div>

            <div>
              <div className="mb-1.5 text-xs font-semibold text-ink-soft">Código</div>
              <Input
                autoFocus
                inputMode="numeric"
                maxLength={6}
                placeholder="123456"
                value={code}
                onChange={(e) => setCode(e.target.value.replace(/\D/g, '').slice(0, 6))}
                required
                disabled={enviando}
              />
            </div>

            {error && <div className="rounded-lg bg-danger-bg px-3 py-2 text-xs font-medium text-danger">{error}</div>}

            <motion.div whileHover={{ y: -2, scale: 1.01 }} whileTap={{ scale: 0.98 }}>
              <Button
                type="submit"
                disabled={enviando}
                variant="gradient"
                className="w-full rounded-2xl py-6 text-[14.5px] font-bold"
              >
                {enviando ? 'Verificando…' : 'Continuar'}
              </Button>
            </motion.div>

            <button
              type="button"
              onClick={handleReenviarCodigo}
              disabled={segundosParaReenviar > 0 || enviando}
              className="mt-1 text-center text-xs font-semibold text-brand hover:text-accent-2 disabled:cursor-not-allowed disabled:text-ink-faint disabled:hover:text-ink-faint"
            >
              {segundosParaReenviar > 0 ? `Reenviar código (${segundosParaReenviar}s)` : 'Reenviar código'}
            </button>

            <div className="mt-1 text-center text-xs text-ink-faint">
              <Link to="/login" className="font-semibold text-brand hover:text-accent-2">
                ← Volver a ingresar
              </Link>
            </div>
          </form>
        )}
      </GlassCard>
    </div>
  );
}

function NuevaPasswordForm({ resetToken }) {
  const navigate = useNavigate();
  const [password, setPassword] = useState('');
  const [repetirPassword, setRepetirPassword] = useState('');
  const [error, setError] = useState(null);
  const [enviando, setEnviando] = useState(false);
  const [listo, setListo] = useState(false);

  function validar() {
    if (password !== repetirPassword) return 'Las contraseñas no coinciden.';
    if (password.length < PASSWORD_MIN_LENGTH) return `La contraseña debe tener al menos ${PASSWORD_MIN_LENGTH} caracteres.`;
    if (!PASSWORD_TIENE_MAYUSCULA_Y_DIGITO.test(password)) return 'La contraseña debe tener al menos una mayúscula y un número.';
    return null;
  }

  async function handleSubmit(event) {
    event.preventDefault();
    setError(null);

    const errorValidacion = validar();
    if (errorValidacion) {
      setError(errorValidacion);
      return;
    }

    setEnviando(true);
    try {
      await apiFetch('/auth/reset-password', { method: 'POST', body: JSON.stringify({ resetToken, newPassword: password }) });
      setListo(true);
      setTimeout(() => navigate('/login', { replace: true }), 2000);
    } catch {
      setError('El código venció mientras elegías la contraseña. Pedí uno nuevo.');
    } finally {
      setEnviando(false);
    }
  }

  if (listo) {
    return (
      <div className="flex flex-col gap-4 text-center">
        <div className="rounded-lg bg-success-bg px-3 py-3 text-sm font-medium text-success">
          Contraseña actualizada. Te llevamos a Login…
        </div>
      </div>
    );
  }

  return (
    <form onSubmit={handleSubmit} className="flex flex-col gap-3.5">
      <div>
        <div className="mb-1.5 text-xs font-semibold text-ink-soft">Nueva contraseña</div>
        <PasswordInput
          autoFocus
          placeholder="Nueva contraseña"
          value={password}
          onChange={(e) => setPassword(e.target.value)}
          required
          disabled={enviando}
        />
      </div>
      <div>
        <div className="mb-1.5 text-xs font-semibold text-ink-soft">Repetir contraseña</div>
        <PasswordInput
          placeholder="Repetir contraseña"
          value={repetirPassword}
          onChange={(e) => setRepetirPassword(e.target.value)}
          required
          disabled={enviando}
        />
      </div>
      <div className="-mt-1.5 text-[11.5px] text-ink-faint">Mínimo {PASSWORD_MIN_LENGTH} caracteres, con una mayúscula y un número.</div>

      {error && <div className="rounded-lg bg-danger-bg px-3 py-2 text-xs font-medium text-danger">{error}</div>}

      <motion.div whileHover={{ y: -2, scale: 1.01 }} whileTap={{ scale: 0.98 }}>
        <Button
          type="submit"
          disabled={enviando}
          className="w-full rounded-2xl bg-gradient-to-br from-brand to-accent-2 py-6 text-[14.5px] font-bold text-white"
        >
          {enviando ? 'Guardando…' : 'Cambiar contraseña'}
        </Button>
      </motion.div>
    </form>
  );
}
