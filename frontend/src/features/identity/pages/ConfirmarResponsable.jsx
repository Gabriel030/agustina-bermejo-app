import { useEffect, useRef } from 'react';
import { Link, useSearchParams } from 'react-router-dom';
import { CheckCircle2, Loader2, XCircle } from 'lucide-react';
import { GlassCard } from '@/components/GlassCard';
import { MeshBackground } from '@/components/MeshBackground';
import { InstitutionBrand } from '@/components/InstitutionBrand';
import { describeError } from '@/api/describeError';
import { useConfirmGuardian } from '@/features/identity/hooks/useLegal';

export function ConfirmarResponsable() {
  const [searchParams] = useSearchParams();
  const token = searchParams.get('token');
  const confirm = useConfirmGuardian();
  const requestedTokenRef = useRef(null);

  useEffect(() => {
    if (!token || requestedTokenRef.current === token) return;
    requestedTokenRef.current = token;
    confirm.mutate(token);
  }, [token, confirm]);

  const state = !token || confirm.isError ? 'error' : confirm.isSuccess ? 'ok' : 'loading';
  const errorMessage = token
    ? describeError(confirm.error).message
    : 'El enlace está incompleto. Abrilo desde el correo que recibiste.';

  return (
    <div className="relative flex min-h-screen flex-col items-center justify-center p-4">
      <MeshBackground />
      <div className="w-full max-w-sm">
        <div className="mb-8 flex flex-col items-center gap-3">
          <InstitutionBrand />
        </div>
        <GlassCard className="flex flex-col items-center gap-4 p-8 text-center" aria-live="polite">
          {state === 'loading' && (
            <>
              <Loader2 size={40} className="animate-spin text-brand" aria-hidden="true" />
              <p className="text-size-14 text-ink-soft">Confirmando…</p>
            </>
          )}
          {state === 'ok' && (
            <>
              <div className="flex h-16 w-16 items-center justify-center rounded-full bg-success-bg">
                <CheckCircle2 size={36} className="text-success" aria-hidden="true" />
              </div>
              <h1 className="font-heading text-lg font-bold text-ink">Confirmación registrada</h1>
              <p className="text-size-13 text-ink-soft">
                Gracias. Ya registramos tu confirmación: la persona menor a tu cargo puede usar el sistema.
              </p>
              <Link to="/login" className="text-size-13 font-semibold text-brand hover:text-accent-2">
                Ir a ingresar
              </Link>
            </>
          )}
          {state === 'error' && (
            <>
              <div className="flex h-16 w-16 items-center justify-center rounded-full bg-danger-bg">
                <XCircle size={36} className="text-danger" aria-hidden="true" />
              </div>
              <h1 className="font-heading text-lg font-bold text-ink">No pudimos confirmar</h1>
              <p role="alert" className="text-size-13 text-ink-soft">
                {errorMessage}
              </p>
              <Link to="/login" className="text-size-13 font-semibold text-brand hover:text-accent-2">
                Ir a ingresar
              </Link>
            </>
          )}
        </GlassCard>
      </div>
    </div>
  );
}
