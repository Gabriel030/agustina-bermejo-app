import { useEffect, useState } from 'react';
import { postLoginPathFor } from '@/lib/activeRole';
import { Link, useNavigate } from 'react-router-dom';
import { m } from 'motion/react';
import { GlassCard } from '@/components/GlassCard';
import { MeshBackground } from '@/components/MeshBackground';
import { InstitutionBrand } from '@/components/InstitutionBrand';
import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';
import { PasswordInput } from '@/components/ui/password-input';
import { Checkbox } from '@/components/ui/checkbox';
import { useAuth } from '@/context/AuthContext';
import { apiFetch } from '@/api/client';
import { describeError } from '@/api/describeError';
import { messageForAuthError } from '@/features/identity/lib/loginErrors';
import { useCurrentLegalDocuments } from '@/features/identity/hooks/useLegal';
import { isMinorOn, LEGAL_DOCUMENT_LABEL, LEGAL_DOCUMENT_PATH, formatLegalDate } from '@/features/identity/lib/legalDocuments';

const LEGAL_ERROR_MESSAGE = 'Tenés que aceptar los términos y la política de privacidad.';
const LEGAL_CODES = ['LEGAL_ACCEPTANCE_REQUIRED', 'BIRTH_DATE_REQUIRED', 'MINOR_GUARDIAN_REQUIRED'];

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
  const [birthDate, setBirthDate] = useState('');
  const [guardianName, setGuardianName] = useState('');
  const [guardianEmail, setGuardianEmail] = useState('');
  const [guardianRelationship, setGuardianRelationship] = useState('');
  const [acceptedLegal, setAcceptedLegal] = useState(false);
  const [acceptedNewsletter, setAcceptedNewsletter] = useState(false);
  const [legalError, setLegalError] = useState(null);
  const [institutionId, setInstitutionId] = useState(null);
  const [errorInstitucion, setErrorInstitucion] = useState(false);
  const [error, setError] = useState(null);
  const [enviando, setEnviando] = useState(false);
  const { usuario, register } = useAuth();
  const navigate = useNavigate();
  const { data: legalDocuments } = useCurrentLegalDocuments();
  const isMinor = isMinorOn(birthDate);
  const termsVersion = legalDocuments?.documents?.find((document) => document.type === 'TERMS')?.version;

  useEffect(() => {
    if (usuario) {
      navigate(postLoginPathFor(usuario), { replace: true });
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
    setLegalError(null);

    const errorValidacion = validar();
    if (errorValidacion) {
      setError(errorValidacion);
      return;
    }

    if (!acceptedLegal) {
      setLegalError(LEGAL_ERROR_MESSAGE);
      document.getElementById('accepted-legal')?.focus();
      return;
    }

    setEnviando(true);
    try {
      await register({
        nombre,
        apellido,
        email,
        username: email,
        password,
        institutionId,
        birthDate,
        acceptedTerms: acceptedLegal,
        acceptedPrivacy: acceptedLegal,
        acceptedNewsletter,
        ...(isMinor && { guardianName, guardianEmail, guardianRelationship }),
      });
    } catch (err) {
      if (LEGAL_CODES.includes(err.code)) {
        setError(describeError(err).message);
        return;
      }
      setError(
        err.code === 'EMAIL_ALREADY_EXISTS'
          ? 'Ese email ya está registrado.'
          : messageForAuthError(err, { fallback: 'No pudimos crear tu cuenta. Revisá los datos e intentá de nuevo.' }),
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
            <div className="mt-1 text-size-11-5 text-ink-faint">Este email va a ser tu usuario para ingresar.</div>
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
          <div className="-mt-1.5 text-size-11-5 text-ink-faint">Mínimo {PASSWORD_MIN_LENGTH} caracteres, con una mayúscula y un número.</div>

          <div>
            <label htmlFor="birth-date" className="mb-1.5 block text-xs font-semibold text-ink-soft">
              Fecha de nacimiento
            </label>
            <Input
              id="birth-date"
              type="date"
              value={birthDate}
              onChange={(e) => setBirthDate(e.target.value)}
              max={new Date().toISOString().slice(0, 10)}
              required
              disabled={enviando}
            />
          </div>

          {isMinor && (
            <fieldset className="flex flex-col gap-3.5 rounded-lg border border-border p-3">
              <legend className="px-1.5 text-size-13 font-bold text-ink">Responsable (obligatorio para menores de 18)</legend>
              <div>
                <label htmlFor="guardian-name" className="mb-1.5 block text-xs font-semibold text-ink-soft">
                  Nombre del responsable
                </label>
                <Input
                  id="guardian-name"
                  value={guardianName}
                  maxLength={255}
                  onChange={(e) => setGuardianName(e.target.value)}
                  required
                  disabled={enviando}
                />
              </div>
              <div>
                <label htmlFor="guardian-email" className="mb-1.5 block text-xs font-semibold text-ink-soft">
                  Correo del responsable
                </label>
                <Input
                  id="guardian-email"
                  type="email"
                  value={guardianEmail}
                  maxLength={255}
                  onChange={(e) => setGuardianEmail(e.target.value)}
                  required
                  disabled={enviando}
                />
              </div>
              <div>
                <label htmlFor="guardian-relationship" className="mb-1.5 block text-xs font-semibold text-ink-soft">
                  Vínculo con el responsable
                </label>
                <Input
                  id="guardian-relationship"
                  placeholder="Madre, padre, tutor…"
                  value={guardianRelationship}
                  maxLength={60}
                  onChange={(e) => setGuardianRelationship(e.target.value)}
                  required
                  disabled={enviando}
                />
              </div>
              <p className="text-size-11-5 text-ink-faint">
                La aceptación de los documentos la confirma tu responsable desde el correo que le enviamos.
              </p>
            </fieldset>
          )}

          <div className={`rounded-lg border p-3 ${legalError ? 'border-danger bg-danger-bg' : 'border-border'}`}>
            <div className="flex items-start gap-3">
              <Checkbox
                id="accepted-legal"
                checked={acceptedLegal}
                onCheckedChange={(checked) => {
                  setAcceptedLegal(checked === true);
                  if (checked === true) setLegalError(null);
                }}
                aria-invalid={Boolean(legalError)}
                aria-describedby="accepted-legal-links accepted-legal-error"
                disabled={enviando}
                className="mt-0.5"
              />
              <label htmlFor="accepted-legal" className="text-size-13 font-semibold text-ink">
                Acepto los términos y condiciones y la política de privacidad.
              </label>
            </div>
            <div id="accepted-legal-links" className="mt-1.5 pl-7 text-size-12 text-ink-soft">
              {['TERMS', 'PRIVACY'].map((type, index) => (
                <span key={type}>
                  {index > 0 && ' · '}
                  <a
                    href={LEGAL_DOCUMENT_PATH[type]}
                    target="_blank"
                    rel="noopener noreferrer"
                    className="font-semibold text-brand hover:text-accent-2"
                  >
                    {LEGAL_DOCUMENT_LABEL[type]}
                  </a>
                </span>
              ))}{' '}
              (se abren en una pestaña nueva; tu formulario se conserva)
              {termsVersion && <span className="block">Versión {formatLegalDate(termsVersion) || termsVersion}</span>}
            </div>
            <p id="accepted-legal-error" role="alert" className="mt-1.5 pl-7 text-size-12 font-medium text-danger empty:hidden">
              {legalError}
            </p>
          </div>

          <div className="flex items-start gap-3 rounded-lg border border-border p-3">
            <Checkbox
              id="accepted-newsletter"
              checked={acceptedNewsletter}
              onCheckedChange={(checked) => setAcceptedNewsletter(checked === true)}
              disabled={enviando}
              className="mt-0.5"
            />
            <label htmlFor="accepted-newsletter" className="text-size-13 text-ink">
              Quiero recibir novedades del instituto por correo (opcional).
            </label>
          </div>

          {errorInstitucion && (
            <div className="rounded-lg bg-danger-bg px-3 py-2 text-xs font-medium text-danger">
              No pudimos preparar el registro. Probá de nuevo más tarde o consultá en Secretaría.
            </div>
          )}
          {error && <div className="rounded-lg bg-danger-bg px-3 py-2 text-xs font-medium text-danger">{error}</div>}

          <m.div whileHover={{ y: -2, scale: 1.01 }} whileTap={{ scale: 0.98 }}>
            <Button
              type="submit"
              disabled={enviando || !institutionId}
              variant="gradient"
              className="w-full rounded-2xl py-6 text-size-14-5 font-bold"
            >
              {enviando ? 'Creando cuenta…' : 'Crear cuenta'}
            </Button>
          </m.div>

          <div className="-mt-1 text-center text-size-11-5 text-ink-faint">
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
