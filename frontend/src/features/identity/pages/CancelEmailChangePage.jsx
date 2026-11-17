import { useEffect, useRef, useState } from 'react';
import { Link, useNavigate, useSearchParams } from 'react-router-dom';
import { m } from 'motion/react';
import { CheckCircle2, XCircle, WifiOff } from 'lucide-react';
import { GlassCard } from '@/components/GlassCard';
import { MeshBackground } from '@/components/MeshBackground';
import { InstitutionBrand } from '@/components/InstitutionBrand';
import { Button } from '@/components/ui/button';
import { apiFetch } from '@/api/client';
import { describeError } from '@/api/describeError';

export function CancelEmailChangePage() {
  const [searchParams] = useSearchParams();
  const navigate = useNavigate();
  const [token] = useState(() => searchParams.get('token'));
  const messageRef = useRef(null);
  const [status, setStatus] = useState('idle');
  const view = token ? status : 'invalid';

  useEffect(() => {
    navigate('/cancelar-cambio-email', { replace: true });
  }, [navigate]);

  useEffect(() => {
    if (view !== 'idle') {
      messageRef.current?.focus();
    }
  }, [view]);

  function cancelChange() {
    if (status === 'submitting') {
      return;
    }
    setStatus('submitting');
    apiFetch('/cancel-email-change', { method: 'POST', body: JSON.stringify({ token }) })
      .then(() => setStatus('done'))
      .catch((error) => setStatus(describeError(error).retryable ? 'network' : 'invalid'));
  }

  return (
    <div className="relative flex min-h-screen flex-col items-center justify-center p-4">
      <MeshBackground />
      <m.div
        initial={{ opacity: 0, y: 12 }}
        animate={{ opacity: 1, y: 0 }}
        transition={{ duration: 0.25 }}
        className="w-full max-w-sm"
      >
        <div className="mb-8 flex flex-col items-center gap-3">
          <InstitutionBrand />
        </div>

        <GlassCard
          ref={messageRef}
          tabIndex={-1}
          role={view === 'invalid' || view === 'network' ? 'alert' : 'status'}
          aria-live={view === 'invalid' || view === 'network' ? 'assertive' : 'polite'}
          className="p-8 flex flex-col items-center gap-4 text-center outline-none"
        >
          {(view === 'idle' || view === 'submitting') && (
            <>
              <div>
                <p className="font-heading text-lg font-bold text-ink">¿No pediste cambiar tu email?</p>
                <p className="mt-1 text-size-13 text-ink-soft">
                  Si no fuiste vos, cancelá el cambio y tu cuenta seguirá con el mismo correo.
                </p>
              </div>
              <Button variant="gradient" className="w-full" loading={view === 'submitting'} onClick={cancelChange}>
                Cancelar el cambio
              </Button>
            </>
          )}

          {view === 'done' && (
            <>
              <div className="flex h-16 w-16 items-center justify-center rounded-full bg-success-bg">
                <CheckCircle2 size={36} className="text-success" />
              </div>
              <div>
                <p className="font-heading text-lg font-bold text-ink">Cambio cancelado</p>
                <p className="mt-1 text-size-13 text-ink-soft">
                  Cancelamos el cambio de email; tu cuenta sigue con el mismo correo.
                </p>
              </div>
              <Link to="/login" className="text-size-13 font-semibold text-brand hover:text-accent-2">
                Ir a ingresar
              </Link>
            </>
          )}

          {view === 'invalid' && (
            <>
              <div className="flex h-16 w-16 items-center justify-center rounded-full bg-danger-bg">
                <XCircle size={36} className="text-danger" />
              </div>
              <div>
                <p className="font-heading text-lg font-bold text-ink">El enlace venció o no es válido</p>
                <p className="mt-1 text-size-13 text-ink-soft">
                  Si el cambio de email sigue sin ser tuyo, ingresá a Mi perfil y revisalo desde ahí.
                </p>
              </div>
              <Link to="/login" className="text-size-13 font-semibold text-brand hover:text-accent-2">
                Ir a ingresar
              </Link>
            </>
          )}

          {view === 'network' && (
            <>
              <div className="flex h-16 w-16 items-center justify-center rounded-full bg-danger-bg">
                <WifiOff size={36} className="text-danger" />
              </div>
              <div>
                <p className="font-heading text-lg font-bold text-ink">No pudimos conectarnos</p>
                <p className="mt-1 text-size-13 text-ink-soft">Revisá tu conexión e intentá de nuevo.</p>
              </div>
              <Button variant="outline" className="w-full" onClick={cancelChange}>
                Reintentar
              </Button>
            </>
          )}
        </GlassCard>
      </m.div>
    </div>
  );
}
