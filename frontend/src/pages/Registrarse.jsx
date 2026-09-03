import { useEffect, useState } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { motion } from 'motion/react';
import { GlassCard } from '@/components/GlassCard';
import { MeshBackground } from '@/components/MeshBackground';
import { InstitutionBrand } from '@/components/InstitutionBrand';
import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';
import { PasswordInput } from '@/components/ui/password-input';
import { useAuth } from '@/context/AuthContext';
import { apiFetch } from '@/api/client';

const PASSWORD_MIN_LENGTH = 8;
// Mismo criterio que auth.password.require-letter-and-digit/require-uppercase del
// backend (RF-AUTH-05) — se valida temprano en el cliente para no hacerle esperar
// el viaje de red por un error que ya se puede saber acá, pero la fuente de
// verdad sigue siendo el backend (ver docs/context/frontend.md, manejo de errores).
const PASSWORD_TIENE_MAYUSCULA_Y_DIGITO = /(?=.*[A-Z])(?=.*\d)/;

// Mapeo 1:1 a Registrarse.dc.html. Sin verificación de email (decisión del
// usuario: se descartó por complejidad) y sin institutionId visible — el
// sistema es de una sola institución por despliegue, se resuelve solo.
export function Registrarse() {
  const [nombre, setNombre] = useState('');
  const [apellido, setApellido] = useState('');
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [repetirPassword, setRepetirPassword] = useState('');
  const [institutionId, setInstitutionId] = useState(null);
  const [errorInstitucion, setErrorInstitucion] = useState(false);
  const [error, setError] = useState(null);
  const [enviando, setEnviando] = useState(false);
  const { usuario, register } = useAuth();
  const navigate = useNavigate();

  useEffect(() => {
    if (usuario) {
      navigate(usuario.roles.length ? `/${usuario.roles[0].toLowerCase()}/inicio` : '/catalogo', { replace: true });
    }
  }, [usuario, navigate]);

  useEffect(() => {
    apiFetch('/institutions')
      .then((instituciones) => {
        if (instituciones?.length) {
          setInstitutionId(instituciones[0].id);
        } else {
          setErrorInstitucion(true);
        }
      })
      .catch(() => setErrorInstitucion(true));
  }, []);

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
      await register({ nombre, apellido, email, username: email, password, institutionId });
    } catch (err) {
      setError(
        err.message === 'La operación entra en conflicto con el estado actual.'
          ? 'Ese email ya está registrado.'
          : 'No pudimos crear tu cuenta. Revisá los datos e intentá de nuevo.',
      );
    } finally {
      setEnviando(false);
    }
  }

  return (
    <div className="flex min-h-screen items-center justify-center px-4 py-8">
      <MeshBackground />
      <GlassCard className="w-full max-w-[480px] p-6 pb-7 sm:p-10 sm:pb-8">
        <div className="mb-6">
          <InstitutionBrand titulo="Creá tu cuenta" subtitulo="Registrate para solicitar una carrera o anotarte a un curso" />
        </div>

        <form onSubmit={handleSubmit} className="flex flex-col gap-3.5">
          <div className="grid grid-cols-1 gap-3.5 sm:grid-cols-2">
            <div>
              <div className="mb-1.5 text-xs font-semibold text-ink-soft">Nombre</div>
              <Input
                autoFocus
                placeholder="Nombre"
                value={nombre}
                onChange={(e) => setNombre(e.target.value)}
                required
                disabled={enviando}
              />
            </div>
            <div>
              <div className="mb-1.5 text-xs font-semibold text-ink-soft">Apellido</div>
              <Input
                placeholder="Apellido"
                value={apellido}
                onChange={(e) => setApellido(e.target.value)}
                required
                disabled={enviando}
              />
            </div>
          </div>

          <div>
            <div className="mb-1.5 text-xs font-semibold text-ink-soft">Email</div>
            <Input
              type="email"
              placeholder="tu@email.com"
              value={email}
              onChange={(e) => setEmail(e.target.value)}
              required
              disabled={enviando}
            />
            <div className="mt-1 text-[11.5px] text-ink-faint">Este email va a ser tu usuario para ingresar.</div>
          </div>

          <div className="grid grid-cols-1 gap-3.5 sm:grid-cols-2">
            <div>
              <div className="mb-1.5 text-xs font-semibold text-ink-soft">Contraseña</div>
              <PasswordInput
                placeholder="Contraseña"
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
          </div>
          <div className="-mt-1.5 text-[11.5px] text-ink-faint">Mínimo {PASSWORD_MIN_LENGTH} caracteres, con una mayúscula y un número.</div>

          {errorInstitucion && (
            <div className="rounded-lg bg-danger-bg px-3 py-2 text-xs font-medium text-danger">
              No pudimos preparar el registro. Probá de nuevo más tarde o consultá en Secretaría.
            </div>
          )}
          {error && <div className="rounded-lg bg-danger-bg px-3 py-2 text-xs font-medium text-danger">{error}</div>}

          <motion.div whileHover={{ y: -2, scale: 1.01 }} whileTap={{ scale: 0.98 }}>
            <Button
              type="submit"
              disabled={enviando || !institutionId}
              className="w-full rounded-2xl bg-gradient-to-br from-brand to-accent-2 py-6 text-[14.5px] font-bold text-white"
            >
              {enviando ? 'Creando cuenta…' : 'Crear cuenta'}
            </Button>
          </motion.div>

          <div className="-mt-1 text-center text-[11.5px] text-ink-faint">
            Al crear tu cuenta entrás directo — todavía no hay verificación de email.
          </div>
        </form>

        <div className="mt-6 text-center text-xs text-ink-faint">
          ¿Ya tenés cuenta?{' '}
          <Link to="/login" className="font-semibold text-brand hover:text-accent-2">
            Ingresá
          </Link>
        </div>
      </GlassCard>
    </div>
  );
}
